package com.my.amali.system

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings as SystemSettings
import com.my.amali.data.model.DeviceCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Выполняет список [DeviceCommand], полученных от LLM.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ЧТО ИЗМЕНИЛОСЬ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Раньше исполнитель возвращал строки вида «wifi: не удалось (Android 13+
 * требует системного экрана)» — и эта строка уходила и в лог, и в промпт.
 * Модель видела «не удалось» и отвечала пользователю отказом, хотя система
 * могла выполнить команду через панель.
 *
 * Теперь каждый результат формируется из [ControlResult]: он знает
 * фактический уровень доступа ([ControlAccess]) и сообщает **что именно
 * произошло**, а не просто «получилось / нет». Для модели это две разные
 * ситуации:
 *
 *  — `control_level: direct` → «включила»;
 *  — `control_level: panel/screen` → «открыла панель, нажми плитку»;
 *  — `error` → «на этом устройстве так нельзя» (нет вспышки, старая ОС).
 *
 * Ни один обработчик не бросает исключений наружу: [execute] оборачивает
 * каждый вызов, поэтому одна неисправная команда не ломает весь набор.
 */
class DeviceCommandExecutor(
    private val context: Context,
    private val hub: SystemControllerHub,
) {

    /**
     * Выполняет команды по очереди.
     *
     * Последовательно, а не параллельно: команды часто зависимы (например,
     * «убавить громкость» дважды), а параллельная запись в AudioManager даёт
     * гонку и неверный итоговый уровень.
     */
    suspend fun execute(commands: List<DeviceCommand>): List<String> =
        commands.map { cmd ->
            runCatching { dispatch(cmd) }.getOrElse { error ->
                "«${cmd.action.key}»: ошибка — ${error.message ?: "неизвестная причина"}"
            }
        }

    private suspend fun dispatch(cmd: DeviceCommand): String = when (cmd.action) {

        // ── Wi-Fi ───────────────────────────────────────────────────────
        DeviceCommand.Action.SET_WIFI -> {
            val on = cmd.boolValue()
            hub.setWifiEnabled(on).describe(
                target = "wifi",
                applied = { if (on) "Wi-Fi включён" else "Wi-Fi выключен" },
                delegated = { level ->
                    "Wi-Fi: открыла ${level.ruName} — переключи плитку сам"
                },
            )
        }

        // ── Bluetooth ───────────────────────────────────────────────────
        DeviceCommand.Action.SET_BLUETOOTH -> {
            val on = cmd.boolValue()
            hub.setBluetoothEnabled(on).describe(
                target = "bluetooth",
                applied = { if (on) "Bluetooth включён" else "Bluetooth выключен" },
                delegated = { level ->
                    "Bluetooth: открыла ${level.ruName} — переключи сам"
                },
            )
        }

        // ── Яркость ─────────────────────────────────────────────────────
        DeviceCommand.Action.SET_BRIGHTNESS -> {
            val pct = cmd.intValue().coerceIn(0, 100)
            hub.setBrightness((pct * 255) / 100).describe(
                target = "brightness",
                applied = { "яркость → $pct%" },
                delegated = { level -> "яркость: открыла ${level.ruName}" },
            )
        }

        // ── Громкость ───────────────────────────────────────────────────
        DeviceCommand.Action.SET_VOLUME -> {
            val pct = cmd.intValue().coerceIn(0, 100)
            hub.setVolumePercent(pct).describe(
                target = "volume",
                applied = { "громкость → $pct%" },
                delegated = { level -> "громкость: открыла ${level.ruName}" },
            )
        }

        DeviceCommand.Action.VOLUME_UP -> {
            val step = cmd.intValue().coerceIn(1, 50)
            hub.adjustVolume(step).describe(
                target = "volume",
                applied = { "громкость +$step%" },
                delegated = { level -> "громкость: открыла ${level.ruName}" },
            )
        }

        DeviceCommand.Action.VOLUME_DOWN -> {
            val step = cmd.intValue().coerceIn(1, 50)
            hub.adjustVolume(-step).describe(
                target = "volume",
                applied = { "громкость −$step%" },
                delegated = { level -> "громкость: открыла ${level.ruName}" },
            )
        }

        // ── Фонарик ─────────────────────────────────────────────────────
        DeviceCommand.Action.SET_FLASHLIGHT -> {
            val on = cmd.boolValue()
            hub.setFlashlight(on).describe(
                target = "flashlight",
                applied = { if (on) "фонарик включён" else "фонарик выключен" },
                delegated = { level -> "фонарик недоступен, открыла ${level.ruName}" },
            )
        }

        // ── Таймер ──────────────────────────────────────────────────────
        DeviceCommand.Action.SET_TIMER -> {
            val seconds = cmd.intValue().coerceAtLeast(1)
            val launched = launchSafely(
                Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
            )
            if (launched) {
                "таймер на ${formatDuration(seconds)}"
            } else {
                "таймер: на устройстве нет приложения часов"
            }
        }

        // ── Будильник ───────────────────────────────────────────────────
        DeviceCommand.Action.SET_ALARM -> {
            val time = cmd.value?.toString() ?: "07:00"
            val parts = time.split(":").mapNotNull { it.toIntOrNull() }
            val hour = parts.getOrElse(0) { 7 }.coerceIn(0, 23)
            val minute = parts.getOrElse(1) { 0 }.coerceIn(0, 59)
            val launched = launchSafely(
                Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
            )
            if (launched) "будильник на %02d:%02d".format(hour, minute)
            else "будильник: на устройстве нет приложения часов"
        }

        DeviceCommand.Action.CANCEL_ALARMS -> {
            val launched = launchSafely(
                Intent(AlarmClock.ACTION_DISMISS_ALARM)
                    .putExtra(
                        AlarmClock.EXTRA_ALARM_SEARCH_MODE,
                        AlarmClock.ALARM_SEARCH_MODE_ALL,
                    ),
            )
            if (launched) "будильники отключены" else "не нашла приложение часов"
        }

        // ── Приложения и экраны ─────────────────────────────────────────
        DeviceCommand.Action.OPEN_APP -> {
            val query = cmd.value?.toString().orEmpty()
            if (query.isBlank()) {
                "open_app: не указано приложение"
            } else {
                openAppByName(query)
            }
        }

        DeviceCommand.Action.OPEN_SETTINGS -> {
            val section = cmd.value?.toString()?.lowercase().orEmpty()
            when (section) {
                "wifi" -> hub.openWifiSettings()
                "bluetooth" -> hub.openBluetoothSettings()
                "display", "brightness" -> hub.openDisplaySettings()
                "sound", "volume" -> hub.openSoundSettings()
                "location" -> hub.openLocationSettings()
                "notifications" -> hub.openNotificationSettings()
                else -> hub.openMainSettings()
            }
            "открыты настройки${if (section.isBlank()) "" else ": $section"}"
        }

        DeviceCommand.Action.OPEN_NOTIFICATION_SETTINGS -> {
            hub.openNotificationSettings()
            "открыты настройки уведомлений"
        }

        DeviceCommand.Action.OPEN_BATTERY_SETTINGS -> {
            if (hub.isBatteryOptimizationIgnored()) {
                hub.openAppInfo(context.packageName)
                "открыты настройки приложения"
            } else {
                hub.requestIgnoreBatteryOptimizations()
                "открыт экран оптимизации батареи"
            }
        }

        // ── Поиск и звонок ──────────────────────────────────────────────
        DeviceCommand.Action.WEB_SEARCH -> {
            val query = cmd.value?.toString().orEmpty()
            if (query.isBlank()) {
                "web_search: нет запроса"
            } else {
                searchWeb(query)
            }
        }

        DeviceCommand.Action.MAKE_CALL -> {
            val raw = cmd.value?.toString().orEmpty()
            if (hub.openDialer(raw)) "открыт набор номера $raw"
            else "не похоже на телефонный номер"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ВСПОМОГАТЕЛЬНОЕ
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Открывает приложение по человеческому имени или пакету.
     *
     * Сначала пробуем имя пакета напрямую, потом — поиск обработчика по
     * `ACTION_MAIN` среди установленных приложений: так работает открытие
     * любого приложения, а не только тех, что перечислены в каталоге.
     * Раньше неизвестное приложение всегда уходило в Play Market, даже если
     * оно уже установлено.
     */
    private suspend fun openAppByName(query: String) = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val trimmed = query.trim()

        val direct = pm.getLaunchIntentForPackage(trimmed)
        if (direct != null && launchSafely(direct)) {
            return@withContext "открыто: $trimmed"
        }

        val lower = trimmed.lowercase()
        val match = runCatching {
            pm.getInstalledApplications(0).firstOrNull { info ->
                val label = runCatching {
                    pm.getApplicationLabel(info).toString().lowercase()
                }.getOrDefault("")
                label == lower || (label.isNotBlank() && label.contains(lower))
            }
        }.getOrNull()

        if (match != null) {
            val intent = pm.getLaunchIntentForPackage(match.packageName)
            if (intent != null && launchSafely(intent)) {
                val label = runCatching { pm.getApplicationLabel(match).toString() }
                    .getOrDefault(match.packageName)
                return@withContext "открыто: $label"
            }
        }

        // Финальный фоллбэк — магазин приложений по поисковой строке.
        val market = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("market://search?q=$trimmed"),
        )
        if (launchSafely(market)) {
            "приложение «$trimmed» не установлено, открыла магазин"
        } else {
            "приложение «$trimmed» не найдено"
        }
    }

    /**
     * Веб-поиск.
     *
     * `ACTION_WEB_SEARCH` не имеет обработчика на устройствах без сервисов
     * Google — там он молча ничего не делал. Поэтому проверяем наличие
     * обработчика и падаем в обычный `ACTION_VIEW` с поисковым URL: браузер
     * есть практически всегда.
     */
    private fun searchWeb(query: String): String {
        val encoded = android.net.Uri.encode(query)
        val systemSearch = Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(android.app.SearchManager.QUERY, query)
        if (launchSafely(systemSearch)) return "поиск: $query"

        val browser = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("https://duckduckgo.com/?q=$encoded"),
        )
        return if (launchSafely(browser)) "поиск в браузере: $query"
        else "не нашла, чем выполнить поиск"
    }

    /**
     * Запускает интент, проверяя, что под него есть обработчик.
     *
     * Без этой проверки `startActivity` на устройстве без нужного приложения
     * бросает `ActivityNotFoundException` — а в старом коде результата
     * проверки не было вообще, и команда «выполнялась», ничего не сделав.
     */
    private fun launchSafely(intent: Intent): Boolean = runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) return false
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    /** «90» → «1 мин 30 с»: человеку понятнее, чем «90 секунд». */
    private fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        val rest = seconds % 60
        return when {
            minutes == 0 -> "$rest с"
            rest == 0 -> "$minutes мин"
            else -> "$minutes мин $rest с"
        }
    }

    /**
     * Превращает [ControlResult] в строку лога/промпта с учётом уровня доступа.
     *
     * Любой прямой успех → [applied]. Делегирование в системный UI → не
     * «ошибка», а [delegated] с честным упоминанием, что делать пользователю.
     * Отказ → причина без прикрас.
     */
    private inline fun ControlResult.describe(
        target: String,
        applied: () -> String,
        delegated: (ControlAccess) -> String,
    ): String = when (this) {
        is ControlResult.Applied ->
            if (state) applied() else "$target: система вернула прежнее значение"
        is ControlResult.Delegated -> delegated(level)
        is ControlResult.Unsupported -> "$target: $hint"
    }

    /** Русское название уровня доступа — для честных сообщений пользователю. */
    private val ControlAccess.ruName: String
        get() = when (this) {
            ControlAccess.DIRECT -> "прямое переключение"
            ControlAccess.PANEL -> "панель быстрых настроек"
            ControlAccess.SCREEN -> "системные настройки"
        }

    // ── Хелперы для извлечения значений ──────────────────────────────────

    private fun DeviceCommand.boolValue(): Boolean = when (val v = value) {
        is Boolean -> v
        is String -> v.lowercase() in TRUE_WORDS
        is Number -> v.toInt() != 0
        else -> false
    }

    private fun DeviceCommand.intValue(): Int = when (val v = value) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: 0
        else -> 0
    }

    private companion object {
        val TRUE_WORDS = setOf("true", "on", "вкл", "включить", "включи", "1", "да")
    }
}
