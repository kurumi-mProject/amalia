package com.my.amali.data.ai

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings as SystemSettings
import com.my.amali.system.SystemControllerHub
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Набор «рук» Амалии — конкретных инструментов, которые она может вызвать.
 *
 * Каждый инструмент — это [AmaliaTool] (определение для LLM + обработчик);
 * возвращает [ToolOutcome], а не [ToolResult], потому что идентификатор
 * вызова подставляет [ToolRegistry] и делать это в обработчиках запрещено
 * (раньше там по ошибке попадало имя инструмента, и Groq отвечал 400).
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
) {

    /**
     * Все инструменты, которые видит LLM.
     *
     * Имена уникальны — [ToolRegistry.of] отбрасывает дубликаты, но лучше
     * до такого не доводить: модель не узнает, какой инструмент выиграл.
     */
    val all: List<AmaliaTool> = listOf(
        // ── Устройство: Wi-Fi / Bluetooth / яркость / громкость ──────────
        setWifi(),
        setBluetooth(),
        setBrightness(),
        setVolume(),
        volumeUp(),
        volumeDown(),

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
    //  Мосты к репозиториям (подключаются в ServiceLocator.init)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Поиск по прошлым разговорам. Возвращает JSON-строку ≤ 8000 символов.
     * [ServiceLocator] подключает эту лямбду после своей инициализации,
     * потому что репозитории — lazy и зависят от appContext.
     */
    @Volatile
    var searchHistoryProvider: (suspend (String) -> String)? = null

    /** Сводка N последних разговоров. */
    @Volatile
    var recentConversationsProvider: (suspend (Int) -> String)? = null

    /** Полная очистка истории. Возвращает JSON со счётчиком удалённых. */
    @Volatile
    var clearHistoryProvider: (suspend () -> String)? = null

    /** Применение настройки (язык / автослушать / тема). */
    @Volatile
    var settingsChangeProvider: (suspend (String, String) -> String)? = null

    // ════════════════════════════════════════════════════════════════════
    //  Устройство: Wi-Fi / Bluetooth / яркость / громкость
    // ════════════════════════════════════════════════════════════════════

    private fun setWifi(): AmaliaTool {
        val def = ToolDefinition(
            name = "set_wifi",
            description = "Включает или выключает Wi-Fi на устройстве. " +
                "На Android 13+ прямой переключатель закрыт системой — " +
                "вернёт ошибку с явной причиной, чтобы Амалия открыла системный экран.",
            parameters = listOf(
                ToolParameter(
                    name = "enabled",
                    type = ToolParameter.JsonType.BOOLEAN,
                    description = "true = включить Wi-Fi, false = выключить.",
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val on = args.bool("enabled")
            val ok = hub.setWifiEnabled(on)
            if (ok) {
                ToolOutcome.json("wifi" to on, "source" to "system")
            } else {
                ToolOutcome.failed(
                    "Wi-Fi нельзя переключить напрямую на этом устройстве " +
                        "(Android 13+ требует системный экран).",
                )
            }
        }
        return AmaliaTool(def, handler)
    }

    private fun setBluetooth(): AmaliaTool {
        val def = ToolDefinition(
            name = "set_bluetooth",
            description = "Включает или выключает Bluetooth. " +
                "На Android 12+ прямой переключатель закрыт — " +
                "вернёт ошибку с явной причиной.",
            parameters = listOf(
                ToolParameter(
                    name = "enabled",
                    type = ToolParameter.JsonType.BOOLEAN,
                    description = "true = включить Bluetooth, false = выключить.",
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val on = args.bool("enabled")
            val ok = hub.setBluetoothEnabled(on)
            if (ok) {
                ToolOutcome.json("bluetooth" to on, "source" to "system")
            } else {
                ToolOutcome.failed(
                    "Bluetooth нельзя переключить напрямую на этом устройстве " +
                        "(Android 12+ требует системный экран).",
                )
            }
        }
        return AmaliaTool(def, handler)
    }

    private fun setBrightness(): AmaliaTool {
        val def = ToolDefinition(
            name = "set_brightness",
            description = "Устанавливает яркость экрана в процентах 0..100. " +
                "Требует разрешения WRITE_SETTINGS — если его нет, " +
                "вернёт ошибку и откроет системный экран.",
            parameters = listOf(
                ToolParameter(
                    name = "percent",
                    type = ToolParameter.JsonType.INTEGER,
                    description = "Уровень яркости 0..100 (0 = почти темно, 100 = максимум).",
                    min = 0.0, max = 100.0,
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val pct = args.int("percent", default = 50).coerceIn(0, 100)
            if (!hub.canWriteBrightness()) {
                hub.openBrightnessSettingsScreen()
                return@ToolHandler ToolOutcome.failed(
                    "WRITE_SETTINGS не выдан — открыла системный экран яркости, " +
                        "попроси пользователя дать доступ «Изменять системные настройки».",
                )
            }
            val actualLevel = (pct * 255) / 100
            val granted = hub.setBrightness(actualLevel)
            ToolOutcome.of(
                ok = granted,
                reasonIfFailed = "WRITE_SETTINGS отозван системой.",
                "brightness_percent" to pct,
                "system_level" to actualLevel,
            )
        }
        return AmaliaTool(def, handler)
    }

    private fun setVolume(): AmaliaTool {
        val def = ToolDefinition(
            name = "set_volume",
            description = "Устанавливает громкость мультимедиа в процентах 0..100. " +
                "Возвращает реально установленный уровень, чтобы модель не выдумывала.",
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
            val pct = args.int("percent", default = 50).coerceIn(0, 100)
            val maxVol = hub.mediaVolumeMax()
            val applied = hub.setVolume((pct * maxVol) / 100).coerceAtMost(maxVol)
            ToolOutcome.json(
                "volume_percent" to pct,
                "system_level" to applied,
                "max" to maxVol,
            )
        }
        return AmaliaTool(def, handler)
    }

    private fun volumeUp(): AmaliaTool {
        val def = ToolDefinition(
            name = "volume_up",
            description = "Увеличивает громкость мультимедиа на указанное количество процентов.",
            parameters = listOf(
                ToolParameter(
                    name = "step",
                    type = ToolParameter.JsonType.INTEGER,
                    description = "На сколько процентов увеличить (по умолчанию 10).",
                    required = false, min = 1.0, max = 50.0,
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val step = args.int("step", default = 10).coerceIn(1, 50)
            val maxVol = hub.mediaVolumeMax()
            val current = hub.currentVolume()
            val currentPct = current * 100 / maxVol
            val newPct = (currentPct + step).coerceIn(0, 100)
            val applied = hub.setVolume(newPct * maxVol / 100).coerceAtMost(maxVol)
            ToolOutcome.json("volume_percent" to newPct, "system_level" to applied)
        }
        return AmaliaTool(def, handler)
    }

    private fun volumeDown(): AmaliaTool {
        val def = ToolDefinition(
            name = "volume_down",
            description = "Уменьшает громкость мультимедиа на указанное количество процентов.",
            parameters = listOf(
                ToolParameter(
                    name = "step",
                    type = ToolParameter.JsonType.INTEGER,
                    description = "На сколько процентов уменьшить (по умолчанию 10).",
                    required = false, min = 1.0, max = 50.0,
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val step = args.int("step", default = 10).coerceIn(1, 50)
            val maxVol = hub.mediaVolumeMax()
            val current = hub.currentVolume()
            val currentPct = current * 100 / maxVol
            val newPct = (currentPct - step).coerceIn(0, 100)
            val applied = hub.setVolume(newPct * maxVol / 100).coerceAtMost(maxVol)
            ToolOutcome.json("volume_percent" to newPct, "system_level" to applied)
        }
        return AmaliaTool(def, handler)
    }

    // ════════════════════════════════════════════════════════════════════
    //  Устройство: фонарик / таймер / будильник
    // ════════════════════════════════════════════════════════════════════

    private fun setFlashlight(): AmaliaTool {
        val def = ToolDefinition(
            name = "set_flashlight",
            description = "Включает или выключает фонарик устройства. " +
                "На устройствах без вспышки вернёт ошибку.",
            parameters = listOf(
                ToolParameter(
                    name = "enabled",
                    type = ToolParameter.JsonType.BOOLEAN,
                    description = "true = включить фонарик, false = выключить.",
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val on = args.bool("enabled")
            val failure = runCatching {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = cm.cameraIdList.firstOrNull()
                    ?: throw IllegalStateException("на устройстве нет камеры с фонариком")
                cm.setTorchMode(cameraId, on)
                true
            }
            if (failure.getOrDefault(false)) {
                ToolOutcome.json("flashlight" to on)
            } else {
                ToolOutcome.failed(
                    "Не удалось переключить фонарик: " +
                        (failure.exceptionOrNull()?.message ?: "неизвестная причина"),
                )
            }
        }
        return AmaliaTool(def, handler)
    }

    private fun setTimer(): AmaliaTool {
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
            val seconds = args.int("seconds", default = 60).coerceAtLeast(1)
            val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            if (launched) {
                ToolOutcome.json(
                    "timer_seconds" to seconds,
                    "minutes" to seconds / 60,
                )
            } else {
                ToolOutcome.failed("Не удалось открыть приложение таймера.")
            }
        }
        return AmaliaTool(def, handler)
    }

    private fun setAlarm(): AmaliaTool {
        val def = ToolDefinition(
            name = "set_alarm",
            description = "Ставит будильник на указанное время. " +
                "Формат параметра time — 'HH:mm' (24-часовой).",
            parameters = listOf(
                ToolParameter(
                    name = "time",
                    type = ToolParameter.JsonType.STRING,
                    description = "Время будильника в формате 'HH:mm' " +
                        "(например, '07:30' или '22:15').",
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val raw = args.string("time", default = "07:00").trim()
            val parts = raw.split(":").mapNotNull { it.toIntOrNull() }
            val hour = parts.getOrElse(0) { 7 }.coerceIn(0, 23)
            val minute = parts.getOrElse(1) { 0 }.coerceIn(0, 59)
            val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            if (!launched) {
                return@ToolHandler ToolOutcome.failed("Не удалось открыть приложение будильника.")
            }
            val now = LocalDateTime.now()
            val alarmTime = now.toLocalDate().atTime(hour, minute)
            val deltaMin = if (alarmTime.isAfter(now)) {
                java.time.Duration.between(now, alarmTime).toMinutes()
            } else {
                java.time.Duration.between(now, alarmTime.plusDays(1)).toMinutes()
            }
            ToolOutcome.json(
                "alarm_time" to "%02d:%02d".format(hour, minute),
                "minutes_until" to deltaMin,
            )
        }
        return AmaliaTool(def, handler)
    }

    // ════════════════════════════════════════════════════════════════════
    //  Системные действия: открыть / найти / позвонить / написать
    // ════════════════════════════════════════════════════════════════════

    private fun openApp(): AmaliaTool {
        val def = ToolDefinition(
            name = "open_app",
            description = "Открывает установленное приложение по имени или пакету. " +
                "Если точного названия нет, можно передать пакет " +
                "(например 'com.android.chrome').",
            parameters = listOf(
                ToolParameter(
                    name = "name",
                    type = ToolParameter.JsonType.STRING,
                    description = "Имя приложения ('YouTube', 'Chrome') или его пакет " +
                        "('com.google.android.youtube'). Если передано имя — " +
                        "выбирается наиболее вероятный пакет из локального каталога.",
                ),
            ),
        )
        val apps = AppCatalog.index(context.packageManager)
        val handler = ToolHandler { args ->
            val name = args.string("name").trim()
            if (name.isEmpty()) {
                return@ToolHandler ToolOutcome.failed("Не передано имя приложения.")
            }
            val resolved = apps.resolve(name)
            if (resolved == null) {
                return@ToolHandler ToolOutcome.failed(
                    "Не нашла приложение '$name' на устройстве.",
                )
            }
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(resolved.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val safeLaunch = launchIntent != null &&
                runCatching { context.startActivity(launchIntent) }.isSuccess
            if (safeLaunch) {
                ToolOutcome.json(
                    "package" to resolved.packageName,
                    "label" to resolved.label,
                )
            } else {
                // Fallback в market — приложение существует, но не запускается напрямую
                val marketIntent = Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse("market://details?id=${resolved.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val marketOk = runCatching { context.startActivity(marketIntent) }.isSuccess
                if (marketOk) {
                    ToolOutcome.json(
                        "package" to resolved.packageName,
                        "label" to resolved.label,
                        "fallback" to "play_market",
                    )
                } else {
                    ToolOutcome.failed("Не удалось запустить '${resolved.label}'.")
                }
            }
        }
        return AmaliaTool(def, handler)
    }

    private fun openSettings(): AmaliaTool {
        val def = ToolDefinition(
            name = "open_settings",
            description = "Открывает системный экран настроек. " +
                "Можно указать конкретную секцию ('display', 'sound', 'apps'…).",
            parameters = listOf(
                ToolParameter(
                    name = "section",
                    type = ToolParameter.JsonType.STRING,
                    description = "Опциональная секция: display / sound / apps / network / privacy / location / all.",
                    required = false,
                    enumValues = listOf(
                        "display", "sound", "apps", "network", "privacy",
                        "location", "all",
                    ),
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val section = args.string("section").lowercase()
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
            if (launched) {
                ToolOutcome.json("section" to if (section.isEmpty()) "all" else section)
            } else {
                ToolOutcome.failed("Не удалось открыть настройки.")
            }
        }
        return AmaliaTool(def, handler)
    }

    private fun webSearch(): AmaliaTool {
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
            val query = args.string("query").trim()
            if (query.isEmpty()) {
                return@ToolHandler ToolOutcome.failed("Пустой поисковый запрос.")
            }
            val intent = Intent(Intent.ACTION_WEB_SEARCH)
                .putExtra(SearchManager.QUERY, query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            if (launched) {
                ToolOutcome.json("query" to query)
            } else {
                ToolOutcome.failed("Не удалось запустить поиск.")
            }
        }
        return AmaliaTool(def, handler)
    }

    private fun makePhoneCall(): AmaliaTool {
        val def = ToolDefinition(
            name = "make_call",
            description = "Открывает номеронабиратель с уже набранным номером. " +
                "Прямой звонок требует CALL_PHONE; мы ограничиваемся диалогом набора " +
                "— пользователь сам нажмёт трубку.",
            parameters = listOf(
                ToolParameter(
                    name = "phone_number",
                    type = ToolParameter.JsonType.STRING,
                    description = "Номер телефона в международном или локальном формате.",
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val raw = args.string("phone_number").trim()
            if (raw.isEmpty() || raw.length < 3) {
                return@ToolHandler ToolOutcome.failed("Не похоже на телефонный номер.")
            }
            val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$raw"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            if (launched) {
                ToolOutcome.json("phone_number" to raw, "mode" to "dial")
            } else {
                ToolOutcome.failed("Не удалось открыть набор номера.")
            }
        }
        return AmaliaTool(def, handler)
    }

    private fun sendSms(): AmaliaTool {
        val def = ToolDefinition(
            name = "send_sms",
            description = "Открывает приложение SMS с предзаполненным номером " +
                "(опционально) и текстом.",
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
            val phone = args.string("phone_number").trim()
            val text = args.string("text")
            val uri = if (phone.isNotBlank()) {
                android.net.Uri.parse("smsto:$phone")
            } else {
                android.net.Uri.parse("smsto:")
            }
            val intent = Intent(Intent.ACTION_SENDTO, uri)
                .putExtra("sms_body", text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            if (launched) {
                ToolOutcome.json(
                    "phone_number" to phone,
                    "has_text" to text.isNotEmpty(),
                )
            } else {
                ToolOutcome.failed("Не удалось открыть SMS.")
            }
        }
        return AmaliaTool(def, handler)
    }

    // ════════════════════════════════════════════════════════════════════
    //  Мультимедиа
    // ════════════════════════════════════════════════════════════════════

    private fun takePhoto(): AmaliaTool {
        val def = ToolDefinition(
            name = "take_photo",
            description = "Открывает системное приложение камеры в режиме фото.",
            parameters = emptyList(),
        )
        val handler = ToolHandler {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching { context.startActivity(intent) }.isSuccess
            if (launched) {
                ToolOutcome.json("camera_opened" to true)
            } else {
                ToolOutcome.failed("Камера не запустилась.")
            }
        }
        return AmaliaTool(def, handler)
    }

    private fun openYouTube(): AmaliaTool {
        val def = ToolDefinition(
            name = "open_youtube",
            description = "Открывает приложение YouTube, если оно установлено. " +
                "Если нет — открывает мобильный сайт.",
            parameters = emptyList(),
        )
        val handler = ToolHandler {
            val appIntent = context.packageManager
                .getLaunchIntentForPackage("com.google.android.youtube")
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = appIntent != null &&
                runCatching { context.startActivity(appIntent) }.isSuccess
            if (launched) {
                ToolOutcome.json("youtube_opened" to true, "source" to "app")
            } else {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://m.youtube.com"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val webOk = runCatching { context.startActivity(webIntent) }.isSuccess
                if (webOk) {
                    ToolOutcome.json("youtube_opened" to true, "source" to "web")
                } else {
                    ToolOutcome.failed("YouTube недоступен на этом устройстве.")
                }
            }
        }
        return AmaliaTool(def, handler)
    }

    // ════════════════════════════════════════════════════════════════════
    //  Информация для LLM
    // ════════════════════════════════════════════════════════════════════

    private fun getCurrentTime(): AmaliaTool {
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
            ToolOutcome.json(
                "time" to time,
                "date" to date,
                "weekday" to dow,
            )
        }
        return AmaliaTool(def, handler)
    }

    private fun getDeviceStatus(): AmaliaTool {
        val def = ToolDefinition(
            name = "get_device_status",
            description = "Считывает актуальное состояние Wi-Fi, Bluetooth, яркости, " +
                "громкости, геолокации и разрешений на уведомления/контакты.",
            parameters = emptyList(),
        )
        val handler = ToolHandler {
            val snapshot = hub.status.value
            ToolOutcome.json(
                "wifi" to snapshot.wifiEnabled,
                "bluetooth" to snapshot.bluetoothEnabled,
                "brightness_percent" to (snapshot.brightnessLevel * 100) / 255,
                "volume_percent" to snapshot.volumeLevel,
                "location" to snapshot.locationEnabled,
                "contacts_permission" to snapshot.hasContactsPermission,
                "notifications_permission" to snapshot.hasNotificationPermission,
            )
        }
        return AmaliaTool(def, handler)
    }

    private fun getBatteryLevel(): AmaliaTool {
        val def = ToolDefinition(
            name = "get_battery_level",
            description = "Возвращает уровень заряда батареи в процентах и статус зарядки.",
            parameters = emptyList(),
        )
        val handler = ToolHandler {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val ifilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)
            val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val chargingSource = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                0 -> if (charging) "Disconnected" else "None"
                else -> "Unknown"
            }
            ToolOutcome.json(
                "level" to level.coerceIn(0, 100),
                "charging" to charging,
                "source" to chargingSource,
            )
        }
        return AmaliaTool(def, handler)
    }

    private fun getLocationStatus(): AmaliaTool {
        val def = ToolDefinition(
            name = "get_location_status",
            description = "Сообщает, включены ли службы геолокации на устройстве. " +
                "Координаты не возвращает — только флаг.",
            parameters = emptyList(),
        )
        val handler = ToolHandler {
            ToolOutcome.json("location_enabled" to hub.isLocationEnabled())
        }
        return AmaliaTool(def, handler)
    }

    private fun getWeather(): AmaliaTool {
        val def = ToolDefinition(
            name = "get_weather",
            description = "Возвращает демо-прогноз погоды. " +
                "В продакшене подключается к внешнему API, но сейчас — " +
                "детерминированный мок без сети.",
            parameters = listOf(
                ToolParameter(
                    name = "city",
                    type = ToolParameter.JsonType.STRING,
                    description = "Опциональный город. Сейчас игнорируется в вычислениях.",
                    required = false,
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val city = args.string("city")
            val hour = LocalTime.now().hour
            val temp = 12 + (hour % 8)
            val cond = when (hour % 4) {
                0 -> "ясно"
                1 -> "облачно"
                2 -> "небольшой дождь"
                else -> "переменная облачность"
            }
            ToolOutcome.json(
                "city" to if (city.isNotEmpty()) city else "твой город",
                "temperature_c" to temp,
                "condition" to cond,
                "source" to "demo",
            )
        }
        return AmaliaTool(def, handler)
    }

    // ════════════════════════════════════════════════════════════════════
    //  Память разговоров
    // ════════════════════════════════════════════════════════════════════

    private fun searchHistory(): AmaliaTool {
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
            val q = args.string("query").trim()
            if (q.isEmpty()) {
                return@ToolHandler ToolOutcome.failed("Пустой запрос поиска.")
            }
            val provider = searchHistoryProvider
                ?: return@ToolHandler ToolOutcome.failed("История ещё не подключена.")
            ToolOutcome.raw(provider(q).take(8000))
        }
        return AmaliaTool(def, handler)
    }

    private fun getRecentConversations(): AmaliaTool {
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
            val limit = args.int("limit", default = 5).coerceIn(1, 20)
            val provider = recentConversationsProvider
                ?: return@ToolHandler ToolOutcome.failed("История ещё не подключена.")
            ToolOutcome.raw(provider(limit).take(8000))
        }
        return AmaliaTool(def, handler)
    }

    private fun clearHistory(): AmaliaTool {
        val def = ToolDefinition(
            name = "clear_history",
            description = "Полностью очищает сохранённую историю разговоров. " +
                "Действие необратимо — возвращает, сколько разговоров удалено.",
            parameters = emptyList(),
        )
        val handler = ToolHandler {
            val provider = clearHistoryProvider
                ?: return@ToolHandler ToolOutcome.failed("История ещё не подключена.")
            ToolOutcome.raw(provider())
        }
        return AmaliaTool(def, handler)
    }

    // ════════════════════════════════════════════════════════════════════
    //  Настройки ассистента
    // ════════════════════════════════════════════════════════════════════

    private fun changeLanguage(): AmaliaTool {
        val def = ToolDefinition(
            name = "change_language",
            description = "Меняет язык интерфейса и распознавания. " +
                "Поддерживает 'ru', 'en', 'es', 'de', 'fr', 'ja', 'zh', 'hi', 'ar', " +
                "'system' (как в системе).",
            parameters = listOf(
                ToolParameter(
                    name = "language",
                    type = ToolParameter.JsonType.STRING,
                    description = "Код языка: ru, en, es, de, fr, ja, zh, hi, ar, system.",
                    enumValues = listOf(
                        "ru", "en", "es", "de", "fr", "ja", "zh", "hi", "ar", "system",
                    ),
                ),
            ),
        )
        val handler = ToolHandler { args ->
            val provider = settingsChangeProvider
                ?: return@ToolHandler ToolOutcome.failed("Настройки ещё не подключены.")
            ToolOutcome.raw(provider("language", args.string("language").lowercase().trim()))
        }
        return AmaliaTool(def, handler)
    }

    private fun toggleAutoListen(): AmaliaTool {
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
            val provider = settingsChangeProvider
                ?: return@ToolHandler ToolOutcome.failed("Настройки ещё не подключены.")
            ToolOutcome.raw(provider("auto_listen", args.bool("enabled").toString()))
        }
        return AmaliaTool(def, handler)
    }
}

/**
 * Локальный каталог приложений: маппит человеческие имена на пакеты.
 *
 * Нужен, чтобы LLM могла сказать «открой ютуб», а не угадывать
 * `com.google.android.youtube`. Полный список установленных пакетов
 * мы в контекст LLM не отдаём — слишком шумно.
 */
private class AppCatalog {

    data class Entry(
        val packageName: String,
        val label: String,
        val keywords: List<String>,
    )

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
        return null
    }

    companion object {
        fun index(pm: android.content.pm.PackageManager): AppCatalog = AppCatalog()
    }
}
