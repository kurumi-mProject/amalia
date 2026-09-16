package com.my.amali.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Реестр инструментов Амалии — единственное место, где вызов модели
 * превращается в реальное действие на телефоне.
 *
 * ## Что здесь исправлено по сравнению с прошлой версией
 *
 * 1. **Дубликаты больше не роняют приложение.** Раньше [from] делал
 *    `require(put(...) == null)`, а в наборе инструментов лежала пара
 *    `take_photo` (алиас `open_camera` возвращал тот же самый инструмент).
 *    Первое же обращение к реестру бросало `IllegalArgumentException`
 *    прямо в `ServiceLocator` — приложение падало при старте диалога.
 *    Теперь дубликаты **отбрасываются** с записью в [droppedDuplicates]:
 *    голосовой ассистент не имеет права умирать из-за опечатки в каталоге.
 *
 * 2. **`tool_call_id` подставляет реестр.** Обработчики возвращают
 *    [ToolOutcome] без идентификаторов, поэтому «забыть id» невозможно.
 *
 * 3. **Зависший инструмент не вешает диалог.** Любой обработчик
 *    ограничен [HANDLER_TIMEOUT_MS]: системный вызов, который не вернулся
 *    (частая история с `dispatchGesture` и `takeScreenshot`), превращается
 *    в честную ошибку для модели, а не в вечное «думаю…».
 */
class ToolRegistry private constructor(
    private val tools: Map<String, AmaliaTool>,
    /** Имена, выброшенные при сборке из-за конфликта. Диагностика, не краш. */
    val droppedDuplicates: List<String>,
) {

    /** Имена всех зарегистрированных инструментов. */
    val names: Set<String> get() = tools.keys

    /** Определения для отправки модели в `tools[]`. */
    val definitions: List<ToolDefinition> get() = tools.values.map { it.definition }

    /** Сколько инструментов доступно модели. */
    val size: Int get() = tools.size

    /** Определение по имени или null. */
    fun definition(name: String): ToolDefinition? = tools[name]?.definition

    /** Есть ли такой инструмент. */
    fun contains(name: String): Boolean = tools.containsKey(name)

    /**
     * Выполняет вызов модели и возвращает результат, уже привязанный к
     * [ToolCall.id].
     *
     * Никогда не бросает исключений (кроме отмены корутины): любая ошибка
     * обработчика становится [ToolResult] с `ok = false`, потому что модель
     * должна получить ответ на **каждый** вызов — иначе следующий запрос к
     * Groq уйдёт с непарным `tool_calls` и получит 400.
     */
    suspend fun execute(call: ToolCall): ToolResult {
        val tool = tools[call.toolName]
            ?: return failure(
                call,
                "Инструмента '${call.toolName}' не существует. " +
                    "Доступные: ${names.joinToString(", ")}",
            )

        return try {
            val outcome = withTimeout(HANDLER_TIMEOUT_MS) {
                tool.handler.invoke(ToolArguments(call.argumentsMap))
            }
            when (outcome) {
                is ToolOutcome.Success -> ToolResult(
                    toolCallId = call.id,
                    toolName = call.toolName,
                    ok = true,
                    output = outcome.output,
                )

                is ToolOutcome.Failure -> failure(call, outcome.reason)
            }
        } catch (e: TimeoutCancellationException) {
            failure(call, "Действие '${call.toolName}' не ответило за ${HANDLER_TIMEOUT_MS / 1000} с.")
        } catch (e: CancellationException) {
            // Отмена всего цикла (пользователь нажал «Стоп») — пробрасываем.
            throw e
        } catch (e: Throwable) {
            failure(
                call,
                "Сбой в '${call.toolName}': ${e.message ?: e.javaClass.simpleName}",
            )
        }
    }

    /**
     * Выполняет цепочку вызовов последовательно.
     *
     * Последовательность важна: «убавь яркость и включи фонарик» —
     * это два системных действия, параллельный запуск которых даёт гонку
     * на одних и тех же настройках.
     */
    suspend fun executeAll(calls: List<ToolCall>): List<ToolResult> =
        calls.map { execute(it) }

    private fun failure(call: ToolCall, reason: String) = ToolResult(
        toolCallId = call.id,
        toolName = call.toolName,
        ok = false,
        output = "",
        errorMessage = reason,
    )

    companion object {

        /**
         * Максимальное время одного обработчика. Скриншот и жесты через
         * специальный доступ — самые медленные операции (до ~4 с), поэтому
         * лимит с запасом, но конечный.
         */
        const val HANDLER_TIMEOUT_MS = 15_000L

        /**
         * Собирает реестр, отбрасывая дубликаты имён.
         *
         * Первое объявление имени выигрывает — порядок в каталоге
         * инструментов осмысленный, а дубль почти всегда является
         * копипастой/алиасом.
         */
        fun of(tools: List<AmaliaTool>): ToolRegistry {
            val accepted = LinkedHashMap<String, AmaliaTool>(tools.size)
            val dropped = mutableListOf<String>()
            for (tool in tools) {
                val name = tool.name.trim()
                when {
                    name.isEmpty() -> dropped += "(пустое имя)"
                    accepted.containsKey(name) -> dropped += name
                    else -> accepted[name] = tool
                }
            }
            return ToolRegistry(accepted, dropped)
        }

        /** Реестр без инструментов — чистый диалог без управления телефоном. */
        fun empty(): ToolRegistry = ToolRegistry(emptyMap(), emptyList())
    }
}
