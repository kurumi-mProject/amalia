package com.my.amali.data.ai

import com.my.amali.data.model.ChatMessage
import com.my.amali.data.model.DeviceStatus
import com.my.amali.domain.entity.AppLanguage
import com.my.amali.domain.entity.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.util.Locale

/**
 * Приложение, которое пользователь отметил в настройках как «своё».
 *
 * Это тот мост, через который выбор пользователя доходит до модели: без него
 * LLM видит только текст запроса и угадывает пакет. На не-Google прошивках
 * угадывание промахивается мимо реальных пакетов (там нет
 * `com.google.android.youtube`), поэтому список собирается из фактического
 * сканирования устройства — см. `AppRegistry`.
 *
 * @property label название, которое пользователь видит на экране
 *   («Галерея», «Telegram»). Именно его просим передавать в `open_app`:
 *   человек и модель говорят на одном языке, а пакет подставляет поиск.
 * @property packageName точный пакет — единственный стабильный идентификатор;
 *   название может измениться при обновлении приложения, пакет — нет.
 * @property aliases слова, которыми пользователь называет это приложение
 *   вслух («музон», «телега»). Пустой список, если синонимов нет.
 */
data class KnownApp(
    val label: String,
    val packageName: String,
    val aliases: List<String> = emptyList(),
)

/**
 * Параметры работы движков: язык распознавания/синтеза и характеристики речи.
 * Строятся из [UserSettings], поэтому изменение настроек мгновенно влияет
 * на следующий запрос без пересоздания движков.
 *
 * @property languageCode двухбуквенный код языка ("ru", "en", …) — уже разрешённый,
 *   то есть [AppLanguage.SYSTEM] заменён на язык устройства.
 * @property speechRate множитель скорости синтеза, [0.5, 2.0].
 * @property speechPitch множитель высоты голоса, [0.5, 2.0].
 * @property deviceStatus текущее состояние устройства — передаётся в системный промпт
 *   чтобы Амалия знала какие разрешения выданы/не выданы.
 * @property conversationSummary краткое резюме истории (3-5 предложений) — заменяет старые сообщения
 *   чтобы не переполнять контекст. null если сессия только началась.
 * @property knownApps приложения, которые пользователь отметил как «свои»,
 *   в виде готовых пар «как называть» → пакет. Уходят в промпт отдельным
 *   блоком, потому что без них модель угадывает пакет и на не-Google
 *   прошивках промахивается (там `com.google.android.youtube` может
 *   отсутствовать вовсе). Пустой список означает «пользователь ещё не
 *   выбирал» — тогда блок в промпт не добавляется вообще.
 * @property appAliases пользовательские синонимы «как говорю» → пакет.
 *   Передаются рядом с приложениями: модель должна видеть ровно тот
 *   словарь, которым человек разговаривает.
 */
