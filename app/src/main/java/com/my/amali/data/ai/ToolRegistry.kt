package com.my.amali.data.ai

import kotlinx.coroutines.CancellationException

import kotlinx.coroutines.CancellationException

/**
 * Реестр инструментов Амалии.
 *
 * Оркестратор не знает, какие именно инструменты есть — он спрашивает
 * реестр по имени. Это позволяет:
 * - добавлять/удалять инструменты в одном месте ([AmaliaTools.all]);
 * - в тестах подменять реестр на mock без переписывания оркестратора;
 * - в одной и той же сборке иметь разные наборы (basic / pro / debug).
 */
class ToolRegistry private constructor(
    private val definitionsByName: Map<String, ToolDefinition>,
    private val handlersByName: Map<String, ToolHandler>,
) {

    /** Имена всех зарегистрированных инструментов. */
    val names: Set<String> get() = definitionsByName.keys

    /** Все определения инструментов — для отдачи LLM в `tools[]`. */
    val definitions: List<ToolDefinition> get() = definitionsByName.values.toList()

    /** Возвращает определение инструмента по имени или null, если такого нет. */
    fun definition(name: String): ToolDefinition? = definitionsByName[name]

    /**
     * Выполняет один [ToolCall] через зарегистрированный обработчик.
     *
     * Если для [ToolCall.toolName] нет обработчика — возвращает результат
     * с ошибкой вместо броска исключения. LLM получит явный текст ошибки
     * и сможет перефразировать ответ.
     */
    suspend fun execute(call: ToolCall): ToolResult {
        val definition = definitionsByName[call.toolName]
        if (definition == null) {
            return ToolResult(
                toolCallId = call.id,
                toolName = call.toolName,
                ok = false,
                output = "",
                errorMessage = "Неизвестный инструмент '${call.toolName}'.",
            )
        }
        return executeWithHandler(call, definition)
    }

    private suspend fun executeWithHandler(call: ToolCall, definition: ToolDefinition): ToolResult {
        val handler = handlersByName[call.toolName] ?: return ToolResult(
            toolCallId = call.id,
            toolName = call.toolName,
            ok = false,
            output = "",
            errorMessage = "Для '${call.toolName}' нет обработчика.",
        )
        return try {
            handler.invoke(call.argumentsMap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            ToolResult(
                toolCallId = call.id,
                toolName = call.toolName,
                ok = false,
                output = "",
                errorMessage = "Исключение в '${call.toolName}': ${e.message ?: e.javaClass.simpleName}",
            )
        }
    }

    companion object {
        /**
         * Собирает реестр из [AmaliaTools.all]. Имена должны быть уникальны —
         * при коллизии более новый инструмент перезаписывает старый и
         * кидается исключение, чтобы это не уходило в тихий баг.
         */
        fun from(tools: List<Pair<ToolDefinition, ToolHandler>>): ToolRegistry {
            val defs = LinkedHashMap<String, ToolDefinition>()
            val handlers = LinkedHashMap<String, ToolHandler>()
            for ((def, handler) in tools) {
                require(defs.put(def.name, def) == null) {
                    "Дубликат имени инструмента '${def.name}' в реестре."
                }
                require(handlers.put(def.name, handler) == null) {
                    "Дубликат обработчика '${def.name}' в реестре."
                }
            }
            return ToolRegistry(defs, handlers)
        }

        /** Пустой реестр — для тестов и режима «без инструментов». */
        fun empty(): ToolRegistry = ToolRegistry(emptyMap(), emptyMap())
    }
}
