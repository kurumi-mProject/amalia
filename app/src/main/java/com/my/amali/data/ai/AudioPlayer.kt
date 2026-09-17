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
import java.util.ArrayDeque
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Воспроизводит поток [AudioChunk] (PCM 16-bit mono) через [AudioTrack].
 *
 * ## Почему первые слова больше не «шуршат»
 *
 * PCM-16 — это кадры по 2 байта. Если в звуковой тракт попадают НЕЧЁТНЫЕ
 * куски сети, весь последующий поток сдвигается на полкадра: младший байт
 * одного сэмпла склеивается со старшим другого, и вместо голоса слышен треск
 * ровно в первые полсекунды (дальше выравнивание «случайно» восстанавливается
 * на очередном чётном байте). Раньше кадрирование делал только steady-state
 * путь, а prefill писал сырые куски напрямую — отсюда «первые слова с помехами».
 *
 * Теперь кадрирование гарантирует **продюсер**: в очередь уходят только чанки
 * с чётной длиной, нечётный хвост переносится в следующий. Потребителю нечего
 * доклеивать, и обе фазы записи идут через один и тот же выравнивающий путь.
 *
 * ## Что ещё здесь важно
 *
 * - **Пример тишины** перед первым сэмплом: аудио-конвейер и DAC поднимаются
 *   не мгновенно, и без него начало первой гласной съедалось.
 * - **Prefill урезан** с 6×minBufferSize до 1×minBufferSize: первый звук
 *   приходит примерно на 300 мс раньше, при этом ёмкость самого трека
 *   осталась 6× периодов — есть запас на джиттер сети.
 * - **GENERATION-страховка**: старый потребитель обязан замолчать мгновенно,
 *   иначе его трек продолжает играть параллельно новому и два голоса
 *   накладываются друг на друга (тоже выглядело как «помехи»).
 * - `PERFORMANCE_MODE_NONE`: LOW_LATENCY даёт слишком маленький аппаратный
 *   буфер и underrun при малейшем джиттере планировщика.
 */
class AudioPlayer {

    @Volatile
    private var track: AudioTrack? = null

    /**
     * Номер проигрывания. `stopImmediately()` увеличивает его — потребитель
     * с устаревшим номером мгновенно выходит и не лезет в новый трек.
     */
    private val generation = AtomicLong(0L)

    /** Нечётный байт, перенесённый из предыдущего чанка (см. [align]). */
    private var carry: Int? = null

    /** Маркер конца стрима — сравнивается по идентичности, а не по значению. */
    private val endOfStream = AudioChunk(ByteArray(0), -1)

