package com.my.amali.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.my.amali.domain.entity.AppLanguage
import com.my.amali.domain.entity.UserSettings
import com.my.amali.ui.theme.AmaliaMotif
import com.my.amali.ui.theme.AmaliaVisualTheme
import com.my.amali.ui.theme.DarkModePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Репозиторий пользовательских настроек поверх DataStore Preferences.
 *
 * Хранит каждое поле [UserSettings] отдельным ключом, поэтому обновление
 * одного параметра не переписывает остальные и переживает изменения схемы.
 * Все методы потокобезопасны; UI подписывается на [settings].
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    // ── Ключи ────────────────────────────────────────────────────────────

    private object Keys {
        val VISUAL_THEME = stringPreferencesKey("pref_visual_theme")
        val DARK_MODE = stringPreferencesKey("pref_dark_mode")
        val USE_BIO_TIME = booleanPreferencesKey("pref_use_bio_time")
        val GLASS_INTENSITY = floatPreferencesKey("pref_glass_intensity")
        val SPEECH_RATE = floatPreferencesKey("pref_speech_rate")
        val SPEECH_PITCH = floatPreferencesKey("pref_speech_pitch")
        val AUTO_LISTEN = booleanPreferencesKey("pref_auto_listen")
        val WAKE_WORD = booleanPreferencesKey("pref_wake_word")
        val DATA_RETENTION = intPreferencesKey("pref_data_retention_days")
        val LANGUAGE = stringPreferencesKey("pref_app_language")
        val RESUME_SESSION = booleanPreferencesKey("pref_resume_session")
        val MOTIF = stringPreferencesKey("pref_motif")
        val MOTIF_DENSITY = floatPreferencesKey("pref_motif_density")
    }

    // ── Чтение ───────────────────────────────────────────────────────────

    /** Поток всех настроек; эмитится при каждом изменении любого поля. */
    val settings: Flow<UserSettings> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs.toUserSettings() }

    private fun emptyPreferences(): Preferences =
        androidx.datastore.preferences.core.emptyPreferences()

    private fun Preferences.toUserSettings(): UserSettings {
        val theme = this[Keys.VISUAL_THEME]
            ?.let { stored ->
                AmaliaVisualTheme.entries.firstOrNull { it.name == stored }
            } ?: UserSettings.DEFAULT.visualTheme
        val dark = this[Keys.DARK_MODE]
            ?.let { stored -> DarkModePreference.entries.firstOrNull { it.name == stored } }
            ?: UserSettings.DEFAULT.darkModePref
        val language = this[Keys.LANGUAGE]
            ?.let { stored -> AppLanguage.fromCode(stored) }
            ?: UserSettings.DEFAULT.selectedLanguage
        val motif = AmaliaMotif.fromName(this[Keys.MOTIF])
        return UserSettings(
            visualTheme = theme,
            darkModePref = dark,
            useBioTime = this[Keys.USE_BIO_TIME] ?: UserSettings.DEFAULT.useBioTime,
            glassIntensity = (this[Keys.GLASS_INTENSITY] ?: UserSettings.DEFAULT.glassIntensity)
                .coerceIn(0f, 1f),
            speechRate = (this[Keys.SPEECH_RATE] ?: UserSettings.DEFAULT.speechRate)
                .coerceIn(0.5f, 2f),
            speechPitch = (this[Keys.SPEECH_PITCH] ?: UserSettings.DEFAULT.speechPitch)
                .coerceIn(0.5f, 2f),
            autoListen = this[Keys.AUTO_LISTEN] ?: UserSettings.DEFAULT.autoListen,
            wakeWordEnabled = this[Keys.WAKE_WORD] ?: UserSettings.DEFAULT.wakeWordEnabled,
            dataRetentionDays = UserSettings.normalizeRetention(
                this[Keys.DATA_RETENTION] ?: UserSettings.DEFAULT.dataRetentionDays
            ),
            selectedLanguage = language,
            resumeLastSession = this[Keys.RESUME_SESSION] ?: UserSettings.DEFAULT.resumeLastSession,
            motif = motif,
            motifDensity = (this[Keys.MOTIF_DENSITY] ?: UserSettings.DEFAULT.motifDensity)
                .coerceIn(0f, 1f),
        )
    }

    // ── Запись ───────────────────────────────────────────────────────────

    /** Сохраняет визуальную тему (Liquid Glass / Биофильная). */
    suspend fun setVisualTheme(theme: AmaliaVisualTheme) {
        dataStore.edit { it[Keys.VISUAL_THEME] = theme.name }
    }

    /** Сохраняет режим тёмности (системный / всегда тёмная / всегда светлая). */
    suspend fun setDarkModePreference(preference: DarkModePreference) {
        dataStore.edit { it[Keys.DARK_MODE] = preference.name }
    }

    /** Включает/выключает адаптацию палитры по времени суток. */
    suspend fun setUseBioTime(enabled: Boolean) {
        dataStore.edit { it[Keys.USE_BIO_TIME] = enabled }
    }

    /** Интенсивность glass-эффекта, [0.0, 1.0]. */
    suspend fun setGlassIntensity(value: Float) {
        dataStore.edit { it[Keys.GLASS_INTENSITY] = value.coerceIn(0f, 1f) }
    }

    /** Скорость речи TTS, [0.5, 2.0]. */
    suspend fun setSpeechRate(value: Float) {
        dataStore.edit { it[Keys.SPEECH_RATE] = value.coerceIn(0.5f, 2f) }
    }

    /** Высота голоса TTS, [0.5, 2.0]. */
    suspend fun setSpeechPitch(value: Float) {
        dataStore.edit { it[Keys.SPEECH_PITCH] = value.coerceIn(0.5f, 2f) }
    }

    /** Автостарт прослушивания при открытии приложения. */
    suspend fun setAutoListen(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_LISTEN] = enabled }
    }

    /** Фоновое детектирование wake-word (заготовка под будущий движок). */
    suspend fun setWakeWordEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.WAKE_WORD] = enabled }
    }

    /** Язык интерфейса и речи; [AppLanguage.SYSTEM] — следовать системе. */
    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    /** Срок хранения истории в днях; [UserSettings.RETENTION_FOREVER] — бессрочно. */
    suspend fun setDataRetentionDays(days: Int) {
        dataStore.edit { it[Keys.DATA_RETENTION] = UserSettings.normalizeRetention(days) }
    }

    /** Продолжать ли последний диалог при запуске, а не начинать новый. */
    suspend fun setResumeLastSession(enabled: Boolean) {
        dataStore.edit { it[Keys.RESUME_SESSION] = enabled }
    }

    /** Декоративный слой фона: сакура/листья/снег/звёзды/светлячки или ничего. */
    suspend fun setMotif(motif: AmaliaMotif) {
        dataStore.edit { it[Keys.MOTIF] = motif.name }
    }

    /** Густота декораций, [0.0, 1.0]. 0 фактически выключает слой. */
    suspend fun setMotifDensity(value: Float) {
        dataStore.edit { it[Keys.MOTIF_DENSITY] = value.coerceIn(0f, 1f) }
    }

    /** Сбрасывает все настройки к значениям по умолчанию. */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
