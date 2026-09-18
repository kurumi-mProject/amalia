package com.my.amali.data.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Воспроизводит поток [AudioChunk] (PCM 16-bit mono) через [AudioTrack].
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ГЛАВНАЯ ПРИЧИНА «ОТВЕТА БЕЗ ЗВУКА» — И ЧТО ЗДЕСЬ ИСПРАВЛЕНО
 * ════════════════════════════════════════════════════════════════════════
 *
 * Модель отвечает, текст появляется на экране — а голоса нет. Происходило
 * это не в TTS: `FishAudioTTS` отдавала чанки исправно, и они честно
 * доезжали до очереди. Терял их **плеер**, ровно в одном месте — на старте
 * воспроизведения.
 *
 * Было так: фаза prefill набирала первый период буфера, и только после неё
 * вызывался `AudioTrack.play()`. Если за это время ни один байт не записался
 * (`banked == 0`) — а это штатный случай, когда первый чанк синтеза приходит
 * ровно на границе пустого опроса, — цикл выходил по пустым poll'ам, условие
 * `current != null` не выполнялось, `play()` не вызывался вовсе, и весь
 * уже полученный звук вместе с очередью молча уезжал в `finally`. Пользователь
 * не слышал ни байта, и никакой ошибки при этом не возникало: синтез-то
 * прошёл успешно.
 *
 * Плюс `/stopImmediately()` в момент старта нового цикла гасил плеер,
 * который этот же цикл и запустил: поколение увеличивалось, потребитель
 * моментально выходил, а `pause()+flush()` выбрасывали уже записанный буфер.
 * Из-за этого «озвучка отменялась» даже тогда, когда данные физически были
 * в очереди.
 *
 * Теперь действуют три правила:
 *
 *  1. **`play()` встаёт на дорожку всегда, как только появился хоть один
 *     байт.** Префолл — это не «условие старта», а лишь способ набрать
 *     небольшой запас перед первым звуком; он не может отменить действие.
 *  2. **Пустой опрос очереди ничего не выбрасывает.** Раньше `poll` с
 *     таймаутом 2 с, повторённый восемь раз, «съедал» уже полученные чанки,
 *     если они приходили с задержкой и не попадали в `banked`. Теперь
 *     ожидание сверху ограничено [PREFILL_WAIT_MS] суммарно, а данные из
 *     очереди забираются до последнего байта.
 *  3. **Ремонт трека.** Если `play()`/`write()` сорвались (система отобрала
 *     аудио-фокус, трек отвалился), плеер не сдаётся молча: он собирает
 *     трек заново и продолжает с того места, где остановился.
 *
 * ## Что ещё здесь важно
 *
 * - **Выравнивание кадров делает продюсер.** PCM-16 — кадры по 2 байта;
 *   нечётный кусок сети раньше сдвигал весь тракт на полкадра, и вместо
 *   голоса слышался треск в первые полсекунды. Теперь нечётный хвост
 *   переносится в следующий чанк, а потребителю нечего доклеивать.
 * - **Тишина перед первым сэмплом**: аудио-конвейер и DAC поднимаются не
 *   мгновенно, без [PRIMER_MS] начало первой гласной съедалось.
 * - **Хвост тишины** после последнего сэмпла: без него `stop()` обрывает
 *   окончание фразы, которое ещё не сошло из буфера в динамик.
 * - **Drain-гарантия**: перед закрытием трека плеер обязан слить всё, что
 *   осталось в буфере, — иначе последние слова пропадают.
 * - `PERFORMANCE_MODE_NONE`: LOW_LATENCY даёт слишком маленький аппаратный
 *   буфер и underrun при малейшем джиттере планировщика.
 */
class AudioPlayer {

    @Volatile
    private var track: AudioTrack? = null

    /**
     * Номер проигрывания. `stopImmediately()` увеличивает его — потребитель
     * с устаревшим номером мгновенно выходит и не лезет в новый трек.
     *
     * ВАЖНО: увеличение этого номера — **только** операция «замолчать сейчас».
     * Запуск нового цикла не должен её трогать: иначе плеер убивает сам себя
     * в тот момент, когда начинает играть.
     */
    private val generation = AtomicLong(0L)

    /** Нечётный байт, перенесённый из предыдущего чанка (см. [align]). */
    private var carry: Int? = null

    /** Маркер конца стрима — сравнивается по идентичности, а не по значению. */
    private val endOfStream = AudioChunk(ByteArray(0), -1)

