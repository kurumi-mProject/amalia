package com.my.amali.core.navigation

import androidx.annotation.StringRes
import com.my.amali.R

/**
 * Маршруты навигации приложения.
 *
 * Экраны делятся на три группы:
 * - [ONBOARDING] — однократный вход, показывается до основного UI;
 * - [ASSISTANT], [HISTORY], [SETTINGS] — три вкладки нижней навигации;
 * - [PERMISSIONS], [CONVERSATION] — вторичные экраны поверх вкладок.
 *
 * [CONVERSATION] параметризован идентификатором разговора.
 */
sealed class Destinations(val route: String) {

    /** Онбординг: приветствие, фичи, финальный CTA. */
    data object Onboarding : Destinations("onboarding")

    /** Главный экран ассистента (вкладка 1). */
    data object Assistant : Destinations("assistant")

    /** История разговоров (вкладка 2). */
    data object History : Destinations("history")

    /** Настройки (вкладка 3). */
    data object Settings : Destinations("settings")

    /** Экран управления разрешениями. */
    data object Permissions : Destinations("permissions")

    /** Детали конкретного разговора. */
    data object Conversation : Destinations("conversation/{conversationId}") {
        /** Собирает конкретный маршрут с подставленным id. */
        fun createRoute(conversationId: String): String = "conversation/$conversationId"

        /** Аргумент маршрута. */
        const val ARG_ID: String = "conversationId"
    }

    companion object {
        /** Все вкладки нижней навигации в порядке отображения. */
        val bottomTabs: List<Destinations> = listOf(Assistant, History, Settings)
    }
}

/**
 * UI-описание вкладки нижней навигации: маршрут, иконка, подпись.
 * Используется [BottomNavBar]-компонентом.
 */
data class BottomTab(
    val destination: Destinations,
    // @param: — явная цель аннотации. Kotlin 2.2 предупреждает, что без неё
    // аннотация применяется только к параметру, а в будущем начнёт применяться
    // ещё и к полю; здесь достаточно параметра (проверка значения в конструкторе).
    @param:StringRes val labelRes: Int,
)

/** Три вкладки Амалии. */
val AmaliaBottomTabs: List<BottomTab> = listOf(
    BottomTab(Destinations.Assistant, R.string.nav_assistant),
    BottomTab(Destinations.History, R.string.nav_history),
    BottomTab(Destinations.Settings, R.string.nav_settings),
)