data class EngineOptions(
    val languageCode: String = "ru",
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val deviceStatus: DeviceStatus = DeviceStatus.Offline,
    val conversationSummary: String? = null,
    val knownApps: List<KnownApp> = emptyList(),
    val appAliases: Map<String, String> = emptyMap(),
) {
    /**
     * Готовый блок для системного промпта: «название → пакет» плюс словарь
     * синонимов. Пустая строка, если пользователь ничего не выбрал, — тогда
     * промпт не тратит на этот блок ни одного токена.
     *
     * Формат намеренно компактный (одна строка на приложение): при 15
     * приложениях это ~200 символов вместо развёрнутого JSON, а разницы для
     * модели нет — она всё равно использует только пару «имя → пакет».
     */
    val appsPromptSection: String
        get() {
            if (knownApps.isEmpty() && appAliases.isEmpty()) return ""
            val builder = StringBuilder()
            builder.append("\n# ПРИЛОЖЕНИЯ ПОЛЬЗОВАТЕЛЯ\n")
            if (knownApps.isNotEmpty()) {
                builder.append("Пользователь отметил эти приложения как свои. ")
                builder.append("Передавай в open_app именно название из списка, ")
                builder.append("а не пакет, — поиск разберётся сам.\n")
                knownApps.forEach { app ->
                    builder.append("- ")
                    builder.append(app.label)
                    builder.append(" (")
                    builder.append(app.packageName)
                    builder.append(")")
                    if (app.aliases.isNotEmpty()) {
                        builder.append(" — он говорит: ")
                        builder.append(app.aliases.joinToString(", ") { "\"$it\"" })
                    }
                    builder.append('\n')
                }
            }
            if (appAliases.isNotEmpty()) {
                builder.append("Личный словарь синонимов (высший приоритет): ")
                builder.append(
                    appAliases.entries.joinToString(", ") { "\"${it.key}\" → ${it.value}" },
                )
                builder.append('\n')
            }
            return builder.toString()
        }
    /** Человекочитаемое имя языка для системного промпта LLM. */
    val languageName: String
        get() = LANGUAGE_NAMES[languageCode] ?: "русском"

    companion object {
        val Default: EngineOptions = EngineOptions()

        private val LANGUAGE_NAMES = mapOf(
            "ru" to "русском",
            "en" to "английском",
            "es" to "испанском",
            "ar" to "арабском",
            "de" to "немецком",
            "fr" to "французском",
            "hi" to "хинди",
            "ja" to "японском",
            "zh" to "китайском",
        )

        /** Языки, поддерживаемые моделью Deepgram nova-2. */
        private val SUPPORTED_STT = setOf("ru", "en", "es", "de", "fr", "hi", "ja", "zh")

        /** Строит параметры из пользовательских настроек. */
        fun from(settings: UserSettings): EngineOptions = EngineOptions(
            languageCode = resolveLanguage(settings.selectedLanguage),
            speechRate = settings.speechRate.coerceIn(0.5f, 2f),
            speechPitch = settings.speechPitch.coerceIn(0.5f, 2f),
        )

        private fun resolveLanguage(language: AppLanguage): String {
            val code = if (language.isSystem) {
                Locale.getDefault().language.lowercase(Locale.ROOT)
            } else {
                language.code
            }
            return if (code in SUPPORTED_STT) code else "ru"
        }
    }
}

/**
 * Событие движка распознавания речи.
 *
 * Порядок для одной фразы: серия [Level] и [Partial] → один [Final] на
 * завершённый сегмент → завершение потока. Долгая речь даёт несколько
 * [Final]: их нужно склеивать, а не заменять.
 */
sealed interface SttEvent {
    /** Мгновенная громкость микрофона, 0..1 — для анимации волны. */
    data class Level(val level: Float) : SttEvent

    /** Промежуточная (неточная) гипотеза распознавания. */
    data class Partial(val text: String) : SttEvent

    /** Финальный текст очередного сегмента речи. */
    data class Final(val text: String) : SttEvent
}

/**
 * Контракт движка распознавания речи. Реализация сама владеет микрофоном
 * и закрывает поток, когда пользователь закончил говорить.
 */
interface SpeechToTextEngine {
    /** Готовит ресурсы движка. */
    suspend fun initialize()

    /** Освобождает ресурсы. Безопасно вызывать повторно. */
    suspend fun close()

    /**
     * Заранее открывает WS соединение без микрофона — warmup при касании кнопки.
     * Вызывается в момент onPress, до onClick (~150-300ms раньше).
     * По умолчанию ничего не делает — реализуется только в реальном движке.
     */
    suspend fun preconnect() {}

    /**
     * Открывает микрофон и стримит события распознавания.
     * Поток завершается сам после окончания фразы (VAD) либо по отмене корутины.
     */
    fun transcribe(options: EngineOptions = EngineOptions.Default): Flow<SttEvent>
}

/**
 * Контракт движка синтеза речи.
 *
 * WebSocket-режим: движок держит открытое соединение и принимает токены
 * по одному через [sendToken]/[flush]/[stop] вместо одного вызова [speak].
 * Это позволяет Fish Audio начать генерацию аудио пока LLM ещё говорит.
 */
