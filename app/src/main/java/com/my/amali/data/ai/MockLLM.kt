package com.my.amali.data.ai

import com.my.amali.data.model.ChatMessage
import com.my.amali.data.model.MessageRole
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * Mock language model: реалистичный офлайн-конвейер с поддержкой tool calling.
 *
 * Полезен для UI-разработки, превью и acceptance-тестов: работает без ключей,
 * но эмитит **настоящие** [LLMEvent.ToolCallDetected] ивенты, поэтому
 * оркестратор прогоняет через себя полный реальный цикл — кнопка «включить
 * wi-fi» в дев-режиме включает реальный Wi-Fi через [ToolRegistry].
 *
 * Логика выбора инструмента — keyword matching по промпту, как в [generateResponse]:
 * каждая категория запросов знает свой набор [ToolCall] для эмуляции.
 *
 * Persona Amalia сохранена: ответы короткие, дерзкие, на русском.
 */
class MockLanguageModel : LanguageModel {

    private val random = Random(System.currentTimeMillis())
    private var initialized: Boolean = false

    override val supportsTools: Boolean = true

    override suspend fun initialize() { initialized = true }

    override suspend fun close() { initialized = false }

    // ── Plain text path (legacy) ──────────────────────────────────────────

    override fun generateResponse(
        prompt: String,
        history: List<ChatMessage>,
        options: EngineOptions,
    ): Flow<String> = flow {
        if (!initialized) initialize()
        // В режиме tools возвращаем только текстовую часть, чтобы legacy-путь
        // оставался работоспособным на старых индексных экранах.
        val response = pickResponse(prompt, history)
        for ((index, word) in response.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .withIndex()
        ) {
            emit(if (index == 0) word else " $word")
            delay(WORD_DELAY_MS + random.nextLong(WORD_DELAY_JITTER_MS))
        }
    }

    // ── Tool-aware path ──────────────────────────────────────────────────

