package com.my.amali.ui.onboarding

import com.my.amali.R

/**
 * Direction the guide arrow points relative to the highlighted target.
 * [NONE] is used for steps that highlight themselves without an arrow.
 */
enum class ArrowDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    NONE,
}

/**
 * Describes one step of the step-by-step guide overlay.
 *
 * @property targetKey     stable key of the UI element to highlight
 *                         (matched by the guide overlay renderer).
 * @property titleResId    string resource for the step title.
 * @property descriptionResId string resource for the step description.
 * @property arrowDirection direction of the arrow drawn from the tooltip
 *                         towards the target element.
 */
data class GuideStep(
    val targetKey: String,
    val titleResId: Int,
    val descriptionResId: Int,
    val arrowDirection: ArrowDirection,
) {
    companion object {

        /**
         * Ordered steps of the onboarding guide, covering the main
         * interactive elements of the assistant screen.
         */
        val guideSteps: List<GuideStep> = listOf(
            GuideStep(
                targetKey = "mic",
                titleResId = R.string.guide_mic_title,
                descriptionResId = R.string.guide_mic_desc,
                arrowDirection = ArrowDirection.UP,
            ),
            GuideStep(
                targetKey = "chips",
                titleResId = R.string.guide_chips_title,
                descriptionResId = R.string.guide_chips_desc,
                arrowDirection = ArrowDirection.DOWN,
            ),
            GuideStep(
                targetKey = "settings",
                titleResId = R.string.guide_settings_title,
                descriptionResId = R.string.guide_settings_desc,
                arrowDirection = ArrowDirection.RIGHT,
            ),
            GuideStep(
                targetKey = "history",
                titleResId = R.string.guide_history_title,
                descriptionResId = R.string.guide_history_desc,
                arrowDirection = ArrowDirection.LEFT,
            ),
        )
    }
}