interface TextToSpeechEngine {
    suspend fun initialize()
    suspend fun close()

    /** Синтезирует [text] целиком (используется для коротких фраз). */
    fun speak(text: String, options: EngineOptions = EngineOptions.Default): Flow<AudioChunk>

    /** Открывает сессию стриминга — вызвать перед первым [sendToken]. */
    suspend fun startStreaming(options: EngineOptions = EngineOptions.Default) {}

    /** Отправляет один токен LLM в открытую сессию. */
    suspend fun sendToken(token: String) {}

    /** Форсирует синтез накопленного текста. */
    suspend fun flushStreaming() {}

    /** Завершает сессию и закрывает соединение. */
    suspend fun stopStreaming() {}

    /** Поток аудио чанков из стриминговой сессии. */
    val streamingAudio: Flow<AudioChunk> get() = kotlinx.coroutines.flow.emptyFlow()
}

// ─────────────────────────────────────────────────────────────────────
//  Tool-aware LLM события
// ─────────────────────────────────────────────────────────────────────

/**
 * Причина завершения LLM-вызова.
 *
 * OpenAI/Groq возвращают `finish_reason`:
 * — [Stop] — модель закончила естественно (ответ на пользователя);
 * — [ToolCalls] — модель хочет вызвать инструменты, нужен второй раунд;
 * — [Length] — упёрлись в лимит токенов (можно продолжить, но не обязательно);
 * — [ContentFilter] — контент отфильтрован по политике;
 * — [Error] — внутренняя ошибка движка.
 */
enum class FinishReason {
    STOP,
    TOOL_CALLS,
    LENGTH,
    CONTENT_FILTER,
    ERROR;

    companion object {
        fun fromWireName(name: String?): FinishReason = when (name) {
            "stop" -> STOP
            "tool_calls" -> TOOL_CALLS
            "length" -> LENGTH
            "content_filter" -> CONTENT_FILTER
            "error" -> ERROR
            else -> STOP
        }
    }
}

/**
 * Событие движка языковой модели при работе с инструментами.
 *
 * Последовательность для одного вызова LLM:
 * 1. Серия [ContentDelta] — естественный текст по токенам.
 * 2. Возможно — серия [ToolCallDetected] (по одному на каждый вызов).
 *    Большинство моделей стримят их параллельно с `ContentDelta`,
 *    поэтому UI должен выводить только delta-текст, а уведомления
 *    показывать после полного сбора аргументов.
 * 3. Один [Completed] с [FinishReason] — поток LLM завершён.
 *
 * [Completed] НЕ означает конец всей беседы — оркестратор выполняет
 * вызовы, добавляет их результаты в историю и ещё раз зовёт LLM.
 */
sealed interface LLMEvent {

    /** Очередной кусок естественного текста от модели. */
    data class ContentDelta(val text: String) : LLMEvent

    /**
     * LLM завершил аргументацию вызова и хочет его исполнить.
     * Каждый такой ивент содержит уже распарсенный [ToolCall] с id.
     */
    data class ToolCallDetected(val call: ToolCall) : LLMEvent

    /**
     * Поток модели закрыт. Если [reason] == [FinishReason.TOOL_CALLS],
     * оркестратор должен исполнить [ToolCall] и перезапустить LLM
     * с историей пополненной результатами; иначе — это финальный ответ.
     */
    data class Completed(val reason: FinishReason) : LLMEvent
}

/**
 * Контракт языковой модели.
 *
 * Реализуется двумя способами:
 * - [generateResponse] — простой текстовый контракт для моделей без
 *   поддержки tools (или для офлайн-режима Mock);
 * - [chatWithTools] — полноценный multi-turn цикл с вызовом инструментов.
 *
 * Оркестратор предпочитает [chatWithTools]. Если драйвер не умеет в tools
 * (специальный флаг в реализации), оркестратор падает на [generateResponse]
 * и парсит команды из свободного текста — для совместимости.
 */
interface LanguageModel {
    suspend fun initialize()
    suspend fun close()

