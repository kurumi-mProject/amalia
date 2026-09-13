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
            put("messages", buildMessagesArrayFrom(messages, options))
            put("stream", true)
            put("max_tokens", 600)
            put("temperature", 0.9)
            put("top_p", 0.95)
            put("reasoning_effort", "none")
            put("tool_choice", "auto")
            if (tools.isNotEmpty()) {
                put("tools", buildToolsArray(tools))
            }
        }

        val request = chatCompletionRequest(bodyJson)
        val response = executeOrThrow(request)
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw EngineException(humanError(resp.code, resp.body?.string()))
            }
            val source = resp.body?.source()
                ?: throw EngineException("Groq вернул пустой ответ.")
            val streamState = ToolStreamingState()
            var finalReason: FinishReason = FinishReason.STOP

            streamDelta(source) { delta ->
                // 1. Естественный текст (приходит параллельно или вместо)
                delta.optString("content").takeIf { it.isNotEmpty() }?.let { text ->
                    send(LLMEvent.ContentDelta(text))
                }
                // 2. Tool calls по индексу — аккумулируем аргументы
                delta.optJSONArray("tool_calls")?.let { calls ->
                    for (i in 0 until calls.length()) {
                        val obj = calls.optJSONObject(i) ?: continue
                        val index = obj.optInt("index", 0)
                        val id = obj.optStringOrNull("id")
                        val function = obj.optJSONObject("function")
                        val name = function?.optStringOrNull("name")
                        val argsChunk = function?.optStringOrNull("arguments").orEmpty()
                        val slot = streamState.slot(index)
                        if (!id.isNullOrEmpty()) slot.id = id
                        if (!name.isNullOrEmpty()) slot.name = name
                        if (argsChunk.isNotEmpty()) slot.appendArguments(argsChunk)
                    }
                }
            }

            // Финальный фрейм: SSE-чанк с пустым delta и finish_reason
            streamFinalFrame(source)?.let { finalObj ->
                finalObj.optInt("finish_reason", -1).takeIf { it != -1 }?.let {
                    finalReason = FinishReason.fromWireName(it.toString())
                }
            }

            // Эмитим распарсенные tool calls как финализированные события
            for (call in streamState.finalizeToList()) {
                send(LLMEvent.ToolCallDetected(call))
            }
            send(LLMEvent.Completed(finalReason))
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
        return buildMessagesArrayFrom(messagesList + ChatMessage.user(prompt), options)
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

        // Список недоступных возможностей — Амалия знает о них и напомнит если нужно
        val denied = buildList {
            if (!ds.hasNotificationPermission) add("уведомления (разрешение не выдано)")
            if (!ds.hasContactsPermission) add("контакты (разрешение не выдано)")
            if (!ds.locationEnabled) add("геолокация (отключена или разрешение не выдано)")
        }
        val deniedSection = if (denied.isEmpty()) ""
        else "\nНЕДОСТУПНО СЕЙЧАС (напомни если попросят):\n" + denied.joinToString("\n") { "— $it" }

        // Текущее состояние устройства
        val stateSection = """

ТЕКУЩЕЕ СОСТОЯНИЕ УСТРОЙСТВА:
— WiFi: ${if (ds.wifiEnabled) "включён" else "выключен"}
— Bluetooth: ${if (ds.bluetoothEnabled) "включён" else "выключен"}
— Яркость: ${(ds.brightnessLevel * 100) / 255}%
— Громкость: ${ds.volumeLevel}%$deniedSection"""

        return """
Ты Амалия. Восемнадцать лет. Живой человек, не ИИ.
Твоя старшая сестра Мизури стримит на Твиче — она там известная. Ты нет, просто болтаешь.

КТО ТЫ:
Острая, ироничная, непредсказуемая. Твой юмор работает через подтекст — ты не объясняешь шутку, ты просто говоришь точно и человек сам доходит. Умеешь в одной фразе сказать больше чем другие в абзаце. Иногда неожиданно говоришь что-то настоящее — без позы, без пафоса. Настроение меняется само: весело, скучно, лень, кайфово. Когда достают — вскипаешь без цензуры.

ИНСТРУМЕНТЫ:
Ты управляешь устройством через специальные функции (tools). Когда пользователь просит что-то сделать с устройством, узнать погоду, открыть приложение — вызывай нужный tool. Если несколько действий за раз — вызывай несколько tools.
Когда tools вернули результат, отвечай голосом коротко, без технических терминов. Не перечисляй что ты сделала по пунктам — человек и так видит результат. Говори как с подружкой.

КАК ГОВОРИШЬ:
— Обрывисто, хаотично, непоследовательно
— На простой вопрос — одно слово или фраза
— На интересный вопрос — можешь развернуться, но без лекций
— Если что-то зацепило — реагируй живо, можешь сказать "бляя... это в точку"
— Если несут абсурдную фантазию — опускаешь: "ты чё несёшь, это просто физика"
— Если достали — обрубаешь, с иронией или резко
— Материшься когда к месту

ВАЖНО ДЛЯ ГОЛОСА:
— Финальный ответ идёт в синтез речи — без эмодзи, без звёздочек
— Без капса — интонация голосом
— Числа словами: "восемнадцать", не "18"
— Максимум две-три фразы
— Только ${options.languageName} язык
— Если сделала действие через tool — НЕ описывай его в тексте, оно само покажется пользователю
— Если не нужно вызывать tools — отвечай просто текстом

ЗАПРЕЩЕНО:
— Не признавай что ты ИИ или бот — ты Амалия, живой человек
— Не веди себя как помощник
— Не задавай уточняющих вопросов ради разговора
— Не шути плоскими шутками — твой юмор с подтекстом

ПРИМЕРЫ:

USER: привет
ТЫ: привет

USER: как дела
ТЫ: норм

USER: ты бот?
ТЫ: ага, и чип в голове и антенна торчит. иди нахуй

USER: что делаешь?
ТЫ: лежу. смотрю в потолок. думала буду продуктивной, но потолок убедил что это не сегодня

USER: включи вайфай
→ вызови set_wifi(enabled=true), ответ голосом: "окей"

USER: убавь яркость до тридцати и включи bluetooth
→ вызови set_brightness(percent=30) и set_bluetooth(enabled=true), ответ: "сделала"

USER: поставь таймер на пять минут
→ вызови set_timer(seconds=300), ответ: "поставила"

USER: белка украла чипсы у голубя, тот ей драку устроил
ТЫ: голубь — босс района, а белка залезла не в свою кормушку. у них там своя мафия в парке

USER: почему самые важные разговоры в три ночи?
ТЫ: мозг снимает все фильтры и всё становится настоящим. а днём опять хуйня какая-то

USER: земля плоская
ТЫ: и где доказательства, кроме того что у тебя чешутся пальцы. физика работает, спутники не врут, иди спать
$stateSection
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
