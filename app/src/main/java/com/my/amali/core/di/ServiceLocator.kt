package com.my.amali.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.my.amali.data.ai.AIOrchestrator
import com.my.amali.data.ai.MockLanguageModel
import com.my.amali.data.ai.MockSpeechToTextEngine
import com.my.amali.data.ai.MockTextToSpeechEngine
import com.my.amali.data.repository.ConversationRepository
import com.my.amali.data.repository.SettingsRepository
import com.my.amali.system.SystemControllerHub

/**
 * Ленивый контекст уровня приложения.
 * Создаётся один раз на процесс — повторные вызовы [init] безопасны.
 */
private val Context.amaliaDataStore: DataStore<Preferences> by preferencesDataStore(name = "amalia_settings")

/**
 * Ручной DI-контейнер приложения (вместо Hilt — осознанное решение для
 * надёжности CI). Все зависимости — синглтоны, инициализируются лениво
 * при первом обращении.
 *
 * Жизненный цикл: [AmaliaApp.onCreate] вызывает [init]; компоненты
 * обращаются к свойствам объекта напрямую.
 */
object ServiceLocator {

    @Volatile
    private var initialized = false

    private lateinit var appContext: Context

    /** Инициализирует контейнер контекстом приложения. Идемпотентно. */
    fun init(context: Context) {
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            initialized = true
        }
    }

    /** Контекст приложения (доступен после [init]). */
    val appContextValue: Context
        get() = appContext

    /** Единое хранилище настроек и истории (DataStore Preferences). */
    val dataStore: DataStore<Preferences>
        get() = appContext.amaliaDataStore

    /** Репозиторий пользовательских настроек. */
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(dataStore) }

    /** Репозиторий разговоров (персистентная история диалогов). */
    val conversationRepository: ConversationRepository by lazy {
        ConversationRepository(appContext, dataStore)
    }

    /**
     * Оркестратор AI-конвейера STT → LLM → TTS.
     * Сейчас собран на Mock-движках; замена на реальные — точечная правка здесь.
     */
    val aiOrchestrator: AIOrchestrator by lazy {
        AIOrchestrator(
            sttEngine = MockSpeechToTextEngine(),
            llmEngine = MockLanguageModel(),
            ttsEngine = MockTextToSpeechEngine(),
        )
    }

    /** Хаб системных контроллеров (Wi-Fi, BT, яркость, громкость и т.д.). */
    val systemControllers: SystemControllerHub by lazy { SystemControllerHub(appContext) }
}
