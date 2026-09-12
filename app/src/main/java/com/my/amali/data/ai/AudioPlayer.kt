package com.my.amali.data.ai

import android.content.Context
import android.media.AudioTrack
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
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
 * [DefaultAudioSink] — production-grade обёртка над AudioTrack от Google (YouTube, TV).
 * Решает проблемы которые давали треск:
 * 1. Корректный resampling через AudioProcessorChain без артефактов
 * 2. Non-blocking write — не теряет сэмплы при полном буфере
 * 3. Правильная инициализация буферов и lifecycle
 */
@OptIn(UnstableApi::class)
class AudioPlayer(private val context: Context) {

    @Volatile private var sink: DefaultAudioSink? = null

    suspend fun play(
        chunks: Flow<AudioChunk>,
        onLevel: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val audioSink = buildSink()
        sink = audioSink

        var configuredSampleRate = -1
        var leftover: Byte? = null

        try {
            chunks.collect { chunk ->
                if (chunk.data.isEmpty()) return@collect

                // Конфигурируем sink при первом чанке или смене sample rate.
                if (chunk.sampleRate != configuredSampleRate) {
                    if (configuredSampleRate != -1) audioSink.flush()
                    configuredSampleRate = chunk.sampleRate
                    leftover = null

                    val format = Format.Builder()
                        .setSampleMimeType(MimeTypes.AUDIO_RAW)
                        .setPcmEncoding(C.ENCODING_PCM_16BIT)
                        .setSampleRate(chunk.sampleRate)
                        .setChannelCount(1)
                        .build()

                    audioSink.configure(format, /* specifiedBufferSize= */ 0, /* outputChannels= */ null)
                    audioSink.play()
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

                val buffer = ByteBuffer.wrap(rawData, 0, evenSize)
                    .order(ByteOrder.LITTLE_ENDIAN)

                // Non-blocking loop: DefaultAudioSink буферизует данные сам.
                while (buffer.hasRemaining()) {
                    val accepted = audioSink.handleBuffer(
                        buffer,
                        /* presentationTimeUs= */ C.TIME_UNSET,
                        /* encodedAccessUnitCount= */ 1,
                    )
                    if (!accepted) {
                        // Буфер занят — ждём чтобы не спинлупить
                        audioSink.handleDiscontinuity()
                    }
                }
            }

            audioSink.playToEndOfStream()

        } finally {
            onLevel(0f)
            sink = null
            runCatching { audioSink.reset() }
        }
    }

    fun stopImmediately() {
        val s = sink ?: return
        runCatching { s.flush() }
        runCatching { s.reset() }
        sink = null
    }

    private fun buildSink(): DefaultAudioSink {
        val sink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(false)
            .setEnableAudioOutputPlaybackParameters(false)
            .build()

        sink.setAudioAttributes(
            Media3AudioAttributes.Builder()
                .setUsage(C.USAGE_VOICE_COMMUNICATION)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build()
        )

        return sink
    }
}