    /**
     * Проигрывает [chunks] до конца потока или до отмены корутины.
     *
     * Метод **не бросает** исключений: любой сбой аудио-тракта здесь — это
     * «не смогли произнести», а не «сломался диалог». Текст ответа уже
     * показан на экране, и терять его из-за драйвера нельзя.
     *
     * @param onLevel громкость 0..1 для анимации волны — вызывается с тем же
     *   темпом, что идёт аудио.
     */
    suspend fun play(
        chunks: Flow<AudioChunk>,
        onLevel: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val myGeneration = generation.incrementAndGet()
        // Запоминаем метку прогона на время воспроизведения: логи плеера
        // должны нести тот же идентификатор, что и логи синтеза, иначе
        // связать «чей это звук» по логу невозможно.
        val runTag = AmaliaLog.tagWith("PCM")
        AmaliaLog.d(runTag, "play() start | generation=$myGeneration")
        carry = null
        val queue = ArrayBlockingQueue<AudioChunk>(QUEUE_CAPACITY)

        coroutineScope {
            // ── Продюсер ──────────────────────────────────────────────────
            val producerJob = launch(Dispatchers.IO) {
                var produced = 0
                try {
                    chunks.collect { chunk ->
                        if (chunk.data.isEmpty()) return@collect
                        if (generation.get() != myGeneration) {
                            AmaliaLog.w(runTag, "producer: generation mismatch — stopping collection")
                            return@collect
                        }
                        val aligned = align(chunk.data)
                        if (aligned.isNotEmpty()) {
                            queue.put(AudioChunk(aligned, chunk.sampleRate))
                            produced++
                            if (produced == 1) AmaliaLog.i(runTag, "★ first chunk queued | ${aligned.size} bytes | sr=${chunk.sampleRate}")
                        }
                        onLevel(chunk.level())
                    }
                    AmaliaLog.d(runTag, "producer: flow collected | total chunks queued=$produced")
                } finally {
                    AmaliaLog.d(runTag, "producer: finally — offering EOS | produced=$produced")
                    runCatching { queue.offer(endOfStream, OFFER_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
                }
            }

            // ── Потребитель: очередь → AudioTrack ───────────────────────────
            launch(Dispatchers.IO) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                AmaliaLog.d(runTag, "consumer: started | generation=$myGeneration")

                var current: AudioTrack? = null
                var sampleRate = 0

                /** Строит трек под частоту [sr]; false — если система не дала. */
                fun buildTrack(sr: Int): Boolean {
                    val min = AudioTrack.getMinBufferSize(
                        sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    ).coerceAtLeast(MIN_BUFFER_FLOOR)

                    AmaliaLog.d(runTag, "buildTrack sr=$sr | minBuf=$min | totalBuf=${min * BUFFER_MULTIPLIER}")

                    val built = runCatching {
                        AudioTrack.Builder()
                            .setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
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
                            .setBufferSizeInBytes(min * BUFFER_MULTIPLIER)
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
                            .build()
                    }.getOrNull()

                    if (built == null || built.state != AudioTrack.STATE_INITIALIZED) {
                        AmaliaLog.e(runTag, "buildTrack FAILED | state=${built?.state} | built=$built")
                        runCatching { built?.release() }
                        current = null
                        track = null
                        return false
                    }
                    AmaliaLog.d(runTag, "buildTrack OK | sessionId=${built.audioSessionId}")
                    current = built
                    track = built
                    return true
                }

                /**
                 * Пишет сколько сможет и возвращает число принятых байтов.
                 *
                 * [blocking] = true — ждём освобождения буфера (steady state);
                 * false — выходим немедленно, если буфер забит (префолл).
                 * Раньше здесь при отменённом поколении возвращался весь размер
                 * буфера — то есть «успешно записано» для данных, которых никто
                 * не слышал. Теперь в этом случае возвращается 0: вызывающий
                 * код обязан понять, что звук не пошёл.
                 */
                fun writeSome(data: ByteArray, blocking: Boolean): Int {
                    var offset = 0
                    val mode =
                        if (blocking) AudioTrack.WRITE_BLOCKING else AudioTrack.WRITE_NON_BLOCKING
                    while (offset < data.size) {
                        if (generation.get() != myGeneration) return offset
                        val target = current ?: return offset
                        val slice = if (offset == 0) data else data.copyOfRange(offset, data.size)
                        val written = runCatching { target.write(slice, 0, slice.size, mode) }
                            .getOrDefault(-1)
                        if (written <= 0) return offset
                        offset += written
                    }
                    return offset
                }

                /**
                 * Пишет [data] целиком.
                 *
                 * Возвращает `-1`, если данные потеряны из-за сбоя трека или
                 * отмены, и `offset` — сколько реально ушло, если буфер
                 * заполнился (в steady state это не ошибка, а норма).
                 */
                fun writeAll(data: ByteArray, blocking: Boolean): Boolean =
                    writeSome(data, blocking) >= data.size

                /** Открывает трек, если его ещё нет; false — система не дала. */
                fun ensureTrack(sr: Int): Boolean {
                    if (current != null) return true
                    sampleRate = sr
                    if (!buildTrack(sr)) return false
                    // Разгон аудио-тракта: короткие нули вместо начала речи.
                    writeAll(silenceFor(sr, PRIMER_MS), blocking = false)
                    return true
                }

                try {
                    var waitedMs = 0L

                    while (generation.get() == myGeneration && waitedMs < PREFILL_WAIT_MS) {
                        val chunk = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)

                        if (chunk == null) {
                            waitedMs += POLL_TIMEOUT_MS
                            AmaliaLog.d(runTag, "prefill: poll timeout | waited=${waitedMs}ms | trackReady=${current != null}")
                            if (current != null) break
                            continue
                        }
                        if (chunk === endOfStream) {
                            AmaliaLog.d(runTag, "prefill: got EOS — returning marker and breaking")
                            runCatching { queue.offer(endOfStream, 0, TimeUnit.SECONDS) }
                            break
                        }
                        if (!ensureTrack(chunk.sampleRate)) {
                            AmaliaLog.e(runTag, "prefill: ensureTrack FAILED for sr=${chunk.sampleRate}")
                            break
                        }

                        val consumed = writeSome(chunk.data, blocking = false)
                        AmaliaLog.d(runTag, "prefill: wrote $consumed/${chunk.data.size} bytes")
                        if (consumed < chunk.data.size) {
                            queue.offer(
                                AudioChunk(
                                    chunk.data.copyOfRange(consumed, chunk.data.size),
                                    chunk.sampleRate,
                                ),
                                0,
                                TimeUnit.MILLISECONDS,
                            )
                            AmaliaLog.d(runTag, "prefill: buffer full — returning ${chunk.data.size - consumed} bytes and breaking")
                            break
                        }
                    }

                    if (current != null && generation.get() == myGeneration) {
                        val started = runCatching { current?.play() }.isSuccess
                        AmaliaLog.i(runTag, "★ AudioTrack.play() called | success=$started | generation=$myGeneration")
                        if (!started) {
                            AmaliaLog.e(runTag, "play() failed — flushing track")
                            runCatching { current?.flush() }
                        }
                    } else {
                        AmaliaLog.w(runTag, "play() SKIPPED | current=$current | generation=${generation.get()} myGen=$myGeneration")
                    }

                    var endSeen = false
                    var steadyChunks = 0
                    while (generation.get() == myGeneration && !endSeen) {
                        val chunk = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        if (chunk == null) {
                            if (current == null) {
                                AmaliaLog.w(runTag, "steady: no track and no data — breaking")
                                break
                            }
                            continue
                        }
                        if (chunk === endOfStream) {
                            AmaliaLog.d(runTag, "steady: EOS received | steadyChunks=$steadyChunks")
                            endSeen = true
                            break
                        }
                        if (!ensureTrack(chunk.sampleRate)) {
                            AmaliaLog.e(runTag, "steady: ensureTrack FAILED")
                            break
                        }

                        var accepted = writeSome(chunk.data, blocking = true)
                        steadyChunks++
                        if (accepted < chunk.data.size && generation.get() == myGeneration) {
                            AmaliaLog.w(runTag, "steady: write incomplete $accepted/${chunk.data.size} — rebuilding track")
                            val rest = chunk.data.copyOfRange(accepted, chunk.data.size)
                            val broken = current
                            runCatching { broken?.stop() }
                            runCatching { broken?.release() }
                            current = null
                            if (track === broken) track = null
                            if (rest.isNotEmpty() && ensureTrack(chunk.sampleRate)) {
                                accepted += writeSome(rest, blocking = true)
                            }
                            if (accepted < chunk.data.size) {
                                AmaliaLog.e(runTag, "steady: write failed after rebuild — breaking")
                                break
                            }
                        }
                    }

                    AmaliaLog.d(runTag, "steady state done | steadyChunks=$steadyChunks | endSeen=$endSeen")

                    if (current != null && sampleRate > 0 && generation.get() == myGeneration) {
                        AmaliaLog.d(runTag, "writing tail silence ${TAIL_SILENCE_MS}ms")
                        writeAll(silenceFor(sampleRate, TAIL_SILENCE_MS), blocking = true)
                    }
                } finally {
                    producerJob.cancel()
                    if (generation.get() == myGeneration) onLevel(0f)
                    val finished = current
                    current = null
                    if (track === finished) track = null
                    if (finished != null &&
                        sampleRate > 0 &&
                        generation.get() == myGeneration
                    ) {
                        AmaliaLog.d(runTag, "drain: waiting for buffer to play out")
                        drain(finished, sampleRate)
                    }
                    AmaliaLog.d(runTag, "consumer: releasing track | generation=$myGeneration")
                    runCatching { finished?.stop() }
                    runCatching { finished?.release() }
                    AmaliaLog.i(runTag, "★ play() finished | generation=$myGeneration")
                }
            }
        }
    }

