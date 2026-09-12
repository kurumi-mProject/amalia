package com.my.amali.core.di

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.my.amali.BuildConfig
import com.my.amali.data.ai.AIConfig
import com.my.amali.data.ai.AIOrchestrator
import com.my.amali.data.ai.DeepgramSTT
import com.my.amali.data.ai.FishAudioTTS
import com.my.amali.data.ai.GroqLLM
import com.my.amali.data.ai.MockLanguageModel
import com.my.amali.data.ai.MockSpeechToTextEngine
import com.my.amali.data.ai.MockTextToSpeechEngine
import com.my.amali.data.repository.ConversationRepository
import com.my.amali.data.repository.SettingsRepository
import com.my.amali.system.SystemControllerHub

private val Context.amaliaDataStore: DataStore<Preferences> by preferencesDataStore(name = "amalia_settings")

/**
 * Ручной DI-контейнер приложения (вместо Hilt — осознанное решение для
 * надёжности CI). Все зависимости — синглтоны, создаются лениво.
 *
 * Жизненный цикл: [com.my.amali.AmaliaApp.onCreate] вызывает [init].
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
     * True, если в сборку зашиты все три ключа и конвейер может работать
     * с реальными сервисами. Иначе приложение поднимает офлайн-заглушки,
     * чтобы интерфейс оставался полностью рабочим, а не падал в ошибку.
     */
    val hasLiveKeys: Boolean
        get() = BuildConfig.DEEPGRAM_API_KEY.isNotBlank() &&
            BuildConfig.GROQ_API_KEY.isNotBlank() &&
            BuildConfig.FISH_AUDIO_API_KEY.isNotBlank()

    /** Описание активных движков — показывается в настройках голоса. */
    val aiConfig: AIConfig
        get() = if (hasLiveKeys) AIConfig.Live else AIConfig.Mock

    /**
     * Оркестратор AI-конвейера STT → LLM → TTS.
     * Реальные движки: Deepgram nova-2, Groq gpt-oss-20b, Fish Audio s2.1-pro.
     */
    val aiOrchestrator: AIOrchestrator by lazy {
        if (hasLiveKeys) {
            AIOrchestrator(
                sttEngine = DeepgramSTT(appContext),
                llmEngine = GroqLLM(),
                ttsEngine = FishAudioTTS(),
            )
        } else {
            AIOrchestrator(
                sttEngine = MockSpeechToTextEngine(),
                llmEngine = MockLanguageModel(),
                ttsEngine = MockTextToSpeechEngine(),
            )
        }
    }

    /** Хаб системных контроллеров (Wi-Fi, BT, яркость, громкость и т.д.). */
    val systemControllers: SystemControllerHub by lazy { SystemControllerHub(appContext) }

    /** Выдан ли прямо сейчас доступ к микрофону. */
    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
