package com.my.amali.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility-сервис Амалии — «руки» модели за пределами собственного UI.
 *
 * Через него LLM может:
 * - нажимать «назад», «домой», «недавние», открывать шторку и быстрые настройки;
 * - читать содержимое активного окна любого приложения;
 * - тапать по элементу, найденному по тексту, скроллить и свайпать жестами;
 * - делать скриншот (API 30+).
 *
 * ## Почему сервис такой тонкий
 *
 * Сервис живёт в системном биндинге и переживает пересоздание процесса
 * приложения. Поэтому он не знает ни про инструменты, ни про JSON: только
 * публикует себя в [instance] и хранит минимальный контекст последнего окна.
 * Вся логика поиска узлов, жестов и снимков — в
 * [com.my.amali.core.control.AccessibilityController], её можно менять
 * без риска сломать системный контракт сервиса.
 *
 * ## Важно про ссылки на системные константы
 *
 * Kotlin **не** наследует Java-статику в пространство имён подкласса,
 * поэтому `GLOBAL_ACTION_*`, `GestureResultCallback` и
 * `TakeScreenshotCallback` нужно брать у [AccessibilityService] напрямую.
 * Прошлая версия обращалась к ним через имя этого класса, и контроллер
 * просто не компилировался.
 */
class AmaliaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()

        // serviceInfo может быть null до полного биндинга — тогда собираем свой.
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_VIEW_CLICKED or
            AccessibilityEvent.TYPE_VIEW_FOCUSED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = info.flags or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = EVENT_TIMEOUT_MS
        // packageNames = null означает «все приложения». Пустая строка в
        // XML-конфиге давала пустой массив, из-за чего сервис не получал
        // события вообще и модель считала любой экран пустым.
        info.packageNames = null

        // Некоторые прошивки бросают на setServiceInfo при гонке отключения.
        runCatching { serviceInfo = info }

        instance = this
        isRunning = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        when (type) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                event.packageName?.toString()?.let { pkg ->
                    lastWindowPackage = pkg
                    lastWindowChangeAt = System.currentTimeMillis()
                }
                event.className?.toString()?.let { lastWindowClass = it }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                lastContentChangeAt = System.currentTimeMillis()
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                lastClickTarget = event.className?.toString()
            }
        }
    }

    override fun onInterrupt() {
        // Сервис не воспроизводит непрерывный фидбек — прерывать нечего.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isRunning = false
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {

        private const val EVENT_TIMEOUT_MS = 50L

        /** true, пока сервис подключён системой. */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** Живой экземпляр сервиса или null, если специальный доступ выключен. */
        @Volatile
        var instance: AmaliaAccessibilityService? = null
            private set

        /** Пакет активного окна — fallback-контекст, если дерево не отдалось. */
        @Volatile
        var lastWindowPackage: String? = null
            private set

        /** Класс активного окна (диагностика). */
        @Volatile
        var lastWindowClass: String? = null
            private set

        /** Класс последнего нажатого view (диагностика). */
        @Volatile
        var lastClickTarget: String? = null
            private set

        /** Момент последней смены окна — им ждём применения навигации. */
        @Volatile
        var lastWindowChangeAt: Long = 0L
            private set

        /** Момент последнего изменения содержимого активного окна. */
        @Volatile
        var lastContentChangeAt: Long = 0L
            private set

        /** Сервис доступен и готов выполнять действия. */
        val isOperational: Boolean
            get() = isRunning && instance != null
    }
}
