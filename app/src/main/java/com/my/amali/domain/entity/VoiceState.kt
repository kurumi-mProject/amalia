package com.my.amali.domain.entity

import androidx.compose.ui.graphics.Color

/**
 * Represents the current state of the voice assistant's interaction cycle.
 *
 * The state machine flows: Idle -> Listening -> Thinking -> Speaking -> Idle.
 * The Error state can occur from any state and returns to Idle after being shown.
 *
 * @property label подпись для логов и отладки. UI её не использует: строки
 *   интерфейса живут в ресурсах (`R.string.assistant_listening` и т. д.),
 *   иначе в девяти локалях приложения состояние читалось бы по-русски.
 */
enum class VoiceState(
    @Deprecated("UI берёт подпись из строковых ресурсов, а не из enum")
    val label: String,
    val color: Color,
) {
    /** Assistant is waiting for user interaction. */
    Idle("Готова", Color(0xFF8E9AAF)),

    /** Assistant is actively listening to the user's voice. */
    Listening("Слушаю", Color(0xFF4CAF50)),

    /** The user's request is being processed by the language model. */
    Thinking("Думаю", Color(0xFFFFA726)),

    /** The assistant is speaking the response out loud. */
    Speaking("Говорю", Color(0xFF2196F3)),

    /** An error occurred during the interaction. */
    Error("Ошибка", Color(0xFFEF5350));

    /** Whether the visual orb should be animating in this state. */
    val isAnimating: Boolean
        get() = this != Idle
}

/**
 * Visual properties describing how the assistant's orb should be rendered
 * at any given moment.
 *
 * @property audioLevel normalized audio input level in the range [0.0, 1.0].
 * @property isAnimating whether the orb should currently animate (pulse, rotate, shimmer).
 */
data class VoiceStateVisual(
    val audioLevel: Float = 0f,
    val isAnimating: Boolean = false
) {
    /** Clamped copy of the visual state guaranteeing valid audio level bounds. */
    fun sanitized(): VoiceStateVisual = copy(
        audioLevel = audioLevel.coerceIn(0f, 1f)
    )

    companion object {
        /** Default visual state used when the assistant is idle. */
        val Default: VoiceStateVisual = VoiceStateVisual()

        /** Creates a visual state for the given [state] and current [audioLevel]. */
        fun from(state: VoiceState, audioLevel: Float = 0f): VoiceStateVisual = VoiceStateVisual(
            audioLevel = audioLevel.coerceIn(0f, 1f),
            isAnimating = state.isAnimating
        )
    }
}