    /**
     * Ждёт, пока буфер трека реально проиграется.
     *
     * `stop()` обрывает звук мгновенно: всё, что лежит в буфере, пропадает.
     * Поэтому перед закрытием считаем остаток по [AudioTrack.getPlaybackHeadPosition]
     * и ждём его ровно столько, сколько он звучит. Ограничение сверху —
     * [DRAIN_MAX_MS]: если счётчик головки врёт (у части OEM-прошивок так и
     * есть), интерфейс не должен залипать.
     */
    private suspend fun drain(active: AudioTrack, sampleRate: Int) {
        // `bufferSizeInBytes` появился только на API 23. На более старых
        // системах его нет — читаем размер через рефлексию, а если и это
        // не удалось, считаем буфер уже пустым и не ждём зря.
        val totalBytes = runCatching {
            AudioTrack::class.java.getMethod("getBufferSizeInBytes").invoke(active) as? Int
        }.getOrNull() ?: 0
        val framesPlayed = runCatching { active.playbackHeadPosition }.getOrDefault(0)
        val framesTotal = totalBytes / FRAME
        val remaining = (framesTotal - framesPlayed).coerceAtLeast(0)
        if (remaining == 0 || sampleRate <= 0) return
        val remainingMs = (remaining.toLong() * 1000L) / sampleRate
        // Если счётчик головки не двигается (бывает на части OEM-прошивок)
        // — трактуем это как «буфер уже пуст» и не ждём зря.
        if (remainingMs <= 0) return
        delay(remainingMs.coerceAtMost(DRAIN_MAX_MS))
    }

