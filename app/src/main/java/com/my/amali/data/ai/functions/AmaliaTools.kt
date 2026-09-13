package com.my.amali.data.ai

import android.app.AlarmManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings as SystemSettings
import com.my.amali.system.DeviceCommandExecutor
import com.my.amali.system.SystemControllerHub
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Набор «рук» Амалии — конкретных инструментов, которые она может вызвать.
 *
 * Каждая функция — это пара (определение для LLM, обработчик для рантайма).
 * Определение содержит имя, описание и JSON Schema параметров; описание
 * критично: LLM опирается на него, решая, когда вызывать инструмент.
 *
 * ## Персона инструментов
 *
 * Амалия — ироничная восемнадцатилетняя девчонка, поэтому описания
 * инструментов сделаны конкретными и без воды: модель не должна думать,
 * что `set_wifi` умеет готовить кофе. Заодно имена остаются стабильными
 * между итерациями модели, чтобы handler не разъезжался со схемой.
 */
class AmaliaTools(
    private val context: Context,
    private val hub: SystemControllerHub,
    private val commandExecutor: DeviceCommandExecutor,
) {

    /** Все инструменты, которые видит LLM. Имена уникальны. */
    val all: List<Pair<ToolDefinition, ToolHandler>> = listOf(
        // ── Устройство: Wi-Fi / Bluetooth / яркость / громкость ──────────
        setWifi(),
        setBluetooth(),
        setBrightness(),
        setVolume(),

        // ── Устройство: фонарик / таймер / будильник ──────────────────────
        setFlashlight(),
        setTimer(),
        setAlarm(),

        // ── Системные действия: открыть / найти ──────────────────────────
        openApp(),
        openSettings(),
        webSearch(),
        makePhoneCall(),
        sendSms(),

        // ── Мультимедиа ────────────────────────────────────────────────────
        takePhoto(),
        openYouTube(),
        openCamera(),

        // ── Информация для LLM ───────────────────────────────────────────
        getCurrentTime(),
        getDeviceStatus(),
        getBatteryLevel(),
        getLocationStatus(),
        getWeather(),

        // ── Память разговоров ─────────────────────────────────────────────
        searchHistory(),
        getRecentConversations(),
        clearHistory(),

        // ── Настройки ассистента ──────────────────────────────────────────
        changeLanguage(),
        toggleAutoListen(),
    )

    // ════════════════════════════════════════════════════════════════════
    //  Устройство: Wi-Fi / Bluetooth / яркость / громкость
    // ════════════════════════════════════════════════════════════════════

    private fun setWifi(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "set_wifi",
            description = "Включает или выключает Wi-Fi на устройстве. " +
                "На Android 13+ прямой переключатель закрыт системой — покажет системный экран.",
            parameters = listOf(
                ToolParameter(
                    name = "enabled",
                    type = ToolParameter.JsonType.BOOLEAN,
                    description = "true = включить Wi-Fi, false = выключить.",
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val on = ToolArgs.bool(args, "enabled")
            val ok = hub.setWifiEnabled(on)
            if (ok) {
                ToolResult(def.name, def.name, ok = true, output = """{"wifi":${if (on) "true" else "false"}}""")
            } else {
                ToolResult(
                    def.name, def.name, ok = false,
                    output = "",
                    errorMessage = "Wi-Fi нельзя переключить напрямую (Android 13+ требует системный экран).",
                )
            }
        }
        return def to handler
    }

    private fun setBluetooth(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "set_bluetooth",
            description = "Включает или выключает Bluetooth. " +
                "На Android 12+ требует системный экран.",
            parameters = listOf(
                ToolParameter(
                    name = "enabled",
                    type = ToolParameter.JsonType.BOOLEAN,
                    description = "true = включить Bluetooth, false = выключить.",
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val on = ToolArgs.bool(args, "enabled")
            val ok = hub.setBluetoothEnabled(on)
            if (ok) {
                ToolResult(def.name, def.name, ok = true, output = """{"bluetooth":${if (on) "true" else "false"}}""")
            } else {
                ToolResult(
                    def.name, def.name, ok = false,
                    output = "",
                    errorMessage = "Bluetooth нельзя переключить напрямую (Android 12+ требует системный экран).",
                )
            }
        }
        return def to handler
    }

    private fun setBrightness(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "set_brightness",
            description = "Устанавливает яркость экрана в процентах 0..100. " +
                "Требует разрешения WRITE_SETTINGS — если его нет, вернёт ошибку.",
            parameters = listOf(
                ToolParameter(
                    name = "percent",
                    type = ToolParameter.JsonType.INTEGER,
                    description = "Уровень яркости от 0 до 100 (0 = почти темно, 100 = максимум).",
                    min = 0.0, max = 100.0,
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val pct = ToolArgs.int(args, "percent", default = 50).coerceIn(0, 100)
            if (!hub.canWriteBrightness()) {
                // Открываем системный экран разрешения и сообщаем модели, что
                // она должна попросить пользователя выдать доступ вручную.
                hub.openBrightnessSettingsScreen()
                return@ToolHandler ToolResult(
                    def.name, def.name, ok = false, output = "",
                    errorMessage = "WRITE_SETTINGS не выдан — открыла системный экран настроек.",
                )
            }
            val actualLevel = (pct * 255) / 100
            val granted = hub.setBrightness(actualLevel)
            ToolResult(
                def.name, def.name, ok = granted,
                output = """{"brightness_percent":$pct,"system_level":$actualLevel}""",
                errorMessage = if (granted) null else "WRITE_SETTINGS не выдан.",
            )
        }
        return def to handler
    }

    private fun setVolume(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "set_volume",
            description = "Устанавливает громкость мультимедиа в процентах 0..100.",
            parameters = listOf(
                ToolParameter(
                    name = "percent",
                    type = ToolParameter.JsonType.INTEGER,
                    description = "Громкость 0..100.",
                    min = 0.0, max = 100.0,
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val pct = ToolArgs.int(args, "percent", default = 50).coerceIn(0, 100)
            val maxVol = hub.mediaVolumeMax()
            val applied = hub.setVolume((pct * maxVol) / 100)
            // Возвращаем фактически установленный уровень, чтобы LLM не выдумывала.
            ToolResult(
                def.name, def.name, ok = true,
                output = """{"volume_percent":$pct,"system_level":$applied,"max":$maxVol}""",
            )
        }
        return def to handler
    }

    // ════════════════════════════════════════════════════════════════════
    //  Устройство: фонарик / таймер / будильник
    // ════════════════════════════════════════════════════════════════════

    private fun setFlashlight(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "set_flashlight",
            description = "Включает или выключает фонарик устройства. " +
                "Требует разрешения CAMERA, если система запросит.",
            parameters = listOf(
                ToolParameter(
                    name = "enabled",
                    type = ToolParameter.JsonType.BOOLEAN,
                    description = "true = включить фонарик, false = выключить.",
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val on = ToolArgs.bool(args, "enabled")
            val result = runCatching {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = cm.cameraIdList.firstOrNull()
                    ?: throw IllegalStateException("на устройстве нет камеры с фонариком")
                cm.setTorchMode(cameraId, on)
                true
            }
            if (result.getOrDefault(false)) {
                ToolResult(def.name, def.name, ok = true, output = """{"flashlight":${if (on) "true" else "false"}}""")
            } else {
                ToolResult(
                    def.name, def.name, ok = false, output = "",
                    errorMessage = "Не удалось переключить фонарик: ${result.exceptionOrNull()?.message}",
                )
            }
        }
        return def to handler
    }

    private fun setTimer(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "set_timer",
            description = "Ставит таймер обратного отсчёта. " +
                "Значение в секундах (300 = пять минут, 600 = десять).",
            parameters = listOf(
                ToolParameter(
                    name = "seconds",
                    type = ToolParameter.JsonType.INTEGER,
                    description = "Длительность таймера в секундах (минимум 1).",
                    min = 1.0, max = 86400.0,
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val seconds = ToolArgs.int(args, "seconds", default = 60).coerceAtLeast(1)
            val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            if (launched) {
                ToolResult(
                    def.name, def.name, ok = true,
                    output = """{"timer_seconds":$seconds,"minutes":${seconds / 60}}""",
                )
            } else {
                ToolResult(
                    def.name, def.name, ok = false, output = "",
                    errorMessage = "Не удалось открыть приложение таймера.",
                )
            }
        }
        return def to handler
    }

    private fun setAlarm(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "set_alarm",
            description = "Ставит будильник на указанное время. " +
                "Формат параметра time — 'HH:mm' (24-часовой).",
            parameters = listOf(
                ToolParameter(
                    name = "time",
                    type = ToolParameter.JsonType.STRING,
                    description = "Время будильника в формате 'HH:mm' (например, '07:30' или '22:15').",
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val raw = ToolArgs.string(args, "time", default = "07:00")
            val parts = raw.split(":").mapNotNull { it.toIntOrNull() }
            val hour = parts.getOrElse(0) { 7 }.coerceIn(0, 23)
            val minute = parts.getOrElse(1) { 0 }.coerceIn(0, 59)
            val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            if (launched) {
                val now = LocalDateTime.now()
                val alarmTime = now.toLocalDate().atTime(hour, minute)
                val deltaMin = if (alarmTime.isAfter(now)) {
                    java.time.Duration.between(now, alarmTime).toMinutes()
                } else {
                    java.time.Duration.between(now, alarmTime.plusDays(1)).toMinutes()
                }
                ToolResult(
                    def.name, def.name, ok = true,
                    output = """{"alarm_time":"${"%02d:%02d".format(hour, minute)}","minutes_until":$deltaMin}""",
                )
            } else {
                ToolResult(
                    def.name, def.name, ok = false, output = "",
                    errorMessage = "Не удалось открыть приложение будильника.",
                )
            }
        }
        return def to handler
    }

    // ════════════════════════════════════════════════════════════════════
    //  Системные действия: открыть / найти / позвонить / написать
    // ════════════════════════════════════════════════════════════════════

    private fun openApp(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "open_app",
            description = "Открывает установленное приложение по имени или пакету. " +
                "Если точного названия нет, можно передать пакет (например 'com.android.chrome').",
            parameters = listOf(
                ToolParameter(
                    name = "name",
                    type = ToolParameter.JsonType.STRING,
                    description = "Имя приложения ('YouTube', 'Chrome') или его пакет ('com.google.android.youtube'). " +
                        "Если передано имя — выбирается наиболее вероятный пакет.",
                ),
            ),
        )
        val apps = AppCatalog.index(context.packageManager)
        val handler = ToolHandler { args ->
            val name = ToolArgs.string(args, "name").trim()
            if (name.isEmpty()) {
                return@ToolHandler ToolResult(
                    def.name, def.name, ok = false, output = "",
                    errorMessage = "Не передано имя приложения.",
                )
            }
            val resolved = apps.resolve(name)
            if (resolved == null) {
                return@ToolHandler ToolResult(
                    def.name, def.name, ok = false, output = "",
                    errorMessage = "Не нашла приложение '$name' на устройстве.",
                )
            }
            val intent = context.packageManager.getLaunchIntentForPackage(resolved.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=${resolved.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            if (launched) {
                ToolResult(
                    def.name, def.name, ok = true,
                    output = """{"package":"${resolved.packageName}","label":"${resolved.label.replace("\"", "'")}"}""",
                )
            } else {
                ToolResult(
                    def.name, def.name, ok = false, output = "",
                    errorMessage = "Не удалось запустить '${resolved.label}'.",
                )
            }
        }
        return def to handler
    }

    private fun openSettings(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "open_settings",
            description = "Открывает системный экран настроек. " +
                "Можно указать конкретную секцию ('display', 'sound', 'apps').",
            parameters = listOf(
                ToolParameter(
                    name = "section",
                    type = ToolParameter.JsonType.STRING,
                    description = "Опциональная секция: display / sound / apps / network / privacy / location.",
                    required = false,
                    enumValues = listOf("display", "sound", "apps", "network", "privacy", "location", "all"),
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val section = ToolArgs.string(args, "section").lowercase()
            val action = when (section) {
                "display" -> SystemSettings.ACTION_DISPLAY_SETTINGS
                "sound" -> SystemSettings.ACTION_SOUND_SETTINGS
                "apps" -> SystemSettings.ACTION_APPLICATION_SETTINGS
                "network" -> SystemSettings.ACTION_WIRELESS_SETTINGS
                "privacy" -> SystemSettings.ACTION_PRIVACY_SETTINGS
                "location" -> SystemSettings.ACTION_LOCATION_SOURCE_SETTINGS
                else -> SystemSettings.ACTION_SETTINGS
            }
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            ToolResult(
                def.name, def.name, ok = launched,
                output = """{"section":"${if (section.isEmpty()) "all" else section}"}""",
                errorMessage = if (launched) null else "Не удалось открыть настройки.",
            )
        }
        return def to handler
    }

    private fun webSearch(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "web_search",
            description = "Запускает системный веб-поиск по запросу. " +
                "Открывает выбранное пользователем приложение поиска.",
            parameters = listOf(
                ToolParameter(
                    name = "query",
                    type = ToolParameter.JsonType.STRING,
                    description = "Поисковый запрос (на любом языке).",
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val query = ToolArgs.string(args, "query").trim()
            if (query.isEmpty()) {
                return@ToolHandler ToolResult(
                    def.name, def.name, ok = false, output = "",
                    errorMessage = "Пустой поисковый запрос.",
                )
            }
            val intent = Intent(Intent.ACTION_WEB_SEARCH)
                .putExtra(SearchManager.QUERY, query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            if (launched) {
                ToolResult(def.name, def.name, ok = true, output = """{"query":"${query.replace("\"", "'")}"}""")
            } else {
                ToolResult(def.name, def.name, ok = false, output = "",
                    errorMessage = "Не удалось запустить поиск.")
            }
        }
        return def to handler
    }

    private fun makePhoneCall(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "make_call",
            description = "Открывает номеронабиратель с уже набранным номером. " +
                "Прямой звонок требует CALL_PHONE; мы ограничиваемся диалогом набора.",
            parameters = listOf(
                ToolParameter(
                    name = "phone_number",
                    type = ToolParameter.JsonType.STRING,
                    description = "Номер телефона в международном или локальном формате.",
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val raw = ToolArgs.string(args, "phone_number").trim()
            if (raw.isEmpty() || raw.length < 3) {
                return@ToolHandler ToolResult(
                    def.name, def.name, ok = false, output = "",
                    errorMessage = "Не похоже на телефонный номер.",
                )
            }
            val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$raw"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            ToolResult(
                def.name, def.name, ok = launched,
                output = """{"phone_number":"$raw","mode":"dial"}""",
                errorMessage = if (launched) null else "Не удалось открыть набор номера.",
            )
        }
        return def to handler
    }

    private fun sendSms(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "send_sms",
            description = "Открывает приложение SMS с предзаполненным номером (опционально) и текстом.",
            parameters = listOf(
                ToolParameter(
                    name = "phone_number",
                    type = ToolParameter.JsonType.STRING,
                    description = "Номер получателя. Можно опустить — пользователь выберет сам.",
                    required = false,
                ),
                ToolParameter(
                    name = "text",
                    type = ToolParameter.JsonType.STRING,
                    description = "Текст сообщения.",
                    required = false,
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val phone = ToolArgs.string(args, "phone_number")
            val text = ToolArgs.string(args, "text")
            val uri = if (phone.isNotBlank()) android.net.Uri.parse("smsto:$phone") else android.net.Uri.parse("smsto:")
            val intent = Intent(Intent.ACTION_SENDTO, uri)
                .putExtra("sms_body", text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            ToolResult(
                def.name, def.name, ok = launched,
                output = """{"phone_number":"$phone","has_text":${text.isNotEmpty()}}""",
                errorMessage = if (launched) null else "Не удалось открыть SMS.",
            )
        }
        return def to handler
    }

    // ════════════════════════════════════════════════════════════════════
    //  Мультимедиа
    // ════════════════════════════════════════════════════════════════════

    private fun takePhoto(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "take_photo",
            description = "Открывает системное приложение камеры в режиме фото.",
            parameters = emptyList(),
        )
        val handler = ToolHandler {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            ToolResult(
                def.name, def.name, ok = launched, output = """{"camera_opened":$launched}""",
                errorMessage = if (launched) null else "Камера не запустилась.",
            )
        }
        return def to handler
    }

    private fun openYouTube(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "open_youtube",
            description = "Открывает приложение YouTube, если оно установлено.",
            parameters = emptyList(),
        )
        val handler = ToolHandler {
            val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://m.youtube.com"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            ToolResult(
                def.name, def.name, ok = launched, output = """{"youtube_opened":$launched}""",
                errorMessage = if (launched) null else "YouTube не установлен.",
            )
        }
        return def to handler
    }

    private fun openCamera(): Pair<ToolDefinition, ToolHandler> = takePhoto() // алиас

    // ════════════════════════════════════════════════════════════════════
    //  Информация для LLM
    // ════════════════════════════════════════════════════════════════════

    private fun getCurrentTime(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "get_current_time",
            description = "Возвращает текущее локальное время устройства в формате HH:mm:ss и дату.",
            parameters = emptyList(),
        )
        val handler = ToolHandler {
            val now = LocalDateTime.now()
            val time = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            val date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val dow = now.format(DateTimeFormatter.ofPattern("EEEE"))
            ToolResult(
                def.name, def.name, ok = true,
                output = """{"time":"$time","date":"$date","weekday":"$dow"}""",
            )
        }
        return def to handler
    }

    private fun getDeviceStatus(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "get_device_status",
            description = "Считывает актуальное состояние Wi-Fi, Bluetooth, яркости, громкости, " +
                "геолокации и разрешений на уведомления/контакты.",
            parameters = emptyList(),
        )
        val handler = ToolHandler {
            val snapshot = hub.status.value
            ToolResult(
                def.name, def.name, ok = true,
                output = buildString {
                    append("""{"wifi":${snapshot.wifiEnabled},"bluetooth":${snapshot.bluetoothEnabled},""")
                    append("\"brightness_percent\":${(snapshot.brightnessLevel * 100) / 255},")
                    append("\"volume_percent\":${snapshot.volumeLevel},")
                    append("\"location\":${snapshot.locationEnabled},")
                    append("\"contacts_permission\":${snapshot.hasContactsPermission},")
                    append("\"notifications_permission\":${snapshot.hasNotificationPermission}}")
                },
            )
        }
        return def to handler
    }

    private fun getBatteryLevel(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "get_battery_level",
            description = "Возвращает уровень заряда батареи в процентах и статус зарядки.",
            parameters = emptyList(),
        )
        val handler = ToolHandler {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            } else 0
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val plugged = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_PLUGGED)
            val chargingSource = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                0 -> "None"
                else -> "Unknown"
            }
            ToolResult(
                def.name, def.name, ok = true,
                output = """{"level":$level,"charging":$charging,"source":"$chargingSource"}""",
            )
        }
        return def to handler
    }

    private fun getLocationStatus(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "get_location_status",
            description = "Сообщает, включены ли службы геолокации на устройстве. " +
                "Координаты не возвращает — только флаг.",
            parameters = emptyList(),
        )
        val handler = ToolHandler {
            val enabled = hub.isLocationEnabled()
            ToolResult(
                def.name, def.name, ok = true,
                output = """{"location_enabled":$enabled}""",
            )
        }
        return def to handler
    }

    private fun getWeather(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "get_weather",
            description = "Возвращает демо-прогноз погоды. " +
                "В продакшене подключается к внешнему API, но сейчас — мок без сети.",
            parameters = listOf(
                ToolParameter(
                    name = "city",
                    type = ToolParameter.JsonType.STRING,
                    description = "Опциональный город. Сейчас игнорируется.",
                    required = false,
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val city = ToolArgs.string(args, "city")
            val hour = LocalTime.now().hour
            val temp = 12 + (hour % 8)
            val cond = when (hour % 4) {
                0 -> "ясно"
                1 -> "облачно"
                2 -> "небольшой дождь"
                else -> "переменная облачность"
            }
            val cityLabel = if (city.isNotEmpty()) city else "твой город"
            ToolResult(
                def.name, def.name, ok = true,
                output = """{"city":"$cityLabel","temperature_c":$temp,"condition":"$cond","source":"demo"}""",
            )
        }
        return def to handler
    }

    // ════════════════════════════════════════════════════════════════════
    //  Память разговоров
    // ════════════════════════════════════════════════════════════════════

    /**
     * Поиск по истории разговоров и сводка последних бесед.
     *
     * Реализация подключается через [commandExecutor] в AmaliaToolsHolder —
     * сами хелперы ниже — это пара (определение, lazy-handler фабрика),
     * они не должны лезть в репозиторий напрямую, чтобы не зависнуть
     * от цикла инициализации ServiceLocator.
     */
    var searchHistoryProvider: (suspend (String) -> String)? = null
    var recentConversationsProvider: (suspend (Int) -> String)? = null
    var clearHistoryProvider: (suspend () -> String)? = null
    var settingsChangeProvider: (suspend (String, String) -> String)? = null

    private fun searchHistory(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "search_history",
            description = "Ищет прошлые разговоры по ключевому слову и возвращает краткие сводки.",
            parameters = listOf(
                ToolParameter(
                    name = "query",
                    type = ToolParameter.JsonType.STRING,
                    description = "Поисковый запрос (на русском или английском).",
                ),
                ToolParameter(
                    name = "limit",
                    type = ToolParameter.JsonType.INTEGER,
                    description = "Сколько разговоров вернуть (по умолчанию 5).",
                    required = false, min = 1.0, max = 20.0,
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val q = ToolArgs.string(args, "query").trim()
            val limit = ToolArgs.int(args, "limit", default = 5)
            if (q.isEmpty()) {
                return@ToolHandler ToolResult(
                    def.name, def.name, ok = false, output = "",
                    errorMessage = "Пустой запрос поиска.",
                )
            }
            val provider = searchHistoryProvider
            if (provider == null) {
                return@ToolHandler ToolResult(
                    def.name, def.name, ok = false, output = "",
                    errorMessage = "История ещё не подключена.",
                )
            }
            val out = provider(q).take(8000)
            ToolResult(def.name, def.name, ok = true, output = out)
        }
        return def to handler
    }

    private fun getRecentConversations(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "get_recent_conversations",
            description = "Возвращает заголовки и краткое превью N последних разговоров.",
            parameters = listOf(
                ToolParameter(
                    name = "limit",
                    type = ToolParameter.JsonType.INTEGER,
                    description = "Сколько разговоров вернуть (по умолчанию 5).",
                    required = false, min = 1.0, max = 20.0,
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val limit = ToolArgs.int(args, "limit", default = 5).coerceIn(1, 20)
            val provider = recentConversationsProvider
            if (provider == null) {
                return@ToolHandler ToolResult(
                    def.name, def.name, ok = false, output = "",
                    errorMessage = "История ещё не подключена.",
                )
            }
            val out = provider(limit).take(8000)
            ToolResult(def.name, def.name, ok = true, output = out)
        }
        return def to handler
    }

    private fun clearHistory(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "clear_history",
            description = "Полностью очищает сохранённую историю разговоров. " +
                "Действие необратимо — возвращает, сколько разговоров удалено.",
            parameters = emptyList(),
        )
        val handler = ToolHandler {
            val provider = clearHistoryProvider
            if (provider == null) {
                return@ToolHandler ToolResult(
                    def.name, def.name, ok = false, output = "",
                    errorMessage = "История ещё не подключена.",
                )
            }
            val report = provider()
            ToolResult(def.name, def.name, ok = true, output = report)
        }
        return def to handler
    }

    // ════════════════════════════════════════════════════════════════════
    //  Настройки ассистента
    // ════════════════════════════════════════════════════════════════════

    private fun changeLanguage(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "change_language",
            description = "Меняет язык интерфейса и распознавания. " +
                "Поддерживает 'ru', 'en', 'system' (как в системе).",
            parameters = listOf(
                ToolParameter(
                    name = "language",
                    type = ToolParameter.JsonType.STRING,
                    description = "Код языка: ru, en, es, de, fr, ja, zh, hi, ar, system.",
                    enumValues = listOf("ru", "en", "es", "de", "fr", "ja", "zh", "hi", "ar", "system"),
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val lang = ToolArgs.string(args, "language").lowercase().trim()
            val provider = settingsChangeProvider
            if (provider == null) {
                return@ToolHandler ToolResult(
                    def.name, def.name, ok = false, output = "",
                    errorMessage = "Настройки ещё не подключены.",
                )
            }
            ToolResult(
                def.name, def.name, ok = true,
                output = provider("language", lang),
            )
        }
        return def to handler
    }

    private fun toggleAutoListen(): Pair<ToolDefinition, ToolHandler> {
        val def = ToolDefinition(
            name = "toggle_auto_listen",
            description = "Включает или выключает автоматическое прослушивание " +
                "после ответа (hands-free режим).",
            parameters = listOf(
                ToolParameter(
                    name = "enabled",
                    type = ToolParameter.JsonType.BOOLEAN,
                    description = "true = включить hands-free, false = выключить.",
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val on = ToolArgs.bool(args, "enabled")
            val provider = settingsChangeProvider
            if (provider == null) {
                return@ToolHandler ToolResult(
                    def.name, def.name, ok = false, output = "",
                    errorMessage = "Настройки ещё не подключены.",
                )
            }
            ToolResult(
                def.name, def.name, ok = true,
                output = provider("auto_listen", on.toString()),
            )
        }
        return def to handler
    }
}

/**
 * Лёгкий локальный каталог приложений: маппит ключевые слова на пакеты.
 *
 * Нужен, чтобы LLM могла сказать «открой ютуб», а не угадывать
 * `com.google.android.youtube`. Полный список установленных пакетов
 * мы в контекст LLM не отдаём — слишком шумно.
 */
private class AppCatalog {

    data class Entry(val packageName: String, val label: String, val keywords: List<String>)

    private val entries: List<Entry> = listOf(
        Entry("com.google.android.youtube", "YouTube", listOf("ютуб", "youtube", "ют")),
        Entry("com.google.android.apps.maps", "Google Maps", listOf("карты", "maps", "google maps")),
        Entry("com.android.chrome", "Chrome", listOf("хром", "chrome", "браузер", "browser")),
        Entry("org.telegram.messenger", "Telegram", listOf("телеграм", "telegram", "тг")),
        Entry("com.whatsapp", "WhatsApp", listOf("ватсап", "whatsapp")),
        Entry("com.instagram.android", "Instagram", listOf("инстаграм", "instagram", "инста")),
        Entry("com.spotify.music", "Spotify", listOf("спотифай", "spotify")),
        Entry("com.google.android.gm", "Gmail", listOf("почта", "gmail", "мейл")),
        Entry("com.google.android.calendar", "Google Calendar", listOf("календарь", "calendar")),
        Entry("com.android.settings", "Settings", listOf("настройки", "settings")),
        Entry("com.android.camera", "Camera", listOf("камера", "camera")),
        Entry("com.google.android.apps.photos", "Google Photos", listOf("фото", "photos", "фотографии")),
        Entry("com.google.android.dialer", "Phone", listOf("телефон", "звонки", "phone")),
        Entry("com.google.android.contacts", "Contacts", listOf("контакты", "contacts")),
        Entry("com.google.android.keep", "Google Keep", listOf("заметки", "keep", "записки")),
        Entry("com.netflix.mediaclient", "Netflix", listOf("нетфликс", "netflix")),
        Entry("ru.yandex.searchplugin", "Яндекс", listOf("яндекс", "yandex")),
        Entry("com.vkontakte.android", "VK", listOf("вк", "vk", "вконтакте")),
    )

    fun resolve(name: String): Entry? {
        val low = name.trim().lowercase()
        if (low.isEmpty()) return null
        // Прямое совпадение по package
        entries.firstOrNull { it.packageName.equals(low, ignoreCase = true) }?.let { return it }
        // По ключевым словам
        entries.firstOrNull { entry ->
            entry.keywords.any { low.contains(it) } || entry.label.lowercase().contains(low)
        }?.let { return it }
        // По подстроке package (fallback для редких приложений)
        return null
    }

    companion object {
        fun index(pm: android.content.pm.PackageManager): AppCatalog = AppCatalog()
    }
}
