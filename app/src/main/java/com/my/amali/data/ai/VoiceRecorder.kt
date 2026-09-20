package com.my.amali.data.ai

import java.io.ByteArrayOutputStream

/**
 * Запись фразы: накопление звука, определение конца речи, упаковка в WAV.
 *
 * Здесь нет ни микрофона, ни сети — только арифметика над PCM и время.
 * Вся логика, в которой легко ошибиться (когда начинать слушать конец
 * фразы, сколько тишины считать концом, что отправлять в запрос), вынесена
 * в один маленький класс, который можно проверить без телефона, без
 * микрофона и без ключей.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ЖИЗНЕННЫЙ ЦИКЛ ОДНОЙ ФРАЗЫ
 * ════════════════════════════════════════════════════════════════════════
 *
 * ```
 * нажали микрофон
 *      │
 *      ├─ 0 … 1000 мс ──«глухая зона»──▶   пишем всё, конец фразы НЕ проверяем
 *      │                                    (человек может кашлянуть, вздохнуть,
 *      │                                     сказать «э-э» — это не конец)
 *      │
 *      ├─ 1000 мс … ──▶                    тишина 600 мс = конец фразы
 *      │
 *      └─ 20 с ──▶                          жёсткий стоп, дальше не пишем
 * ```
 *
 * Зачем «глухая зона»: между нажатием и первым словом человек делает вдох.
 * Микрофон слышит его как короткий всплеск, и без задержки детектор решил бы,
 * что фраза сказана и закончилась, — за миллисекунду до того, как человек
 * вообще открыл рот. Секунда нужна именно для этого: за неё дыхание, щелчок
 * и «э-э» успевают пройти, а настоящее молчание уже не наступает, потому
 * что человек начинает говорить.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ЧТО УХОДИТ В ЗАПРОС
 * ════════════════════════════════════════════════════════════════════════
 *
 * Весь буфер целиком, от первого до последнего сэмпла. Никаких обрезаний
 * «только речь» и склеек из кусков: Whisper обучен на непрерывной записи,
 * и паузы внутри фразы дают ему естественный ритм речи. Обрезка по
 * громкости, наоборот, рвёт слова на слогах и превращает «включи» в «ключи».
 *
 * Тишина в начале и конце фразы при этом стоит копейки: 20 секунд аудио —
 * это 640 КБ и 20 секунд из 7200 бесплатных в сутки, то есть 360 фраз
 * в день даже при непрерывной речи.
 */
internal class VoiceRecorder(
    private val maxUtteranceMs: Long = 20_000L,
    private val silenceMs: Long = END_SILENCE_MS,
) {

    /** Сколько уже записано, в сэмплах. */
    var length: Int = 0
        private set

    /** Общий размер записи в миллисекундах — для индикации и логов. */
    var durationMs: Long = 0
        private set

    /** Сколько миллисекунд тишины накопилось с последнего речевого кадра. */
    var silentForMs: Long = 0
        private set

    /** Была ли во фразе речь. Если нет — отправлять нечего. */
    var hasSpeech: Boolean = false
        private set

    /** Пришло ли время проверять конец фразы (после «глухой зоны»). */
    var endWatchActive: Boolean = false
        private set

    private val capacity: Int = (maxUtteranceMs * VoiceAudio.SAMPLE_RATE / 1000).toInt()
    private val buffer = ShortArray(capacity.coerceAtLeast(1))

    /**
     * Добавляет кадр в запись.
     *
     * @param frame PCM 16-bit mono.
     * @param isSpeech вердикт детектора громкости для этого кадра.
     * @param frameMs длительность кадра.
     * @return true, если пора перестать слушать и отправить запись.
     */
    fun accept(frame: ShortArray, isSpeech: Boolean, frameMs: Long): Boolean {
        write(frame)
        durationMs += frameMs

        endWatchActive = durationMs >= START_GUARD_MS

        if (isSpeech) {
            hasSpeech = true
            silentForMs = 0
        } else if (endWatchActive) {
            // Считаем тишину ТОЛЬКО в «глухой зоне» после первого слова.
            // До неё тишина — это ещё не «человек договорил», а «человек
            // ещё не начал», и она обрабатывается отдельным таймаутом.
            silentForMs += frameMs
        }

        if (durationMs >= maxUtteranceMs) return true
        return hasSpeech && endWatchActive && silentForMs >= silenceMs
    }

    /** Копия записанного — ровно то, что уйдёт в распознавание. */
    fun samples(): ShortArray = buffer.copyOf(length)

    /** Упакованный WAV с той же самой записью. */
    fun toWav(): ByteArray = VoiceAudio.toWav(samples())

    private fun write(frame: ShortArray) {
        val room = capacity - length
        if (room <= 0) return
        val count = minOf(room, frame.size)
        System.arraycopy(frame, 0, buffer, length, count)
        length += count
    }

    companion object {
        /**
         * «Глухая зона» в начале записи, миллисекунды.
         *
         * Секунда — время между нажатием кнопки и первым словом. В ней
         * человек делает вдох, микрофон ловит щелчок, а детектор громкости
         * получает короткий всплеск. Пока зона активна, конец фразы не
         * проверяется вообще: даже если детектор уверенно говорит «была
         * речь, потом тихо», это не конец фразы, а ещё не начатая фраза.
         */
        const val START_GUARD_MS = 1_000L

        /**
         * Тишина, означающая «человек договорил», миллисекунды.
         *
         * 600 мс — компромисс между двумя ошибками. Меньше (300 мс) и пауза
         * между двумя предложениями режет одну фразу на две команды:
         * «включи вайфай… и скинь громкость» обрабатывалось бы дважды.
         * Больше (1 секунда) и после каждой фразы приходится ждать, пока
         * ассистент соизволит заметить, что речь закончилась.
         */
        const val END_SILENCE_MS = 600L
    }
}

