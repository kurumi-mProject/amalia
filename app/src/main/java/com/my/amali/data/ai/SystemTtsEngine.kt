package com.my.amali.data.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import java.util.UUID

/**
 * Запасной движок синтеза речи — системный [TextToSpeech] Android.
 *
 * ═══════════════════════════════════════════════════════════
 *  ЗАЧЕМ ОН НУЖЕН
 * ═══════════════════════════════════════════════════════════
 *
 * Облачный синтез (Fish Audio) зависит от двух вещей, которыми приложение
 * не управляет: баланса аккаунта и очереди на стороне сервиса. На практике
 * это выглядит так:
 *
 *  — при нулевом балансе платная модель отвечает `402 Payment Required`
 *    мгновенно, и ассистент замолкает целиком;
 *  — бесплатная модель синтезирует **одну короткую фразу около 38 секунд**.
 *
 * В обоих случаях пользователь не слышит ответ, хотя модель уже всё
 * сгенерировала и текст лежит в карточке. Это и есть «ассистент не говорит».
 *
 * Системный TTS установлен на каждом устройстве, работает офлайн, не требует
 * ни ключей, ни баланса, и отвечает за ~0.3–1 с. Поэтому он используется как
 * страховка: см. [ResilientTtsEngine], который сам решает, когда переключиться.
 *
 * ═══════════════════════════════════════════════════════════
 *  КАК ЭТО РАБОТАЕТ
 * ═══════════════════════════════════════════════════════════
 *
 * Контракт движка — поток сырых PCM-чанков, а системный API умеет только
 * «синтезировать в файл». Поэтому:
 *
 *  1. текст синтезируется в WAV во временный файл кэша;
 *  2. WAV разбирается вручную (заголовок RIFF → `fmt ` → `data`);
 *  3. из чанка `data` читаются PCM-чанки по [CHUNK_BYTES] и уходят наружу
 *     в том же виде, в каком их отдаёт облачный движок;
 *  4. временный файл удаляется в `finally` — кэш не растёт.
 *
 * Если система синтезирует стерео, каналы сводятся в моно: проигрыватель
 * ([AudioPlayer]) строит дорожку как 16-бит моно, и стерео-поток звучал бы
 * вдвое быстрее и с эхом.
 *
 * @param context любой контекст; внутри берётся `applicationContext`.
 */
class SystemTtsEngine(private val context: Context) : TextToSpeechEngine {

    private val appContext = context.applicationContext

    /** Созданный системный движок; null — ещё не инициализирован. */
    @Volatile
    private var tts: TextToSpeech? = null

    /**
     * Одноразовый сигнал «движок готов» от [TextToSpeech.OnInitListener].
     *
     * Пересоздаётся на каждую инициализацию: после [close] следующий
     * [initialize] обязан ждать готовности НОВОГО движка, а не мгновенно
     * получать результат предыдущего.
     */
    private var initSignal = CompletableDeferred<Boolean>()

    override suspend fun initialize() {
        if (tts != null) return

        // TextToSpeech поднимает сервис и зовёт OnInitListener на главном
        // потоке, поэтому создаём его именно там.
        withContext(Dispatchers.Main.immediate) {
            if (tts == null) {
                initSignal = CompletableDeferred()
                tts = TextToSpeech(appContext) { status ->
                    initSignal.complete(status == TextToSpeech.SUCCESS)
                }
            }
        }

        val ok = withTimeoutOrNull(INIT_TIMEOUT_MS) { initSignal.await() } ?: false
        if (!ok) {
            throw EngineException("Системный синтез речи недоступен на этом устройстве.")
        }
    }

    override suspend fun close() {
        withContext(Dispatchers.Main.immediate) {
            runCatching { tts?.stop() }
            runCatching { tts?.shutdown() }
            tts = null
        }
    }

