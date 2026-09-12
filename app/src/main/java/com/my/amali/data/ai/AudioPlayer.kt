package com.my.amali.data.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Воспроизводит поток [AudioChunk] (PCM 16-bit mono) через [AudioTrack].
 *
 * ## Почему раньше был треск
 * 1. `play()` вызывался сразу при создании дорожки — до заполнения
 *    минимального буфера. AudioTrack начинал читать пустые байты → белый шум.
 * 2. Размер буфера `sampleRate * 2 * 400 / 1000` некорректен: при 24 000 Гц
 *    это 19 200 байт, но `getMinBufferSize` мог вернуть больше — в итоге
 *    буфер оказывался меньше минимума и система дополняла его мусором.
 * 3. Чанки с нечётным числом байт (обрезанный PCM-16 сэмпл) давали
 *    щелчки на границах.
 *
 * ## Как исправлено
 * - Собираем prefill-буфер (≥ `minBufferSize` байт) перед первым `play()`.
 *   Это убирает треск в начале при сохранении низкой задержки.
 * - Все чанки выравниваются до чётного числа байт; «хвостовой» байт
 *   переносится в следующий чанк.
 * - В конце вместо `stop()` вызываем `stop()` только после того, как
 *   AudioTrack сам дописал буфер (`AudioTrack.PLAYSTATE_STOPPED` ≠ сброс).
 */
class AudioPlayer {

    @Volatile
    private var track: AudioTrack? = null

    suspend fun play(
        chunks: Flow<AudioChunk>,
        onLevel: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        // Поднимаем приоритет треда до URGENT_AUDIO — ОС не будет прерывать нас.
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

        var current: AudioTrack? = null
        var sampleRate = -1
        var minBufSize = 0

        // Остаток от предыдущего чанка с нечётным числом байт.
        var leftover: Byte? = null

        // Prefill: накапливаем байты до старта play() чтобы убрать треск.
        val prefill = mutableListOf<ByteArray>()
        var prefillSize = 0
        var playing = false

        try {
            chunks.collect { chunk ->
                if (chunk.data.isEmpty()) return@collect

                // ── Создаём / пересоздаём AudioTrack при смене sample rate ──
                if (current == null || chunk.sampleRate != sampleRate) {
                    if (current != null) {
                        runCatching { current!!.stop() }
                        runCatching { current!!.release() }
                    }
                    leftover = null
                    prefill.clear()
                    prefillSize = 0
                    playing = false

                    sampleRate = chunk.sampleRate
                    minBufSize = AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                    ).coerceAtLeast(4096)

                    current = buildTrack(sampleRate, minBufSize)
                    track = current
                    // НЕ вызываем play() здесь — ждём накопления prefill.
                }

                // ── Выравниваем чанк по 2 байта (PCM-16) ─────────────────────
                val rawData = if (leftover != null) {
                    // Предыдущий чанк оставил 1 байт — приклеиваем его в начало.
                    ByteArray(1 + chunk.data.size).also { buf ->
                        buf[0] = leftover!!
                        chunk.data.copyInto(buf, destinationOffset = 1)
                    }
                } else {
                    chunk.data
                }

                leftover = if (rawData.size % 2 != 0) rawData[rawData.size - 1] else null
                val evenSize = rawData.size - (rawData.size % 2)
                if (evenSize <= 0) return@collect
                val evenData = if (evenSize == rawData.size) rawData else rawData.copyOf(evenSize)

                onLevel(chunk.level())

                if (!playing) {
                    // Накапливаем prefill.
                    prefill.add(evenData)
                    prefillSize += evenSize

                    if (prefillSize >= minBufSize) {
                        // Достаточно данных — записываем всё накопленное и стартуем.
                        runCatching { current?.play() }
                        playing = true
                        for (buf in prefill) writeAll(current, buf)
                        prefill.clear()
                    }
                } else {
                    writeAll(current, evenData)
                }
            }

            // Если prefill так и не набрался (очень короткий ответ) — играем что есть.
            if (!playing && prefill.isNotEmpty()) {
                runCatching { current?.play() }
                for (buf in prefill) writeAll(current, buf)
                prefill.clear()
            }

            // Даём AudioTrack доиграть остаток внутреннего буфера.
            runCatching { current?.stop() }

        } finally {
            onLevel(0f)
            track = null
            current?.let { done ->
                runCatching { done.release() }
            }
        }
    }

    /** Мгновенно обрывает воспроизведение (кнопка «Стоп»). */
    fun stopImmediately() {
        val active = track ?: return
        runCatching { active.pause() }
        runCatching { active.flush() }
    }

    // Записывает все байты в AudioTrack, обрабатывая частичные write().
    private fun writeAll(t: AudioTrack?, data: ByteArray) {
        if (t == null) return
        var offset = 0
        while (offset < data.size) {
            val written = t.write(data, offset, data.size - offset)
            if (written <= 0) break
            offset += written
        }
    }

    private fun buildTrack(sampleRate: Int, minBufSize: Int): AudioTrack {
        // 3× минимума — перекрывает паузы Fish Audio (~200ms) без большой задержки.
        // Fish Audio делает внутренние паузы до 200ms при генерации,
        // буфер должен их поглощать чтобы не было underrun и треска.
        val bufSize = minBufSize * 3

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
            .build()
    }
}
