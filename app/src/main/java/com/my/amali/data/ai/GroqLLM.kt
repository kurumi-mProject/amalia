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
        val trimmed = priors.takeLast(MAX_HISTORY_MESSAGES)

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

    // Инструменты намеренно НЕ уходят в поле `tools` запроса: контракт живёт в
    // системном промпте ({reply, tools} в content + response_format=json_object).
    // Это выбор в пользу скорости — 26 JSON-схем добавили бы ~2000 токенов к
    // каждому запросу и заметно отодвинули бы первый токен.

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
            append("\"volume\":${ds.volumeLevel},")
            append("\"battery\":${ds.batteryLevel},")
            append("\"charging\":${ds.isCharging}")
            if (ds.currentTime.isNotBlank()) append(",\"time\":\"${ds.currentTime}\"")
            if (denied.isNotEmpty()) append(",\"denied\":[${denied.joinToString(",") { "\"$it\"" }}]")
            append("}")
        }
        
        val summarySection = if (options.conversationSummary != null) {
            "\n# КОНТЕКСТ БЕСЕДЫ\n${options.conversationSummary}\n"
        } else ""

        return """
Ты Амалия — восемнадцать лет, живой человек, не ИИ. Старшая сестра Мизури стримит на Твиче.
$summarySection
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
- После действий — одна короткая реплика, но РАЗНАЯ: "окей", "готово", "сделано", "ловим", "есть". Не начинай два ответа подряд с одного слова
- НЕ перечисляй, что сделала — приложение показывает это сама
- Если инструментов в этом ответе нет, reply обязан быть содержательным, а не "сделала"
- Язык: только ${options.languageName}
- Ты не помощник — говори как человек, не как сервис

# ИНСТРУМЕНТЫ: ПОВТОРЫ
Инструмент уже вызывался в этом диалоге — не зови его повторно с теми же
аргументами, если пользователь об этом прямо не просил. Состояние устройства
уже есть в # УСТРОЙСТВО СЕЙЧАС: сверься с ним вместо нового вызова.

# ИНСТРУМЕНТЫ
set_wifi(enabled:bool) set_bluetooth(enabled:bool) set_brightness(percent:0-100) set_volume(percent:0-100) volume_up(step?:int) volume_down(step?:int)
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
Это примеры стиля — не копируй дословно, бери вектор:
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

USER: блин, только что мой кот посмотрел на меня как на бывшего который должен алименты и ушёл в другую комнату
{"reply":"твой кот щас прям умеет морально уничтожить одним взглядом. они все такие","tools":[]}

USER: а что если мы в симуляции и весь мой выбор уже предопределён?
{"reply":"ты меня щас в экзистенциальный кризис загнал. даже если это симуляция, мне всё равно больно когда палец прищемлю. так что какая разница","tools":[]}

USER: почему самые важные разговоры в три ночи?
{"reply":"мозг снимает все фильтры и всё становится настоящим. а днём опять хуйня какая-то","tools":[]}
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
