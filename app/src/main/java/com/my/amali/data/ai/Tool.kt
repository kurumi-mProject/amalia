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
 *   "properties": { "enabled": {"type": "boolean", "description": "…"} },
 *   "required": ["enabled"],
 *   "additionalProperties": false
 * }
 * ```
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
        BOOLEAN("boolean"),
        ;

        companion object {
            fun fromWireName(name: String): JsonType =
                entries.firstOrNull { it.wireName == name } ?: STRING
        }
    }
}

/**
 * Полное определение одного инструмента, который LLM может вызвать.
 *
 * @property name машинное имя (snake_case) — LLM и обработчик используют его.
 * @property description описание для модели: именно по нему LLM решает,
 *   когда вызывать инструмент, поэтому формулировка критична.
 * @property parameters параметры — генерируют `properties`/`required` в схеме.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter> = emptyList(),
) {
    /** JSON Schema в OpenAI-формате для `tools[].function.parameters`. */
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
 * @property id идентификатор вызова, выданный моделью. Его обязательно нужно
 *   вернуть без изменений в `tool_call_id`, иначе Groq/OpenAI отвечает 400.
 * @property toolName имя инструмента (= [ToolDefinition.name]).
 * @property argumentsMap распарсенные аргументы (JSON-объект → Map).
 */
data class ToolCall(
    val id: String,
    val toolName: String,
    val argumentsMap: Map<String, Any?> = emptyMap(),
) {
    /**
     * Аргументы обратно в JSON-строку — ровно в таком виде API ждёт
     * `function.arguments` при возврате вызова в истории.
     */
    fun argumentsJson(): String {
        val obj = JSONObject()
        argumentsMap.forEach { (key, value) ->
            obj.put(key, value ?: JSONObject.NULL)
        }
        return obj.toString()
    }

    /** Компактное описание для логов и UI-подписей. */
    fun describe(): String = "$toolName(${argumentsMap.entries.joinToString { "${it.key}=${it.value}" }})"
}

/**
 * Результат выполнения [ToolCall], уже привязанный к конкретному вызову.
 *
 * ## Почему тут нет публичного конструктора с toolCallId
 *
 * Раньше каждый обработчик сам собирал `ToolResult(...)` и обязан был
 * подставить `toolCallId`. На практике во всех обработчиках туда попадало
 * **имя инструмента** вместо id вызова, и Groq отвечал `400 invalid
 * tool_call_id` — весь конвейер управления телефоном молча умирал.
 *
 * Теперь обработчик возвращает [ToolOutcome] (успех/ошибка, без id), а
 * привязку к вызову делает [ToolRegistry] — единственное место, где id
 * вообще известен. Ошибку «забыл id» стало невозможно допустить.
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
     * `{"role":"tool","tool_call_id":"…","content":"…"}`.
     *
     * При ошибке в `content` уходит человекочитаемая причина — модель
     * должна уметь объяснить пользователю, почему действие не вышло.
     */
    fun asToolMessageJson(): JSONObject = JSONObject().apply {
        put("role", "tool")
        put("tool_call_id", toolCallId)
        put("content", contentForModel())
    }

    /** Текст, который увидит модель как результат инструмента. */
    fun contentForModel(): String = when {
        ok -> output.ifBlank { """{"status":"ok"}""" }
        else -> JSONObject()
            .put("status", "error")
            .put("reason", errorMessage ?: "unknown failure")
            .toString()
    }
}

/**
 * Итог работы обработчика: успех с машинно-читаемым выводом либо отказ
 * с причиной, которую модель сможет пересказать пользователю.
 */
sealed interface ToolOutcome {

    /** Успех. [output] — JSON-строка для модели. */
    data class Success(val output: String) : ToolOutcome

    /** Отказ с понятной причиной. */
    data class Failure(val reason: String) : ToolOutcome

    companion object {
        /** Успех без данных — только факт выполнения. */
        fun done(): ToolOutcome = Success("""{"status":"ok"}""")

        /** Успех с парами ключ-значение: `json("wifi" to true)`. */
        fun json(vararg pairs: Pair<String, Any?>): ToolOutcome {
            val obj = JSONObject()
            pairs.forEach { (key, value) -> obj.put(key, value ?: JSONObject.NULL) }
            return Success(obj.toString())
        }

        /** Успех с готовой JSON-строкой (например от контроллера доступности). */
        fun raw(json: String): ToolOutcome =
            Success(json.ifBlank { """{"status":"ok"}""" })

        /** Отказ. */
        fun failed(reason: String): ToolOutcome = Failure(reason)

        /**
         * Успех/отказ по флагу — самый частый случай для системных
         * переключателей, где API возвращает только boolean.
         */
        fun of(ok: Boolean, reasonIfFailed: String, vararg pairs: Pair<String, Any?>): ToolOutcome =
            if (ok) json(*pairs) else Failure(reasonIfFailed)
    }
}

/**
 * Контракт обработчика инструмента: получает валидированные аргументы,
 * возвращает [ToolOutcome]. Привязку к `tool_call_id` делает [ToolRegistry].
 */
fun interface ToolHandler {
    suspend fun invoke(args: ToolArguments): ToolOutcome
}

/** Инструмент = определение для модели + обработчик для рантайма. */
data class AmaliaTool(
    val definition: ToolDefinition,
    val handler: ToolHandler,
) {
    val name: String get() = definition.name
}

/**
 * Типобезопасное чтение аргументов от LLM.
 *
 * Модели регулярно врут в типах: присылают `"30"` вместо `30`, `"true"`
 * вместо `true`, `"вкл"` вместо boolean, иногда вкладывают всё в объект
 * `{"args":{…}}`. Раньше это разбиралось в каждом обработчике вручную и
 * местами молча превращалось в 0/false. Здесь приведение одно для всех.
 */
@JvmInline
value class ToolArguments(val raw: Map<String, Any?>) {

    /** Строка; пустая/отсутствующая → [default]. */
    fun string(key: String, default: String = ""): String {
        val value = raw[key] ?: return default
        if (value === JSONObject.NULL) return default
        val text = value.toString().trim()
        return text.ifEmpty { default }
    }

    /** Целое; терпит строки, дроби и boolean. */
    fun int(key: String, default: Int = 0): Int = when (val value = raw[key]) {
        is Int -> value
        is Number -> value.toDouble().toInt()
        is Boolean -> if (value) 1 else 0
        is String -> value.trim().toIntOrNull()
            ?: value.trim().toDoubleOrNull()?.toInt()
            ?: NUMBER_IN_TEXT.find(value)?.value?.toIntOrNull()
            ?: default
        else -> default
    }

    /** Дробное; терпит строки с запятой. */
    fun double(key: String, default: Double = 0.0): Double = when (val value = raw[key]) {
        is Number -> value.toDouble()
        is Boolean -> if (value) 1.0 else 0.0
        is String -> value.trim().replace(',', '.').toDoubleOrNull() ?: default
        else -> default
    }

    /**
     * Boolean; понимает человеческие формулировки на русском и английском,
     * потому что модели под голосовой сценарий любят присылать «вкл».
     */
    fun bool(key: String, default: Boolean = false): Boolean = when (val value = raw[key]) {
        is Boolean -> value
        is Number -> value.toDouble() != 0.0
        is String -> when (value.trim().lowercase()) {
            in TRUE_WORDS -> true
            in FALSE_WORDS -> false
            else -> default
        }
        else -> default
    }

    /** Есть ли ключ с непустым значением. */
    fun has(key: String): Boolean {
        val value = raw[key] ?: return false
        if (value === JSONObject.NULL) return false
        return value.toString().isNotBlank()
    }

    /**
     * Значение enum по [values] с нормализацией регистра и дефисов.
     * null, если модель прислала что-то вне списка.
     */
    fun enum(key: String, values: List<String>): String? {
        val input = string(key).lowercase().replace('-', '_')
        if (input.isEmpty()) return null
        return values.firstOrNull { it.lowercase() == input }
            ?: values.firstOrNull { input.contains(it.lowercase()) }
    }

    private companion object {
        val NUMBER_IN_TEXT = Regex("-?\\d+")
        val TRUE_WORDS = setOf(
            "true", "1", "on", "yes", "y", "enable", "enabled",
            "вкл", "включить", "включи", "да", "истина",
        )
        val FALSE_WORDS = setOf(
            "false", "0", "off", "no", "n", "disable", "disabled",
            "выкл", "выключить", "выключи", "нет", "ложь",
        )
    }
}
