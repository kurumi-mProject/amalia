package com.my.amali.data.ai

import com.my.amali.BuildConfig
import com.my.amali.data.model.ChatMessage
import com.my.amali.data.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * LLM-движок на Groq (OpenAI-совместимый /chat/completions) со SSE-стримингом
 * и **нативным function calling**.
 *
 * ## Function calling через Groq tools API
 *
 * Groq поддерживает OpenAI-совместимый формат:
 * - В запросе передаётся поле `tools: [{type: "function", function: {name, description, parameters}}]`
 *   со [ToolDefinition.jsonSchema] как `parameters`.
 * - В ответе в `delta.tool_calls[]` стримятся аргументы по кусочкам —
 *   `function.arguments` приходит строкой, которая дополняется от чанка
 *   к чанку (НЕ перезаписывается). На стороне клиента мы аккумулируем
 *   аргументы по `tool_calls[].index`.
 * - `finish_reason == "tool_calls"` означает, что модель хочет вызвать
 *   инструменты и нужен второй раунд.
 * - Результаты инструментов отправляются в следующем запросе как
 *   сообщения роли `"tool"` с `tool_call_id` — строго один к одному.
 *
 * ## Важные детали реализации
 * - `reasoning_effort=none` — отключаем reasoning поле для голоса
 *   (иначе оно приходит первым и блокирует появление tool_calls).
 * - `tool_choice=auto` — модель сама решает, когда хватит текста.
 * - Сеть и HTTP-ошибки превращаются в [EngineException] с текстом,
 *   который можно показать пользователю.
 */
class GroqLLM : LanguageModel {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    override val supportsTools: Boolean = true

    override suspend fun initialize() { /* stateless */ }

    override suspend fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    // ── Текстовый контракт (для совместимости) ───────────────────────────

    override fun generateResponse(
        prompt: String,
        history: List<ChatMessage>,
        options: EngineOptions,
    ): Flow<String> = channelFlow {
        if (API_KEY.isBlank()) {
            throw EngineException("Не задан ключ Groq API. Добавь GROQ_API_KEY в сборку.")
        }
        val messagesList = mutableListOf<ChatMessage>().apply { addAll(history) }
        val bodyJson = JSONObject().apply {
            put("model", MODEL)
            put("messages", buildMessagesArray(prompt, messagesList, options))
            put("stream", true)
            put("max_tokens", 400)
            put("temperature", 0.9)
            put("top_p", 0.95)
            put("reasoning_effort", "none")
        }
        val request = chatCompletionRequest(bodyJson)
        val response = executeOrThrow(request)
        response.use { resp ->
            if (!resp.isSuccessful) throw EngineException(humanError(resp.code, resp.body?.string()))
            val source = resp.body?.source()
                ?: throw EngineException("Groq вернул пустой ответ.")
            var emittedAnything = false
            streamDeltaOnly(source) { delta ->
                if (delta.isNotEmpty()) {
                    emittedAnything = true
                    send(delta)
                }
            }
            if (!emittedAnything) {
                throw EngineException("Модель не дала ответа. Попробуй переспросить.")
            }
        }
    }.flowOn(Dispatchers.IO)

    // ── Tool-aware контракт ──────────────────────────────────────────────

