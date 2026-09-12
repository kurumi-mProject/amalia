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
 * Особенности:
 * - Работает на [Dispatchers.IO] и никогда не блокирует UI-поток.
 * - Буфер рассчитан на ~400 мс звука: запись не отстаёт от сети, но и
 *   задержка старта остаётся минимальной.
 * - Первый чанк начинает играть сразу — [AudioTrack.play] вызывается
 *   до первой записи.
 * - `onLevel` отдаёт громкость играемого фрагмента: волна на экране
 *   двигается синхронно с голосом.
 * - При отмене корутины дорожка гасится через `pause()+flush()`, поэтому
 *   звук обрывается мгновенно, а не доигрывает буфер.
 */
class AudioPlayer {

    @Volatile
    private var track: AudioTrack? = null

    /**
     * Проигрывает все чанки из [chunks] в порядке поступления.
     * Приостанавливается до завершения потока.
     *
     * @param onLevel коллбэк громкости 0..1 для анимации (вызывается часто).
     */
    suspend fun play(
        chunks: Flow<AudioChunk>,
        onLevel: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        var current: AudioTrack? = null
        var sampleRate = -1
        try {
            chunks.collect { chunk ->
                if (chunk.data.isEmpty()) return@collect
                if (current == null || chunk.sampleRate != sampleRate) {
                    current?.let { old ->
                        runCatching { old.stop() }
                        runCatching { old.release() }
                    }
                    sampleRate = chunk.sampleRate
                    current = buildTrack(sampleRate)
                    track = current
                    runCatching { current?.play() }
                }
                onLevel(chunk.level())
                var offset = 0
                while (offset < chunk.data.size) {
                    val written = current?.write(chunk.data, offset, chunk.data.size - offset) ?: -1
                    if (written <= 0) break
                    offset += written
                }
            }
            // Даём дорожке доиграть остаток буфера перед освобождением.
            runCatching { current?.stop() }
        } finally {
            onLevel(0f)
            track = null
            current?.let { done ->
                runCatching { done.release() }
            }
        }
    }

    /** Мгновенно обрывает воспроизведение (используется при «Стоп»). */
    fun stopImmediately() {
        val active = track ?: return
        runCatching { active.pause() }
        runCatching { active.flush() }
    }

    private fun buildTrack(sampleRate: Int): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)
        // ~400 мс запаса: сеть может «икать», а речь не должна рваться.
        val targetBuffer = maxOf(minBuffer * 2, sampleRate * 2 * 400 / 1000)

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
            .setBufferSizeInBytes(targetBuffer)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
            .build()
    }
}
