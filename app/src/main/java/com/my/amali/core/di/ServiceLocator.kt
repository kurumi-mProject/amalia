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
import com.my.amali.data.ai.ToolRegistry
import com.my.amali.data.ai.AmaliaTools
import com.my.amali.data.apps.AppRegistry
import com.my.amali.data.model.ChatMessage
import com.my.amali.data.repository.AppPreferencesRepository
import com.my.amali.data.repository.ConversationRepository
import com.my.amali.data.repository.SettingsRepository
import com.my.amali.domain.entity.AppLanguage
import com.my.amali.domain.entity.UserApiSettings
import com.my.amali.system.DeviceCommandExecutor
import com.my.amali.system.SystemControllerHub
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

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
        // Подключаем «мосты» от инструментов к репозиториям. Раньше этого
        // делать нельзя — репозитории ленивые и зависят от appContext.
        amaliaTools.searchHistoryProvider = { query: String ->
            queryForHistory(query)
        }
        // Реестр приложений: инструмент open_app должен видеть реальный
        // список установленного, а не захардкоженный каталог.
        amaliaTools.installedAppsProvider = {
            appRegistry.installedApps()
        }
        amaliaTools.pinnedAppsProvider = {
            pinnedAppsSnapshot()
        }
        amaliaTools.appAliasesProvider = {
            appAliasesSnapshot()
        }
        amaliaTools.recentConversationsProvider = { limit: Int ->
            recentConversations(limit)
        }
        amaliaTools.clearHistoryProvider = {
            clearAllConversations()
        }
        amaliaTools.settingsChangeProvider = { key: String, value: String ->
            applySettingChange(key, value)
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
     * Реестр установленных приложений.
     *
     * Именно он отвечает на вопрос «что вообще есть на этом телефоне»:
     * сканирует `PackageManager` вместо захардкоженного списка пакетов,
     * поэтому работает на Xiaomi, Huawei, Samsung и любой кастомной прошивке.
     */
    val appRegistry: AppRegistry by lazy { AppRegistry(appContext) }

    /**
     * Избранные приложения и голосовые синонимы к ним.
     *
     * Пользователь сам отмечает нужное на экране «Приложения Амалии»
     * и задаёт названия, которыми это называет вслух.
     */
    val appPreferencesRepository: AppPreferencesRepository by lazy {
        AppPreferencesRepository(dataStore)
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

    /**
     * Хватит ли ключей, чтобы поднять реальный конвейер.
     *
     * Свой ключ пользователя считается наравне с ключом сборки: если человек
     * вписал только Groq и Fish Audio (а Deepgram у него нет), конвейер всё
     * равно поднимается живым — иначе введённые ключи не давали бы ничего,
     * пока не заполнены все три поля.
     *
     * Правило простое: реальные движки нужны, когда **текст и голос** есть
     * (это минимальный осмысленный ассистент), а распознавание подтянется
     * любым доступным ключом.
     */
    private fun effectiveKeys(): Triple<String, String, String> {
        val api = runCatching {
            kotlinx.coroutines.runBlocking {
                settingsRepository.settings.first().api
            }
        }.getOrDefault(UserApiSettings())
        return Triple(
            api.groqKey.trim().ifBlank { BuildConfig.GROQ_API_KEY },
            api.deepgramKey.trim().ifBlank { BuildConfig.DEEPGRAM_API_KEY },
            api.fishAudioKey.trim().ifBlank { BuildConfig.FISH_AUDIO_API_KEY },
        )
    }

    /** Есть ли пара «текст + голос» — этого достаточно для живого конвейера. */
    private val hasCoreKeys: Boolean
        get() {
            val (llm, _, tts) = effectiveKeys()
            return llm.isNotBlank() && tts.isNotBlank()
        }

    /** Описание активных движков — показывается в настройках голоса. */
    val aiConfig: AIConfig
        get() = if (hasLiveKeys) AIConfig.Live else AIConfig.Mock

    /**
     * Ключи, с которыми собран текущий конвейер.
     *
     * Оркестратор дорогой (HTTP-клиенты, реестр инструментов), поэтому
     * пересобирать его на каждой рекомпозиции нельзя. Но и держать один
     * экземпляр навсегда тоже нельзя: пользователь может вписать свой ключ
     * прямо в настройках, и он обязан заработать сразу.
     *
     * Отпечаток — единственное, что нас здесь интересует: ключи, модели и
     * голос. Настройки вида «тема» или «язык» на состав движков не влияют,
     * поэтому в отпечаток не входят: иначе смена языка пересобирала бы
     * конвейер на ровном месте.
     */
    private val apiFingerprint: String
        get() {
            val api = apiSnapshot()
            return listOf(
                api.groqKey,
                api.deepgramKey,
                api.fishAudioKey,
                api.llmModel,
                api.sttModel,
                api.ttsModel,
                api.fishVoiceId,
            ).joinToString(FINGERPRINT_SEPARATOR)
        }

    /** Снимок API-настроек без подписки на весь поток настроек. */
    private fun apiSnapshot(): UserApiSettings =
        runCatching {
            kotlinx.coroutines.runBlocking {
                settingsRepository.settings.first().api
            }
        }.getOrDefault(UserApiSettings())

    /**
     * Оркестратор конвейера STT → LLM → TTS.
     *
     * Пересобирается ровно тогда, когда изменились ключи или модели: раньше
     * движки создавались один раз из `BuildConfig`, и введённый пользователем
     * ключ вступал в силу только после перезапуска приложения — то есть
     * выглядел как неработающий.
     */
    val aiOrchestrator: AIOrchestrator
        get() {
            val fingerprint = apiFingerprint
            cachedOrchestrator?.takeIf { cachedFingerprint == fingerprint }?.let { return it }
            synchronized(this) {
                val current = apiFingerprint
                cachedOrchestrator?.takeIf { cachedFingerprint == current }?.let { return it }
                val built = buildOrchestrator()
                cachedOrchestrator = built
                cachedFingerprint = current
                return built
            }
        }

    @Volatile
    private var cachedOrchestrator: AIOrchestrator? = null

    @Volatile
    private var cachedFingerprint: String? = null

    private fun buildOrchestrator(): AIOrchestrator = if (hasCoreKeys) {
        AIOrchestrator(
            sttEngine = DeepgramSTT(appContext),
            llmEngine = GroqLLM(),
            ttsEngine = ttsEngine,
            registry = toolRegistry,
            commandExecutor = deviceCommandExecutor,
        )
    } else {
        AIOrchestrator(
            sttEngine = MockSpeechToTextEngine(),
            llmEngine = MockLanguageModel(),
            ttsEngine = MockTextToSpeechEngine(),
            registry = toolRegistry,
        )
    }

    /** Хаб системных контроллеров (Wi-Fi, BT, яркость, громкость и т.д.). */
    val systemControllers: SystemControllerHub by lazy { SystemControllerHub(appContext) }

    /** Исполнитель команд устройства от LLM (используется в legacy-пути). */
    val deviceCommandExecutor: DeviceCommandExecutor by lazy {
        DeviceCommandExecutor(appContext, systemControllers)
    }

    /** Набор инструментов Амалии — конкретные AmaliaTool (definition + handler). */
    private val amaliaTools: AmaliaTools by lazy {
        AmaliaTools(
            context = appContext,
            hub = systemControllers,
        )
    }

    /** Реестр инструментов, доступных LLM. Передаётся в [aiOrchestrator]. */
    val toolRegistry: ToolRegistry by lazy { ToolRegistry.of(amaliaTools.all) }


    /**
     * Синтез речи: голос Амалии через Fish Audio.
     *
     * Движок ровно один — и это осознанное решение: ассистент обязан
     * говорить голосом Амалии, а не «каким-нибудь» голосом системы.
     * Раньше здесь стояла обёртка с подменой на системный TTS Android
     * при сбое облака — она убрана, потому что подмена голоса ломает сам
     * продукт: пользователь слышит чужой голос и не понимает, почему.
     *
     * Что происходит, если синтез не удался: оркестратор не считает сбой
     * озвучки ошибкой цикла, поэтому ответ остаётся текстом в карточке,
     * а не превращается в экран ошибки. Тихо и честно — без подмены.
     */
    val ttsEngine: FishAudioTTS by lazy { FishAudioTTS() }

    /** Выдан ли прямо сейчас доступ к микрофону. */
    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    // ── Провайдеры для AmaliaTools ───────────────────────────────────────

    /**
     * Поиск по истории: возвращает JSON-список заголовков разговоров и
     * сводку первого подходящего сообщения (≤ 8000 символов на ответ).
     */
    private suspend fun queryForHistory(query: String): String {
        val normalised = query.trim().lowercase()
        if (normalised.isEmpty()) return "{\"results\":[]}"
        return try {
            val all = conversationRepository.conversations.first()
            val matched = all
                .filter { conversation ->
                    conversation.title.lowercase().contains(normalised) ||
                        conversation.messages.any { it.content.lowercase().contains(normalised) }
                }
                .take(8)
            val arr = JSONArray()
            matched.forEach { conversation ->
                val firstUser = conversation.messages
                    .firstOrNull { it.isFromUser }
                    ?.content
                    ?.take(120)
                    .orEmpty()
                val lastReply = conversation.messages
                    .lastOrNull { it.isFromAssistant }
                    ?.content
                    ?.take(120)
                    .orEmpty()
                arr.put(JSONObject().apply {
                    put("id", conversation.id)
                    put("title", conversation.title)
                    put("first_user_message", firstUser)
                    put("last_reply", lastReply)
                    put("message_count", conversation.messages.size)
                    put("updated_at", conversation.updatedAt)
                })
            }
            JSONObject().put("query", query).put("results", arr).toString()
        } catch (e: Throwable) {
            "{\"error\":\"${e.message?.replace("\"", "'") ?: "search failed"}\"}"
        }
    }

    private suspend fun recentConversations(limit: Int): String {
        return try {
            val all = conversationRepository.conversations.first()
                .sortedByDescending { it.updatedAt }
                .take(limit.coerceAtLeast(1))
            val arr = JSONArray()
            all.forEach { conversation ->
                arr.put(JSONObject().apply {
                    put("id", conversation.id)
                    put("title", conversation.title)
                    put("message_count", conversation.messages.size)
                    put("updated_at", conversation.updatedAt)
                })
            }
            JSONObject().put("count", all.size).put("conversations", arr).toString()
        } catch (e: Throwable) {
            "{\"error\":\"${e.message?.replace("\"", "'") ?: "failed"}\"}"
        }
    }

    private suspend fun clearAllConversations(): String {
        return try {
            val count = conversationRepository.count()
            conversationRepository.clearAll()
            JSONObject().put("deleted", count).toString()
        } catch (e: Throwable) {
            "{\"error\":\"${e.message?.replace("\"", "'") ?: "failed"}\"}"
        }
    }

    /**
     * Снимок избранных приложений для инструмента открытия.
     *
     * Берём только те, что реально установлены: если приложение удалили,
     * отдавать модели его пакет нельзя — она пообещает открыть то, чего нет.
     */
    private suspend fun pinnedAppsSnapshot(): List<com.my.amali.data.apps.InstalledApp> {
        val pinned = appPreferencesRepository.pinnedApps.first()
        if (pinned.isEmpty()) return emptyList()
        val installed = appRegistry.installedApps()
        val byPackage = installed.associateBy { it.packageName }
        return pinned.mapNotNull { byPackage[it.packageName] }
    }

    /** Снимок пользовательских синонимов «как говорю» → пакет. */
    private suspend fun appAliasesSnapshot(): Map<String, String> =
        appPreferencesRepository.aliases.first()

    private suspend fun applySettingChange(key: String, value: String): String {
        return when (key) {
            "language" -> {
                val language = AppLanguage.fromCode(value)
                settingsRepository.setLanguage(language)
                JSONObject()
                    .put("language_code", language.code)
                    .put("language_name", language.nativeName)
                    .toString()
            }
            "auto_listen" -> {
                val on = value.lowercase() in setOf("true", "1", "yes", "on", "вкл")
                settingsRepository.setAutoListen(on)
                JSONObject().put("auto_listen", on).toString()
            }
            "theme" -> {
                // Расширение на будущее: темы сохраняются здесь
                JSONObject().put("theme", value).toString()
            }
            else -> JSONObject().put("warning", "unknown_setting_key: $key").toString()
        }
    }

    @Suppress("unused")
    private fun nowId(): String = ChatMessage.newId()

    private companion object {
        /**
         * Разделитель полей в отпечатке конфигурации.
         *
         * Символ выбран так, чтобы не встречаться в ключах и именах моделей:
         * склейка «ключ + разделитель + модель» не должна давать ложных
         * совпадений, когда два разных набора настроек складываются в одну
         * и ту же строку.
         */
        const val FINGERPRINT_SEPARATOR = "\u0001"
    }
}
