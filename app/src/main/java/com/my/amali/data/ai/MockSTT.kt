package com.my.amali.data.ai

import com.my.amali.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Заглушка STT-движка с реалистичным поведением: «слушает», эмитит
 * промежуточные транскрипты и завершается финальной фразой.
 *
 * ТОЧКА ПОДКЛЮЧЕНИЯ РЕАЛЬНОГО STT: реализуйте [SpeechToTextEngine]
 * (например, Vosk / Whisper / SpeechRecognizer) и подставьте в
 * [com.my.amali.core.di.ServiceLocator.aiOrchestrator].
 */
class MockSpeechToTextEngine : SpeechToTextEngine {

    private var initialized = false

    override suspend fun initialize() {
        initialized = true
    }

    override suspend fun close() {
        initialized = false
    }

    override fun transcribe(audioLevel: Float): Flow<String> = kotlinx.coroutines.flow.flow {
        if (!initialized) initialize()
        // Имитируем распознавание: пауза «слушания», затем промежуточные результаты.
        kotlinx.coroutines.delay(LISTEN_PAUSE_MS)
        emit(INTERIM_1)
        kotlinx.coroutines.delay(INTERIM_STEP_MS)
        emit(INTERIM_2)
        kotlinx.coroutines.delay(INTERIM_STEP_MS)
        emit(FINAL_TRANSCRIPT)
    }

    private companion object {
        const val LISTEN_PAUSE_MS = 600L
        const val INTERIM_STEP_MS = 350L
        const val INTERIM_1 = "Слушаю…"
        const val INTERIM_2 = "Распознаю речь…"
        const val FINAL_TRANSCRIPT = "Привет, Амалия"
    }
}
