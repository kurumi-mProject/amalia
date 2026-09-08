package com.my.amali.domain.entity

import com.my.amali.ui.theme.AmaliaVisualTheme
import com.my.amali.ui.theme.DarkModePreference

/**
 * Holds all user-adjustable settings for the Amalia voice assistant.
 *
 * The instance is persisted via DataStore (as JSON) and injected manually
 * through the app's DI graph; UI and feature layers treat it as immutable
 * and emit a new copy whenever a setting changes.
 *
 * @property visualTheme overall visual style of the assistant's orb and surfaces.
 * @property darkModePref how the app picks between light and dark appearance.
 * @property useBioTime enable adaptive theming based on the time of day.
 * @property glassIntensity glassmorphism blur strength, expected in [0.0, 1.0].
 * @property speechRate TTS speaking rate multiplier, expected in [0.5, 2.0].
 * @property speechPitch TTS pitch multiplier, expected in [0.5, 2.0].
 * @property autoListen automatically start listening when the app comes to the foreground.
 * @property wakeWordEnabled keep the wake-word detector active in the background.
 * @property dataRetentionDays conversation retention window; one of 7, 30, 90,
 *   or -1 meaning "keep forever".
 * @property selectedLanguage language used for STT/TTS and UI localization.
 */
data class UserSettings(
    val visualTheme: AmaliaVisualTheme = AmaliaVisualTheme.LIQUID_GLASS,
    val darkModePref: DarkModePreference = DarkModePreference.SYSTEM,
    val useBioTime: Boolean = true,
    val glassIntensity: Float = 0.6f,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val autoListen: Boolean = false,
    val wakeWordEnabled: Boolean = false,
    val dataRetentionDays: Int = 30,
    val selectedLanguage: AppLanguage = AppLanguage.SYSTEM
) {
    /** Returns a copy with [glassIntensity] clamped to the valid [0.0, 1.0] range. */
    fun withClampedGlass(): UserSettings = copy(glassIntensity = glassIntensity.coerceIn(0f, 1f))

    /** Returns a copy with speech [rate] clamped to the valid [0.5, 2.0] range. */
    fun withRate(rate: Float): UserSettings = copy(speechRate = rate.coerceIn(0.5f, 2.0f))

    /** Returns a copy with speech [pitch] clamped to the valid [0.5, 2.0] range. */
    fun withPitch(pitch: Float): UserSettings = copy(speechPitch = pitch.coerceIn(0.5f, 2.0f))

    companion object {
        /** Forever retention sentinel used by [dataRetentionDays]. */
        const val RETENTION_FOREVER: Int = -1

        /** Valid retention windows in days. */
        val RETENTION_OPTIONS: List<Int> = listOf(7, 30, 90, RETENTION_FOREVER)

        /** Default settings instance used before persistence is loaded. */
        val DEFAULT: UserSettings = UserSettings()

        /** Normalizes [days] to one of [RETENTION_OPTIONS], defaulting to 30. */
        fun normalizeRetention(days: Int): Int =
            if (days in RETENTION_OPTIONS) days else 30
    }
}
