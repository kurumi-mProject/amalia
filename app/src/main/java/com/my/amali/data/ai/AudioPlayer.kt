package com.my.amali.data.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Воспроизводит поток [AudioChunk] (PCM 16-bit mono) через [AudioTrack].
 *
 * Двухуровневый буфер:
 * - [ArrayBlockingQueue] поглощает паузы генерации Fish Audio (~500ms)
 * - Буфер AudioTrack поглощает джиттер планировщика (~десятки мс)
 *
 * Ключевые правила:
 * - NON_BLOCKING только при prefill (до play()), потом только BLOCKING
 * - THREAD_PRIORITY_URGENT_AUDIO ставится в самом треде писателя
 * - Байтовое выравнивание (carry byte) хранится между пачками
 * - Все возвраты write() обрабатываются — нет потери байтов
 */
class AudioPlayer {

    @Volatile private var track: AudioTrack? = null

    // Sentinel — маркер конца стрима
    private val END_OF_STREAM = AudioChunk(ByteArray(0), -1)

    // PCM-16 mono: кадр = 2 байта
    private val FRAME = 2

    suspend fun play(
        chunks: Flow<AudioChunk>,
        onLevel: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {

        val queue = ArrayBlockingQueue<AudioChunk>(64)

        coroutineScope {
            // Producer: читает из Flow в очередь максимально быстро
            val producerJob = launch(Dispatchers.IO) {
                try {
                    chunks.collect { chunk ->
                        if (chunk.data.isEmpty()) return@collect
                        // copyOf() обязателен — producer продолжает читать пока
                        // consumer ещё не взял этот чанк из очереди
                        queue.put(AudioChunk(chunk.data.copyOf(), chunk.sampleRate))
                        onLevel(chunk.level())
                    }
                } finally {
                    queue.put(END_OF_STREAM)
                }
            }

            // Consumer: тянет из очереди и пишет в AudioTrack
            // Весь критический путь в одном потоке — URGENT_AUDIO здесь
            launch(Dispatchers.IO) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

                var current: AudioTrack? = null
                var sampleRate = -1
                var minBufSize = 0
                var capacity = 0

                // carry: хвостовой байт от предыдущей пачки (нечётный хвост PCM-16)
                var hasTail = false
                var tailByte: Byte = 0

                // Отложенные данные при prefill NON_BLOCKING
                val deferred = ArrayDeque<AudioChunk>()

                fun buildAndInitTrack(sr: Int) {
                    minBufSize = AudioTrack.getMinBufferSize(
                        sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                    ).coerceAtLeast(4096)
                    // Буфер = 6× minBufSize — ёмкость бесплатна (задержку определяет prefill).
                    // PERFORMANCE_MODE_NONE: LOW_LATENCY даёт слишком маленький аппаратный буфер
                    // и вызывает underrun при малейшем джиттере планировщика.
                    capacity = minBufSize * 6

                    current = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setSampleRate(sr)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(capacity)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
                        .build()
                    track = current
                }

                // Запись с гарантией — loop пока не запишем все len байт
                fun writeAll(data: ByteArray, off: Int, len: Int) {
                    var o = off; var n = len
                    while (n > 0) {
                        val w = current?.write(data, o, n, AudioTrack.WRITE_BLOCKING) ?: break
                        if (w <= 0) break
                        o += w; n -= w
                    }
                }

                // Склейка кадров: сохраняет carry между пачками
                fun feed(data: ByteArray) {
                    var p = 0; var n = data.size
                    // Добиваем кадр из предыдущей пачки
                    if (hasTail && n > 0) {
                        val pair = ByteArray(2) { if (it == 0) tailByte else data[0] }
                        writeAll(pair, 0, 2)
                        hasTail = false; p = 1; n -= 1
                    }
                    // Целые кадры
                    val aligned = (n / FRAME) * FRAME
                    if (aligned > 0) writeAll(data, p, aligned)
                    // Хвостовой байт — запомним для следующей пачки
                    if (n > aligned) {
                        tailByte = data[p + aligned]
                        hasTail = true
                    }
                }

                try {
                    // ── Фаза 1: Prefill до play() ──────────────────────────────
                    // NON_BLOCKING: BLOCKING до play() зависнет — места в буфере
                    // освобождает только играющий трек
                    var banked = 0

                    while (true) {
                        val chunk = queue.poll(5, TimeUnit.SECONDS) ?: continue
                        if (chunk === END_OF_STREAM) {
                            // Стрим кончился до prefill — играем что есть
                            queue.put(END_OF_STREAM)
                            break
                        }

                        if (current == null) {
                            sampleRate = chunk.sampleRate
                            buildAndInitTrack(sampleRate)
                        }

                        var done = 0
                        while (done < chunk.data.size) {
                            val w = current?.write(
                                chunk.data, done, chunk.data.size - done,
                                AudioTrack.WRITE_NON_BLOCKING
                            ) ?: break
                            if (w <= 0) {
                                // Буфер полон — откладываем остаток
                                deferred.addLast(AudioChunk(chunk.data.copyOfRange(done, chunk.data.size), sampleRate))
                                break
                            }
                            done += w; banked += w
                        }

                        if (banked >= capacity) break
                    }

                    current?.play()

                    // ── Фаза 2: Steady state — только BLOCKING ─────────────────
                    while (true) {
                        val chunk: AudioChunk = deferred.removeFirstOrNull()
                            ?: queue.poll(5, TimeUnit.SECONDS)
                            ?: continue

                        if (chunk === END_OF_STREAM) break
                        feed(chunk.data)
                    }

                    current?.stop()

                } finally {
                    producerJob.cancel()
                    onLevel(0f)
                    track = null
                    current?.let { runCatching { it.release() } }
                }
            }
        }
    }

    /** Мгновенно обрывает воспроизведение. */
    fun stopImmediately() {
        val active = track ?: return
        runCatching { active.pause() }
        runCatching { active.flush() }
    }
}
