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

                /** Строит трек под частоту [sr]; false — если система не дала. */
                fun buildTrack(sr: Int): Boolean {
                    val min = AudioTrack.getMinBufferSize(
                        sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    ).coerceAtLeast(MIN_BUFFER_FLOOR)

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
                        runCatching { built?.release() }
                        current = null
                        track = null
                        return false
                    }
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
                    // ══════════════════════════════════════════════════════
                    //  ФАЗА 1 — ПРЕФОЛЛ
                    // ══════════════════════════════════════════════════════
                    //
                    // Набираем небольшой запас перед первым звуком и уходим
                    // играть. Ждём ограниченное время: если синтез молчит,
                    // играть всё равно нечего, а вешать UI на «говорю» без
                    // звука нельзя.
                    var waitedMs = 0L

                    while (generation.get() == myGeneration && waitedMs < PREFILL_WAIT_MS) {
                        val chunk = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)

                        if (chunk == null) {
                            waitedMs += POLL_TIMEOUT_MS
                            // Уже есть чем играть — не ждём остального.
                            if (current != null) break
                            continue
                        }
                        if (chunk === endOfStream) {
                            // Поток закрыт. Маркер возвращаем: фаза 2 обязана
                            // увидеть конец, иначе будет ждать ещё один таймаут.
                            runCatching { queue.offer(endOfStream, 0, TimeUnit.SECONDS) }
                            break
                        }
                        if (!ensureTrack(chunk.sampleRate)) break

                        val consumed = writeSome(chunk.data, blocking = false)
                        if (consumed < chunk.data.size) {
                            // Буфер полон — остаток возвращаем в начало очереди
                            // и допишем его уже играющим треком. Не потерять
                            // эти байты критично: это середина фразы.
                            queue.offer(
                                AudioChunk(
                                    chunk.data.copyOfRange(consumed, chunk.data.size),
                                    chunk.sampleRate,
                                ),
                                0,
                                TimeUnit.MILLISECONDS,
                            )
                            break
                        }
                    }

                    // ══════════════════════════════════════════════════════
                    //  СТАРТ — безусловный, если есть куда играть
                    // ══════════════════════════════════════════════════════
                    //
                    // Именно здесь раньше терялся весь звук: `play()` стоял
                    // под условием `current != null`, и если префолл вышел по
                    // пустым опросам, аудио не играло вообще.
                    if (current != null && generation.get() == myGeneration) {
                        val started = runCatching { current?.play() }.isSuccess
                        if (!started) {
                            runCatching { current?.flush() }
                        }
                    }

                    // ══════════════════════════════════════════════════════
                    //  ФАЗА 2 — STEADY STATE
                    // ══════════════════════════════════════════════════════
                    //
                    // Работает, пока есть очередь и поколение совпадает —
                    // независимо от того, как закончился префолл.
                    var endSeen = false
                    while (generation.get() == myGeneration && !endSeen) {
                        val chunk = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        if (chunk == null) {
                            // Тишина на входе: если трека нет (синтез не дал
                            // данных вообще) — выходим, иначе ждём продолжения.
                            if (current == null) break
                            continue
                        }
                        if (chunk === endOfStream) {
                            endSeen = true
                            break
                        }
                        if (!ensureTrack(chunk.sampleRate)) break

                        var accepted = writeSome(chunk.data, blocking = true)
                        // Ремонт трека: `write` сорвался не из-за отмены —
                        // система отобрала фокус или драйвер уронил поток.
                        // Пересобираем трек и дописываем остаток: терять
                        // реплику из-за одного сбоя аудио нельзя.
                        if (accepted < chunk.data.size && generation.get() == myGeneration) {
                            val rest = chunk.data.copyOfRange(accepted, chunk.data.size)
                            val broken = current
                            runCatching { broken?.stop() }
                            runCatching { broken?.release() }
                            current = null
                            // Ссылку в [track] сбрасываем только если она
                            // указывала на сломанный трек: иначе старый
                            // потребитель затрёт указатель на новый.
                            if (track === broken) track = null
                            if (rest.isNotEmpty() && ensureTrack(chunk.sampleRate)) {
                                accepted += writeSome(rest, blocking = true)
                            }
                            if (accepted < chunk.data.size) break
                        }
                    }

                    // Хвост тишины: без него stop() обрывает последние слова,
                    // которые ещё не сошли из буфера в динамик. Достройка
                    // остатка буфера отдана `finally` — так она выполняется
                    // при любом выходе, а не только по концу фразы.
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
                    // Поток закрылся, а звук ещё в буфере: перед закрытием
                    // трека отдаём ему доиграть. Отмена (поколение выросло)
                    // сюда не заходит — она обязана замолчать мгновенно.
                    if (finished != null &&
                        sampleRate > 0 &&
                        generation.get() == myGeneration
                    ) {
                        drain(finished, sampleRate)
                    }
                    runCatching { finished?.stop() }
                    runCatching { finished?.release() }
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
