package com.my.amali.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Отказоустойчивый синтез речи: облачный движок + системный голос Android.
 *
 * ═══════════════════════════════════════════════════════════
 *  ПРОБЛЕМА, КОТОРУЮ ЭТО ЛЕЧИТ
 * ═══════════════════════════════════════════════════════════
 *
 * Конвейер Амалии заканчивается фразой `ttsEngine.speak(...)`, и до этой
 * правки единственным движком был Fish Audio. У него две независимые
 * точки отказа, и обе наблюдались на реальном ключе:
 *
 *  1. **Баланс.** При нулевом балансе платные модели отвечают
 *     `402 Payment Required` — мгновенно и на каждую фразу. Раньше это
 *     означало, что ассистент молчит полностью: текст в карточке есть,
 *     голоса нет.
 *  2. **Холодный старт.** Бесплатная модель (`s2.1-pro-free`) после паузы
 *     разогревается **~38 секунд**, хотя в прогретом состоянии отвечает
 *     за 2.4–3 с. Для голосового ассистента первая цифра — это «не
 *     работает»: пользователь успевает решить, что приложение сломалось.
 *
 * Системный TTS отвечает за доли секунды, работает офлайн и не зависит
 * ни от ключей, ни от баланса. Он и становится страховкой.
 *
 * ═══════════════════════════════════════════════════════════
 *  ЛОГИКА ПЕРЕКЛЮЧЕНИЯ
 * ═══════════════════════════════════════════════════════════
 *
 *  1. Ждём **первый чанк** облачного движка не дольше
 *     [firstChunkDeadlineMs]. Ждать целую фразу бессмысленно: если звук
 *     не начался за 9 секунд, он уже не нужен — ответ должен звучать.
 *  2. Пришёл — дальше стримим облако как есть, без единой правки потока.
 *  3. Не пришёл (таймаут) или движок упал до первого звука — снимаем
 *     облако и синтезируем системным голосом.
 *  4. После сбоя облако **уходит на карантин** [PRIMARY_COOLDOWN_MS]:
 *     следующая фраза не заставляет пользователя ждать те же 9 секунд.
 *     Карантин истекает сам — как только сервис оживёт, голос вернётся.
 *
 * Ничего не «залипает»: оба движка остаются живыми, а решение
 * принимается на каждой фразе отдельно.
 *
 * @param primary облачный движок (голос Амалии).
 * @param fallback системный движок Android (страховка).
 * @param firstChunkDeadlineMs сколько ждать первый звук от облака.
 */