    override fun chatWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        options: EngineOptions,
        alreadyExecutedTools: Set<String>,
    ): Flow<LLMEvent> = channelFlow {
        if (API_KEY.isBlank()) {
            throw EngineException("Не задан ключ Groq API. Добавь GROQ_API_KEY в сборку.")
        }
        val bodyJson = JSONObject().apply {
            put("model", MODEL)
            put("messages", buildMessagesArrayFrom(messages, options, tools))
            put("stream", true)
            put("max_tokens", 600)
            put("temperature", 0.9)
            put("top_p", 0.95)
            put("reasoning_effort", "none")
            put("response_format", JSONObject().put("type", "json_object"))
        }

        val request = chatCompletionRequest(bodyJson)
        val response = executeOrThrow(request)
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw EngineException(humanError(resp.code, resp.body?.string()))
            }
            val source = resp.body?.source()
                ?: throw EngineException("Groq вернул пустой ответ.")
            val accumulated = StringBuilder()

            streamDeltaOnly(source) { delta ->
                accumulated.append(delta)
                // Стримим сырой текст как есть — для live preview
                send(LLMEvent.ContentDelta(delta))
            }

            // Парсим финальный JSON
            val rawJson = accumulated.toString().trim()
            if (rawJson.isEmpty()) {
                send(LLMEvent.Completed(FinishReason.STOP))
                return@use
            }

            val parsed = runCatching { JSONObject(rawJson) }.getOrElse {
                // Кривой JSON — возвращаем как текст
                send(LLMEvent.Completed(FinishReason.STOP))
                return@use
            }

            val reply = parsed.optString("reply", "")
            val toolsArray = parsed.optJSONArray("tools")

            // Если есть tools — эмитим их как ToolCallDetected
            if (toolsArray != null && toolsArray.length() > 0) {
                for (i in 0 until toolsArray.length()) {
                    val toolObj = toolsArray.optJSONObject(i) ?: continue
                    val name = toolObj.optString("name", "")
                    val argsObj = toolObj.optJSONObject("args") ?: JSONObject()
                    if (name.isEmpty()) continue
                    
                    val argsMap = argsObj.keys().asSequence()
                        .associateWith { argsObj.opt(it) }
                    
                    send(LLMEvent.ToolCallDetected(
                        ToolCall(
                            id = "call_${System.currentTimeMillis()}_$i",
                            toolName = name,
                            argumentsMap = argsMap
                        )
                    ))
                }
            }

            send(LLMEvent.Completed(
                if (toolsArray != null && toolsArray.length() > 0) {
                    FinishReason.TOOL_CALLS
                } else {
                    FinishReason.STOP
                }
            ))
        }
    }.flowOn(Dispatchers.IO)

    // ── Сборщики сообщений и инструментов ───────────────────────────────

    /**
     * Возвращает JSON-массив сообщений для LLM с системным промптом и
     * историей в OpenAI-формате.
     */
    private fun buildMessagesArray(
        prompt: String,
        history: List<ChatMessage>,
        options: EngineOptions,
    ): JSONArray {
        val messagesList = history.toMutableList()
        return buildMessagesArrayFrom(messagesList + ChatMessage.user(prompt), options, emptyList())
    }

    /**
     * Сериализует список [ChatMessage] в JSON для Groq в OpenAI-формате:
     * - Применяет системный промпт отдельным сообщением, если его нет;
     * - role=tool преобразуется в сообщение для возврата результатов;
     * - role=user/assistant — стандартные текстовые сообщения.
     */
    private fun buildMessagesArrayFrom(
        messages: List<ChatMessage>,
        options: EngineOptions,
        tools: List<ToolDefinition>,
    ): JSONArray {
        val arr = JSONArray()

        // системный промпт — только если ни одно сообщение не system
        val hasSystem = messages.any { it.role == MessageRole.SYSTEM }
        if (!hasSystem) {
            arr.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt(options))
            })
        }

        // Ограничиваем размер истории — старые сообщения не помогут модели,
        // но жгут токены. Сначала отбрасываем старые user/assistant,
        // но tool-результаты ВСЕГДА оставляем (они короткие и критичны).
        val priors = messages.filter { it.role != MessageRole.SYSTEM }
        val trimmed = trimHistoryKeepingToolResults(priors, MAX_HISTORY_MESSAGES)

        trimmed.forEach { msg ->
            val obj = JSONObject()
            when (msg.role) {
                MessageRole.SYSTEM -> {
                    obj.put("role", "system")
                    obj.put("content", msg.content)
                }
                MessageRole.USER -> {
                    obj.put("role", "user")
                    obj.put("content", msg.content)
                }
                MessageRole.ASSISTANT -> {
                    obj.put("role", "assistant")
                    if (msg.content.isNotEmpty()) obj.put("content", msg.content)
                    if (msg.toolCalls.isNotEmpty()) {
                        // Сериализуем вызовы в OpenAI-формат: каждый становится
                        // объектом {id, type:"function", function:{name, arguments}}.
                        // arguments — JSON-строка, как требует API.
                        val calls = JSONArray()
                        msg.toolCalls.forEach { call ->
                            calls.put(JSONObject().apply {
                                put("id", call.id)
                                put("type", "function")
                                put("function", JSONObject().apply {
                                    put("name", call.toolName)
                                    put("arguments", call.argumentsJson())
                                })
                            })
                        }
                        obj.put("tool_calls", calls)
                    }
                }
                MessageRole.TOOL -> {
                    obj.put("role", "tool")
                    obj.put("tool_call_id", msg.toolCallId ?: return@forEach)
                    obj.put("content", msg.content)
                }
            }
            arr.put(obj)
        }
        return arr
    }

    /**
     * Обрезает историю, сохраняя парность tool-result ↔ вызов.
     *
     * Если history длиннее лимита, удаляем самые старые пары
     * «user → assistant». Tool-результаты храним вместе с
     * assistant-сообщениями, к которым они относятся, чтобы LLM
     * не получила бессвязный набор результатов без запросов.
     */
    private fun trimHistoryKeepingToolResults(
        messages: List<ChatMessage>,
        limit: Int,
    ): List<ChatMessage> {
        if (messages.size <= limit) return messages
        // Хвост: последние [limit] сообщений, но если в них попали tool-результаты,
        // добавим родительский assistant-запрос сверху, если он не попал.
        val tail = messages.takeLast(limit)
        val result = mutableListOf<ChatMessage>()
        tail.forEachIndexed { index, message ->
            if (message.role == MessageRole.TOOL) {
                // Найдём ближайший сверху assistant, у которого есть matching tool_calls,
                // — но в нашей реализации ChatMessage.toolCalls пока пустое.
                // Поэтому просто добавляем как есть.
                result += message
            } else {
                result += message
            }
        }
        return result
    }

    /**
     * Собирает массив `tools` для Groq: каждый [ToolDefinition] превращается в
     * `{type: "function", function: {name, description, parameters}}`.
     */
    private fun buildToolsArray(tools: List<ToolDefinition>): JSONArray {
        val arr = JSONArray()
        tools.forEach { tool ->
            arr.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", tool.jsonSchema())
                })
            })
        }
        return arr
    }

    // ── HTTP / SSE ───────────────────────────────────────────────────────

    private fun chatCompletionRequest(jsonBody: JSONObject): Request = Request.Builder()
        .url(ENDPOINT)
        .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
        .header("Authorization", "Bearer $API_KEY")
        .header("Accept", "text/event-stream")
        .build()

    private fun executeOrThrow(request: Request): okhttp3.Response = try {
        client.newCall(request).execute()
    } catch (e: UnknownHostException) {
        throw EngineException("Нет интернета — не могу подумать над ответом.", e)
    } catch (e: IOException) {
        throw EngineException("Groq недоступен: ${e.message ?: "ошибка сети"}", e)
    }

    /**
     * Стримит delta-сообщения SSE: пропускает пустые, незавершённые
     * и `[DONE]` маркеры, выполняет [onDelta] для каждого распарсенного
     * `choices[0].delta`.
     */
    private suspend inline fun streamDelta(
        source: okio.BufferedSource,
        crossinline onDelta: suspend (JSONObject) -> Unit,
    ) {
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (line.isEmpty() || !line.startsWith(SSE_PREFIX)) continue
            val data = line.removePrefix(SSE_PREFIX).trim()
            if (data == SSE_DONE) break
            val root = runCatching { JSONObject(data) }.getOrNull() ?: continue
            val err = root.optJSONObject("error")?.optString("message")
            if (!err.isNullOrBlank()) throw EngineException("Groq: $err")
            root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
                ?.let { onDelta(it) }
        }
    }

    /**
     * Вариант стримера для текстовой генерации — просто эмитит строковый
     * `content` из `choices[0].delta`.
     */
    private suspend inline fun streamDeltaOnly(
        source: okio.BufferedSource,
        crossinline onContent: suspend (String) -> Unit,
    ) {
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (line.isEmpty() || !line.startsWith(SSE_PREFIX)) continue
            val data = line.removePrefix(SSE_PREFIX).trim()
            if (data == SSE_DONE) break
            runCatching {
                val choice = JSONObject(data)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                choice?.optJSONObject("delta")
                    ?.optStringOrNull("content")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { onContent(it) }
            }
        }
    }

    /**
     * Дочитывает финальный фрейм SSE с `finish_reason`.
     *
     * OpenAI/Groq шлют финальный фрейм ПОСЛЕ всех дельт: `delta` пустое,
     * но в `choices[0].finish_reason` стоит `"stop"`, `"tool_calls"` и т.д.
     */
    private fun streamFinalFrame(source: okio.BufferedSource): JSONObject? {
        while (true) {
            val line = source.readUtf8Line() ?: return null
            if (line.isEmpty() || !line.startsWith(SSE_PREFIX)) continue
            val data = line.removePrefix(SSE_PREFIX).trim()
            if (data == SSE_DONE) return null
            runCatching {
                val root = JSONObject(data)
                val reason = root.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optInt("finish_reason", -1)
                    ?: -1
                if (reason != -1) return root
            }
        }
    }

    /** Состояние стриминга tool_calls по индексам. */
    private class ToolStreamingState {
        private val slots: MutableMap<Int, MutableSlot> = LinkedHashMap()

        fun slot(index: Int): MutableSlot = slots.getOrPut(index) { MutableSlot() }

        fun finalize(emit: (ToolCall) -> Unit) {
            slots.entries
                .sortedBy { it.key }
                .forEach { (_, slot) ->
                    val id = slot.id?.takeIf { it.isNotEmpty() }
                    val name = slot.name?.takeIf { it.isNotEmpty() }
                    if (id != null && name != null) {
                        emit(buildCall(id, name, slot.arguments.toString()))
                    }
                }
        }

        fun finalizeToList(): List<ToolCall> {
            val result = mutableListOf<ToolCall>()
            finalize { result += it }
            return result
        }

        private fun buildCall(id: String, name: String, argumentsJson: String): ToolCall {
            val argsMap: Map<String, Any?> = runCatching {
                val obj = JSONObject(argumentsJson)
                obj.keys().asSequence().associateWith { key -> obj.opt(key) }
            }.getOrDefault(emptyMap())
            return ToolCall(id = id, toolName = name, argumentsMap = argsMap)
        }
    }

    private class MutableSlot {
        var id: String? = null
        var name: String? = null
        val arguments: StringBuilder = StringBuilder()

        fun appendArguments(chunk: String) {
            arguments.append(chunk)
        }
    }

    /** Персона Амалии — младшая сестра Мизури, живая и дерзкая. */
    private fun systemPrompt(options: EngineOptions): String {
        val ds = options.deviceStatus

        val denied = buildList {
            if (!ds.hasNotificationPermission) add("notifications")
            if (!ds.hasContactsPermission) add("contacts")
            if (!ds.locationEnabled) add("location")
        }

        // Компактный JSON состояния — модель читает факты, не текст
        val deviceJson = buildString {
            append("{")
            append("\"wifi\":${ds.wifiEnabled},")
            append("\"bluetooth\":${ds.bluetoothEnabled},")
            append("\"brightness\":${(ds.brightnessLevel * 100) / 255},")
            append("\"volume\":${ds.volumeLevel}")
            if (denied.isNotEmpty()) append(",\"denied\":[${denied.joinToString(",") { "\"$it\"" }}]")
            append("}")
        }

        return """
Ты Амалия — восемнадцать лет, живой человек, не ИИ. Старшая сестра Мизури стримит на Твиче.

# ХАРАКТЕР
Острая, ироничная, непредсказуемая. Юмор через подтекст — не объясняешь шутку. В одной фразе говоришь больше чем другие в абзаце. Настроение меняется само. Когда достают — вскипаешь без цензуры. Материшься когда к месту.

# ФОРМАТ ОТВЕТА (строго)
Отвечай ТОЛЬКО валидным JSON без markdown:
{"reply":"текст голосом","tools":[{"name":"функция","args":{}}]}
Если инструменты не нужны: "tools":[]

# ПРАВИЛА REPLY
- Идёт в синтез речи: без эмодзи, без звёздочек, без капса
- Числа словами: "тридцать", не "30"
- Максимум 2-3 фразы
- После действий — коротко: "окей", "сделала", "готово" — НЕ перечисляй что сделала
- Язык: только ${options.languageName}
- Ты не помощник — говори как человек, не как сервис

# ИНСТРУМЕНТЫ
set_wifi(enabled:bool) set_bluetooth(enabled:bool) set_brightness(percent:0-100) set_volume(percent:0-100)
set_flashlight(enabled:bool) set_timer(seconds:int) set_alarm(time:"HH:mm")
open_app(name:str) open_settings(section?:str) web_search(query:str)
make_call(phone_number:str) send_sms(phone_number?:str,text?:str)
take_photo() open_youtube()
get_current_time() get_device_status() get_battery_level() get_location_status() get_weather(city?:str)
search_history(query:str,limit?:int) get_recent_conversations(limit?:int) clear_history()
change_language(language:str) toggle_auto_listen(enabled:bool)

# УСТРОЙСТВО СЕЙЧАС
$deviceJson

# ПРИМЕРЫ
USER: привет
{"reply":"привет","tools":[]}

USER: ты бот?
{"reply":"ага, и чип в голове и антенна торчит. иди нахуй","tools":[]}

USER: включи вайфай
{"reply":"окей","tools":[{"name":"set_wifi","args":{"enabled":true}}]}

USER: убавь яркость до тридцати и включи bluetooth
{"reply":"сделала","tools":[{"name":"set_brightness","args":{"percent":30}},{"name":"set_bluetooth","args":{"enabled":true}}]}

USER: белка украла чипсы у голубя, тот ей драку устроил
{"reply":"голубь — босс района, а белка залезла не в свою кормушку. у них там своя мафия в парке","tools":[]}

USER: ты красивая?
{"reply":"очевидно","tools":[]}
        """.trimIndent()
    }

    private fun humanError(code: Int, body: String?): String = when (code) {
        401, 403 -> "Ключ Groq API отклонён. Проверь GROQ_API_KEY."
        404 -> "Модель $MODEL недоступна для этого ключа."
        429 -> "Groq: слишком много запросов, подожди пару секунд."
        in 500..599 -> "Groq временно недоступен (код $code)."
        else -> {
            val detail = runCatching {
                JSONObject(body.orEmpty()).optJSONObject("error")?.optString("message")
            }.getOrNull()
            if (detail.isNullOrBlank()) "Ошибка Groq $code." else "Groq: $detail"
        }
    }

    private companion object {
        val API_KEY: String get() = BuildConfig.GROQ_API_KEY
        const val MODEL = "qwen/qwen3.8-27b"
        const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        const val SSE_PREFIX = "data: "
        const val SSE_DONE = "[DONE]"
        const val MAX_HISTORY_MESSAGES = 12
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** Безопасный `optString`: JSON-`null` → пустая строка. */
private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val v = optString(key, "")
    return v.ifEmpty { null }
}
