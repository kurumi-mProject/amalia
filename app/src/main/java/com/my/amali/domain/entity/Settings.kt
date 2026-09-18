package com.my.amali.domain.entity

import com.my.amali.ui.theme.AmaliaMotif
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
 * @property resumeLastSession continue the most recent conversation on launch
 *   instead of a blank one, so the compressed context survives a restart.
 * @property motif decorative layer over the living background: falling petals,
 *   leaves, snow, stars or fireflies. [AmaliaMotif.OFF] keeps the screen bare.
 */
/**
 * API-ключи и выбранные модели трёх провайдеров конвейера.
 *
 * ## Почему ключи живут в настройках, а не только в сборке
 *
 * Зашитый в APK ключ — это общий ресурс: он принадлежит сборке, а не человеку,
 * который этой сборкой пользуется. Отсюда три проблемы, которые решаются
 * ровно одним способом — дать ввести свой ключ:
 *
 *  1. **Квота.** Ключ из сборки может быть исчерпан («недостаточно средств»),
 *     и тогда приложение замолкает у всех сразу. Свой ключ возвращает голос
 *     немедленно и не требует пересборки.
 *  2. **Модели.** Линейка моделей у провайдера меняется чаще, чем выходит
 *     новая версия приложения. Имя модели — такая же настройка, как язык.
 *  3. **Доверие.** Пользователь вправе видеть, куда уходят его данные и чьим
 *     ключом оплачивается обращение.
 *
 * Пустое значение поля = «взять из сборки». Это не то же самое, что «выключить
 * провайдера»: конвейер обязан оставаться рабочим на дефолтной конфигурации,
 * иначе первый запуск приложения без ключей выглядел бы сломанным.
 *
 * @property groqKey ключ Groq (генерация текста и вызов инструментов).
 * @property deepgramKey ключ Deepgram (распознавание речи).
 * @property fishAudioKey ключ Fish Audio (синтез голоса).
 * @property llmModel идентификатор модели Groq; пусто → рекомендованная.
 * @property sttModel идентификатор модели Deepgram; пусто → рекомендованная.
 * @property ttsModel идентификатор модели Fish Audio; пусто → рекомендованная.
 * @property fishVoiceId reference_id голоса Амалии в библиотеке Fish Audio.
 *   Вынесен сюда потому, что голос — это тоже выбор пользователя: у Fish
 *   Audio можно клонировать свой голос и подставить его идентификатор.
 */
data class UserApiSettings(
    val groqKey: String = "",
    val deepgramKey: String = "",
    val fishAudioKey: String = "",
    val llmModel: String = "",
    val sttModel: String = "",
    val ttsModel: String = "",
    val fishVoiceId: String = "",
) {

    /** Задан ли хотя бы один собственный ключ. */
    val hasAnyKey: Boolean
        get() = groqKey.isNotBlank() || deepgramKey.isNotBlank() || fishAudioKey.isNotBlank()

    /** Сколько провайдеров настроено своими ключами (для строки-сводки). */
    val configuredProviders: Int
        get() = listOf(groqKey, deepgramKey, fishAudioKey).count { it.isNotBlank() }
}

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
    val selectedLanguage: AppLanguage = AppLanguage.SYSTEM,
    val resumeLastSession: Boolean = true,
    val motif: AmaliaMotif = AmaliaMotif.AUTO,
    val motifDensity: Float = 0.85f,
    /**
     * Ключи и модели провайдеров, которые пользователь задал сам.
     *
     * Собственные ключи **перебивают** зашитые в сборку: если человек вписал
     * свой Groq-ключ, запросы идут с ним и за его счёт — иначе он платил бы
     * за чужую квоту, не понимая, почему у него «недостаточно средств».
     */
    val api: UserApiSettings = UserApiSettings(),
) {
    /** Returns a copy with [motifDensity] clamped to the valid [0.0, 1.0] range. */
    fun withClampedMotif(): UserSettings = copy(motifDensity = motifDensity.coerceIn(0f, 1f))

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
