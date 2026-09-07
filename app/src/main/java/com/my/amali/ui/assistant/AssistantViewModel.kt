package com.my.amali.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

/**
 * Расширенное состояние главного экрана Амалии.
 * Полностью покрывает все реальные состояния продакшн-качества.
 */
data class AssistantUiState(
    val orbState: OrbState = OrbState.Idle,
    val userTranscript: String = "",
    val amaliaReply: String = "",
    val isActive: Boolean = false,
    val audioLevel: Float = 0f,           // 0f..1f для визуализации во время прослушивания
    val suggestions: List<String> = emptyList(),
    val errorMessage: String? = null,
    val hasMicPermission: Boolean = true, // в демо всегда true; в реальном — проверяется
    val isFirstLaunch: Boolean = false,
    val conversationCount: Int = 0,
)

/**
 * Главный ViewModel экрана голосового ассистента Амалия.
 *
 * Реализует полноценный, красивый, живой опыт:
 * - Прерывание в любой момент
 * - Реалистичная симуляция аудио-уровня при прослушивании
 * - Стриминговый набор текста (как настоящий LLM)
 * - Контекстные предложения
 * - Разные эмоциональные ответы
 * - Обработка ошибок и first-launch состояния
 */
class AssistantViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var sessionJob: Job? = null

    init {
        // Показываем красивые предложения при первом открытии
        _uiState.update {
            it.copy(
                suggestions = defaultSuggestions,
                isFirstLaunch = true
            )
        }
    }

    /** Пользователь нажал на большую кнопку микрофона */
    fun onMicPressed() {
        val current = _uiState.value

        if (current.isActive) {
            // Прерывание сессии
            interruptSession()
        } else {
            if (!current.hasMicPermission) {
                _uiState.update { it.copy(errorMessage = "Нужен доступ к микрофону") }
                return
            }
            startListeningSession()
        }
    }

    /** Пользователь выбрал быстрое предложение */
    fun onSuggestionClicked(suggestion: String) {
        // Сразу стартуем сессию с готовой фразой
        if (_uiState.value.isActive) interruptSession()

        _uiState.update {
            it.copy(
                userTranscript = suggestion,
                suggestions = emptyList(),
                isFirstLaunch = false
            )
        }

        // Небольшая пауза, чтобы человек увидел, что его фраза принята
        viewModelScope.launch {
            delay(280)
            processUserInput(suggestion)
        }
    }

    /** Пользователь хочет повторить последний ответ голосом (демо) */
    fun onReplayLastReply() {
        val reply = _uiState.value.amaliaReply
        if (reply.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(orbState = OrbState.Speaking, isActive = true) }
            // Просто "проиграем" ответ ещё раз (в реальности — TTS)
            delay(1600)
            _uiState.update { it.copy(orbState = OrbState.Idle, isActive = false) }
        }
    }

    /** Закрыть ошибку */
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ==================== ВНУТРЕННЯЯ ЛОГИКА СЕССИИ ====================

    private fun startListeningSession() {
        sessionJob?.cancel()

        sessionJob = viewModelScope.launch {
            // Сброс предыдущего состояния
            _uiState.update {
                it.copy(
                    orbState = OrbState.Listening,
                    isActive = true,
                    userTranscript = "",
                    amaliaReply = "",
                    audioLevel = 0f,
                    suggestions = emptyList(),
                    errorMessage = null,
                    isFirstLaunch = false
                )
            }

            // === ФАЗА 1: СЛУШАЕМ ===
            // Симулируем живой уровень голоса (как настоящий аудио-коллбэк)
            val listeningDuration = Random.nextLong(1350, 2450)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < listeningDuration) {
                // Красивая живая амплитуда — не случайный шум, а органичная волна
                val progress = (System.currentTimeMillis() - startTime) / listeningDuration.toFloat()
                val wave = (sin(progress * 6.2f) * 0.5f + 0.5f) * 0.65f +
                           (sin(progress * 14f + 1.3f) * 0.5f + 0.5f) * 0.35f

                val level = (0.25f + wave * 0.75f).coerceIn(0f, 1f)

                _uiState.update { it.copy(audioLevel = level) }
                delay(38)
            }

            _uiState.update { it.copy(audioLevel = 0.15f) }
            delay(120)

            // Выбираем реалистичную фразу пользователя
            val spokenText = realisticUserPhrases.random()
            _uiState.update { it.copy(userTranscript = spokenText) }

            delay(280)

            // === ФАЗА 2: ДУМАЕМ ===
            _uiState.update {
                it.copy(orbState = OrbState.Thinking, audioLevel = 0f)
            }
            delay(920 + Random.nextLong(0, 480))

            // === ФАЗА 3: ГОВОРИМ (стриминговый ответ) ===
            val reply = generateBeautifulReply(spokenText)

            _uiState.update {
                it.copy(
                    orbState = OrbState.Speaking,
                    amaliaReply = ""
                )
            }

            // Стриминговый набор ответа (как реальный LLM токен за токеном)
            val words = reply.split(" ")
            val builder = StringBuilder()

            for (word in words) {
                builder.append(word).append(" ")
                _uiState.update { it.copy(amaliaReply = builder.toString().trim()) }
                delay(38 + Random.nextLong(0, 28))
            }

            // Небольшая пауза после завершения речи
            delay(1350)

            // === ФАЗА 4: ЗАВЕРШЕНИЕ ===
            val newCount = _uiState.value.conversationCount + 1

            _uiState.update {
                it.copy(
                    orbState = OrbState.Idle,
                    isActive = false,
                    audioLevel = 0f,
                    suggestions = generateContextualSuggestions(spokenText),
                    conversationCount = newCount
                )
            }
        }
    }

    private fun processUserInput(text: String) {
        sessionJob?.cancel()

        sessionJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    orbState = OrbState.Thinking,
                    isActive = true,
                    amaliaReply = "",
                    suggestions = emptyList()
                )
            }

            delay(780 + Random.nextLong(0, 420))

            val reply = generateBeautifulReply(text)

            _uiState.update { it.copy(orbState = OrbState.Speaking, amaliaReply = "") }

            val words = reply.split(" ")
            val builder = StringBuilder()
            for (word in words) {
                builder.append(word).append(" ")
                _uiState.update { it.copy(amaliaReply = builder.toString().trim()) }
                delay(32 + Random.nextLong(0, 24))
            }

            delay(1100)

            val newCount = _uiState.value.conversationCount + 1

            _uiState.update {
                it.copy(
                    orbState = OrbState.Idle,
                    isActive = false,
                    suggestions = generateContextualSuggestions(text),
                    conversationCount = newCount
                )
            }
        }
    }

    private fun interruptSession() {
        sessionJob?.cancel()
        _uiState.update {
            it.copy(
                orbState = OrbState.Idle,
                isActive = false,
                audioLevel = 0f,
                amaliaReply = "",
                // сохраняем последний transcript, чтобы пользователь видел, что сказал
                suggestions = if (it.suggestions.isEmpty()) defaultSuggestions else it.suggestions
            )
        }
    }

    /** Красивые, живые, с характером ответы Амалии */
    private fun generateBeautifulReply(userInput: String): String {
        val lower = userInput.lowercase()

        return when {
            lower.contains("привет") || lower.contains("здравствуй") || lower.contains("добрый") ->
                "Привет! Я здесь. Какой у тебя сегодня настрой?"

            lower.contains("как дела") || lower.contains("как ты") ->
                "Спасибо, что спросил. У меня всё хорошо — я в отличной форме и готова помогать. А у тебя как?"

            lower.contains("таймер") || lower.contains("поставь") ->
                "Ставлю таймер. На сколько минут? Могу сделать на 5, 10 или 25."

            lower.contains("расскажи") || lower.contains("интересн") ->
                "Конечно. Хочешь что-то лёгкое и удивительное или глубокое и философское?"

            lower.contains("погода") ->
                "Сейчас посмотрю актуальные данные. Хочешь просто сейчас или на весь день?"

            lower.contains("музык") || lower.contains("включи") ->
                "Какую музыку ты сейчас хочешь услышать? Могу подобрать что-то атмосферное."

            lower.contains("устал") || lower.contains("тяжело") ->
                "Понимаю. Давай сделаем короткую передышку вместе. Хочешь, я помогу тебе на пять минут выдохнуть?"

            lower.contains("шутк") || lower.contains("смешн") ->
                "Хорошо. Почему программисты путают Хэллоуин и Рождество? Потому что Oct 31 == Dec 25."

            else -> listOf(
                "Поняла тебя. Давай разберёмся вместе — с чего хочешь начать?",
                "Хорошо. Я запомнила контекст. Что дальше будет важно?",
                "Интересно. Расскажи чуть больше — я хочу понять тебя точнее.",
                "Я с тобой. Можем пойти как по самому простому пути, так и по самому красивому.",
                "Отлично. Я готова помочь сделать это максимально приятным и эффективным."
            ).random()
        }
    }

    private fun generateContextualSuggestions(lastUserMessage: String): List<String> {
        val lower = lastUserMessage.lowercase()

        return when {
            lower.contains("таймер") -> listOf("5 минут", "10 минут", "25 минут", "Отменить")
            lower.contains("погода") -> listOf("Сейчас", "На сегодня", "На неделю")
            lower.contains("музык") || lower.contains("включи") -> listOf("Спокойная", "Энергичная", "Фоновая", "Джаз")
            lower.contains("расскажи") -> listOf("Что-то лёгкое", "Глубокое", "Смешное", "Факт дня")
            else -> listOf(
                "Расскажи ещё",
                "Что дальше?",
                "Помоги с этим",
                "Другой вариант"
            )
        }
    }

    override fun onCleared() {
        sessionJob?.cancel()
        super.onCleared()
    }

    companion object {
        private val defaultSuggestions = listOf(
            "Привет, Амалия",
            "Как у тебя дела?",
            "Расскажи что-нибудь интересное",
            "Поставь таймер на 10 минут",
            "Что сейчас происходит?"
        )

        private val realisticUserPhrases = listOf(
            "Амалия, как у тебя дела сегодня?",
            "Поставь, пожалуйста, таймер на десять минут",
            "Расскажи мне что-нибудь интересное",
            "Как сейчас погода в Москве?",
            "Включи что-нибудь спокойное, пожалуйста",
            "Я немного устал, можешь помочь расслабиться?",
            "Привет! Ты тут?",
            "Что ты можешь мне посоветовать прямо сейчас?"
        )
    }
}
