package com.my.amali.data.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Воспроизводит PCM-16 audio chunks через AudioTrack с минимальной задержкой.
 *
 * Ключевые техники против треска:
 * - Prefill buffer перед `play()` — на некоторых чипсетах пустой буфер при старте даёт треск
 * - USAGE_VOICE_COMMUNICATION — минимальная обработка DSP
 * - MODE_STREAM — для потоковых данных
 * - Выравнивание по 2 байта (PCM-16 = 2 байта на сэмпл)
 */
class AudioPlayer {

    @Volatile private var track: AudioTrack? = null

    suspend fun play(chunks: Flow<AudioChunk>, onLevel: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        var currentTrack: AudioTrack? = null
        var configuredSampleRate = -1
        var leftover: Byte? = null
        var prefillNeeded = false

        try {
            chunks.collect { chunk ->
                if (chunk.data.isEmpty()) return@collect

                // Пересоздаём track при изменении sample rate
                if (chunk.sampleRate != configuredSampleRate) {
                    currentTrack?.let {
                        runCatching { it.stop() }
                        runCatching { it.release() }
                    }
                    configuredSampleRate = chunk.sampleRate
                    leftover = null

                    val bufferSize = AudioTrack.getMinBufferSize(
                        chunk.sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    ).coerceAtLeast(4096)

                    currentTrack = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setSampleRate(chunk.sampleRate)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize * 2)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()

                    track = currentTrack
                    prefillNeeded = true
                }

                val t = currentTrack ?: return@collect

                // Выравнивание PCM-16 по чётному количеству байт
                val rawData = if (leftover != null) {
                    ByteArray(1 + chunk.data.size).also { buf ->
                        buf[0] = leftover!!
                        chunk.data.copyInto(buf, destinationOffset = 1)
                    }
                } else chunk.data
                leftover = if (rawData.size % 2 != 0) rawData.last() else null
                val evenSize = rawData.size and 0x1.inv()
                if (evenSize <= 0) return@collect

                onLevel(chunk.level())

                // Prefill: накапливаем минимум minBufferSize байт перед play()
                if (prefillNeeded) {
                    val minSize = AudioTrack.getMinBufferSize(
                        configuredSampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    ).coerceAtLeast(2048)
                    
                    val written = t.write(rawData, 0, evenSize, AudioTrack.WRITE_BLOCKING)
                    if (written >= minSize) {
                        t.play()
                        prefillNeeded = false
                    }
                } else {
                    t.write(rawData, 0, evenSize, AudioTrack.WRITE_BLOCKING)
                }
            }
        } finally {
            onLevel(0f)
            currentTrack?.let {
                runCatching { it.stop() }
                runCatching { it.release() }
            }
            track = null
        }
    }

    fun stopImmediately() {
        val t = track ?: return
        runCatching { t.stop() }
        runCatching { t.release() }
        track = null
    }
}
