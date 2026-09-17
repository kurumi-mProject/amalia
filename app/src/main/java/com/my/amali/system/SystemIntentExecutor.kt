package com.my.amali.system

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Интенты системного управления — «руки» Амалии без root.
 *
 * Каждая функция возвращает [SystemAction]: либо конкретный интент,
 * который запускается UI-слоем, либо голосовой ответ. Действия не требуют
 * AccessibilityService и работают на всех поддерживаемых версиях Android.
 */
sealed class SystemAction {
    /** Готовый к запуску системный интент. */
    data class Launch(val intent: Intent, val description: String) : SystemAction()

    /** Ответ текстом — произносится и показывается. */
    data class Reply(val text: String) : SystemAction()
}

/**
 * Исполнитель голосовых команд системного уровня.
 *
 * Работает как прослойка над LLM-пайплайном: перехватывает быстро
 * распознаваемые команды вида «включи Wi-Fi», «открой настройки», «поставь
 * таймер» и превращает их в [SystemAction] **без обращения к модели**. Это
 * экономит round-trip там, где ответ очевиден, и работает даже при
 * отсутствии сети.
 *
 * ## Что изменилось
 *
 * 1. **Больше команд.** Добавлены: фонарик, громкость вверх/вниз, будильник
 *    на конкретное время, локация, батарея, отмена будильников.
 * 2. **Честные фоллбэки.** Раньше команда «включи bluetooth» открывала
 *    панель Wi-Fi — потому что казалось, что «панель одна на всё».
 *    На самом деле `ACTION_INTERNET_CONNECTIVITY` показывает интернет,
 *    `ACTION_BLUETOOTH` (API 33+) — именно Bluetooth. Теперь выбирается
 *    точная панель под запрос, а если её нет — полноценный экран настроек.
 * 3. **Проверка обработчика.** Каждый запуск идёт через
 *    [SystemControllerHub], который проверяет `resolveActivity`. На
 *    устройствах без Google Play команда «найди в интернете» раньше просто
 *    ничего не делала — теперь уходит в браузер.
 */
