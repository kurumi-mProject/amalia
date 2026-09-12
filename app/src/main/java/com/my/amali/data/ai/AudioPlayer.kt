package com.my.amali.data.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ArrayBlockingQueue

/**
 * Воспроизводит поток [AudioChunk] (PCM 16-bit mono) через [AudioTrack].
 *
 * Producer/Consumer через [ArrayBlockingQueue]:
 * - Producer корутина читает из Flow (HTTP сеть) и кладёт чанки в очередь
 * - Consumer (основной тред) тянет из очереди и пишет в AudioTrack
 *
 * Это отвязывает скорость сети от скорости воспроизведения.
 * Паузы Fish Audio (~500ms) поглощаются очередью — AudioTrack не голодает.
 */
class AudioPlayer {

    @Volatile
    private var track: AudioTrack? = null

    // Sentinel — маркер конца стрима
    private val END_OF_STREAM = AudioChunk(ByteArray(0), -1)

    suspend fun play(
        chunks: Flow<AudioChunk>,
        onLevel: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

        // Очередь до ~1.5 сек аудио при 24кГц (72000 байт / 4096 ≈ 18 чанков)
        val queue = ArrayBlockingQueue<AudioChunk>(64)

        coroutineScope {
            // Producer: читает из Flow и кладёт в очередь
            val producerJob = launch(Dispatchers.IO) {
                var leftover: Byte? = null
                try {
                    chunks.collect { chunk ->
                        if (chunk.data.isEmpty()) return@collect

                        val rawData = if (leftover != null) {
                            ByteArray(1 + chunk.data.size).also { buf ->
                                buf[0] = leftover!!
                                chunk.data.copyInto(buf, destinationOffset = 1)
                            }
                        } else chunk.data

                        leftover = if (rawData.size % 2 != 0) rawData[rawData.size - 1] else null
                        val evenSize = rawData.size - (rawData.size % 2)
                        if (evenSize <= 0) return@collect

                        val evenData = if (evenSize == rawData.size) rawData else rawData.copyOf(evenSize)
                        queue.put(AudioChunk(evenData, chunk.sampleRate))
                    }
                } finally {
                    queue.put(END_OF_STREAM)
                }
            }

            // Consumer: тянет из очереди и пишет в AudioTrack
            var current: AudioTrack? = null
            var sampleRate = -1
            var minBufSize = 0
            val prefill = mutableListOf<ByteArray>()
            var prefillSize = 0
            var playing = false

            try {
                while (true) {
                    val chunk = queue.take()
                    if (chunk === END_OF_STREAM) break

                    // Инициализируем AudioTrack при первом чанке
                    if (current == null) {
                        sampleRate = chunk.sampleRate
                        minBufSize = AudioTrack.getMinBufferSize(
                            sampleRate,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                        ).coerceAtLeast(4096)
                        current = buildTrack(sampleRate, minBufSize)
                        track = current
                    }

                    onLevel(chunk.level())

                    if (!playing) {
                        prefill.add(chunk.data)
                        prefillSize += chunk.data.size
                        if (prefillSize >= minBufSize) {
                            runCatching { current?.play() }
                            playing = true
                            for (buf in prefill) writeAll(current, buf)
                            prefill.clear()
                        }
                    } else {
                        writeAll(current, chunk.data)
                    }
                }

                // Короткий ответ — prefill не набрался, играем что есть
                if (!playing && prefill.isNotEmpty()) {
                    runCatching { current?.play() }
                    for (buf in prefill) writeAll(current, buf)
                }

                runCatching { current?.stop() }

            } finally {
                producerJob.cancel()
                onLevel(0f)
                track = null
                current?.let { runCatching { it.release() } }
            }
        }
    }

    /** Мгновенно обрывает воспроизведение (кнопка «Стоп»). */
    fun stopImmediately() {
        val active = track ?: return
        runCatching { active.pause() }
        runCatching { active.flush() }
    }

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
        // AudioTrack буфер = minBufSize × 3 (~300ms при 24кГц).
        // Паузы Fish Audio поглощаются ArrayBlockingQueue в памяти,
        // поэтому AudioTrack буфер может быть меньше — задержка ниже.
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
