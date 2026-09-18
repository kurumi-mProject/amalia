package com.my.amali.data.ai

import java.io.ByteArrayOutputStream

/**
 * Нарезка речевого потока на сегменты и упаковка их в WAV.
 *
 * Здесь нет ни микрофона, ни сети — только арифметика над PCM. Это сделано
 * намеренно: вся логика, в которой легко ошибиться (границы сегмента, хвост
 * аудио, склейка промежуточных гипотез), вынесена в чистые функции, которые
 * можно проверить без телефона и без ключей.
 *
 * ## Что попадает в запрос распознавания
 *
 * Whisper — не потоковый движок: он берёт законченный кусок аудио и
 * возвращает текст целиком. Поэтому запросов к нему ровно столько, сколько
 * решено отправить, и каждый обязан нести **достаточный контекст**:
 *
 *  — [extractTail] ограничивает хвост аудио, который отправляется в
 *    промежуточных запросах: без ограничения каждый чанк нёс бы всю фразу
 *    целиком, и расход квоты рос бы квадратично.
 *  — [collapseRepeats] и [mergeTranscript] чинят два вида брака, который
 *    появляется при перекрытии окон: удвоенный хвост фразы и лишние точки
 *    на стыках сегментов.
 *
 * ## Почему WAV, а не сырой PCM
 *
 * Прямой замер показал: `POST` с `.pcm` без контейнера Groq отклоняет
 * (`HTTP 400`), а WAV 8, 16 и 44.1 кГц, mono и stereo, 8 и 16 бит —
 * принимает все (HTTP 200). Сорок четыре байта заголовка стоят дешевле
 * одного сломанного запроса, поэтому аудио всегда заворачивается в WAV.
 */
internal object VoiceSegmenter {

    /** Частота дискретизации всего конвейера; совпадает с VAD и Whisper. */
    const val SAMPLE_RATE = VoiceActivityDetector.SAMPLE_RATE

    /** Сколько сырых сэмплов приходится на миллисекунду записи (16 кГц). */
    private const val SAMPLES_PER_MS = SAMPLE_RATE / 1000

    /** Байт на один сэмпл: PCM 16-bit mono. */
    private const val BYTES_PER_SAMPLE = 2

    /**
     * Сколько миллисекунд тишины означают «человек договорил».
     *
     * 600 мс — выбрано как компромисс между двумя ошибками. Меньше (300 мс)
     * и пауза между двумя предложениями режет фразу на две команды:
     * «включи вайфай… и скинь громкость» обрабатывалось бы как две команды.
     * Больше (1.2 с) — и после каждой фразы приходится ждать, пока
     * ассистент соизволит заметить конец речи.
     */
    const val END_OF_SPEECH_SILENCE_MS = 600

    /**
     * Сколько миллисекунд уже сказанного добавлять к промежуточному куску.
     *
     * Whisper надёжно склеивает два предложения, если слева есть хотя бы
     * слово-два знакомого текста. 350 мс — это примерно одно короткое слово:
     * достаточно для контекста, мало для роста расхода.
     */
    const val CONTEXT_TAIL_MS = 350

    /**
     * Сколько миллисекунд речи отправлять в промежуточном запросе.
     *
     * Промежуточные запросы существуют только ради живых субтитров: человек
     * видит, что его слышат, и не давит на кнопку второй раз. Точность здесь
     * вторична (итоговый текст приходит отдельно), поэтому кусок ограничен —
     * иначе длинная речь упиралась бы в лимит квоты ещё до конца фразы.
     */
    const val PARTIAL_CHUNK_MS = 3_000

    /**
     * Жёсткий потолок длительности записи.
     *
     * Защита от «вечного» микрофона: пока VAD молчит из-за постоянного шума,
     * запись не должна расти бесконечно. 30 секунд — больше любой разумной
     * голосовой команды.
     */
    const val MAX_UTTERANCE_MS = 30_000L

    /**
     * Если человек так и не заговорил — закрываем микрофон.
     *
     * Шесть секунд: человек успевает нажать кнопку, подумать и сказать
     * первую фразу. Дальше уже не «не успел», а «передумал».
     */
    const val NO_SPEECH_TIMEOUT_MS = 6_000L

