package com.my.amali.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.amali.core.di.ServiceLocator
import com.my.amali.data.ai.ModelCatalog
import com.my.amali.data.repository.ConversationRepository
import com.my.amali.data.repository.SettingsRepository
import com.my.amali.domain.entity.AiProfile
import com.my.amali.domain.entity.AppLanguage
import com.my.amali.domain.entity.UserSettings
import com.my.amali.ui.theme.AmaliaMotif
import com.my.amali.ui.theme.AmaliaVisualTheme
import com.my.amali.ui.theme.DarkModePreference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Общая ViewModel всех экранов настроек.
 *
 * Подписана на [SettingsRepository.settings] и пробрасывает обновления
 * в DataStore. Экраны внешнего вида, языка, голоса, приватности и
 * уведомлений работают с одним и тем же состоянием, поэтому изменение
 * в одной подсекции мгновенно видно в остальных.
 */
class SettingsViewModel : ViewModel() {

    private val settingsRepository: SettingsRepository = ServiceLocator.settingsRepository
    private val conversationRepository: ConversationRepository = ServiceLocator.conversationRepository

    /**
     * Мост к активити для смены языка.
     *
     * ## Почему не через AppCompatDelegate напрямую
     *
     * `AppCompatDelegate.setApplicationLocales()` пересоздаёт активити.
     * Вызванный из ViewModel (или из композиции) он попадает в гонку:
     * новая активити стартует, ещё не прочитав настройки, и снова просит
     * смену языка — так и получался бесконечный цикл перезапусков.
     *
     * Поэтому смена языка идёт через этот интерфейс: активити подписывается
     * на него сама, а метод вызывается ровно один раз — по нажатию
     * пользователя. Ничего самоподдерживающегося тут нет.
     */
    interface LanguageApplier {
        /** Пользователь выбрал язык: применить его и обновить интерфейс. */
        fun onLanguageChanged(language: AppLanguage)
    }

    /** Провайдер слушателя: его ставит активити при старте. */
    companion object {
        @Volatile
        var languageApplier: LanguageApplier? = null
    }

    /** Текущие настройки; стартовое значение — системные умолчания. */
    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserSettings.DEFAULT,
        )

    // ── Внешний вид ──────────────────────────────────────────────────────

    fun setVisualTheme(theme: AmaliaVisualTheme) = viewModelScope.launch {
        settingsRepository.setVisualTheme(theme)
    }

    fun setDarkModePreference(preference: DarkModePreference) = viewModelScope.launch {
        settingsRepository.setDarkModePreference(preference)
    }

    fun setUseBioTime(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setUseBioTime(enabled)
    }

    fun setGlassIntensity(value: Float) = viewModelScope.launch {
        settingsRepository.setGlassIntensity(value)
    }

    /** Декоративный слой фона (лепестки/листья/снег/звёзды/светлячки). */
    fun setMotif(motif: AmaliaMotif) = viewModelScope.launch {
        settingsRepository.setMotif(motif)
    }

    fun setMotifDensity(value: Float) = viewModelScope.launch {
        settingsRepository.setMotifDensity(value)
    }

    // ── Язык ─────────────────────────────────────────────────────────────

    fun setLanguage(language: AppLanguage) {
        // Мгновенный отклик интерфейса: подписка на DataStore в композиции
        // перерисует экран сразу после записи, без перезапуска активити.
        viewModelScope.launch {
            settingsRepository.setLanguage(language)
        }
        // Системная локаль приложения (меню Android «Язык приложения»,
        // разделяемые диалоги) обновляется отдельно и НЕ пересоздаёт
        // активити. Раньше именно этот вызов через AppCompatDelegate
        // запускал цепочку пересозданий — и приложение падало на старте.
        // Вызов идёт строго по действию пользователя, поэтому цикла нет.
        languageApplier?.onLanguageChanged(language)
    }

    // ── Голос ────────────────────────────────────────────────────────────

    fun setSpeechRate(value: Float) = viewModelScope.launch {
        settingsRepository.setSpeechRate(value)
    }

    fun setSpeechPitch(value: Float) = viewModelScope.launch {
        settingsRepository.setSpeechPitch(value)
    }

    fun setAutoListen(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAutoListen(enabled)
    }

    /** Продолжать ли последний диалог после перезапуска приложения. */
    fun setResumeLastSession(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setResumeLastSession(enabled)
    }

    fun setWakeWordEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setWakeWordEnabled(enabled)
    }

    // ── API-ключи и модели ───────────────────────────────────────────────

    /**
     * Профиль «мозга»: Groq с урезанным промптом или свой эндпоинт с полным.
     *
     * Модели распознавания и синтеза не меняются: профиль касается только
     * текстовой модели. Голос остаётся прежним.
     */
    fun setAiProfile(profile: AiProfile) = viewModelScope.launch {
        settingsRepository.setAiProfile(profile)
    }

    /**
     * Сохраняет настройки своего эндпоинта разом.
     *
     * Адрес, модель и ключ пишутся одним действием: по отдельности они
     * давали бы промежуточные состояния, в которых профиль включён, но
     * нерабочий (адрес есть, модели нет), и пользователь видел бы ошибку
     * ещё до того, как закончил ввод.
     */
    fun setCustomProvider(endpoint: String, model: String, key: String) =
        viewModelScope.launch {
            settingsRepository.setCustomProvider(endpoint, model, key)
        }

    /** Сохраняет ключ провайдера (ввод пользователя). */
    fun setProviderKey(provider: ModelCatalog.Provider, key: String) = viewModelScope.launch {
        settingsRepository.setProviderKey(provider, key)
    }

    /** Убирает собственный ключ: провайдер возвращается к ключу из сборки. */
    fun clearProviderKey(provider: ModelCatalog.Provider) = viewModelScope.launch {
        settingsRepository.clearProviderKey(provider)
    }

    /**
     * Сохраняет выбранную модель.
     *
     * Пустая строка означает «рекомендованная»: список моделей открывается
     * именно на ней, а в запрос уйдёт значение из [ModelCatalog].
     */
    fun setProviderModel(provider: ModelCatalog.Provider, model: String) = viewModelScope.launch {
        settingsRepository.setProviderModel(provider, model)
    }

    /** Голос Амалии в Fish Audio (`reference_id`). */
    fun setFishVoiceId(voiceId: String) = viewModelScope.launch {
        settingsRepository.setFishVoiceId(voiceId)
    }

    // ── Приватность ──────────────────────────────────────────────────────

    fun setDataRetentionDays(days: Int) = viewModelScope.launch {
        settingsRepository.setDataRetentionDays(days)
    }
    /** Полностью очищает историю разговоров. */
    fun clearHistory(onDone: () -> Unit = {}) = viewModelScope.launch {
        conversationRepository.clearAll()
        onDone()
    }

    /** Число сохранённых разговоров (для строки приватности). */
    suspend fun conversationCount(): Int = conversationRepository.count()
}
