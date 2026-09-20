package com.my.amali.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Клиент распознавания речи: один HTTP-запрос на один кусок аудио.
 *
 * ## Почему это обычный запрос, а не веб-сокет
 *
 * Предыдущая реализация держала открытое соединение на весь разговор.
 * Плюс — текст появлялся по мере говорения. Минусы оказались дороже:
 *
 *  1. Разговор не начинался, пока не установится соединение. Холодный
 *     старт сокета (TCP + TLS + рукопожатие сервиса) — это те самые
 *     «задержка после нажатия» и «проблема со связью», которые видел
 *     пользователь: соединение не успевало открыться до первых слов.
 *  2. Любой обрыв связи на середине фразы означал потерю всего: сервер не
 *     получал остаток, и финальный текст не приходил вообще.
 *  3. Ключ жил ещё в одном месте, у ещё одного провайдера, со своим счётом.
 *
 * Обычный запрос этих проблем не имеет: он не требует времени на подготовку,
 * его можно повторить, и он живёт на том же ключе Groq, что и мозг.
 *
 * ## Что показал живой замер
 *
 *  — `whisper-large-v3-turbo`: 0.17–0.22 с на секунду русской речи;
 *  — `whisper-large-v3`: 0.33 с (вдвое медленнее, точнее на длинных фразах);
 *  — лимит бесплатного тарифа: 2000 запросов и 7200 секунд аудио в сутки.
 *
 * Отсюда всё устройство движка: короткие сегменты, редкие промежуточные
 * запросы, жёсткий потолок длительности — чтобы 7200 секунд хватило на
 * месяцы нормального использования, а не на один разговор.
 */
internal class GroqSttClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Распознаёт один WAV-кусок.
     *
     * @param wav готовый WAV: 16 кГц, mono, 16 бит (см. [VoiceAudio.toWav]).
     * @param languageCode код языка для подсказки модели; пусто — определить самому.
     * @param model идентификатор модели Whisper; пусто → рекомендованная.
     * @param prompt подсказка со словами, которые модель иначе пишет неверно:
     *   имена устройств, команды, привычные пользователю названия приложений.
     * @param apiKey ключ; пустой означает «нет ключа» и приводит к понятной ошибке.
     * @return распознанный текст (может быть пустым, если в куске не было речи).
     */
    suspend fun transcribe(
        wav: ByteArray,
        languageCode: String,
        model: String,
        apiKey: String,
        prompt: String? = null,
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw EngineException(ERROR_KEY_MISSING)
        }
        if (wav.size <= WAV_HEADER_BYTES) {
            // Пустой кусок отправлять нечего: сервер ответит ошибкой,
            // а пользователь получит «распознавание недоступно» вместо
            // честного «ничего не расслышала».
            return@withContext ""
        }

        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name = "file",
                filename = FILE_NAME,
                body = wav.toRequestBody(AUDIO_MEDIA_TYPE),
            )
            .addFormDataPart("model", model.ifBlank { DEFAULT_MODEL })
            .addFormDataPart("response_format", "json")
            .addFormDataPart("temperature", "0")
        if (languageCode.isNotBlank()) {
            bodyBuilder.addFormDataPart("language", languageCode)
        }
        if (!prompt.isNullOrBlank()) {
            // Подсказка работает как «словарь ожидаемых слов»: с ней модель
            // пишет «вайфай», а не «вай-фай», и не переводит имена приложений.
            bodyBuilder.addFormDataPart("prompt", prompt.take(PROMPT_MAX_CHARS))
        }

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .post(bodyBuilder.build())
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw EngineException(ERROR_NETWORK, e)
        }

        response.use { result ->
            val raw = result.body?.string().orEmpty()
            if (!result.isSuccessful) {
                throw EngineException(describeFailure(result.code, raw))
            }
            runCatching { JSONObject(raw).optString("text", "") }
                .getOrDefault("")
                .trim()
        }
    }

    /** Освобождает соединения: движок закрывается при смене ключей. */
    fun shutdown() {
        runCatching { client.dispatcher.executorService.shutdown() }
        runCatching { client.connectionPool.evictAll() }
    }

    /**
     * Человеческое объяснение отказа вместо кода состояния.
     *
     * Каждая ветка отвечает на вопрос «что мне сделать», а не «что случилось»:
     * ключ — заменить, лимит — подождать, формат — сообщить о баге.
     */
    private fun describeFailure(code: Int, body: String): String = when (code) {
        401, 403 -> ERROR_KEY_REJECTED
        413 -> ERROR_TOO_LONG
        429 -> ERROR_RATE_LIMIT
        in 500..599 -> ERROR_SERVER
        400 -> {
            val detail = runCatching {
                JSONObject(body).optJSONObject("error")?.optString("message")
            }.getOrNull()
            if (detail.isNullOrBlank()) ERROR_BAD_REQUEST else "$ERROR_BAD_REQUEST ($detail)"
        }
        else -> "$ERROR_OTHER (HTTP $code)"
    }

    private companion object {
        const val ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
        const val DEFAULT_MODEL = "whisper-large-v3-turbo"
        const val FILE_NAME = "speech.wav"

        val AUDIO_MEDIA_TYPE = "audio/wav".toMediaType()

        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val READ_TIMEOUT_SECONDS = 30L
        const val WRITE_TIMEOUT_SECONDS = 30L

        /** Размер WAV-заголовка: кусок меньше него — это пустая запись. */
        const val WAV_HEADER_BYTES = 44

        /**
         * Потолок подсказки. Whisper принимает до 224 токенов, но длинная
         * подсказка начинает «подсказывать» лишнее — модель дописывает
         * фразы, которых не было. Список слов короткий и по делу.
         */
        const val PROMPT_MAX_CHARS = 400

        const val ERROR_KEY_MISSING =
            "Нет ключа для распознавания речи. Впиши ключ Groq в настройках «API и модели»."
        const val ERROR_KEY_REJECTED =
            "Ключ Groq отклонён — распознавание речи недоступно. Проверь ключ в настройках."
        const val ERROR_RATE_LIMIT =
            "Лимит распознавания на сегодня исчерпан. Попробуй позже или впиши свой ключ Groq."
        const val ERROR_TOO_LONG = "Слишком длинная запись для распознавания. Скажи короче."
        const val ERROR_NETWORK = "Нет связи с сервисом распознавания речи."
        const val ERROR_SERVER = "Сервис распознавания речи временно недоступен."
        const val ERROR_BAD_REQUEST = "Запись не принята сервисом распознавания."
        const val ERROR_OTHER = "Распознавание речи недоступно."
    }
}