/**
 * Арифметика над звуком: громкость и контейнер WAV.
 *
 * Отдельный объект без состояния, потому что обе функции нужны в разных
 * местах: громкость — для детектора и анимации волны, WAV — только на
 * отправке. Держать их вместе стоит потому, что обе описывают одно и то же
 * представление звука: PCM 16 бит, моно, 16 кГц.
 */
internal object VoiceAudio {

    /**
     * Частота дискретизации всего конвейера.
     *
     * 16 кГц — требование Whisper: модель обучена на этом качестве и,
     * получив 48 кГц, «услышит» речь в два раза медленнее. Наговорено это
     * в одном месте поэтому: микрофон, VAD, упаковка WAV и HTTP-запрос
     * обязаны совпадать до герца.
     */
    const val SAMPLE_RATE = 16_000

    /**
     * Длина кадра, которым читается микрофон.
     *
     * 512 сэмплов = 32 мс при 16 кГц. Это привычный размер окна для
     * голосовых задач: достаточно мелко, чтобы заметить паузу с точностью
     * до трети сотни миллисекунд, и достаточно крупно, чтобы не крутить
     * цикл по десять раз за миллисекунду.
     */
    const val FRAME_SAMPLES = 512

    /**
     * Длительность кадра в миллисекундах.
     *
     * Считается из размера окна и частоты, а не вписана числом: если окно
     * когда-нибудь изменится, пороги тишины поедут вместе с ним.
     */
    const val FRAME_MS = FRAME_SAMPLES * 1000 / SAMPLE_RATE

    /** Байт на один сэмпл: PCM 16-bit mono. */
    private const val BYTES_PER_SAMPLE = 2

    /** RMS, соответствующий уровню 1.0 — громкая речь у микрофона. */
    private const val RMS_FULL_SCALE = 8_000.0

    /**
     * Громкость кадра, нормализованная в 0..1.
     *
     * Делится на эмпирические 8000 из 32767 — «громкая речь вблизи
     * микрофона». Обычный голос даёт 0.3–0.8, шёпот — 0.05–0.15, тишина —
     * меньше 0.02. В таких числах удобно и рисовать волну, и сравнивать
     * кадры между собой.
     */
    fun level(frame: ShortArray): Float {
        if (frame.isEmpty()) return 0f
        return (rms(frame) / RMS_FULL_SCALE).coerceIn(0.0, 1.0).toFloat()
    }

    /** Среднеквадратичная амплитуда кадра: «насколько громко» в шкале int16. */
    fun rms(frame: ShortArray): Double {
        if (frame.isEmpty()) return 0.0
        var sum = 0.0
        for (sample in frame) {
            val value = sample.toDouble()
            sum += value * value
        }
        return kotlin.math.sqrt(sum / frame.size)
    }

    /**
     * Упаковывает PCM 16-bit mono в WAV.
     *
     * Заголовок — 44 байта: RIFF/WAVE + fmt (PCM, 1 канал, 16 бит) + data.
     * Размер `data` заполняется реальным числом байт, а не нулём: с нулевым
     * размером часть серверов читает поток до конца соединения и отвечает
     * таймаутом вместо текста.
     *
     * Контейнер обязателен, а не «на всякий случай»: прямой замер показал,
     * что сырой PCM без заголовка Groq отклоняет с `HTTP 400`, а WAV
     * принимает в любом разумном виде (8/16/44.1 кГц, моно и стерео).
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
        writeIntLE(36 + dataBytes)   // размер файла минус первые 8 байт
        writeAscii("WAVE")
        writeAscii("fmt ")
        writeIntLE(16)               // размер fmt-блока для PCM
        writeShortLE(1)              // сжатие: 1 = PCM
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
}