    /**
     * Сколько миллисекунд аудио оставлять после последнего слова.
     *
     * Последний согласный часто тише и короче остальных, а VAD сглаживает
     * границу в 95 мс. Небольшой «хвост» гарантирует, что «включи» не
     * превратится во «ключи», а «свет» — в «све».
     */
    const val TRAILING_PAD_MS = 250

    /**
     * Хвост аудио после последнего слова, миллисекунды.
     *
     * Возвращается методом, а не только константой: записывающий код
     * сравнивает эту величину с накопленной тишиной, и вызывать константу
     * напрямую значило бы дублировать в двух местах знание о том, что
     * тишина считается в миллисекундах.
     */
    fun trailingPadMs(): Long = TRAILING_PAD_MS.toLong()

    /**
     * Вырезает хвост записи длиной не больше [maxMs] миллисекунд.
     *
     * Нужен промежуточным запросам: они существуют ради субтитров, и нести
     * в них всю фразу целиком незачем — расход квоты рос бы с каждой
     * секундой речи. Хвост всегда содержит самый свежий кусок, потому что
     * именно он интересен человеку на экране.
     */
    fun extractTail(buffer: ShortArray, length: Int, maxMs: Int): ShortArray {
        val maxSamples = maxMs * SAMPLES_PER_MS
        val from = (length - maxSamples).coerceAtLeast(0)
        // Ноль — плотная упаковка без обрезки: получатель работает только с
        // этим массивом, и «висящая» длина ему не нужна.
        return buffer.copyOfRange(from, length)
    }

    /**
     * Склеивает распознанный текст сегментов в одну реплику.
     *
     * Whisper на каждом куске возвращает законченное предложение со своей
     * заглавной буквой и точкой. Если просто склеить их пробелом, получится
     * «Привет. Как дела. Включи свет.». Для голосового ввода естественнее
     * одна строка: точка остаётся только в самом конце.
     *
     * Метод терпим к пустым и повторяющимся кускам: при перекрытии окон
     * Whisper иногда возвращает уже сказанное, и дубль в субтитрах выглядит
     * как сбой приложения.
     */
    fun mergeTranscript(parts: List<String>): String {
        val cleaned = parts
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return ""

        val builder = StringBuilder()
        cleaned.forEach { part ->
            if (builder.isNotEmpty()) {
                // Дубль (перекрытие окон) — молча пропускаем: он уже сказан.
                // Сравниваем по словам, а не по хвосту символов: у точек на
                // стыке предложений `endsWith` не сработает («Привет.» не
                // оканчивается на «Привет. прод», но и «Привет.» не
                // заканчивается на «привет.» из-за точки и регистра).
                val previous = builder.toString()
                val alreadySaid = previous.endsWith(part, ignoreCase = true) ||
                    previous.trimEnd('.', '!', '?', ' ')
                        .endsWith(part.trimEnd('.', '!', '?', ' '), ignoreCase = true)
                if (alreadySaid) return@forEach
                if (part.startsWith(previous, ignoreCase = true) && previous.isNotBlank()) {
                    // Новый кусок содержит всё прежнее целиком — заменяем,
                    // а не дописываем: иначе субтитры «заикаются».
                    builder.setLength(0)
                    builder.append(part)
                    return@forEach
                }
                builder.append(' ')
            }
            // Точка на стыке сегментов заменяется пробелом: Whisper закрывает
            // каждый кусок своим предложением, а нам нужна одна реплика,
            // потому что логические паузы внутри неё уже расставлены VAD.
            builder.append(part.removeSuffix(".").removeSuffix("!").removeSuffix("?"))
        }
        val merged = builder.toString().trim()
        return if (merged.isEmpty()) "" else "$merged."
    }

