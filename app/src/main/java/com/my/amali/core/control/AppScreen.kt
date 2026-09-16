package com.my.amali.core.control

import com.my.amali.core.navigation.Destinations

/**
 * Полный каталог экранов приложения — то, что LLM может открыть голосом.
 *
 * Каждый экран знает:
 * - [route] — маршрут Navigation Compose (совпадает с [Destinations]);
 * - [toolKey] — машинное имя для параметра `navigate_app(screen=…)`;
 * - [title] — человекочитаемое имя, которое попадает в системный промпт
 *   и в JSON-снимок состояния, чтобы модель понимала, где она находится;
 * - [isTab] — вкладка нижней навигации (для таких экранов навигация
 *   сохраняет состояние стека, как при нажатии на таб);
 * - [aliases] — разговорные синонимы: модель часто передаёт
 *   «настройки» вместо `settings`, и резолвер обязан это съесть.
 */
enum class AppScreen(
    val route: String,
    val toolKey: String,
    val title: String,
    val isTab: Boolean = false,
    val aliases: List<String> = emptyList(),
) {

    /** Онбординг: показывается один раз, но доступен для повторного просмотра. */
    ONBOARDING(
        route = Destinations.Onboarding.route,
        toolKey = "onboarding",
        title = "Онбординг",
        aliases = listOf("гайд", "обучение", "guide", "вступление", "intro"),
    ),

    /** Главный экран ассистента (вкладка 1). */
    ASSISTANT(
        route = Destinations.Assistant.route,
        toolKey = "assistant",
        title = "Ассистент",
        isTab = true,
        aliases = listOf("главная", "чат", "ассистент", "home", "chat", "amalia"),
    ),

    /** История разговоров (вкладка 2). */
    HISTORY(
        route = Destinations.History.route,
        toolKey = "history",
        title = "История",
        isTab = true,
        aliases = listOf("история", "диалоги", "разговоры", "history", "conversations"),
    ),

    /** Настройки (вкладка 3). */
    SETTINGS(
        route = Destinations.Settings.route,
        toolKey = "settings",
        title = "Настройки",
        isTab = true,
        aliases = listOf("настройки", "опции", "settings", "preferences", "параметры"),
    ),

    SETTINGS_APPEARANCE(
        route = "${Destinations.Settings.route}/appearance",
        toolKey = "settings_appearance",
        title = "Настройки → Внешний вид",
        aliases = listOf("внешний вид", "тема", "оформление", "appearance", "theme", "дизайн"),
    ),

    SETTINGS_LANGUAGE(
        route = "${Destinations.Settings.route}/language",
        toolKey = "settings_language",
        title = "Настройки → Язык",
        aliases = listOf("язык", "language", "локаль", "locale"),
    ),

    SETTINGS_VOICE(
        route = "${Destinations.Settings.route}/voice",
        toolKey = "settings_voice",
        title = "Настройки → Голос",
        aliases = listOf("голос", "voice", "речь", "speech", "tts"),
    ),

    SETTINGS_DEVICE(
        route = "${Destinations.Settings.route}/device",
        toolKey = "settings_device",
        title = "Настройки → Управление устройством",
        aliases = listOf("устройство", "device", "wi-fi", "вайфай", "яркость", "громкость"),
    ),

    SETTINGS_PRIVACY(
        route = "${Destinations.Settings.route}/privacy",
        toolKey = "settings_privacy",
        title = "Настройки → Приватность",
        aliases = listOf("приватность", "privacy", "данные", "data"),
    ),

    SETTINGS_NOTIFICATIONS(
        route = "${Destinations.Settings.route}/notifications",
        toolKey = "settings_notifications",
        title = "Настройки → Уведомления",
        aliases = listOf("уведомления", "notifications", "нотификации"),
    ),

    SETTINGS_ABOUT(
        route = "${Destinations.Settings.route}/about",
        toolKey = "settings_about",
        title = "Настройки → О приложении",
        aliases = listOf("о приложении", "about", "версия", "version"),
    ),

    /** Экран разрешений: микрофон, локация, контакты и прочие. */
    PERMISSIONS(
        route = Destinations.Permissions.route,
        toolKey = "permissions",
        title = "Разрешения",
        aliases = listOf("разрешения", "доступы", "permissions", "права"),
    ),

    /** Карточка конкретного разговора; требует [conversationId]. */
    CONVERSATION_DETAIL(
        route = Destinations.Conversation.route,
        toolKey = "conversation",
        title = "Разговор",
        aliases = listOf("диалог", "разговор", "conversation"),
    ),
    ;

    /**
     * Маршрут с подставленным идентификатором разговора.
     * Для всех экранов, кроме [CONVERSATION_DETAIL], возвращает [route] как есть.
     */
    fun routeFor(conversationId: String?): String =
        if (this == CONVERSATION_DETAIL && !conversationId.isNullOrBlank()) {
            Destinations.Conversation.createRoute(conversationId)
        } else {
            route
        }

    companion object {

        /** Все экраны, которые LLM может назвать в `navigate_app`. */
        val navigable: List<AppScreen> get() = entries.toList()

        /** Значения enum для JSON Schema инструмента навигации. */
        val toolKeys: List<String> get() = entries.map { it.toolKey }

        /**
         * Разрешает имя экрана из аргумента инструмента.
         *
         * Терпим к формату: точный `toolKey`, маршрут, русское название,
         * английский синоним, «settings/appearance» и «настройки темы».
         * Возвращает null, если экран не распознан — вызывающий код
         * сообщает об этом модели, и она переспрашивает пользователя.
         */
        fun resolve(raw: String?): AppScreen? {
            val input = raw?.trim()?.lowercase()?.replace('-', '_') ?: return null
            if (input.isEmpty()) return null

            entries.firstOrNull { it.toolKey == input }?.let { return it }
            entries.firstOrNull { it.route == input }?.let { return it }
            entries.firstOrNull { screen -> screen.aliases.any { it == input } }?.let { return it }

            // «settings appearance» / «settings_appearance» / «настройки внешний вид»
            val compact = input.replace(' ', '_').replace("→", "")
            entries.firstOrNull { it.toolKey == compact }?.let { return it }

            // Подстрока: «открой настройки темы» → settings_appearance
            val byAliasContains = entries.firstOrNull { screen ->
                screen.aliases.any { alias -> alias.length > 3 && input.contains(alias) }
            }
            if (byAliasContains != null) return byAliasContains

            return entries.firstOrNull { screen ->
                screen.title.lowercase().let { title -> input.contains(title) || title.contains(input) }
            }
        }
    }
}