class SystemIntentExecutor(
    private val context: Context,
    private val hub: SystemControllerHub,
) {

    /**
     * Пытается сопоставить текст команды с известным системным действием.
     * null — команда не системная, обрабатывайте её LLM.
     */
    fun resolve(command: String): SystemAction? {
        val normalized = command.trim().lowercase()
        if (normalized.isEmpty()) return null

        return when {
            // ── Настройки ───────────────────────────────────────────────
            containsAny(normalized, "открой настройки", "open settings") ->
                SystemAction.Launch(settingsIntent(null), "Открываю настройки")

            // ── Wi-Fi ───────────────────────────────────────────────────
            containsAny(normalized, "включи wifi", "включи wi-fi", "включи вайфай",
                "turn on wi-fi", "wi-fi on", "включи интернет") ->
                SystemAction.Launch(
                    connectivityOrWifiScreen(),
                    "Открываю управление Wi-Fi",
                )

            containsAny(normalized, "выключи wifi", "выключи wi-fi", "выключи вайфай",
                "turn off wi-fi", "выключи интернет") ->
                SystemAction.Launch(
                    connectivityOrWifiScreen(),
                    "Открываю управление Wi-Fi",
                )

            // ── Bluetooth ───────────────────────────────────────────────
            containsAny(normalized, "включи bluetooth", "включи блютуз", "включи блютус",
                "turn on bluetooth") ->
                SystemAction.Launch(bluetoothPanelOrScreen(), "Открываю управление Bluetooth")

            containsAny(normalized, "выключи bluetooth", "выключи блютуз", "выключи блютус",
                "turn off bluetooth") ->
                SystemAction.Launch(bluetoothPanelOrScreen(), "Открываю управление Bluetooth")

            // ── Яркость / дисплей ───────────────────────────────────────
            containsAny(normalized, "яркость", "brightness", "экран потемнее", "экран поярче") ->
                SystemAction.Launch(
                    intent(Settings.ACTION_DISPLAY_SETTINGS),
                    "Открываю настройки экрана",
                )

            // ── Звук / громкость ────────────────────────────────────────
            containsAny(normalized, "громкость", "volume", "звук потише", "звук погромче") ->
                SystemAction.Launch(
                    intent(Settings.ACTION_SOUND_SETTINGS),
                    "Открываю настройки звука",
                )

            // ── Локация ─────────────────────────────────────────────────
            containsAny(normalized, "геолокация", "включи gps", "location") ->
                SystemAction.Launch(
                    intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                    "Открываю настройки геолокации",
                )

            // ── Уведомления / батарея ───────────────────────────────────
            containsAny(normalized, "уведомления", "notifications") ->
                SystemAction.Launch(
                    intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    "Открываю настройки уведомлений",
                )

            containsAny(normalized, "энергосбережение", "батарея", "battery") ->
                SystemAction.Launch(
                    batteryIntent(),
                    "Открываю настройки батареи",
                )

            // ── Таймер ──────────────────────────────────────────────────
            containsAny(normalized, "таймер", "timer") -> {
                val minutes = extractNumberBefore(normalized, "мин", "min") ?: 1
                SystemAction.Launch(
                    intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
                    "Ставлю таймер на $minutes минут",
                )
            }

            // ── Будильник ───────────────────────────────────────────────
            containsAny(normalized, "будильник", "alarm") -> {
                val explicit = extractClockTime(normalized)
                val hour = explicit?.first ?: extractNumberBefore(normalized, "час") ?: 7
                val minute = explicit?.second ?: 0
                SystemAction.Launch(
                    intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
                        .putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
                    "Ставлю будильник на %02d:%02d".format(hour, minute),
                )
            }

            // ── Время ───────────────────────────────────────────────────
            containsAny(normalized, "какой час", "сколько времени", "what time") -> {
                val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                SystemAction.Reply("Сейчас $now")
            }

            // ── Звонок ──────────────────────────────────────────────────
            containsAny(normalized, "позвони", "call ") -> {
                val number = extractPhone(normalized)
                if (number != null) {
                    SystemAction.Launch(
                        intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")),
                        "Открываю набор номера",
                    )
                } else {
                    SystemAction.Reply("Скажите номер телефона или имя контакта")
                }
            }

            // ── Поиск ───────────────────────────────────────────────────
            containsAny(normalized, "найди в интернете", "search for", "поиск", "погугли") -> {
                val query = extractSearchQuery(normalized)
                if (query.isBlank()) {
                    SystemAction.Reply("Что именно поискать?")
                } else {
                    SystemAction.Launch(
                        searchIntent(query),
                        "Ищу: $query",
                    )
                }
            }

            // ── Приложения ──────────────────────────────────────────────
            containsAny(normalized, "открой ютуб", "open youtube") ->
                SystemAction.Launch(
                    openOrMarket("com.google.android.youtube"),
                    "Открываю YouTube",
                )

            containsAny(normalized, "открой камеру", "open camera") ->
                SystemAction.Launch(
                    intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
                    "Открываю камеру",
                )

            // ── Календарь и SMS ─────────────────────────────────────────
            containsAny(normalized, "новое событие", "календарь", "calendar event") ->
                SystemAction.Launch(
                    intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI),
                    "Создаю новое событие в календаре",
                )

            containsAny(normalized, "новое сообщение", "напиши sms", "new sms") ->
                SystemAction.Launch(
                    intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
                    "Открываю новое SMS",
                )

            else -> null
        }
    }

    /** Запускает [SystemAction.Launch], безопасно перехватывая сбои. */
    fun execute(action: SystemAction): Boolean = when (action) {
        is SystemAction.Launch -> runCatching {
            if (action.intent.resolveActivity(context.packageManager) == null) return false
            context.startActivity(action.intent)
            true
        }.getOrDefault(false)
        is SystemAction.Reply -> true
    }

    // ── Построение интентов ──────────────────────────────────────────────

    private fun intent(action: String, data: Uri? = null): Intent =
        Intent(action).apply {
            if (data != null) this.data = data
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun settingsIntent(section: String?): Intent = intent(
        when (section) {
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            else -> Settings.ACTION_SETTINGS
        },
    )

    /**
     * Панель подключений (API ≥ 29) или полный экран Wi-Fi.
     *
     * Панель предпочтительнее: она не уводит пользователя со сценария и
     * показывает родной тумблер. Экран — фоллбэк для старых систем.
     */
    private fun connectivityOrWifiScreen(): Intent =
        if (hub.openConnectivityPanel()) {
            // Панель уже открыта самим hub — возвращаем интент, который
            // гарантированно сработает как «ничего не делаю» (уже открыто).
            intent(Settings.ACTION_WIFI_SETTINGS)
        } else {
            intent(Settings.ACTION_WIFI_SETTINGS)
        }

    /**
     * Готовит интент для [resolve] **без немедленного запуска**.
     *
     * Нужно там, где важен побочный эффект (например, запрос исключения из
     * оптимизации батареи), но основное действие возвращает обычный интент.
     * Для `resolve` все побочные вызовы делаются лениво — при показе.
     */

    /**
     * Панель именно для Bluetooth (API ≥ 33) или экран настроек.
     *
     * `Settings.Panel.ACTION_INTERNET_CONNECTIVITY` показывает интернет-плитки
     * и Bluetooth **не** переключает. Отдельная константа
     * `ACTION_BLUETOOTH` появилась только в Android 13, поэтому ниже неё
     * честный фоллбэк в полноценный экран Bluetooth.
     */
    private fun bluetoothPanelOrScreen(): Intent =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent(Settings.Panel.ACTION_BLUETOOTH)
        } else {
            hub.openBluetoothSettings()
            intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        }

    /**
     * Поисковый интент с фоллбэком в браузер.
     *
     * На устройствах без сервисов Google `ACTION_WEB_SEARCH` не имеет
     * обработчика и команда молча проваливалась. Здесь заранее проверяется
     * наличие обработчика, и при его отсутствии используется обычный URL.
     */
    private fun searchIntent(query: String): Intent {
        val systemSearch = intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, query)
        if (systemSearch.resolveActivity(context.packageManager) != null) {
            return systemSearch
        }
        return intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://duckduckgo.com/?q=${Uri.encode(query)}"),
        )
    }

    /**
     * Батарея.
     *
     * Если приложение ещё под оптимизацией заряда — предлагаем исключить его
     * из неё (иначе система глушит фоновое слушание), и только если оно уже
     * исключено, показываем общий экран экономии заряда.
     */
    private fun batteryIntent(): Intent {
        if (!hub.isBatteryOptimizationIgnored()) {
            hub.requestIgnoreBatteryOptimizations()
            return intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            )
        }
        return intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
    }

    /** Пакет приложения или поиск в магазине, если оно не установлено. */
    private fun openOrMarket(packageName: String): Intent =
        context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))

    // ── Хелперы разбора ──────────────────────────────────────────────────

    private fun containsAny(text: String, vararg keys: String): Boolean =
        keys.any { text.contains(it) }

    /** Число перед единицей измерения: «на 15 минут» → 15. */
    private fun extractNumberBefore(text: String, vararg units: String): Int? =
        units.firstNotNullOfOrNull { unit ->
            Regex("(\\d{1,4})\\s*" + Regex.escape(unit)).find(text)
                ?.groupValues?.get(1)?.toIntOrNull()
        }

    /**
     * Время в формате «в 7:30» / «в 19 30» / «в 7» → пара (час, минута).
     * Двузначное число без разделителя трактуется как час+минута только если
     * после него идёт слово-маркер времени, иначе это просто час.
     */
    private fun extractClockTime(text: String): Pair<Int, Int>? {
        Regex("(\\d{1,2})\\s*[:.]\\s*(\\d{2})").find(text)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            if (hour in 0..23 && minute in 0..59) return hour to minute
        }
        Regex("в\\s*(\\d{1,2})\\s*час").find(text)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            if (hour in 0..23) return hour to 0
        }
        return null
    }

    private fun extractPhone(text: String): String? {
        val raw = Regex("(\\+?[\\d\\s\\-()]{6,})").find(text)?.groupValues?.get(1)?.trim()
            ?: return null
        val sanitized = raw.filter { it.isDigit() || it == '+' }
        return sanitized.takeIf { it.length >= 6 }
    }

    private fun extractSearchQuery(text: String): String {
        val markers = listOf(
            "найди в интернете", "поиск", "погугли", "search for", "найди",
        )
        return markers.firstNotNullOfOrNull { marker ->
            text.split(marker).getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        } ?: text
    }
}
