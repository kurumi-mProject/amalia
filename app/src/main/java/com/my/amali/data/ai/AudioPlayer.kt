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
 * ## Почему был треск и как исправлено
 *
 * **Причина**: Fish Audio отдавал PCM на 24 000 Гц, а Android Audio HAL
 * работает нативно на 48 000 Гц. При стриминге маленькими чанками (~4 КБ)
 * Android делал software resampling 24k→48k на каждом чанке отдельно —
 * это давало фазовые артефакты и треск на границах.
 *
 * **Решение**:
 * 1. Fish Audio теперь запрашивается на 24 000 Гц.
 *    24 000 → 48 000 — целочисленное соотношение 1:2, Android использует
 *    fast integer resampler без артефактов.
 * 2. `USAGE_VOICE_COMMUNICATION` + `CONTENT_TYPE_SPEECH` активирует
 *    low-latency audio path на большинстве устройств.
 * 3. Буфер = 2× minBufferSize — минимально необходимый запас без лишней
 *    буферизации, которая добавляла бы задержку.
 * 4. `play()` вызывается ДО первого `write()` — AudioTrack начинает
 *    работать сразу и не накапливает задержку.
 * 5. Выравнивание PCM-16 по 2 байта на границах чанков — убирает щелчки
 *    от разрезанных сэмплов.
 */
class AudioPlayer {

    @Volatile
    private var track: AudioTrack? = null

    suspend fun play(
        chunks: Flow<AudioChunk>,
        onLevel: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        var current: AudioTrack? = null
        var sampleRate = -1

        // Перенос «хвостового» байта нечётного чанка в следующий.
        var leftover: Byte? = null

        try {
            chunks.collect { chunk ->
                if (chunk.data.isEmpty()) return@collect

                // Пересоздаём AudioTrack при смене sample rate.
                if (current == null || chunk.sampleRate != sampleRate) {
                    current?.let { runCatching { it.stop(); it.release() } }
                    leftover = null
                    sampleRate = chunk.sampleRate
                    current = buildTrack(sampleRate)
                    track = current
                    // play() сразу — AudioTrack начнёт читать данные без задержки накопления.
                    runCatching { current?.play() }
                }

                // Выравниваем по 2 байта (PCM-16 = 2 байта на сэмпл).
                val rawData = if (leftover != null) {
                    ByteArray(1 + chunk.data.size).also { buf ->
                        buf[0] = leftover!!
                        chunk.data.copyInto(buf, destinationOffset = 1)
                    }
                } else {
                    chunk.data
                }
                leftover = if (rawData.size % 2 != 0) rawData.last() else null
                val evenSize = rawData.size and 0x1.inv() // округление вниз до чётного
                if (evenSize <= 0) return@collect
                val data = if (evenSize == rawData.size) rawData else rawData.copyOf(evenSize)

                onLevel(chunk.level())
                writeAll(current, data)
            }

            // Даём AudioTrack доиграть то что уже записано в буфер.
            runCatching { current?.stop() }

        } finally {
            onLevel(0f)
            track = null
            current?.let { runCatching { it.release() } }
        }
    }

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

    private fun buildTrack(sampleRate: Int): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(2048)

        // 2× minBuffer — достаточный запас без лишней буферизации.
        // При 24 кГц 16-bit mono: minBuf ≈ 3840 байт → буфер = 7680 байт ≈ 160 мс.
        val bufSize = minBuf * 2

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // VOICE_COMMUNICATION активирует низколатентный путь (bypass software mixer).
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
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
            .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
            .build()
    }
}
