package com.my.amali.data.ai

import com.my.amali.data.model.ChatMessage
import com.my.amali.domain.entity.AppLanguage
import com.my.amali.domain.entity.UserSettings
import kotlinx.coroutines.flow.Flow
import java.util.Locale

/**
 * Параметры работы движков: язык распознавания/синтеза и характеристики речи.
 * Строятся из [UserSettings], поэтому изменение настроек мгновенно влияет
 * на следующий запрос без пересоздания движков.
 *
 * @property languageCode двухбуквенный код языка ("ru", "en", …) — уже разрешённый,
 *   то есть [AppLanguage.SYSTEM] заменён на язык устройства.
 * @property speechRate множитель скорости синтеза, [0.5, 2.0].
 * @property speechPitch множитель высоты голоса, [0.5, 2.0].
 */
data class EngineOptions(
    val languageCode: String = "ru",
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
) {
    /** Человекочитаемое имя языка для системного промпта LLM. */
    val languageName: String
        get() = LANGUAGE_NAMES[languageCode] ?: "русском"

    companion object {
        val Default: EngineOptions = EngineOptions()

        private val LANGUAGE_NAMES = mapOf(
            "ru" to "русском",
            "en" to "английском",
            "es" to "испанском",
            "ar" to "арабском",
            "de" to "немецком",
            "fr" to "французском",
            "hi" to "хинди",
            "ja" to "японском",
            "zh" to "китайском",
        )

        /** Языки, поддерживаемые моделью Deepgram nova-2. */
        private val SUPPORTED_STT = setOf("ru", "en", "es", "de", "fr", "hi", "ja", "zh")

        /** Строит параметры из пользовательских настроек. */
        fun from(settings: UserSettings): EngineOptions = EngineOptions(
            languageCode = resolveLanguage(settings.selectedLanguage),
            speechRate = settings.speechRate.coerceIn(0.5f, 2f),
            speechPitch = settings.speechPitch.coerceIn(0.5f, 2f),
        )

        private fun resolveLanguage(language: AppLanguage): String {
            val code = if (language.isSystem) {
                Locale.getDefault().language.lowercase(Locale.ROOT)
            } else {
                language.code
            }
            return if (code in SUPPORTED_STT) code else "ru"
        }
    }
}

/**
 * Событие движка распознавания речи.
 *
 * Порядок для одной фразы: серия [Level] и [Partial] → один [Final] на
 * завершённый сегмент → завершение потока. Долгая речь даёт несколько
 * [Final]: их нужно склеивать, а не заменять.
 */
sealed interface SttEvent {
    /** Мгновенная громкость микрофона, 0..1 — для анимации волны. */
    data class Level(val level: Float) : SttEvent

    /** Промежуточная (неточная) гипотеза распознавания. */
    data class Partial(val text: String) : SttEvent

    /** Финальный текст очередного сегмента речи. */
    data class Final(val text: String) : SttEvent
}

/**
 * Контракт движка распознавания речи. Реализация сама владеет микрофоном
 * и закрывает поток, когда пользователь закончил говорить.
 */
interface SpeechToTextEngine {
    /** Готовит ресурсы движка. */
    suspend fun initialize()

    /** Освобождает ресурсы. Безопасно вызывать повторно. */
    suspend fun close()

    /**
     * Заранее открывает WS соединение без микрофона — warmup при касании кнопки.
     * Вызывается в момент onPress, до onClick (~150-300ms раньше).
     * По умолчанию ничего не делает — реализуется только в реальном движке.
     */
    suspend fun preconnect() {}

    /**
     * Открывает микрофон и стримит события распознавания.
     * Поток завершается сам после окончания фразы (VAD) либо по отмене корутины.
     */
    fun transcribe(options: EngineOptions = EngineOptions.Default): Flow<SttEvent>
}

/**
 * Контракт движка синтеза речи.
 *
 * WebSocket-режим: движок держит открытое соединение и принимает токены
 * по одному через [sendToken]/[flush]/[stop] вместо одного вызова [speak].
 * Это позволяет Fish Audio начать генерацию аудио пока LLM ещё говорит.
 */
