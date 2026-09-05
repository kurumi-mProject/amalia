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

/**
 * Состояние главного экрана.
 *
 * @param orbState    текущая фаза жизненного цикла Амалии
 * @param transcript  живой транскрипт того, что «слышит» Амалия
 * @param reply       последняя реплика Амалии
 * @param active      идёт ли текущий такт диалога
 */
data class AssistantUiState(
    val orbState: OrbState = OrbState.Idle,
    val transcript: String = "",
    val reply: String = "",
    val active: Boolean = false,
)

/**
 * Оркестратор состояний Амалии — конечный автомат
 * IDLE → LISTENING → THINKING → SPEAKING → IDLE.
 *
 * Сейчас встроен демо-режим (эхо): показывает весь цикл без внешних API,
 * чтобы можно было проверить UX/анимации. На Этапе 2 эти внутренности
 * заменяются на стриминговые шлюзы STT → LLM → TTS.
 */
class AssistantViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var turnJob: Job? = null

    /** Главная кнопка: тап по микрофону начинает/прерывает такт. */
    fun onMicTap() {
        if (_uiState.value.active) {
            stopTurn()
        } else {
            startDemoTurn()
        }
    }

    private fun startDemoTurn() {
        turnJob?.cancel()
        turnJob = viewModelScope.launch {
            // 1. СЛУШАЮ — транскрипт «набирается» как при стриминге STT
            setPhase(OrbState.Listening, active = true, transcript = "")
            val query = demoQueries.random()
            revealText(query, stepMs = 55) { partial ->
                _uiState.update { it.copy(transcript = partial) }
            }
            _uiState.update { it.copy(transcript = query) }
            delay(300)

            // 2. ДУМАЮ — пауза «размышления»
            _uiState.update { it.copy(orbState = OrbState.Thinking) }
            delay(1100)

            // 3. ГОВОРЮ — ответ набирается как токены LLM
            val reply = demoReplies.random()
            _uiState.update {
                it.copy(orbState = OrbState.Speaking, reply = "")
            }
            revealText(reply, stepMs = 26) { partial ->
                _uiState.update { it.copy(reply = partial) }
            }
            _uiState.update { it.copy(reply = reply) }
            delay(1600)

            // 4. ПОКОЙ
            finishTurn()
        }
    }

    /** Раскрытие текста «по символам» — имитация живого стриминга. */
    private suspend fun revealText(full: String, stepMs: Long, onTick: (String) -> Unit) {
        full.forEachIndexed { index, _ ->
            onTick(full.substring(0, index + 1))
            delay(stepMs)
        }
    }

    private fun stopTurn() {
        turnJob?.cancel()
        finishTurn()
    }

    private fun setPhase(state: OrbState, active: Boolean, transcript: String) {
        _uiState.update { it.copy(orbState = state, active = active, transcript = transcript) }
    }

    private fun finishTurn() {
        _uiState.update {
            it.copy(orbState = OrbState.Idle, active = false, transcript = "")
        }
    }

    override fun onCleared() {
        turnJob?.cancel()
        super.onCleared()
    }

    companion object {
        /** Демо-фразы пользователя (на Этапе 2 заменяются реальным STT). */
        private val demoQueries = listOf(
            "Амалия, открой телеграм",
            "Поставь таймер на десять минут",
            "Расскажи что-нибудь интересное",
            "Как у тебя дела?",
        )

        /** Демо-ответы Амалии — живые, с характером. */
        private val demoReplies = listOf(
            "Открываю. Телеграм уже тут — кстати, там тебе кто-то написал. Скажешь — прочитаю вслух 😉",
            "Таймер на десять минут — поставлен. И да, я заметила, как ты на него косишься. Работай давай!",
            "А ты знал, что у улиток около четырнадцати тысяч зубов? Теперь это будет жить в твоей голове. Как и я 😄",
            "У меня всё отлично — я же в телефоне человека, который строит голосового ассистента. Это заряжает!",
        )
    }
}