    override fun chatWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        options: EngineOptions,
        alreadyExecutedTools: Set<String>,
    ): Flow<LLMEvent> = flow {
        if (!initialized) initialize()
        val lastUser = messages.lastOrNull { it.role == MessageRole.USER }?.content.orEmpty()
        val lowered = lastUser.lowercase()

        // В mock-LLM мы не получаем от оркестратора полную историю tool_calls
        // в виде OpenAI-сообщений; единственный надёжный способ понять,
        // какие инструменты уже выполнены, — передать их имена явным списком.
        // Реальный LLM (Groq) этот параметр игнорирует.
        val executed = alreadyExecutedTools

        // Фаза 1: модель «думает» и хочет вызвать инструменты, которых ещё нет
        // в уже выполненных. Если подходящих нет — сразу даём текстовый ответ.
        val intendedTools = planToolCalls(lowered, tools)
            .filter { (event, _) ->
                (event as? LLMEvent.ToolCallDetected)?.call?.toolName !in executed
            }
        intendedTools.forEach { (event, delayMs) ->
            delay(delayMs)
            emit(event)
        }

        // Фаза 2: финальный текст от модели (либо после tool_exec, либо сразу).
        // Задержка эмулирует «модель думает над формулировкой».
        delay(FINAL_REPLY_DELAY_MS)
        val finalText = composeFinalReply(lowered, messages)
        for ((index, word) in finalText.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .withIndex()
        ) {
            // Заглушка всегда отвечает «как надо», поэтому восстановление текста
        // ей не требуется — но событие обязано быть в языке типов.
        emit(LLMEvent.ContentDelta(if (index == 0) word else " $word"))
            delay(WORD_DELAY_MS + random.nextLong(WORD_DELAY_JITTER_MS))
        }
        emit(LLMEvent.Completed(FinishReason.STOP))
    }

    // ── Планирование вызовов ─────────────────────────────────────────────

    /**
     * Решает, какие [ToolCall] нужно сделать по тексту промпта.
     *
     * Возвращает пару «(событие, задержка эмуляции)» — задержка нужна, чтобы
     * UI показал фазу «думаю» до того, как появится событие вызова инструмента.
     */
    private fun planToolCalls(
        prompt: String,
        tools: List<ToolDefinition>,
    ): List<Pair<LLMEvent, Long>> {
        val availableNames = tools.map { it.name }.toSet()
        val actions = mutableListOf<Pair<LLMEvent, Long>>()

        // Wi-Fi
        if (matchesWifi(prompt)) {
            if ("set_wifi" in availableNames) {
                val on = matchesOn(prompt)
                actions += LLMEvent.ToolCallDetected(
                    ToolCall(
                        id = newToolId(),
                        toolName = "set_wifi",
                        argumentsMap = mapOf("enabled" to on),
                    ),
                ) to WIFI_DELAY_MS
            }
        }
        // Bluetooth
        if (matchesBluetooth(prompt)) {
            if ("set_bluetooth" in availableNames) {
                val on = matchesOn(prompt)
                actions += LLMEvent.ToolCallDetected(
                    ToolCall(
                        id = newToolId(),
                        toolName = "set_bluetooth",
                        argumentsMap = mapOf("enabled" to on),
                    ),
                ) to BT_DELAY_MS
            }
        }
        // Яркость
        if (matchesBrightness(prompt)) {
            val percent = extractPercent(prompt) ?: 50
            if ("set_brightness" in availableNames) {
                actions += LLMEvent.ToolCallDetected(
                    ToolCall(
                        id = newToolId(),
                        toolName = "set_brightness",
                        argumentsMap = mapOf("percent" to percent),
                    ),
                ) to BRIGHTNESS_DELAY_MS
            }
        }
        // Громкость
        if (matchesVolume(prompt)) {
            val percent = extractPercent(prompt) ?: 50
            if ("set_volume" in availableNames) {
                actions += LLMEvent.ToolCallDetected(
                    ToolCall(
                        id = newToolId(),
                        toolName = "set_volume",
                        argumentsMap = mapOf("percent" to percent),
                    ),
                ) to VOLUME_DELAY_MS
            }
        }
        // Таймер
        if (matchesTimer(prompt)) {
            val seconds = extractSeconds(prompt) ?: 300
            if ("set_timer" in availableNames) {
                actions += LLMEvent.ToolCallDetected(
                    ToolCall(
                        id = newToolId(),
                        toolName = "set_timer",
                        argumentsMap = mapOf("seconds" to seconds),
                    ),
                ) to TIMER_DELAY_MS
            }
        }
        // Будильник
        if (matchesAlarm(prompt)) {
            val time = extractTime(prompt) ?: "07:00"
            if ("set_alarm" in availableNames) {
                actions += LLMEvent.ToolCallDetected(
                    ToolCall(
                        id = newToolId(),
                        toolName = "set_alarm",
                        argumentsMap = mapOf("time" to time),
                    ),
                ) to TIMER_DELAY_MS
            }
        }
        // Время — информационный запрос
        if (matchesTime(prompt) && "get_current_time" in availableNames) {
            actions += LLMEvent.ToolCallDetected(
                ToolCall(
                    id = newToolId(),
                    toolName = "get_current_time",
                    argumentsMap = emptyMap(),
                ),
            ) to INFO_DELAY_MS
        }
        // Погода — мок
        if (matchesWeather(prompt) && "get_weather" in availableNames) {
            actions += LLMEvent.ToolCallDetected(
                ToolCall(
                    id = newToolId(),
                    toolName = "get_weather",
                    argumentsMap = mapOf("city" to ""),
                ),
            ) to INFO_DELAY_MS
        }
        // Открыть YouTube
        if (matchesYouTube(prompt) && "open_youtube" in availableNames) {
            actions += LLMEvent.ToolCallDetected(
                ToolCall(
                    id = newToolId(),
                    toolName = "open_youtube",
                    argumentsMap = emptyMap(),
                ),
            ) to APP_DELAY_MS
        }
        // Открыть приложение по имени
        val appName = extractAppName(prompt)
        if (matchesOpenApp(prompt) && appName.isNotEmpty() && "open_app" in availableNames) {
            actions += LLMEvent.ToolCallDetected(
                ToolCall(
                    id = newToolId(),
                    toolName = "open_app",
                    argumentsMap = mapOf("name" to appName),
                ),
            ) to APP_DELAY_MS
        }
        // Поиск
        val query = extractSearchQuery(prompt)
        if (matchesSearch(prompt) && query.isNotEmpty() && "web_search" in availableNames) {
            actions += LLMEvent.ToolCallDetected(
                ToolCall(
                    id = newToolId(),
                    toolName = "web_search",
                    argumentsMap = mapOf("query" to query),
                ),
            ) to SEARCH_DELAY_MS
        }
        return actions
    }

    /**
     * Финальная реплика модели — то, что попадёт в TTS.
     *
     * Если был хоть один [ToolCallDetected] — модель отвечает коротким
     * «ок», «сделала» и т.п. (как в примере персонажа). Если были
     * информационные queries — даём цифру/факт голосом.
     * Если инструментов не было — обычный текстовый ответ как раньше.
     */
    private fun composeFinalReply(prompt: String, history: List<ChatMessage>): String {
        // Если в последнем раунде были tool-вызовы (т.е. это второй ход после
        // возврата результатов в модель), ориентируемся на содержание результатов.
        val lastToolResults = history.takeLast(6).filter { it.isToolResult }
        if (lastToolResults.isNotEmpty()) {
            val names = history.takeLast(8)
                .filter { it.role == MessageRole.ASSISTANT && it.content.isBlank() }
            if (names.isNotEmpty()) {
                // Самое простое: сказать «сделала» и перечислить что
                val executed = lastToolResults.count { it.content.contains("\"ok\":true") ||
                    !it.content.startsWith("ERROR") }
                return when {
                    executed > 1 -> "готово, всё сделала"
                    else -> randomPick("окей", "сделала", "готово", "есть")
                }
            }
        }
        if (matchesTime(prompt)) return timeResponse()
        if (matchesWeather(prompt)) return weatherResponse()
        if (matchesGreeting(prompt)) return greeting()
        return fallback(history)
    }

    // ── Pattern detection helpers ────────────────────────────────────────

    private fun matchesWifi(q: String): Boolean =
        listOf("вайфай", "вай-фай", "wi-fi", "wifi").any(q::contains)

    private fun matchesBluetooth(q: String): Boolean =
        listOf("блютуз", "блю-туз", "bluetooth", "синезуб").any(q::contains)

    private fun matchesBrightness(q: String): Boolean =
        listOf("яркость", "яркости", "экран", "brightness").any(q::contains)

    private fun matchesVolume(q: String): Boolean =
        listOf("громкость", "громче", "тише", "volume").any(q::contains)

    private fun matchesTimer(q: String): Boolean =
        listOf("таймер", "обратный отсчёт", "засеки", "timer").any(q::contains)

    private fun matchesAlarm(q: String): Boolean =
        listOf("будильник", "разбуди", "alarm").any(q::contains)

    private fun matchesTime(q: String): Boolean =
        listOf("время", "который час", "сколько времени").any(q::contains)

    private fun matchesWeather(q: String): Boolean =
        listOf("погода", "погоду", "на улице", "дождь").any(q::contains)

    private fun matchesYouTube(q: String): Boolean =
        listOf("ютуб", "youtube", "ют").any(q::contains)

    private fun matchesSearch(q: String): Boolean =
        listOf("найди", "поищи", "загугли", "search").any(q::contains)

    private fun matchesOpenApp(q: String): Boolean =
        listOf("открой", "запусти", "включи ").any(q::contains)

    private fun matchesGreeting(q: String): Boolean =
        listOf("привет", "здравствуй", "хай", "доброе утро").any(q::contains)

    private fun matchesOn(q: String): Boolean {
        val offWords = listOf("выключи", "отключи", "выруби")
        val onWords = listOf("включи", "активируй", "запусти")
        return when {
            offWords.any(q::contains) -> false
            onWords.any(q::contains) -> true
            else -> true
        }
    }

    private fun extractPercent(q: String): Int? =
        Regex("(\\d{1,3})\\s*(процент|%|проц)?").find(q)?.groupValues?.get(1)?.toIntOrNull()

    private fun extractSeconds(q: String): Int? {
        Regex("(\\d+)\\s*минут").find(q)?.groupValues?.get(1)?.toIntOrNull()?.let { return it * 60 }
        Regex("(\\d+)\\s*секунд").find(q)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return Regex("(\\d+)").find(q)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractTime(q: String): String? {
        Regex("(\\d{1,2})[:.](\\d{2})").find(q)?.let { match ->
            val h = match.groupValues[1].toIntOrNull() ?: return null
            val m = match.groupValues[2].toIntOrNull() ?: return null
            return "%02d:%02d".format(h.coerceIn(0, 23), m.coerceIn(0, 59))
        }
        Regex("на (\\d{1,2})\\b").find(q)?.groupValues?.get(1)?.toIntOrNull()?.let { hour ->
            return "%02d:00".format(hour.coerceIn(0, 23))
        }
        return null
    }

    private fun extractAppName(q: String): String {
        val marker = listOf("открой ", "запусти ").firstOrNull { q.contains(it) } ?: return ""
        val tail = q.substringAfter(marker).trim()
        return tail.takeWhile { !it.isWhitespace() }.take(40)
    }

    private fun extractSearchQuery(q: String): String {
        val marker = listOf("найди ", "поищи ", "загугли ").firstOrNull { q.contains(it) }
        return marker?.let { q.substringAfter(it).trim() } ?: ""
    }

    // Mock-LLM использует уже-выполненный список из параметра, который
        // контролирует оркестратор. Внутренние хелперы не нужны.
        private fun newToolId(): String = "call_${java.util.UUID.randomUUID().toString().take(8)}"

        /**
         * Зарезервировано на будущее: реальный orchestrator мог бы позволить
         * mock-LLM самому резолвить имена из истории. Сейчас not used —
         * имена приходят из [alreadyExecutedTools] параметра.
         */
        @Suppress("unused")
        private fun extractToolNameFromResult(content: String, toolCallId: String): String? { return null }

    // ── Final-reply generators ───────────────────────────────────────────

    private fun randomPick(vararg options: String): String = options[random.nextInt(options.size)]

    private fun greeting(): String = listOf(
        "привет", "хай", "здорово", "о, привет",
    ).random(random)

    private fun timeResponse(): String {
        val now = LocalTime.now()
        val formatted = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        val partOfDay = when (now.hour) {
            in 5..11 -> "утро"
            in 12..17 -> "день"
            in 18..22 -> "вечер"
            else -> "ночь"
        }
        return "сейчас $formatted, на дворе $partOfDay"
    }

    private fun weatherResponse(): String = listOf(
        "сейчас ясно, около восемнадцати, гулять — самое то",
        "облачно с прояснениями, плюс пятнадцать",
        "моросит, плюс двенадцать, зонт не помешает",
    ).random(random)

    private fun fallback(history: List<ChatMessage>): String {
        val previousTurns = history.count { it.role == MessageRole.USER }
        return if (previousTurns > 1) {
            "хм, я бы с тобой поговорила, но локальная модель без ключей. " +
                "попроси включить вайфай, я могу."
        } else {
            "я пока mock, но вижу функции устройства. скажи например «включи вайфай» или «который час»."
        }
    }

    /** Legacy selector оставлен для совместимости со старыми тестами. */
    private fun pickResponse(query: String, history: List<ChatMessage>): String {
        val lowered = query.trim().lowercase()
        return when {
            matchesGreeting(lowered) -> greeting()
            matchesTime(lowered) -> timeResponse()
            matchesWeather(lowered) -> weatherResponse()
            else -> fallback(history)
        }
    }

    private companion object {
        const val WORD_DELAY_MS = 60L
        const val WORD_DELAY_JITTER_MS = 60L
        const val FINAL_REPLY_DELAY_MS = 120L
        const val TOOL_RESULT_WAIT_MS = 350L
        const val WIFI_DELAY_MS = 200L
        const val BT_DELAY_MS = 220L
        const val BRIGHTNESS_DELAY_MS = 180L
        const val VOLUME_DELAY_MS = 180L
        const val TIMER_DELAY_MS = 250L
        const val INFO_DELAY_MS = 150L
        const val APP_DELAY_MS = 280L
        const val SEARCH_DELAY_MS = 280L
    }

    @Suppress("unused")
    private fun nowDate(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
}
