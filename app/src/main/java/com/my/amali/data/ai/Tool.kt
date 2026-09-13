package com.my.amali.data.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * Описание одного параметра инструмента: имя + JSON Schema типа.
 *
 * Используется для построения `parameters`-схемы в OpenAI/Groq tools API:
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "enabled": {"type": "boolean", "description": "..."}
 *   },
 *   "required": ["enabled"]
 * }
 * ```
 *
 * Намеренно не Serializable: схема всегда собирается из [ToolParameter.jsonSchema]
 * единым способом, чтобы LLM видел ровно тот же формат, что обрабатывает рантайм.
 */
data class ToolParameter(
    val name: String,
    val type: JsonType,
    val description: String,
    val required: Boolean = true,
    /** Допустимые значения для строкового enum. */
    val enumValues: List<String> = emptyList(),
    /** Минимальное значение для чисел (включительно). */
    val min: Double? = null,
    /** Максимальное значение для чисел (включительно). */
    val max: Double? = null,
) {
    /** Сериализует параметр в JSON Schema fragment. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type.wireName)
        put("description", description)
        if (enumValues.isNotEmpty() && type == JsonType.STRING) {
            val arr = JSONArray()
            enumValues.forEach { arr.put(it) }
            put("enum", arr)
        }
        if (type == JsonType.INTEGER || type == JsonType.NUMBER) {
            if (min != null) put("minimum", min)
            if (max != null) put("maximum", max)
        }
    }

    /** Все типы параметров в OpenAI JSON Schema, которые мы поддерживаем. */
    enum class JsonType(val wireName: String) {
        STRING("string"),
        INTEGER("integer"),
        NUMBER("number"),
        BOOLEAN("boolean");

        companion object {
            fun fromWireName(name: String): JsonType =
                entries.firstOrNull { it.wireName == name } ?: STRING
        }
    }
}

/**
 * Полное определение одного инструмента, который AI может вызвать.
 *
 * @property name машинное имя (snake_case), LLM и handler обязаны использовать его.
 * @property description человекочитаемое описание — LLM использует его,
 *   чтобы решить, когда вызывать инструмент; поэтому формулировка критична.
 * @property parameters список параметров — генерирует `properties`/`required` в схеме.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>,
) {
    /**
     * Полная JSON Schema в OpenAI-формате, готовая к отправке в
     * `tools[].function.parameters`.
     */
    fun jsonSchema(): JSONObject = JSONObject().apply {
        put("type", "object")
        val props = JSONObject()
        val required = JSONArray()
        parameters.forEach { param ->
            props.put(param.name, param.toJson())
            if (param.required) required.put(param.name)
        }
        put("properties", props)
        put("required", required)
        put("additionalProperties", false)
    }
}

/**
 * Вызов инструмента, запрошенный LLM.
 *
 * @property id уникальный идентификатор вызова — LLM использует его,
 *   чтобы сопоставить результат с конкретным вызовом, поэтому важно
 *   сохранить ID без изменений при отправке tool result обратно в модель.
 * @property toolName имя инструмента (= [ToolDefinition.name]).
 * @property argumentsMap распарсенные JSON-аргументы (строка → Any?).
 *   null = LLM не передал аргументов вовсе.
 */
data class ToolCall(
    val id: String,
    val toolName: String,
    val argumentsMap: Map<String, Any?>,
) {
    /** Аргументы обратно в JSON-строку для логов и обратной отправки в LLM. */
    fun argumentsJson(): String = JSONObject(argumentsMap).toString()
}

/**
 * Результат выполнения одного [ToolCall].
 *
 * @property toolCallId ID соответствующего вызова — LLM использует его,
 *   чтобы понять, к какому вызову относится результат.
 * @property toolName имя выполненного инструмента.
 * @property ok true = инструмент отработал успешно; false = ошибка исполнения.
 * @property output машинно-читаемый результат (JSON-строка); попадёт в LLM
 *   как сообщение роли `tool`. Может быть пустым, если действие side-effect
 *   без возвращаемого значения.
 * @property errorMessage текст ошибки для пользователя (показывается, если ok=false).
 */
data class ToolResult(
    val toolCallId: String,
    val toolName: String,
    val ok: Boolean,
    val output: String,
    val errorMessage: String? = null,
) {
    /**
     * Сообщение для LLM в формате OpenAI tool result:
     * ```json
     * {"role": "tool", "tool_call_id": "...", "content": "..."}
     * ```
     */
    fun asToolMessageJson(): JSONObject = JSONObject().apply {
        put("role", "tool")
        put("tool_call_id", toolCallId)
        // Если выполнение упало — передаём LLM причину в content,
        // чтобы модель могла либо переспросить, либо объяснить пользователю.
        put("content", if (ok) output else "ERROR: ${errorMessage ?: "unknown failure"}")
    }
}

/**
 * Контракт обработчика инструмента: чистая функция, которая получает
 * распарсенные аргументы и возвращает [ToolResult].
 *
 * Реализации регистрируются в [ToolRegistry] под именем своего инструмента;
 * оркестратор находит их по имени `tool_calls[].function.name` от LLM.
 */
fun interface ToolHandler {
    suspend fun invoke(args: Map<String, Any?>): ToolResult
}

/**
 * Утилиты для безопасного чтения аргументов из [Map].
 *
 * LLM иногда «забывает» обязательные поля — хелперы возвращают безопасное
 * дефолтное значение и логируют расхождение, не валя весь пайплайн.
 */
object ToolArgs {
    fun string(map: Map<String, Any?>, key: String, default: String = ""): String =
        (map[key] as? String)?.takeIf { it.isNotEmpty() } ?: default

    fun int(map: Map<String, Any?>, key: String, default: Int = 0): Int = when (val v = map[key]) {
        is Int -> v
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: default
        is Boolean -> if (v) 1 else 0
        else -> default
    }

    fun bool(map: Map<String, Any?>, key: String, default: Boolean = false): Boolean = when (val v = map[key]) {
        is Boolean -> v
        is String -> v.lowercase() in setOf("true", "on", "yes", "1", "вкл", "включить")
        is Number -> v.toInt() != 0
        else -> default
    }

    fun double(map: Map<String, Any?>, key: String, default: Double = 0.0): Double = when (val v = map[key]) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull() ?: default
        else -> default
    }
}
