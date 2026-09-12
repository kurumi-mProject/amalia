package com.my.amali.data.ai

import android.media.AudioAttributes
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Воспроизводит поток [AudioChunk] (PCM 16-bit mono) через Media3 [DefaultAudioSink].
 *
 * ## Почему Media3 DefaultAudioSink, а не AudioTrack напрямую
 *
 * [DefaultAudioSink] — это production-grade обёртка над AudioTrack которую
 * Google использует в YouTube, Google TV и всех Media3/ExoPlayer приложениях.
 * Она решает именно те проблемы которые давали у нас треск:
 *
 * 1. **Автоматический ресемплинг** — внутренний AudioProcessorChain корректно
 *    конвертирует любой sample rate (24кГц → 48кГц HAL) без артефактов.
 * 2. **Non-blocking write** — не блокирует поток если буфер AudioTrack заполнен,
 *    а ждёт и повторяет — нет пропуска сэмплов.
 * 3. **Правильный lifecycle** — configure → play → handleBuffer → ... → flush/reset,
 *    без edge-cases при создании/пересоздании дорожки.
 * 4. **Обработка discontinuity** — при смене sample rate не даёт щелчков.
 */
@OptIn(UnstableApi::class)
class AudioPlayer {

    @Volatile private var sink: DefaultAudioSink? = null
    @Volatile private var isPlaying = false

    suspend fun play(
        chunks: Flow<AudioChunk>,
        onLevel: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val audioSink = buildSink()
        sink = audioSink

        var configuredSampleRate = -1
        // Перенос хвостового байта нечётного чанка.
        var leftover: Byte? = null

        try {
            chunks.collect { chunk ->
                if (chunk.data.isEmpty()) return@collect

                // Конфигурируем sink при первом чанке или смене sample rate.
                if (chunk.sampleRate != configuredSampleRate) {
                    if (configuredSampleRate != -1) {
                        audioSink.flush()
                    }
                    configuredSampleRate = chunk.sampleRate
                    leftover = null

                    val format = Format.Builder()
                        .setSampleMimeType(MimeTypes.AUDIO_RAW)
                        .setEncoding(C.ENCODING_PCM_16BIT)
                        .setSampleRate(chunk.sampleRate)
                        .setChannelCount(1)
                        .build()

                    audioSink.configure(format, /* specifiedBufferSize= */ 0, /* outputChannels= */ null)
                    audioSink.play()
                    isPlaying = true
                }

                // Выравниваем по 2 байта (PCM-16).
                val rawData = if (leftover != null) {
                    ByteArray(1 + chunk.data.size).also { buf ->
                        buf[0] = leftover!!
                        chunk.data.copyInto(buf, destinationOffset = 1)
                    }
                } else {
                    chunk.data
                }
                leftover = if (rawData.size % 2 != 0) rawData.last() else null
                val evenSize = rawData.size and 0x1.inv()
                if (evenSize <= 0) return@collect

                onLevel(chunk.level())

                // DefaultAudioSink.handleBuffer принимает ByteBuffer.
                val buffer = ByteBuffer.wrap(rawData, 0, evenSize)
                    .order(ByteOrder.LITTLE_ENDIAN)

                // Non-blocking loop: если буфер занят — ждём и повторяем.
                while (buffer.hasRemaining()) {
                    val accepted = audioSink.handleBuffer(
                        buffer,
                        /* presentationTimeUs= */ C.TIME_UNSET,
                        /* encodedAccessUnitCount= */ 1,
                    )
                    if (!accepted) {
                        audioSink.handleDiscontinuity()
                    }
                }
            }

            // Дожидаемся окончания воспроизведения буфера.
            audioSink.playToEndOfStream()

        } finally {
            onLevel(0f)
            isPlaying = false
            sink = null
            runCatching { audioSink.reset() }
        }
    }

    fun stopImmediately() {
        val s = sink ?: return
        runCatching { s.flush() }
        runCatching { s.reset() }
        isPlaying = false
        sink = null
    }

    private fun buildSink(): DefaultAudioSink {
        val attrs = Media3AudioAttributes.Builder()
            .setUsage(C.USAGE_VOICE_COMMUNICATION)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        return DefaultAudioSink.Builder(/* context= */ null)
            .setAudioAttributes(attrs)
            .setEnableFloatOutput(false)
            .setEnableAudioTrackPlaybackParams(true) // аппаратный ресемплинг если доступен
            .build()
    }
}
