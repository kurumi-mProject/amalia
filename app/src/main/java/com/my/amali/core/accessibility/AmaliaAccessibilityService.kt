package com.my.amali.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility-сервис Амалии: фундамент будущего управления устройством
 * голосом (чтение экрана, жесты, нажатия кнопок других приложений).
 *
 * Сейчас сервис работает в пассивном режиме: регистрирует себя в
 * [AmaliaAccessibilityService.isRunning] (чтобы настройки могли показать
 * статус) и логирует ключевые события, не выполняя никаких действий.
 * Конкретные команды будут подключены после включения реальных AI-движков.
 */
class AmaliaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = EVENT_TIMEOUT_MS
        } ?: return
        isRunning = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Пассивный режим: события только фиксируются для будущих сценариев.
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> lastWindowPackage = event.packageName?.toString()
            AccessibilityEvent.TYPE_VIEW_CLICKED -> lastClickTarget = event.className?.toString()
        }
    }

    override fun onInterrupt() {
        // Нечего прерывать в пассивном режиме.
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    /**
     * Выполняет глобальное действие (НАЗАД, ДОМОЙ, НЕДАВНИЕ) через
     * [performGlobalAction]. Используется будущими голосовыми командами.
     */
    fun performBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    /** Голосовая команда «домой». */
    fun performHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    /** Голосовая команда «недавние приложения». */
    fun performRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /** Возвращает текстовое содержимое активного окна (для чтения вслух). */
    fun readActiveWindowText(): String {
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return ""
        return buildString {
            appendNodeText(root)
        }
    }

    private fun StringBuilder.appendNodeText(node: AccessibilityNodeInfo) {
        node.text?.let { append(it).append(' ') }
        node.contentDescription?.let { append(it).append(' ') }
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                appendNodeText(child)
                child.recycle()
            }
        }
    }

    companion object {
        private const val EVENT_TIMEOUT_MS = 100L

        /** true, пока сервис подключён системой (статус для настроек). */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** Последний пакет активного окна (диагностика будущих сценариев). */
        @Volatile
        var lastWindowPackage: String? = null
            private set

        /** Последний класс нажатого view (диагностика). */
        @Volatile
        var lastClickTarget: String? = null
            private set

        /** Ссылка на активный сервис, если он запущен системой. */
        @Volatile
        var instance: AmaliaAccessibilityService? = null
            private set
    }

    init {
        instance = this
    }
}