class ResilientTtsEngine(
    private val primary: TextToSpeechEngine,
    private val fallback: TextToSpeechEngine,
    private val firstChunkDeadlineMs: Long = DEFAULT_DEADLINE_MS,
) : TextToSpeechEngine {

    /** До какого момента облако не трогаем (0 — карантина нет). */
    @Volatile
    private var primaryBlockedUntil: Long = 0L

    /**
     * Чем озвучена последняя фраза: [ENGINE_PRIMARY] или [ENGINE_FALLBACK].
     * Экран настроек показывает это пользователю, чтобы «голос изменился»
     * не выглядело загадкой.
     */
    @Volatile
    var lastUsedEngine: String = ENGINE_PRIMARY
        private set

    /** Почему последний раз переключились на систему (null — не переключались). */
    @Volatile
    var lastFallbackReason: String? = null
        private set

    override suspend fun initialize() {
        // Системный движок — обязательная часть: без него страховки нет.
        runCatching { fallback.initialize() }
        // Облачный может быть недоступен — это не повод не запускаться.
        runCatching { primary.initialize() }
    }

    override suspend fun close() {
        runCatching { primary.close() }
        runCatching { fallback.close() }
    }

    override fun speak(text: String, options: EngineOptions): Flow<AudioChunk> = channelFlow {
        val clean = text.trim()
        if (clean.isEmpty()) return@channelFlow

        // Карантин: облако недавно подвело — не платим за это паузой снова.
        if (System.currentTimeMillis() < primaryBlockedUntil) {
            emitFallback(clean, options, reason = "карантин облачного синтеза")
            return@channelFlow
        }

        // Мост: облачный движок пишет сюда, а решение «ждём или нет»
        // принимает основной поток — так таймаут не рвёт саму коллекцию.
        val bridge = Channel<AudioChunk>(Channel.UNLIMITED)
        val primaryJob = launch {
            try {
                primary.speak(clean, options).collect { chunk -> bridge.send(chunk) }
                bridge.close()
            } catch (e: CancellationException) {
                bridge.close()
                throw e
            } catch (e: Throwable) {
                bridge.close(e)
            }
        }

        val first = withTimeoutOrNull(firstChunkDeadlineMs) { bridge.receiveCatching() }

        when {
            first == null -> {
                // Дедлайн истёк: облако молчит дольше, чем стоит ждать.
                primaryJob.cancel()
                markPrimaryFailed("облачный синтез не начал звучать вовремя")
                emitFallback(clean, options, reason = "таймаут первого звука")
            }

            first.isSuccess -> {
                // Облако ответило — дальше стримим его без изменений.
                lastUsedEngine = ENGINE_PRIMARY
                lastFallbackReason = null
                send(first.getOrThrow())
                for (chunk in bridge) send(chunk)
                primaryJob.join()
            }

            else -> {
                // Упало до первого чанка (нет баланса, нет сети, 402/500…).
                primaryJob.cancel()
                val reason = first.exceptionOrNull()?.message
                    ?: "облачный синтез вернул пустой звук"
                markPrimaryFailed(reason)
                emitFallback(clean, options, reason = reason)
            }
        }
    }

    /**
     * Озвучивает фразу системным голосом.
     *
     * Ошибка системного синтеза **не** поднимается наверх: ответ уже
     * сгенерирован и лежит в карточке, и потеря голоса не должна превращать
     * успешный разговор в экран ошибки. Молчание — честная деградация,
     * «ошибка» на весь экран — нет.
     *
     * Расширение объявлено на [ProducerScope], а не на `FlowCollector`:
     * блок [channelFlow] даёт именно `ProducerScope`, и у него нет `emit` —
     * чанки уходят через `send`. Именно на этом падала первая версия файла:
     * «receiver type mismatch» на всех трёх вызовах.
     */
    private suspend fun ProducerScope<AudioChunk>.emitFallback(
        text: String,
        options: EngineOptions,
        reason: String,
    ) {
        lastUsedEngine = ENGINE_FALLBACK
        lastFallbackReason = reason
        try {
            fallback.speak(text, options).collect { chunk -> send(chunk) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Оба движка промолчали: пользователь увидит текст ответа.
            lastFallbackReason = e.message ?: reason
        }
    }

    /** Ставит облако на карантин, чтобы следующая фраза не ждала дедлайн. */
    private fun markPrimaryFailed(reason: String) {
        lastFallbackReason = reason
        primaryBlockedUntil = System.currentTimeMillis() + PRIMARY_COOLDOWN_MS
    }

    companion object {
        /** Человекочитаемое имя облачного движка. */
        const val ENGINE_PRIMARY: String = "Fish Audio"

        /** Человекочитаемое имя системного движка. */
        const val ENGINE_FALLBACK: String = "Системный голос"

        /**
         * Сколько ждать первый звук от облака.
         *
         * 9 секунд — осознанный компромисс: живой ответ начинается за
         * 0.5–1.5 с, а аномально медленный синтез (десятки секунд) отсекается
         * ровно там, где ожидание начинает вредить.
         */
        const val DEFAULT_DEADLINE_MS: Long = 9_000L

        /** Карантин облака после сбоя: 5 минут, потом пробуем снова. */
        const val PRIMARY_COOLDOWN_MS: Long = 5 * 60 * 1000L
    }
}
