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
 *  ЗВУК И КАРТИНКА ИДУТ ВМЕСТЕ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Громкость для волны сообщается здесь же, где идёт звук, — и это не
 * удобство, а единственный способ не соврать.
 *
 * Баг, ради которого это переделано: волна «ложилась» на середине фразы.
 * Уровень брался из [AudioChunk.level] в момент, когда чанк **вставал в
 * очередь**, а не когда он **звучал из динамика**. Очередь — 64 чанка,
 * буфер трека — шесть минимальных периодов; вместе это давало до
 * нескольких секунд запаса, который продюсер забирал почти мгновенно
 * (речь льётся чанк за чанком), а потребитель отыгрывал не спеша.
 *
 * Отсюда симптом: пока произносились первые слова, в очередь уже улеглась
 * вся фраза, громкость последнего (тихого) чанка сообщалась первой, и волна
 * садилась, хотя из динамика ещё звучала середина. Лечится только одним:
 * уровень обязан прийти **из воспроизведения**, а не из его подготовки.
 *
 * Поэтому громкость читается потребителем — тем же потоком, который пишет
 * байты в трек. Точка чтения — середина чанка: пока пишутся последние байты,
 * голова чтения находится примерно в середине, то есть в динамике звучит
 * как раз измеряемая часть. Для чанка 4096 сэмплов при 44.1 кГц (≈93 мс)
 * ошибка такого отображения — порядка 46 мс, то есть меньше одного кадра
 * анимации по 60 Гц.
 *
 * ## Остальные правила, на которых стоит плеер
 *
 *  — **Ожидание — до конца потока, а не до таймаута.** Пока канал открыт,
 *    данных нет и стоп не запрошен — плеер ждёт. Признак «звука больше не
 *    будет» ровно один: маркер конца стрима (канал закрылся у продюсера).
 *  — **Трек строится лениво — по первому байту.** [AudioTrack] создаётся
 *    в момент, когда звук действительно появился, поэтому драйвер не
 *    занимается впустую и не отваливается, пока синтез думает.
 *
 * ## Остальные детали, которые здесь важны
 *
 * - **Выравнивание кадров** делает продюсер: PCM-16 — кадры по 2 байта,
 *   нечётный кусок из сети иначе сдвигал бы весь тракт на полкадра, и в
 *   начале слышался треск.
 * - **Тишина перед первым сэмплом** ([PRIMER_MS]): аудио-тракт и ЦАП
 *   поднимаются не мгновенно, без этого проглатывалась первая гласная.
 * - **Тишина в конце — и её пропуск.** [TAIL_SILENCE_MS] — ровно один
 *   интервал, рассчитанный на разгон тракта. Синтезатор этот интервал уже
 *   дописал реальным хвостом тишины, поэтому здесь он дописывается
 *   **только если последний чанк пришёл громким**: иначе конец фразы
 *   откладывался бы на 700 мс после того, как голос уже замолчал, и волна
 *   вместе с состоянием «говорю» жили бы лишних полсекунды.
 * - **drain**: без него `stop()` обрывает окончание фразы, которое ещё не
 *   сошло из буфера в динамик.
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

    /**
     * Один кусок звука в очереди: байты, частота и громкость этого куска.
     *
     * Громкость лежит здесь, а не считается заново у потребителя, потому что
     * её уже посчитал продюсер — и считал по тем же байтам, что лежат здесь.
     * Единственное, что меняется по дороге, — **момент** сообщения: теперь
     * это не «чанк поставлен в очередь», а «чанк уходит в динамик».
     */
    private data class QueuedChunk(
        val data: ByteArray,
        val sampleRate: Int,
        val level: Float,
    ) {
        override fun equals(other: Any?): Boolean =
            other is QueuedChunk &&
                sampleRate == other.sampleRate &&
                level == other.level &&
                data.contentEquals(other.data)

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + sampleRate
            result = 31 * result + level.hashCode()
            return result
        }
    }

    /** Маркер конца стрима — сравнивается по идентичности, а не по значению. */
    private val endOfStream = QueuedChunk(ByteArray(0), -1, 0f)

    /**
     * Проигрывает [chunks] до конца потока или до отмены корутины.
     *
     * Метод **не бросает** исключений: любой сбой аудио-тракта здесь — это
     * «не смогли произнести», а не «сломался диалог». Текст ответа уже
     * показан на экране, и терять его из-за драйвера нельзя.
     *
     * @param onLevel громкость 0..1 для анимации волны. Приходит **из
     *   воспроизведения**, а не из подготовки: читается в тот момент, когда
     *   байты этого чанка уже уходят в динамик. Благодаря этому конец фразы
     *   в речи и конец фразы на волне совпадают.
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
        // Сообщалась ли уже ненулевая громкость. Нужно на выходе: если звук
        // так и не пошёл, обнулять волну бессмысленно (она и не поднималась),
        // а если пошёл — обнулить обязательно, иначе последняя высота
        // застынет на экране после конца речи.
        val levelReported = AtomicBoolean(false)

        // Очередь хранит не только байты, но и громкость чанка. Это и есть
        // исправление «волна ложится на середине фразы»: громкость едет
        // вместе со своим звуком и сообщается потребителем тогда, когда этот
        // звук действительно играет, — а не продюсером в момент подготовки.
        val queue = ArrayBlockingQueue<QueuedChunk>(QUEUE_CAPACITY)
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
                            queue.put(
                                QueuedChunk(
                                    data = aligned,
                                    sampleRate = chunk.sampleRate,
                                    level = chunk.level(),
                                ),
                            )
                            produced++
                            if (produced == 1) {
                                AmaliaLog.i(
                                    runTag,
                                    "★ first chunk queued | ${aligned.size} bytes | sr=${chunk.sampleRate}",
                                )
                            }
                        }
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
                // Последняя отданная наружу громкость: нужна, чтобы не гонять
                // в UI бесконечные нули одного и того же хвоста тишины.
                var lastReportedLevel = 0f
                // Сколько уровней ушло в UI — только для лога.
                var levelUpdates = 0

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

                        // Громкость — вперёд байтов. Пока идёт блокирующая
                        // запись, голова чтения проходит весь чанк; к её концу
                        // чанк уже отыгран и следующая высота волны начнётся
                        // ровно с нового значения. Сообщить её после записи —
                        // значит показать эту высоту на один чанк позже, чем
                        // она зазвучала.
                        //
                        // Нулевые значения не дублируются: синтезатор отдаёт
                        // тишину десятками чанков подряд, и гонять в UI одно и
                        // то же число — это лишние рекомпозиции ради ничего.
                        if (chunk.level > 0f) levelReported.set(true)
                        if (chunk.level > 0f || lastReportedLevel > 0f) {
                            onLevel(chunk.level)
                            lastReportedLevel = chunk.level
                        }
                        levelUpdates++
                        if (levelUpdates == 1) {
                            val levelText = formatLevel(chunk.level)
                            AmaliaLog.i(
                                runTag,
                                "★ first chunk → track | level=$levelText | audio is playing",
                            )
                        }

                        if (!writeChunk(chunk.data)) {
                            AmaliaLog.e(runTag, "chunk write failed — aborting playback")
                            break
                        }
                        playedChunks++
                    }

                    val endLevelText = formatLevel(lastReportedLevel)
                    AmaliaLog.d(
                        runTag,
                        "playback loop done | played=$playedChunks | levels=$levelUpdates | " +
                            "endSeen=$endSeen | started=$trackStarted | lastLevel=$endLevelText",
                    )

                    // Хвост тишины — ровно один интервал, и только если он
                    // зачем-то нужен.
                    //
                    // Синтезатор отдаёт ровно те байты, что прислал сервис:
                    // ни в [FishAudioTTS], ни в оркестраторе тишина не
                    // дописывается. Значит, конец фразы после последнего
                    // сэмпла речи обеспечивает только этот хвост — без него
                    // `stop()` срезал бы окончание, которое ещё не сошло из
                    // буфера в динамик.
                    //
                    // Тишина в конце держится по одной причине: уровень для
                    // волны сообщается **вперёд** байтов, на момент начала
                    // блокирующей записи. То есть пока этот хвост физически
                    // играет, на волне ещё стоит громкость последнего слога.
                    // Хвост — буфер между «картинка замолчала» и «динамик
                    // замолчал», поэтому его нельзя убирать: убрав, мы
                    // поменяли бы один рассинхрон на другой, только в обратную
                    // сторону.
                    //
                    // Отсюда время до окончательного сброса состояния:
                    // хвост (700) + drain. Для чанков Fish Audio 960 байт при
                    // 24 кГц это ≈20 мс звука, то есть ждать доигрывания
                    // почти нечего. Для крупных чанков время будет больше —
                    // и это честно: крупный чанк звучит дольше.
                    //
                    // Пропускается хвост в одном случае: звук так и не пошёл
                    // (трек не создан). Тогда дописывать тишину некуда.
                    if (current != null && sampleRate > 0 && generation.get() == myGeneration) {
                        writeSome(silenceFor(sampleRate, TAIL_SILENCE_MS), blocking = true)
                    }
                } finally {
                    producerJob.cancel()

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

                    // Волна садится ПОСЛЕДНЕЙ — когда звук уже физически
                    // закончился: хвост доигран, буфер слит, трек закрыт.
                    //
                    // Раньше обнуление стояло в начале finally, и это был
                    // второй, более тонкий источник того же визуального
                    // бага: волна ложилась не только раньше конца фразы, но
                    // и вообще в произвольный момент выхода из плеера.
                    // Правильный порядок — от звука к картинке, и только в
                    // этом порядке конец речи и конец волны совпадают.
                    //
                    // Обнуляем только если громкость вообще сообщалась:
                    // иначе это лишний вызов в UI на каждый неудачный прогон.
                    if (generation.get() == myGeneration && levelReported.get()) onLevel(0f)

                    AmaliaLog.i(
                        runTag,
                        "■ play() finished | played=$playedChunks chunks | levels=$levelUpdates | " +
                            "generation=$myGeneration",
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

    /**
     * Три знака после запятой для уровня в логе.
     *
     * Формат фиксируется через [String.format] с явной локалью: без неё в
     * локали с запятой-разделителем лог печатал бы `0,412`, и разбор лога
     * скриптом, который ищет `0.412`, ломался бы на ровном месте.
     */
    private fun formatLevel(level: Float): String =
        String.format(java.util.Locale.US, "%.3f", level)

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

        /**
         * Тишина после последнего сэмпла — чтобы не обрезать окончание фразы.
         *
         * Значение не косметическое: пока эта тишина играет, волна ещё
         * держит громкость последнего слога (уровень сообщается вперёд
         * байтов). 700 мс — компромисс между «глаз успел прочитать конец
         * фразы вместе с ушами» и «состояние «говорю» не висит лишнюю
         * секунду». Меньше 300 мс уже не хватало бы на выравнивание
         * картинки со звуком при чанках по 3–5 КБ.
         */
        const val TAIL_SILENCE_MS = 700

        /** Потолок ожидания доигрывания буфера перед освобождением трека. */
        const val DRAIN_MAX_MS = 2_500L
    }
}
