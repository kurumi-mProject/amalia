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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Воспроизводит поток [AudioChunk] (PCM 16-bit mono) через [AudioTrack].
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ПОЧЕМУ ЗВУКА НЕ БЫЛО — И ЧТО ТЕПЕРЬ УСТРОЕНО ИНАЧЕ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Симптом: модель отвечает, текст на экране есть, звука нет — и никакой
 * ошибки тоже нет. Разбор реального лога показал картину до миллисекунд:
 *
 * ```
 * 55.473  play() start | generation=1        плеер открылся и начал ждать
 * 56.202  play() SKIPPED | current=null      «префолл» кончился (700 мс)
 * 56.323  ★ play() finished                  плеер закрылся и освободил трек
 * 56.933  ★ LLM response ready               LLM только-только отдал текст
 * 58.176  ★ FIRST CHUNK in 1241ms            звук пришёл через 1,8 с
 * 58.214… 428 × Audio chunk → audioChannel   все ушли в пустоту
 * ```
 *
 * Триста сорок килобайт отличного PCM (≈7 секунд речи) доехали до очереди,
 * но играть их было уже некому: плеер считал, что «если за 700 мс ничего не
 * пришло, значит синтез не работает» и уходил. Это была принципиальная
 * ошибка проектирования, а не таймаут-настройка: **плеер не тот компонент,
 * который решает, успеет ли синтез**. Он часть того же конвейера и обязан
 * ждать столько, сколько идёт генерация.
 *
 * ## Три правила, на которых теперь стоит плеер
 *
 *  1. **Ожидание — до конца потока, а не до таймаута.** Пока канал открыт,
 *     данных нет и стоп не запрошен — плеер ждёт. Признак «звука больше не
 *     будет» ровно один: маркер конца стрима (канал закрылся у продюсера).
 *  2. **Ждать бесконечно — значит не мешать пользователю.** На время
 *     ожидания UI не блокируется: поток живёт в своей корутине, `Speaking`
 *     держится на уровне ViewModel, а кнопка «Стоп» доступна всегда.
 *  3. **Трек строится лениво — по первому байту.** [AudioTrack] создаётся
 *     в момент, когда звук действительно появился, поэтому драйвер не
 *     занимается впустую и не отваливается, пока синтез думает.
 *
 *  * Когда ждать действительно нельзя: если синтез упал и продюсер завершился,
 * не прислав ни одного байта, ждать нечего — плеер выходит сам, как только
 * видит, что поток закрыт, а очередь пуста. Этот случай отличается от
 * «синтез ещё думает» именно смертью продюсера, а не истечением таймера.
 *
 * ## Остальные детали, которые здесь важны
 *
 * - **Выравнивание кадров** делает продюсер: PCM-16 — кадры по 2 байта,
 *   нечётный кусок из сети иначе сдвигал бы весь тракт на полкадра, и в
 *   начале слышался треск.
 * - **Тишина перед первым сэмплом** ([PRIMER_MS]): аудио-тракт и ЦАП
 *   поднимаются не мгновенно, без этого проглатывалась первая гласная.
 * - **Хвост тишины** ([TAIL_SILENCE_MS]) и **drain**: без них `stop()`
 *   обрывает окончание фразы, которое ещё не сошло из буфера в динамик.
 * - **Ремонт трека**: если система отобрала аудио-фокус и `write` сорвался,
 *   плеер собирает трек заново и дописывает остаток с того же байта.
 * - `USAGE_ASSISTANT`: ассистент должен звучать как ассистент, а не как
 *   медиаплеер, — от этого зависит поведение микшера и уважение к музыке.
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
        // Метка прогона: логи плеера должны нести тот же идентификатор, что и
        // логи синтеза, иначе связать «чей это звук» по логу невозможно.
        val runTag = AmaliaLog.tagWith("PCM")
        AmaliaLog.i(runTag, "► play() start | generation=$myGeneration")
        carry = null

        val queue = ArrayBlockingQueue<AudioChunk>(QUEUE_CAPACITY)
        val producerAlive = AtomicBoolean(true)

        coroutineScope {
            // ── Продюсер: Flow → очередь, с гарантией чётности кадров ──────
            val producerJob = launch(Dispatchers.IO) {
                var produced = 0
                try {
                    chunks.collect { chunk ->
                        if (chunk.data.isEmpty()) return@collect
                        if (generation.get() != myGeneration) return@collect
                        val aligned = align(chunk.data)
                        if (aligned.isNotEmpty()) {
                            queue.put(AudioChunk(aligned, chunk.sampleRate))
                            produced++
                            if (produced == 1) {
                                AmaliaLog.i(
                                    runTag,
                                    "★ first chunk queued | ${aligned.size} bytes | sr=${chunk.sampleRate}",
                                )
                            }
                        }
                        onLevel(chunk.level())
                    }
                    AmaliaLog.d(runTag, "producer: flow collected | chunks queued=$produced")
                } finally {
                    // Продюсер умер. Дальше очередь опустеет навсегда — это
                    // единственный достоверный признак, что «звука больше не
                    // будет», кроме явного маркера конца.
                    producerAlive.set(false)
                    AmaliaLog.d(runTag, "producer: finished | queued=$produced — sending EOS")
                    runCatching { queue.offer(endOfStream, OFFER_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
                }
            }

            // ── Потребитель: очередь → AudioTrack ───────────────────────────
            launch(Dispatchers.IO) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                AmaliaLog.d(runTag, "consumer: started | generation=$myGeneration")

                var current: AudioTrack? = null
                var sampleRate = 0
                var trackStarted = false
                var playedChunks = 0

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
                        AmaliaLog.e(runTag, "buildTrack FAILED sr=$sr | state=${built?.state}")
                        runCatching { built?.release() }
                        current = null
                        track = null
                        return false
                    }
                    AmaliaLog.d(runTag, "buildTrack OK sr=$sr | session=${built.audioSessionId}")
                    current = built
                    track = built
                    return true
                }

                /**
                 * Пишет сколько сможет и возвращает число принятых байтов.
                 *
                 * [blocking] = true — ждём освобождения буфера; false — выходим
                 * немедленно, если буфер забит.
                 *
                 * При отменённом поколении возвращается 0, а не размер буфера:
                 * вызывающий код обязан понять, что звук не пошёл. Раньше здесь
                 * возвращался весь размер — то есть «успешно записано» для
                 * данных, которых никто не слышал.
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
                 * Открывает трек под частоту [sr] и запускает воспроизведение.
                 *
                 * Вызывается **по первому байту** — до этого момента драйвер
                 * не занимается, и система не может «отобрать» несуществующий
                 * трек. Именно отсутствие звука до первого чанка раньше
                 * трактовалось как сбой и убивало всё воспроизведение.
                 */
                fun startTrack(sr: Int): Boolean {
                    if (current != null) return true
                    sampleRate = sr
                    if (!buildTrack(sr)) return false
                    // Разгон тракта: короткие нули вместо начала речи.
                    writeSome(silenceFor(sr, PRIMER_MS), blocking = false)
                    val started = runCatching { current?.play() }.isSuccess
                    trackStarted = started
                    if (started) {
                        AmaliaLog.i(
                            runTag,
                            "★ AudioTrack.play() | sr=$sr | session=${current?.audioSessionId}",
                        )
                    } else {
                        AmaliaLog.e(runTag, "AudioTrack.play() FAILED — flushing")
                        runCatching { current?.flush() }
                    }
                    return started
                }

                /**
                 * Пишет [data] целиком, при необходимости чиня трек.
                 *
                 * @return true, если все байты приняты (или приняты после
                 *   пересборки трека); false — если звук физически не ушёл.
                 */
                fun writeChunk(data: ByteArray): Boolean {
                    var accepted = writeSome(data, blocking = true)
                    if (accepted >= data.size) return true
                    if (generation.get() != myGeneration) return false

                    // Трек отвалился: система отобрала аудио-фокус или драйвер
                    // уронил поток. Пересобираем и дописываем остаток — терять
                    // середину фразы из-за одного сбоя нельзя.
                    AmaliaLog.w(
                        runTag,
                        "write incomplete $accepted/${data.size} — rebuilding track",
                    )
                    val rest = data.copyOfRange(accepted, data.size)
                    val broken = current
                    runCatching { broken?.stop() }
                    runCatching { broken?.release() }
                    current = null
                    if (track === broken) track = null
                    trackStarted = false

                    if (!startTrack(sampleRate)) return false
                    accepted += writeSome(rest, blocking = true)
                    return accepted >= data.size
                }

                try {
                    var endSeen = false
                    while (generation.get() == myGeneration && !endSeen) {
                        val chunk = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)

                        // ── Данных пока нет ─────────────────────────────────
                        if (chunk == null) {
                            // Ждём ровно две вещи: конец потока или стоп.
                            // Пока продюсер жив, «нет данных» означает лишь
                            // «синтез думает» — ждём дальше, сколько нужно.
                            if (producerAlive.get()) continue
                            // Продюсер мёртв. Досчитаем до маркера конца, но
                            // не дольше, чем разумно: маркер может прийти
                            // следующим poll'ом.
                            AmaliaLog.d(
                                runTag,
                                "waiting: producer finished, draining queue | played=$playedChunks",
                            )
                            break
                        }

                        // ── Конец потока ────────────────────────────────────
                        if (chunk === endOfStream) {
                            AmaliaLog.i(
                                runTag,
                                "★ EOS received | played=$playedChunks chunks | trackStarted=$trackStarted",
                            )
                            endSeen = true
                            break
                        }

                        // ── Первый байт: поднимаем тракт ────────────────────
                        if (current == null && !startTrack(chunk.sampleRate)) {
                            AmaliaLog.e(runTag, "track failed to start — aborting playback")
                            break
                        }

                        if (!writeChunk(chunk.data)) {
                            AmaliaLog.e(runTag, "chunk write failed — aborting playback")
                            break
                        }
                        playedChunks++
                        if (playedChunks == 1) {
                            AmaliaLog.i(runTag, "★ first chunk written to track | audio is playing")
                        }
                    }

                    AmaliaLog.d(
                        runTag,
                        "playback loop done | played=$playedChunks | endSeen=$endSeen | started=$trackStarted",
                    )

                    // Хвост тишины: без него stop() обрывает последние слова.
                    if (current != null && sampleRate > 0 && generation.get() == myGeneration) {
                        writeSome(silenceFor(sampleRate, TAIL_SILENCE_MS), blocking = true)
                    }
                } finally {
                    producerJob.cancel()
                    if (generation.get() == myGeneration) onLevel(0f)

                    val finished = current
                    current = null
                    if (track === finished) track = null

                    // Слить буфер обязательно, иначе обрывается окончание
                    // фразы. Отмена (поколение выросло) сюда не заходит —
                    // она обязана замолчать мгновенно.
                    if (finished != null && sampleRate > 0 && generation.get() == myGeneration) {
                        drain(finished, sampleRate)
                    }
                    runCatching { finished?.stop() }
                    runCatching { finished?.release() }
                    AmaliaLog.i(
                        runTag,
                        "■ play() finished | played=$playedChunks chunks | generation=$myGeneration",
                    )
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
        // `bufferSizeInBytes` появился только на API 23. Читаем через
        // рефлексию, а если не удалось — считаем буфер пустым и не ждём.
        val totalBytes = runCatching {
            AudioTrack::class.java.getMethod("getBufferSizeInBytes").invoke(active) as? Int
        }.getOrNull() ?: 0
        val framesPlayed = runCatching { active.playbackHeadPosition }.getOrDefault(0)
        val framesTotal = totalBytes / FRAME
        val remaining = (framesTotal - framesPlayed).coerceAtLeast(0)
        if (remaining == 0 || sampleRate <= 0) return
        val remainingMs = (remaining.toLong() * 1000L) / sampleRate
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
        val genTag = AmaliaLog.tagWith("PCM")
        AmaliaLog.i(genTag, "stopImmediately() | new generation=$gen")
        val active = track ?: run {
            AmaliaLog.d(genTag, "stopImmediately: no active track")
            return
        }
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