    /**
     * Убирает повтор последней фразы в тексте.
     *
     * Whisper с подсказкой контекста иногда «дописывает» уже произнесённое:
     * отправленный кусок с префиксом распознаётся целиком вместе с префиксом,
     * и в результат попадает «включи вайфай включи вайфай». Функция ищет
     * такой удвоенный хвост и оставляет одну копию.
     */
    fun collapseRepeats(text: String): String {
        val trimmed = text.trim()
        if (trimmed.length < MIN_REPEAT_LENGTH) return trimmed
        val half = trimmed.length / 2
        for (cut in half downTo MIN_REPEAT_LENGTH / 2) {
            val left = trimmed.substring(0, cut).trim()
            val right = trimmed.substring(trimmed.length - cut).trim()
            if (left.equals(right, ignoreCase = true) && left.length >= MIN_REPEAT_LENGTH / 2) {
                return trimmed.substring(0, trimmed.length - cut).trim()
            }
        }
        return trimmed
    }

    /**
     * Упаковывает PCM 16-bit mono в WAV-контейнер.
     *
     * Заголовок — 44 байта: RIFF/WAVE + fmt (PCM, 1 канал, 16 бит) + data.
     * Слушатель на той стороне обязан знать длину заранее, поэтому `data`
     * чанк заполняется реальным числом байт, а не нулём: с нулевым размером
     * часть серверов читает поток до конца соединения и отвечает таймаутом.
     *
     * Порядок байт — little-endian, как того требует формат и как отдаёт
     * Android через `AudioRecord`.
     */
    fun toWav(samples: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val dataBytes = samples.size * BYTES_PER_SAMPLE
        val out = ByteArrayOutputStream(44 + dataBytes)
        val header = ByteArray(44)
        var offset = 0

        fun writeAscii(value: String) {
            value.forEach { header[offset++] = it.code.toByte() }
        }

        fun writeIntLE(value: Int) {
            header[offset++] = (value and 0xFF).toByte()
            header[offset++] = ((value shr 8) and 0xFF).toByte()
            header[offset++] = ((value shr 16) and 0xFF).toByte()
            header[offset++] = ((value shr 24) and 0xFF).toByte()
        }

        fun writeShortLE(value: Int) {
            header[offset++] = (value and 0xFF).toByte()
            header[offset++] = ((value shr 8) and 0xFF).toByte()
        }

        writeAscii("RIFF")
        writeIntLE(36 + dataBytes)   // размер всего файла минус первые 8 байт
        writeAscii("WAVE")
        writeAscii("fmt ")
        writeIntLE(16)               // размер fmt-блока для PCM
        writeShortLE(1)              // формат сжатия: 1 = PCM без сжатия
        writeShortLE(1)              // каналов: моно
        writeIntLE(sampleRate)
        writeIntLE(sampleRate * BYTES_PER_SAMPLE)   // байт в секунду
        writeShortLE(BYTES_PER_SAMPLE)              // выравнивание блока
        writeShortLE(16)             // бит на сэмпл
        writeAscii("data")
        writeIntLE(dataBytes)

        out.write(header, 0, offset)
        for (sample in samples) {
            out.write(sample.toInt() and 0xFF)
            out.write((sample.toInt() shr 8) and 0xFF)
        }
        return out.toByteArray()
    }

    /**
     * Амплитуда кадра, нормализованная в 0..1 — для анимации волны.
     *
     * Считается по RMS и делится на эмпирическую «громкую речь вблизи
     * микрофона» (8000 из int16), чтобы обычный голос давал 0.3–0.8, а не
     * упирался в единицу.
     */
    fun level(frame: ShortArray): Float {
        if (frame.isEmpty()) return 0f
        var sum = 0.0
        for (sample in frame) {
            val value = sample.toDouble()
            sum += value * value
        }
        val rms = kotlin.math.sqrt(sum / frame.size)
        return (rms / RMS_FULL_SCALE).coerceIn(0.0, 1.0).toFloat()
    }

    /** Минимальная длина строки, для которой ищем удвоение. */
    private const val MIN_REPEAT_LENGTH = 12

    /** RMS, соответствующий уровню 1.0 — громкая речь у микрофона. */
    private const val RMS_FULL_SCALE = 8_000.0
}