    /**
     * Синтезирует [text] системным голосом и отдаёт PCM-чанки.
     *
     * Поток завершается сам, когда файл дочитан до конца.
     */
    override fun speak(text: String, options: EngineOptions): Flow<AudioChunk> = flow {
        val clean = text.trim()
        if (clean.isEmpty()) return@flow

        initialize()
        val engine = tts
            ?: throw EngineException("Системный синтез речи не инициализирован.")

        applyVoice(engine, options)

        val utteranceId = "amalia-${UUID.randomUUID()}"
        val file = File(appContext.cacheDir, "$utteranceId.wav")
        val done = CompletableDeferred<Unit>()

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit

            override fun onDone(id: String?) {
                done.complete(Unit)
            }

            @Deprecated("Базовый контракт Android; движки зовут его при сбое.")
            override fun onError(id: String?) {
                done.completeExceptionally(
                    EngineException("Системный синтез не смог озвучить текст."),
                )
            }

            override fun onError(id: String?, errorCode: Int) {
                done.completeExceptionally(
                    EngineException("Системный синтез не смог озвучить текст (код $errorCode)."),
                )
            }
        })

        try {
            val started = engine.synthesizeToFile(clean, null, file, utteranceId)
            if (started == TextToSpeech.ERROR) {
                throw EngineException("Системный синтез речи отклонил запрос.")
            }

            // Часть прошивок не зовёт onDone вовсе — ждём с потолком,
            // иначе корутина повисла бы навсегда.
            val finished = withTimeoutOrNull(SYNTHESIS_TIMEOUT_MS) { done.await() }
            if (finished == null && !done.isCompleted) {
                throw EngineException("Системный синтез не ответил вовремя.")
            }
            if (done.isCancelled) {
                throw EngineException("Системный синтез не смог озвучить текст.")
            }

            streamWav(file) { chunk -> emit(chunk) }
        } finally {
            // Кэш не должен расти: файл нужен только на время стрима.
            runCatching { file.delete() }
        }
    }.flowOn(Dispatchers.IO)

    /** Язык, скорость и тон речи. Неподдерживаемый язык — не ошибка. */
    private fun applyVoice(engine: TextToSpeech, options: EngineOptions) {
        val requested = runCatching { Locale.forLanguageTag(options.languageCode) }
            .getOrNull()
            ?: Locale.getDefault()

        val available = runCatching { engine.isLanguageAvailable(requested) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)

        val locale = when (available) {
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE,
            -> requested
            // Языка нет — берём системный, а не молчим: смысл важнее акцента.
            else -> Locale.getDefault()
        }

        runCatching { engine.setLanguage(locale) }
        runCatching { engine.setSpeechRate(options.speechRate.coerceIn(0.5f, 2f)) }
        runCatching { engine.setPitch(options.speechPitch.coerceIn(0.5f, 2f)) }
    }

    /**
     * Читает WAV-файл и отдаёт его PCM-содержимое чанками.
     *
     * Разбор ручной, потому что файл пишет системный движок, а не мы: он
     * вправе добавить свои чанки (`LIST`, `fact`), и стандартные 44 байта
     * в этом случае уже не работают — сдвиг начала `data` определяется
     * только последовательным разбором заголовков.
     */
    private suspend fun streamWav(file: File, emitChunk: suspend (AudioChunk) -> Unit) {
        if (!file.exists() || file.length() <= WAV_HEADER_MIN_BYTES) {
            throw EngineException("Системный синтез вернул пустой звук.")
        }

        DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            val riff = ByteArray(4).also { input.readFully(it) }
            if (String(riff, Charsets.US_ASCII) != "RIFF") {
                throw EngineException("Системный синтез вернул неизвестный формат звука.")
            }
            input.skipExactly(4) // размер файла — не нужен
            val wave = ByteArray(4).also { input.readFully(it) }
            if (String(wave, Charsets.US_ASCII) != "WAVE") {
                throw EngineException("Системный синтез вернул неизвестный формат звука.")
            }

            var sampleRate = FALLBACK_SAMPLE_RATE
            var channels = 1
            var bitsPerSample = 16

            while (true) {
                val idBytes = ByteArray(4)
                val read = input.read(idBytes)
                if (read < 4) break
                val chunkId = String(idBytes, Charsets.US_ASCII)
                val chunkSize = input.readIntLe()

                when (chunkId) {
                    "fmt " -> {
                        /* audioFormat */ input.readShortLe()
                        channels = input.readShortLe().coerceAtLeast(1)
                        sampleRate = input.readIntLe().takeIf { it > 0 } ?: FALLBACK_SAMPLE_RATE
                        input.skipExactly(6) // byteRate + blockAlign
                        bitsPerSample = input.readShortLe()
                        val rest = chunkSize - 16
                        if (rest > 0) input.skipExactly(rest.toLong())
                    }

                    "data" -> {
                        if (bitsPerSample != 16) {
                            throw EngineException("Системный синтез вернул звук в неподдерживаемом формате.")
                        }
                        streamPcm(
                            input = input,
                            available = chunkSize.toLong(),
                            channels = channels,
                            sampleRate = sampleRate,
                            emitChunk = emitChunk,
                        )
                        return
                    }

                    else -> input.skipExactly(chunkSize.toLong())
                }
            }
        }
    }

    /** Отдаёт PCM-чанки из чанка `data`, при необходимости сводя стерео в моно. */
    private suspend fun streamPcm(
        input: DataInputStream,
        available: Long,
        channels: Int,
        sampleRate: Int,
        emitChunk: suspend (AudioChunk) -> Unit,
    ) {
        val frameBytes = 2 * channels
        val framesPerChunk = CHUNK_BYTES / frameBytes
        val chunkBytes = (framesPerChunk * frameBytes).coerceAtLeast(frameBytes)
        val buffer = ByteArray(chunkBytes)
        var remaining = available

        while (remaining >= frameBytes) {
            val toRead = minOf(chunkBytes.toLong(), remaining - (remaining % frameBytes)).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read <= 0) break
            remaining -= read.toLong()

            // Обрезаем хвост, не добивший кадр, чтобы не сломать кадрирование.
            val usable = read - (read % frameBytes)
            if (usable <= 0) continue

            val pcm = if (channels == 1) {
                if (usable == buffer.size) buffer.copyOf() else buffer.copyOf(usable)
            } else {
                downmixToMono(buffer, usable, channels)
            }
            emitChunk(AudioChunk(data = pcm, sampleRate = sampleRate))
        }
    }

    /**
     * Сводит многоканальный PCM-16 в моно: усредняет каналы одного кадра.
     *
     * Проигрыватель строит моно-дорожку, поэтому стерео-поток без этого шага
     * звучал бы вдвое быстрее (кадры читались бы как удвоенное число сэмплов)
     * и с эхом.
     */
    private fun downmixToMono(source: ByteArray, length: Int, channels: Int): ByteArray {
        val frames = length / (2 * channels)
        val out = ByteArray(frames * 2)
        var frame = 0
        while (frame < frames) {
            var sum = 0
            var channel = 0
            while (channel < channels) {
                val index = (frame * channels + channel) * 2
                val low = source[index].toInt() and 0xFF
                val high = source[index + 1].toInt()
                sum += ((high shl 8) or low).toShort().toInt()
                channel++
            }
            val mixed = (sum / channels).toShort()
            out[frame * 2] = (mixed.toInt() and 0xFF).toByte()
            out[frame * 2 + 1] = ((mixed.toInt() shr 8) and 0xFF).toByte()
            frame++
        }
        return out
    }

    private companion object {
        /** ~20 мс звука на чанк — тот же шаг, что у облачного движка. */
        const val CHUNK_BYTES = 960

        /** Если движок не ответил — не ждём больше: озвучка не стоит паузы. */
        const val SYNTHESIS_TIMEOUT_MS = 15_000L

        /** Инициализация системного TTS на холодном старте. */
        const val INIT_TIMEOUT_MS = 6_000L

        /** Частота по умолчанию, если `fmt `-чанк её не сообщил. */
        const val FALLBACK_SAMPLE_RATE = 24_000

        /** Минимальный валидный WAV: RIFF(12) + fmt (24) + data(8). */
        const val WAV_HEADER_MIN_BYTES = 44L
    }
}

/** Пропускает ровно [count] байт или кидает исключение. */
private fun DataInputStream.skipExactly(count: Long) {
    var left = count
    while (left > 0) {
        val step = left.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val skipped = skipBytes(step)
        if (skipped <= 0) {
            // Поток мог закончиться — дальше разбор всё равно бессмыслен.
            throw EngineException("Повреждённый звуковой файл системного синтеза.")
        }
        left -= skipped
    }
}

/** Little-endian int32 — так записан WAV. */
private fun DataInputStream.readIntLe(): Int {
    val b0 = read() and 0xFF
    val b1 = read() and 0xFF
    val b2 = read() and 0xFF
    val b3 = read() and 0xFF
    return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
}

/** Little-endian int16 — так записан WAV. */
private fun DataInputStream.readShortLe(): Int {
    val b0 = read() and 0xFF
    val b1 = read() and 0xFF
    return b0 or (b1 shl 8)
}