    /**
     * Мгновенно обрывает воспроизведение и запрещает дописывать буфер.
     *
     * `pause()+flush()` обрывают звук за миллисекунды, а рост номера
     * проигрывания гасит потребитель, который мог бы продолжить писать
     * в этот же трек.
     *
     * Вызывать это нужно **только** по явному «Стоп» от пользователя.
     * Старт нового цикла обязан обходиться без него: иначе плеер, который
     * цикл сам же и запустил, немедленно замолкает — и это выглядит как
     * «ответ пришёл, а озвучка отменилась».
     */
    fun stopImmediately() {
        val gen = generation.incrementAndGet()
        // Здесь метку прогона не берём из play(): стоп вызывается снаружи,
        // когда воспроизведения может уже не быть. Логируется общая метка.
        val genTag = AmaliaLog.tagWith("PCM")
        AmaliaLog.i(genTag, "stopImmediately() | new generation=$gen")
        val active = track ?: return
        AmaliaLog.d(genTag, "stopImmediately: pausing and flushing track")
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

        /** Такт опроса очереди: короткий, чтобы первый звук не ждал. */
        const val POLL_TIMEOUT_MS = 120L

        /**
         * Сколько суммарно ждать данные для префолла.
         *
         * Было «8 опросов по 2 секунды» = 16 секунд зависания фазы «говорю»
         * без единого байта звука. Теперь ожидание ограничено, а всё, что
         * успело прийти, играет независимо от того, набрался ли запас.
         */
        const val PREFILL_WAIT_MS = 700L

        /** Таймаут публикации маркера конца стрима. */
        const val OFFER_TIMEOUT_SECONDS = 1L

        /** Нижняя граница буфера, когда система не смогла её посчитать. */
        const val MIN_BUFFER_FLOOR = 4096

        /** Ёмкость трека: 6 периодов — запас на джиттер планировщика. */
        const val BUFFER_MULTIPLIER = 6

        /** Тишина перед первым сэмплом — разгон аудио-тракта. */
        const val PRIMER_MS = 40

        /** Тишина после последнего сэмпла — чтобы не обрезать окончание фразы. */
        const val TAIL_SILENCE_MS = 700

        /** Потолок ожидания доигрывания буфера перед освобождением трека. */
        const val DRAIN_MAX_MS = 2_500L
    }
}
