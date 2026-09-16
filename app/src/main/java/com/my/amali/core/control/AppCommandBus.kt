package com.my.amali.core.control

import com.my.amali.ui.permissions.AmaliaPermission
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Команда UI-слою приложения от инструмента LLM.
 *
 * Инструменты выполняются в фоновой корутине оркестратора и не имеют
 * доступа ни к [androidx.navigation.NavHostController], ни к активити.
 * Поэтому они кладут команду в [AppCommandBus], а подписанный UI
 * (NavHost / MainActivity) исполняет её на главной nitке.
 */
sealed interface AppCommand {

    /** Перейти на экран приложения. */
    data class Navigate(
        val screen: AppScreen,
        val conversationId: String? = null,
    ) : AppCommand

    /** Вернуться назад по стеку навигации. */
    data object NavigateBack : AppCommand

    /** Показать системный диалог выдачи runtime-разрешения. */
    data class RequestPermission(val permission: AmaliaPermission) : AppCommand

    /**
     * Открыть системный экран настроек по action
     * (например `android.settings.ACCESSIBILITY_SETTINGS`).
     */
    data class OpenSystemScreen(
        val action: String,
        val withPackageUri: Boolean = false,
    ) : AppCommand
}

/**
 * Команда главному экрану ассистента от инструмента LLM.
 *
 * Позволяет модели управлять собственным жизненным циклом:
 * начать слушать, замолчать, сбросить диалог или произнести фразу.
 */
sealed interface AssistantCommand {

    /** Открыть микрофон сразу после завершения текущего цикла. */
    data object StartListening : AssistantCommand

    /** Немедленно остановить распознавание, генерацию и звук. */
    data object Stop : AssistantCommand

    /** Начать новый диалог: очистить экран и сессию. */
    data object NewChat : AssistantCommand

    /** Произнести [text] голосом (в очередь, если ассистент занят). */
    data class Speak(val text: String) : AssistantCommand

    /** Отправить [text] в диалог как реплику пользователя. */
    data class SendText(val text: String) : AssistantCommand
}

/**
 * Шина команд «инструменты LLM → UI».
 *
 * ## Почему SharedFlow, а не Channel
 * Команды UI мгновенные и теряют смысл, если подписчика нет: открыть
 * «Настройки» через десять минут, когда пользователь уже закрыл экран, —
 * это баг, а не фича. Поэтому реплей не хранится, а [dispatchApp] честно
 * возвращает false, когда подписчиков нет, и инструмент сообщает модели
 * «приложение сейчас не активно».
 *
 * ## Потокобезопасность
 * Все потоки созданы с `extraBufferCapacity` и `DROP_OLDEST`: emit
 * не блокирует фоновую корутину инструмента, даже если UI-поток занят.
 */
class AppCommandBus {

    private val _appCommands = MutableSharedFlow<AppCommand>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _assistantCommands = MutableSharedFlow<AssistantCommand>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _currentScreen = MutableStateFlow(AppScreen.ASSISTANT)

    /** Команды навигации и системных экранов — подписчик [com.my.amali.core.navigation.AmaliaNavHost]. */
    val appCommands: SharedFlow<AppCommand> = _appCommands.asSharedFlow()

    /** Команды ассистенту — подписчик [com.my.amali.ui.assistant.AssistantViewModel]. */
    val assistantCommands: SharedFlow<AssistantCommand> = _assistantCommands.asSharedFlow()

    /** Экран, который сейчас виден пользователю. */
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    /** true, если NavHost жив и готов принять команду. */
    val isUiActive: Boolean
        get() = _appCommands.subscriptionCount.value > 0

    /** true, если главный экран ассистента жив и готов принять команду. */
    val isAssistantActive: Boolean
        get() = _assistantCommands.subscriptionCount.value > 0

    /** Отправляет команду UI. false — подписчика нет (приложение не активно). */
    fun dispatchApp(command: AppCommand): Boolean {
        if (_appCommands.subscriptionCount.value == 0) return false
        return _appCommands.tryEmit(command)
    }

    /** Отправляет команду ассистенту. false — экран ассистента не создан. */
    fun dispatchAssistant(command: AssistantCommand): Boolean {
        if (_assistantCommands.subscriptionCount.value == 0) return false
        return _assistantCommands.tryEmit(command)
    }

    /** Вызывается NavHost'ом при каждой смене маршрута. */
    fun publishScreen(screen: AppScreen) {
        _currentScreen.value = screen
    }
}
