package com.my.amali.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.my.amali.data.ai.ModelCatalog
import com.my.amali.domain.entity.AiProfile
import com.my.amali.domain.entity.AppLanguage
import com.my.amali.domain.entity.UserApiSettings
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

        // ── API-ключи и модели провайдеров ───────────────────────────────
        //
        // Каждый ключ — отдельная запись, а не один JSON: так стирание одного
        // ключа не переписывает остальные, а случайная порча структуры не
        // уносит с собой все три настройки сразу.
        val API_GROQ_KEY = stringPreferencesKey("pref_api_groq_key")
        val API_FISH_KEY = stringPreferencesKey("pref_api_fish_key")
        val API_LLM_MODEL = stringPreferencesKey("pref_api_llm_model")
        val API_STT_MODEL = stringPreferencesKey("pref_api_stt_model")
        val API_TTS_MODEL = stringPreferencesKey("pref_api_tts_model")
        val API_FISH_VOICE = stringPreferencesKey("pref_api_fish_voice")

        /**
         * Длина паузы, означающей конец фразы, секунды.
         *
         * Хранится числом с плавающей точкой: значения вида 0.6 с шагом 0.1
         * целым не выражаются, а округление до секунды дало бы заметную
         * разницу между «обрывает на вдохе» и «ждёшь после каждой фразы».
         */
        val API_STT_SILENCE = floatPreferencesKey("pref_api_stt_silence_seconds")

        /**
         * Профиль «мозга»: имя [com.my.amali.domain.entity.AiProfile].
         *
         * Хранится строкой, а не булевым: профилей уже два, а имя переживёт
         * добавление третьего без смены ключа хранилища.
         */
        val AI_PROFILE = stringPreferencesKey("pref_ai_profile")

        /** Адрес своего OpenAI-совместимого эндпоинта (профиль «Своя модель»). */
        val API_CUSTOM_ENDPOINT = stringPreferencesKey("pref_api_custom_endpoint")

        /** Идентификатор модели на своём эндпоинте. */
        val API_CUSTOM_MODEL = stringPreferencesKey("pref_api_custom_model")

        /** Ключ для своего эндпоинта; может быть пустым (локальный сервер). */
        val API_CUSTOM_KEY = stringPreferencesKey("pref_api_custom_key")
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
            api = UserApiSettings(
                groqKey = this[Keys.API_GROQ_KEY].orEmpty(),
                fishAudioKey = this[Keys.API_FISH_KEY].orEmpty(),
                llmModel = this[Keys.API_LLM_MODEL].orEmpty(),
                sttModel = this[Keys.API_STT_MODEL].orEmpty(),
                ttsModel = this[Keys.API_TTS_MODEL].orEmpty(),
                fishVoiceId = this[Keys.API_FISH_VOICE].orEmpty(),
                sttSilenceSeconds = (
                    this[Keys.API_STT_SILENCE] ?: UserApiSettings.STT_SILENCE_DEFAULT_SECONDS
                    ).coerceIn(
                    UserApiSettings.STT_SILENCE_MIN_SECONDS,
                    UserApiSettings.STT_SILENCE_MAX_SECONDS,
                ),
                customEndpoint = this[Keys.API_CUSTOM_ENDPOINT].orEmpty(),
                customModel = this[Keys.API_CUSTOM_MODEL].orEmpty(),
                customKey = this[Keys.API_CUSTOM_KEY].orEmpty(),
            ),
            aiProfile = AiProfile.fromName(this[Keys.AI_PROFILE]),
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

    /**
     * Переключает профиль «мозга»: Groq с урезанным промптом или свой
     * эндпоинт с полным.
     *
     * Влияет только на текстовую модель. Распознавание и синтез в обоих
     * профилях одинаковые: голос остаётся прежним, меняется только то,
     * кто думает над ответом.
     */
    suspend fun setAiProfile(profile: AiProfile) {
        dataStore.edit { it[Keys.AI_PROFILE] = profile.name }
    }

    /**
     * Сохраняет настройки своего эндпоинта.
     *
     * Все три поля пишутся разом, а не отдельными методами: адрес без
     * модели или модель без адреса — нерабочая конфигурация, и разносить
     * их по трём вызовам значило бы допускать состояние, в котором профиль
     * включается, но не работает.
     */
    suspend fun setCustomProvider(endpoint: String, model: String, key: String) {
        dataStore.edit { prefs ->
            prefs[Keys.API_CUSTOM_ENDPOINT] = endpoint.trim()
            prefs[Keys.API_CUSTOM_MODEL] = model.trim()
            prefs[Keys.API_CUSTOM_KEY] = key.trim()
        }
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

    // ── API-ключи и модели ───────────────────────────────────────────────

    /**
     * Сохраняет ключ провайдера.
     *
     * Значение обрезается по краям: ключи копируют из консоли вместе с
     * пробелом или переводом строки, а провайдер такой ключ уже не примет —
     * и пользователь получит «ключ отклонён», хотя ключ верный.
     */
    suspend fun setProviderKey(provider: ModelCatalog.Provider, key: String) {
        val clean = key.trim()
        dataStore.edit { prefs ->
            when (provider) {
                ModelCatalog.Provider.GROQ -> prefs[Keys.API_GROQ_KEY] = clean
                // Слух живёт на том же ключе, что и мозг: один аккаунт Groq
                // закрывает и распознавание, и генерацию, поэтому отдельного
                // поля для STT-ключа не существует.
                ModelCatalog.Provider.GROQ_STT -> prefs[Keys.API_GROQ_KEY] = clean
                ModelCatalog.Provider.FISH_AUDIO -> prefs[Keys.API_FISH_KEY] = clean
            }
        }
    }

    /** Убирает собственный ключ: провайдер возвращается к ключу из сборки. */
    suspend fun clearProviderKey(provider: ModelCatalog.Provider) {
        dataStore.edit { prefs ->
            when (provider) {
                ModelCatalog.Provider.GROQ -> prefs.remove(Keys.API_GROQ_KEY)
                ModelCatalog.Provider.GROQ_STT -> prefs.remove(Keys.API_GROQ_KEY)
                ModelCatalog.Provider.FISH_AUDIO -> prefs.remove(Keys.API_FISH_KEY)
            }
        }
    }

    /** Сохраняет выбранную модель провайдера (пусто → рекомендованная). */
    suspend fun setProviderModel(provider: ModelCatalog.Provider, model: String) {
        val clean = model.trim()
        dataStore.edit { prefs ->
            when (provider) {
                ModelCatalog.Provider.GROQ -> prefs[Keys.API_LLM_MODEL] = clean
                ModelCatalog.Provider.GROQ_STT -> prefs[Keys.API_STT_MODEL] = clean
                ModelCatalog.Provider.FISH_AUDIO -> prefs[Keys.API_TTS_MODEL] = clean
            }
        }
    }

    /**
     * Длина паузы, означающей конец фразы.
     *
     * Значение обрезается по границам из [UserApiSettings], а не принимается
     * как есть: слайдер — не единственный вызывающий, а запись «0.05
     * секунды» из любого другого места означала бы, что ассистент
     * перестаёт слушать на каждом межсловном промежутке.
     */
    suspend fun setSttSilenceSeconds(seconds: Float) {
        dataStore.edit {
            it[Keys.API_STT_SILENCE] = seconds.coerceIn(
                UserApiSettings.STT_SILENCE_MIN_SECONDS,
                UserApiSettings.STT_SILENCE_MAX_SECONDS,
            )
        }
    }

    /** Голос Амалии в Fish Audio; пусто → голос из сборки. */
    suspend fun setFishVoiceId(voiceId: String) {
        dataStore.edit { prefs -> prefs[Keys.API_FISH_VOICE] = voiceId.trim() }
    }
}