    /**
     * Текстовая генерация: простой запрос → поток дельт.
     *
     * Используется как fallback, если движок не поддерживает инструменты.
     * Реализации с поддержкой tools могут реализовать её как тонкую
     * обёртку над [chatWithTools].
     */
    fun generateResponse(
        prompt: String,
        history: List<ChatMessage>,
        options: EngineOptions = EngineOptions.Default,
    ): Flow<String>

    /**
     * Полноценный чат с инструментами.
     *
     * @param messages полная история диалога, включая результаты tools
     *   (роль `tool`). Последнее сообщение — реплика пользователя.
     * @param tools схемы доступных инструментов.
     * @param options параметры языка/голоса.
     * @param alreadyExecutedTools имена инструментов, которые оркестратор
     *   уже выполнил в текущем multi-turn цикле. Реальные движки игнорируют
     *   поле — у них есть полная история вызовов в сообщениях; mock-LLM
     *   использует его, чтобы не повторять вызов для уже завершённого action.
     */
    fun chatWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        options: EngineOptions = EngineOptions.Default,
        alreadyExecutedTools: Set<String> = emptySet(),
    ): Flow<LLMEvent> = emptyFlow()

    /**
     * Сообщает оркестратору, что драйвер умеет в tool calling.
     * Без этой поддержки оркестратор работает в legacy-режиме через
     * [generateResponse] и парсинг JSON в ответе LLM.
     */
    val supportsTools: Boolean get() = false
}

/**
 * Единица синтезированного аудио.
 *
 * @property data PCM 16-bit mono little-endian.
 * @property sampleRate частота дискретизации [data], Гц.
 */
data class AudioChunk(val data: ByteArray, val sampleRate: Int) {
    /** Количество сэмплов в чанке. */
    val sampleCount: Int
        get() = data.size / 2

    /** Длительность чанка в миллисекундах. */
    val durationMs: Int
        get() = if (sampleRate <= 0) 0 else (sampleCount * 1000) / sampleRate

    /** Нормализованная громкость чанка 0..1 — используется для анимации. */
    fun level(): Float {
        if (sampleCount == 0) return 0f
        var sum = 0.0
        val step = if (sampleCount > 256) sampleCount / 256 else 1
        var counted = 0
        var i = 0
        while (i < sampleCount) {
            val low = data[i * 2].toInt() and 0xFF
            val high = data[i * 2 + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toInt()
            sum += sample.toDouble() * sample.toDouble()
            counted++
            i += step
        }
        if (counted == 0) return 0f
        val rms = Math.sqrt(sum / counted)
        return (rms / 8000.0).coerceIn(0.0, 1.0).toFloat()
    }

    override fun equals(other: Any?): Boolean =
        other is AudioChunk && other.sampleRate == sampleRate && other.data.contentEquals(data)

    override fun hashCode(): Int = 31 * data.contentHashCode() + sampleRate

    override fun toString(): String =
        "AudioChunk(bytes=${data.size}, sampleRate=$sampleRate, durationMs=$durationMs)"
}

/**
 * Описание подключённых движков — для экрана настроек и диагностики.
 */
data class AIConfig(
    val sttEngineName: String,
    val ttsEngineName: String,
    val llmEngineName: String,
) {
    fun describe(): String = "STT=$sttEngineName, TTS=$ttsEngineName, LLM=$llmEngineName"

    companion object {
        /** Реальный продакшен-конвейер. */
        val Live: AIConfig = AIConfig(
            sttEngineName = "Deepgram nova-2",
            ttsEngineName = "Fish Audio s2.1-pro",
            llmEngineName = "Groq qwen3.8-27b + tools",
        )

        /** Встроенный офлайн-конвейер-заглушка (используется в превью и тестах). */
        val Mock: AIConfig = AIConfig(
            sttEngineName = "MockSpeechToTextEngine",
            ttsEngineName = "MockTextToSpeechEngine",
            llmEngineName = "MockLanguageModel + tools",
        )
    }
}

/** Исключение движка с текстом, пригодным для показа пользователю. */
class EngineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