interface TextToSpeechEngine {
    suspend fun initialize()
    suspend fun close()

    /** Синтезирует [text] целиком (используется для коротких фраз). */
    fun speak(text: String, options: EngineOptions = EngineOptions.Default): Flow<AudioChunk>

    /** Открывает сессию стриминга — вызвать перед первым [sendToken]. */
    suspend fun startStreaming(options: EngineOptions = EngineOptions.Default) {}

    /** Отправляет один токен LLM в открытую сессию. */
    suspend fun sendToken(token: String) {}

    /** Форсирует синтез накопленного текста. */
    suspend fun flushStreaming() {}

    /** Завершает сессию и закрывает соединение. */
    suspend fun stopStreaming() {}

    /** Поток аудио чанков из стриминговой сессии. */
    val streamingAudio: Flow<AudioChunk> get() = kotlinx.coroutines.flow.emptyFlow()
}

/**
 * Контракт языковой модели: запрос + история → поток текстовых дельт.
 */
interface LanguageModel {
    suspend fun initialize()
    suspend fun close()

    /**
     * Генерирует ответ на [prompt] с учётом [history] (старые сообщения первыми),
     * эмитя текст инкрементально.
     */
    fun generateResponse(
        prompt: String,
        history: List<ChatMessage>,
        options: EngineOptions = EngineOptions.Default,
    ): Flow<String>
}

/**
 * Единица синтезированного аудио.
 *
 * @property data PCM 16-bit mono little-endian.
 * @property sampleRate частота дискретизации [data], Гц.
 */
data class AudioChunk(val data: ByteArray, val sampleRate: Int) {
    /** Количество сэмплов в чанке. */
    val sampleCount: Int
        get() = data.size / 2

    /** Длительность чанка в миллисекундах. */
    val durationMs: Int
        get() = if (sampleRate <= 0) 0 else (sampleCount * 1000) / sampleRate

    /** Нормализованная громкость чанка 0..1 — используется для анимации. */
    fun level(): Float {
        if (sampleCount == 0) return 0f
        var sum = 0.0
        val step = if (sampleCount > 256) sampleCount / 256 else 1
        var counted = 0
        var i = 0
        while (i < sampleCount) {
            val low = data[i * 2].toInt() and 0xFF
            val high = data[i * 2 + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toInt()
            sum += sample.toDouble() * sample.toDouble()
            counted++
            i += step
        }
        if (counted == 0) return 0f
        val rms = Math.sqrt(sum / counted)
        return (rms / 8000.0).coerceIn(0.0, 1.0).toFloat()
    }

    override fun equals(other: Any?): Boolean =
        other is AudioChunk && other.sampleRate == sampleRate && other.data.contentEquals(data)

    override fun hashCode(): Int = 31 * data.contentHashCode() + sampleRate

    override fun toString(): String =
        "AudioChunk(bytes=${data.size}, sampleRate=$sampleRate, durationMs=$durationMs)"
}

/**
 * Описание подключённых движков — для экрана настроек и диагностики.
 */
data class AIConfig(
    val sttEngineName: String,
    val ttsEngineName: String,
    val llmEngineName: String,
) {
    fun describe(): String = "STT=$sttEngineName, TTS=$ttsEngineName, LLM=$llmEngineName"

    companion object {
        /** Реальный продакшен-конвейер. */
        val Live: AIConfig = AIConfig(
            sttEngineName = "Deepgram nova-2",
            ttsEngineName = "Fish Audio s2.1-pro",
            llmEngineName = "Groq gpt-oss-20b",
        )

        /** Встроенный офлайн-конвейер-заглушка (используется в превью и тестах). */
        val Mock: AIConfig = AIConfig(
            sttEngineName = "MockSpeechToTextEngine",
            ttsEngineName = "MockTextToSpeechEngine",
            llmEngineName = "MockLanguageModel",
        )
    }
}

/** Исключение движка с текстом, пригодным для показа пользователю. */
class EngineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