    /**
     * Проигрывает [chunks] до конца потока или до отмены корутины.
     *
     * @param onLevel громкость 0..1 для анимации волны — вызывается из
     *   продюсера, то есть с тем же темпом, что и приходит аудио.
     */
    suspend fun play(
        chunks: Flow<AudioChunk>,
        onLevel: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val myGeneration = generation.incrementAndGet()
        carry = null
        val queue = ArrayBlockingQueue<AudioChunk>(QUEUE_CAPACITY)

        coroutineScope {
            // ── Продюсер: Flow → очередь, с гарантией чётности кадров ──────
            val producerJob = launch(Dispatchers.IO) {
                try {
                    chunks.collect { chunk ->
                        if (chunk.data.isEmpty()) return@collect
                        if (generation.get() != myGeneration) return@collect
                        val aligned = align(chunk.data)
                        if (aligned.isNotEmpty()) {
                            queue.put(AudioChunk(aligned, chunk.sampleRate))
                        }
                        onLevel(chunk.level())
                    }
                } finally {
                    // offer с таймаутом, а не put: потребитель мог выйти раньше,
                    // и put навсегда повесил бы эту корутину.
                    runCatching { queue.offer(endOfStream, OFFER_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
                }
            }

            // ── Потребитель: очередь → AudioTrack ───────────────────────────
            launch(Dispatchers.IO) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

                var current: AudioTrack? = null
                var sampleRate = 0
                var minBufSize = 0
                var banked = 0
                val deferred = ArrayDeque<ByteArray>()

                /** Строит трек под частоту первого пришедшего чанка. */
                fun buildTrack(sr: Int) {
                    minBufSize = AudioTrack.getMinBufferSize(
                        sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    ).coerceAtLeast(MIN_BUFFER_FLOOR)

                    current = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build(),
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setSampleRate(sr)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build(),
                        )
                        .setBufferSizeInBytes(minBufSize * BUFFER_MULTIPLIER)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
                        .build()
                        .also { track = it }
                }

                /**
                 * Пишет сколько сможет и возвращает число принятых байтов.
                 *
                 * [blocking] = true — ждём освобождения буфера (steady state);
                 * false — возвращаем 0 немедленно, если буфер забит (prefill).
                 * Меньше принятого, чем передано, — признак остановки/ошибки трека.
                 */
                fun writeSome(data: ByteArray, blocking: Boolean): Int {
                    var offset = 0
                    val mode = if (blocking) AudioTrack.WRITE_BLOCKING else AudioTrack.WRITE_NON_BLOCKING
                    while (offset < data.size) {
                        if (generation.get() != myGeneration) return data.size
                        val target = current ?: return data.size
                        val slice = if (offset == 0) data else data.copyOfRange(offset, data.size)
                        val written = runCatching { target.write(slice, 0, slice.size, mode) }
                            .getOrDefault(-1)
                        if (written <= 0) return offset
                        offset += written
                    }
                    return offset
                }

                /** Пишет [data] целиком; false — если трек остановлен или отменены. */
                fun writeAll(data: ByteArray, blocking: Boolean): Boolean =
                    writeSome(data, blocking) >= data.size

                try {
                    // ── Фаза 1: prefill ─────────────────────────────────────
                    // До play() BLOCKING писать нельзя: место в буфере
                    // освобождает только играющий трек.
                    var emptyWaits = 0

                    while (generation.get() == myGeneration) {
                        val chunk = queue.poll(POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        if (chunk == null) {
                            // Данные идут медленно, а трек ещё не запущен:
                            // стартуем с тем, что есть — иначе пользователь
                            // смотрит на «говорю» без звука.
                            if (banked > 0) break
                            if (++emptyWaits >= MAX_EMPTY_WAITS) break
                            continue
                        }
                        if (chunk === endOfStream) {
                            runCatching { queue.offer(endOfStream, 0, TimeUnit.SECONDS) }
                            break
                        }
                        if (current == null) {
                            sampleRate = chunk.sampleRate
                            buildTrack(sampleRate)
                            // Разгон аудио-тракта: короткие нули вместо начала речи.
                            writeAll(silenceFor(sampleRate, PRIMER_MS), blocking = false)
                        }

                        val consumed = writeSome(chunk.data, blocking = false)
                        banked += consumed
                        if (consumed < chunk.data.size) {
                            // Буфер полон — остаток допишем уже играющим треком.
                            deferred.addLast(chunk.data.copyOfRange(consumed, chunk.data.size))
                            break
                        }
                        if (banked >= minBufSize * PREFILL_MULTIPLIER) break
                    }

                    if (current != null && generation.get() == myGeneration) {
                        runCatching { current?.play() }
                    }

                    // ── Фаза 2: steady state — только BLOCKING ─────────────
                    while (current != null && generation.get() == myGeneration) {
                        val pending = deferred.pollFirst()
                        if (pending != null) {
                            if (!writeAll(pending, blocking = true)) break
                            continue
                        }
                        val chunk = queue.poll(POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        if (chunk == null) continue
                        if (chunk === endOfStream) break
                        if (!writeAll(chunk.data, blocking = true)) break
                    }

                    // Хвост тишины: без него stop() обрывает последние слова,
                    // которые ещё не сошли из буфера в динамик.
                    if (current != null && sampleRate > 0 && generation.get() == myGeneration) {
                        writeAll(silenceFor(sampleRate, TAIL_SILENCE_MS), blocking = true)
                    }
                } finally {
                    producerJob.cancel()
                    if (generation.get() == myGeneration) onLevel(0f)
                    val finished = current
                    current = null
                    // Сбрасываем ссылку только на СВОЙ трек: иначе старый
                    // потребитель затрёт указатель уже на новый, играющий.
                    if (track === finished) track = null
                    runCatching { finished?.stop() }
                    runCatching { finished?.release() }
                }
            }
        }
    }

    /**
     * Мгновенно обрывает воспроизведение и запрещает дописывать буфер.
     *
     * `pause()+flush()` обрывают звук за миллисекунды, а рост номера
     * проигрывания гасит потребитель, который мог бы продолжить писать
     * в этот же трек.
     */
    fun stopImmediately() {
        generation.incrementAndGet()
        val active = track ?: return
        runCatching { active.pause() }
        runCatching { active.flush() }
    }

    /**
     * Приводит байты к целому числу PCM-кадров (по 2 байта).
     *
     * Нечётный хвост не выбрасывается, а помечается к переносу в следующий
     * чанк — так ни один байт речи не теряется и тракт никогда не сдвигается.
     */
    private fun align(data: ByteArray): ByteArray {
        val pending = carry
        val bytes = if (pending == null) {
            data
        } else {
            ByteArray(data.size + 1).also {
                it[0] = pending.toByte()
                data.copyInto(it, 1)
            }
        }
        val even = bytes.size - (bytes.size % FRAME)
        carry = if (even < bytes.size) bytes[even].toInt() and 0xFF else null
        return if (even == bytes.size) bytes else bytes.copyOf(even)
    }

    private fun silenceFor(sampleRate: Int, millis: Int): ByteArray =
        ByteArray((sampleRate.toLong() * millis / 1000L).toInt() * FRAME)

    private companion object {
        /** PCM-16 mono: кадр = 2 байта. */
        const val FRAME = 2

        /** Глубина очереди на вход потребителю. */
        const val QUEUE_CAPACITY = 64

        /** Сколько ждать чанк перед решением о старте/выходе. */
        const val POLL_TIMEOUT_SECONDS = 2L

        /** Сколько секунд ждём вообще хоть какие-то данные. */
        const val MAX_EMPTY_WAITS = 8

        /** Таймаут публикации маркера конца стрима. */
        const val OFFER_TIMEOUT_SECONDS = 1L

        /** Нижняя граница буфера, когда система не смогла её посчитать. */
        const val MIN_BUFFER_FLOOR = 4096

        /** Ёмкость трека: 6 периодов — запас на джиттер планировщика. */
        const val BUFFER_MULTIPLIER = 6

        /** Сколько периодов накопить до play(). Больше — дольше ждать первый звук. */
        const val PREFILL_MULTIPLIER = 1

        /** Тишина перед первым сэмплом — разгон аудио-тракта. */
        const val PRIMER_MS = 40

        /** Тишина после последнего сэмпла — чтобы не обрезать окончание фразы. */
        const val TAIL_SILENCE_MS = 700
    }
}
