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

data class AssistantUiState(
    val voiceState: VoiceState = VoiceState.Idle,
    val audioLevel: Float = 0f,
    val userTranscript: String = "",
    val amaliaReply: String = "",
    val replyProgress: Float = 0f,
    val isSpeaking: Boolean = false,
    val isFirstLaunch: Boolean = true,
    val errorMessage: String? = null,
    val conversationCount: Int = 0,
    val suggestions: List<String> = emptyList(),
)

class AssistantViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var conversationJob: Job? = null

    private val defaultSuggestions = listOf("Привет", "Как дела?", "Расскажи о себе")
    private val activeSuggestions = listOf("Спасибо", "Ещё", "Повтори")

    fun onFirstLaunchHandled() {
        _uiState.update { it.copy(isFirstLaunch = false) }
    }

    fun toggleConversation() {
        val current = _uiState.value
        if (current.voiceState == VoiceState.Listening ||
            current.voiceState == VoiceState.Thinking ||
            current.voiceState == VoiceState.Speaking
        ) {
            cancelConversation()
        } else {
            startConversation()
        }
    }

    fun startConversation(prompt: String? = null) {
        conversationJob?.cancel()
        _uiState.update {
            it.copy(
                voiceState = VoiceState.Listening,
                userTranscript = "",
                amaliaReply = "",
                replyProgress = 0f,
                isSpeaking = false,
                errorMessage = null,
                suggestions = activeSuggestions,
            )
        }

        conversationJob = viewModelScope.launch {
            // === СЛУШАЕМ ===
            val listeningDuration = Random.nextLong(1400, 2400)
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < listeningDuration) {
                val progress = (System.currentTimeMillis() - startTime) / listeningDuration.toFloat()
                val wave = (sin(progress * 6.2f) * 0.5f + 0.5f) * 0.65f +
                        (sin(progress * 14f + 1.3f) * 0.5f + 0.5f) * 0.35f
                val level = (0.25f + wave * 0.75f).coerceIn(0f, 1f)
                _uiState.update { it.copy(audioLevel = level) }
                delay(38)
            }
            _uiState.update { it.copy(audioLevel = 0f) }

            // === ДУМАЕМ ===
            _uiState.update {
                it.copy(
                    voiceState = VoiceState.Thinking,
                    audioLevel = 0f,
                    userTranscript = prompt ?: generateUserPrompt(),
                )
            }
            delay(Random.nextLong(800, 1400))

            // === ГОВОРИМ ===
            val reply = generateReply(_uiState.value.userTranscript)
            _uiState.update {
                it.copy(
                    voiceState = VoiceState.Speaking,
                    amaliaReply = reply,
                    isSpeaking = true,
                    replyProgress = 0f,
                )
            }

            // Стриминг ответа по словам
            val words = reply.split(" ")
            val totalDelay = 2200L
            val perWord = (totalDelay / words.size).coerceAtLeast(45L)
            for (i in words.indices) {
                delay(perWord)
                _uiState.update {
                    it.copy(
                        replyProgress = (i + 1f) / words.size,
                        audioLevel = (0.3f + sin(i * 0.9f) * 0.25f).coerceIn(0.1f, 0.7f)
                    )
                }
            }

            _uiState.update {
                it.copy(
                    voiceState = VoiceState.Idle,
                    audioLevel = 0f,
                    isSpeaking = false,
                    replyProgress = 1f,
                    conversationCount = it.conversationCount + 1,
                    suggestions = defaultSuggestions,
                )
            }
        }
    }

    fun cancelConversation() {
        conversationJob?.cancel()
        _uiState.update {
            it.copy(
                voiceState = VoiceState.Idle,
                audioLevel = 0f,
                isSpeaking = false,
                errorMessage = null,
                suggestions = defaultSuggestions,
            )
        }
    }

    private fun generateUserPrompt(): String {
        val prompts = listOf(
            "Привет, Амалия",
            "Как у тебя дела?",
            "Расскажи что-нибудь",
            "Чем занимаешься?",
            "Помоги мне",
        )
        return prompts.random()
    }

    private fun generateReply(userText: String): String {
        val lower = userText.lowercase()
        return when {
            "привет" in lower || "хай" in lower || "здрав" in lower ->
                "Привет. Я Амалия — твой голосовой ассистент. Чем могу помочь?"
            "как дела" in lower || "как ты" in lower || "чё как" in lower ->
                "У меня всё стабильно. Готова к работе. Что нужно?"
            "кто ты" in lower || "о себе" in lower || "расскажи" in lower ->
                "Я Амалия. Голосовой ассистент на Kotlin и Compose. Слушаю, думаю, отвечаю."
            "пока" in lower || "до свид" in lower || "бай" in lower ->
                "До встречи. Я на связи."
            "спасибо" in lower || "благодар" in lower ->
                "Обращайся в любой момент."
            "помоги" in lower || "что умеешь" in lower || "что ты можешь" in lower ->
                "Могу разговаривать, отвечать на вопросы и помогать с задачами. Пока работаю в демо-режиме, но скоро подключу реальные модели."
            else -> "Я тебя услышала. Пока работаю в демо-режиме, но скоро буду отвечать полнее."
        }
    }
}
