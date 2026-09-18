package com.my.amali.data.model

/**
 * Snapshot of device-level features and permissions the assistant can
 * inspect or toggle on behalf of the user.
 *
 * ## Зачем здесь так много полей
 *
 * Снимок читает **модель**, а не человек: он уходит в системный промпт
 * блоком `# УСТРОЙСТВО СЕЙЧАС`. Каждое поле отвечает на вопрос, который иначе
 * модель задавала бы наугад:
 *
 *  — «включи вайфай» → можно ли вообще переключать, или только открыть панель;
 *  — «позвони маме» → выдан ли READ_CONTACTS, есть ли вообще звонилка;
 *  — «сколько заряда» → есть ли смысл спрашивать, или значение уже в промпте;
 *  — «сфоткай» → есть ли камера и разрешение, или честнее отказать сразу.
 *
 * Раньше промпт получал пять полей, и на вопрос «сколько времени» модель
 * отвечала догадкой, потому что не знала, что время уже дано. Теперь знает.
 *
 * @property wifiEnabled whether Wi-Fi is currently on.
 * @property bluetoothEnabled whether Bluetooth is currently on.
 * @property brightnessLevel display brightness in the [0, 255] range.
 * @property brightnessPercent то же значение в процентах 0..100 — именно его
 *   читает модель, чтобы случайно не сказать «яркость двести пятьдесят пять».
 * @property volumeLevel media volume as a percentage in the [0, 100] range.
 *   Normalised rather than raw, because the per-device stream maximum varies
 *   (7, 10, 15, 25, 30): a percentage is the only portable unit.
 * @property batteryLevel battery charge percentage [0, 100]; -1 если чтение
 *   не удалось (на части прошивок BatteryManager молчит).
 * @property isCharging whether the device is charging.
 * @property currentTime current time as "HH:mm" string.
 * @property currentDate текущая дата как "2026-09-18" (ISO) — нужна для
 *   «какой сегодня день», «сколько до пятницы», «что было вчера».
 * @property weekday день недели словами — модель не должна сама считать его
 *   из даты, на этом она ошибается.
 * @property timezone текущая зона ("Europe/Moscow"), чтобы модель не строила
 *   догадок о времени суток в другом регионе.
 * @property locationEnabled whether location services are on.
 * @property hasContactsPermission whether READ_CONTACTS was granted.
 * @property hasNotificationPermission whether notifications are enabled
 *   (runtime grant *and* system toggle on API 33+).
 * @property hasMicrophonePermission выдан ли RECORD_AUDIO: без него голосовой
 *   конвейер не работает вообще, и это первое, что стоит проверить.
 * @property hasCameraPermission выдан ли CAMERA — от него зависят снимки.
 * @property hasPhonePermission выдан ли CALL_PHONE: без него звонок только
 *   через набор номера, сами мы позвонить не можем.
 * @property hasSmsPermission выдан ли SEND_SMS.
 * @property canWriteSettings выдано ли WRITE_SETTINGS — им управляется
 *   яркость; без него яркость меняется только вручную в настройках.
 * @property hasAccessibilityService включена ли служба спец. возможностей
 *   Амалии: она даёт глубокое управление чужими приложениями.
 * @property internetAvailable whether a validated network is up right now.
 * @property flashlightOn whether the torch is believed to be on.
 * @property isDeviceLocked экран заперт сейчас или нет — влияет на то,
 *   откроются ли фоновые действия.
 * @property powerSaveMode включён ли режим экономии: часть фоновых задач
 *   система придушивает, и об этом честнее сказать заранее.
 * @property isMuted глобальный звук выключен (режим «без звука»).
 * @property isDnd включён режим «не беспокоить».
 * @property isInCall идёт ли телефонный звонок.
 * @property isHeadsetConnected подключены ли наушники — определяет, куда
 *   уйдёт звук и стоит ли вообще звать голосом.
 * @property audioMode название режима аудио (normal/silent/vibrate).
 * @property alarmsCount сколько будильников заведено на устройстве.
 * @property notificationPolicyState человекочитаемое состояние фильтра
 *   уведомлений; совпадает с [isDnd] по смыслу, но приходит строкой, чтобы
 *   модель могла произнести его дословно.
 * @property sdkVersion версия API Android — по ней модель понимает, почему
 *   Wi-Fi не переключается напрямую (Android 13+ запрещает).
 * @property androidVersion та же версия словами ("14"), удобнее для речи.
 * @property deviceModel модель телефона — иногда нужна для объяснений.
 * @property deviceManufacturer производитель.
 * @property wifiAccess how deeply Wi-Fi can be controlled on this device.
 * @property bluetoothAccess how deeply Bluetooth can be controlled.
 * @property brightnessAccess насколько глубоко управляется яркость.
 * @property flashlightAccess насколько глубоко управляется фонарик.
 * @property volumeAccess насколько глубоко управляется громкость.
 * @property hasCamera есть ли на устройстве камера вообще.
 * @property hasFlashlight есть ли вспышка (не у всех телефонов).
 * @property hasBluetoothAdapter есть ли Bluetooth-адаптер.
 * @property hasTelephony есть ли телефонный модуль (нет на планшетах).
 * @property hasWifiAdapter есть ли Wi-Fi-модуль.
 * @property batteryTemperatureC температура батареи в градусах, null если
 *   система не отдаёт значение.
 * @property batteryHealth человекочитаемое состояние батареи
 *   ("good", "overheat", "unknown") — из BatteryManager.EXTRA_HEALTH.
 * @property capturedAtMillis время снимка: если данные подустарели, модель
 *   видит это и решает, переспросить ли состояние перед действием.
 */
data class DeviceStatus(
    val wifiEnabled: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val brightnessLevel: Int = 128,
    val volumeLevel: Int = 50,
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val currentTime: String = "",
    val locationEnabled: Boolean = false,
    val hasContactsPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val internetAvailable: Boolean = false,
    val flashlightOn: Boolean = false,
    val wifiAccess: ControlAccessLevel = ControlAccessLevel.SCREEN,
    val bluetoothAccess: ControlAccessLevel = ControlAccessLevel.SCREEN,
    val brightnessPercent: Int = 50,
    val currentDate: String = "",
    val weekday: String = "",
    val timezone: String = "",
    val hasMicrophonePermission: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val hasPhonePermission: Boolean = false,
    val hasSmsPermission: Boolean = false,
    val canWriteSettings: Boolean = false,
    val hasAccessibilityService: Boolean = false,
    val isDeviceLocked: Boolean = false,
    val powerSaveMode: Boolean = false,
    val isMuted: Boolean = false,
    val isDnd: Boolean = false,
    val isInCall: Boolean = false,
    val isHeadsetConnected: Boolean = false,
    val audioMode: String = "normal",
    val alarmsCount: Int = 0,
    val notificationPolicyState: String = "",
    val sdkVersion: Int = 0,
    val androidVersion: String = "",
    val deviceModel: String = "",
    val deviceManufacturer: String = "",
    val brightnessAccess: ControlAccessLevel = ControlAccessLevel.SCREEN,
    val flashlightAccess: ControlAccessLevel = ControlAccessLevel.SCREEN,
    val volumeAccess: ControlAccessLevel = ControlAccessLevel.DIRECT,
    val hasCamera: Boolean = true,
    val hasFlashlight: Boolean = true,
    val hasBluetoothAdapter: Boolean = true,
    val hasTelephony: Boolean = true,
    val hasWifiAdapter: Boolean = true,
    val batteryTemperatureC: Float? = null,
    val batteryHealth: String = "",
    val capturedAtMillis: Long = 0L,
) {
    /** Normalized brightness in the [0.0, 1.0] range for UI sliders. */
    val brightnessFraction: Float
        get() = brightnessLevel.coerceIn(0, 255) / 255f

    /** Normalized volume in the [0.0, 1.0] range for UI sliders. */
    val volumeFraction: Float
        get() = volumeLevel.coerceIn(0, 100) / 100f

    /** Возраст снимка в секундах; -1, если время снимка не заполнено. */
    val ageSeconds: Long
        get() = if (capturedAtMillis <= 0L) -1L
        else (System.currentTimeMillis() - capturedAtMillis) / 1000L

    /** Returns a copy with [level] clamped to the valid [0, 255] brightness range. */
    fun withBrightness(level: Int): DeviceStatus =
        copy(
            brightnessLevel = level.coerceIn(0, 255),
            brightnessPercent = (level.coerceIn(0, 255) * 100) / 255,
        )

    /** Returns a copy with [level] clamped to the valid [0, 100] volume range. */
    fun withVolume(level: Int): DeviceStatus =
        copy(volumeLevel = level.coerceIn(0, 100))

    /** Returns true if the given [feature] is currently enabled/available. */
    fun isEnabled(feature: DeviceFeature): Boolean = when (feature) {
        DeviceFeature.WIFI -> wifiEnabled
        DeviceFeature.BLUETOOTH -> bluetoothEnabled
        DeviceFeature.BRIGHTNESS -> brightnessLevel > 0
        DeviceFeature.VOLUME -> volumeLevel > 0
        DeviceFeature.LOCATION -> locationEnabled
        DeviceFeature.CONTACTS -> hasContactsPermission
        DeviceFeature.NOTIFICATIONS -> hasNotificationPermission
        DeviceFeature.FLASHLIGHT -> flashlightOn
    }

    /** Deepest control level available for [feature], for honest UI labels. */
    fun accessFor(feature: DeviceFeature): ControlAccessLevel = when (feature) {
        DeviceFeature.WIFI -> wifiAccess
        DeviceFeature.BLUETOOTH -> bluetoothAccess
        DeviceFeature.BRIGHTNESS -> brightnessAccess
        DeviceFeature.FLASHLIGHT -> flashlightAccess
        DeviceFeature.VOLUME -> volumeAccess
        else -> ControlAccessLevel.DIRECT
    }

    companion object {
        /** Snapshot with every feature reported off and defaults zeroed. */
        val Offline: DeviceStatus = DeviceStatus(
            wifiEnabled = false,
            bluetoothEnabled = false,
            brightnessLevel = 0,
            brightnessPercent = 0,
            volumeLevel = 0,
            batteryLevel = 0,
            isCharging = false,
            currentTime = "",
            locationEnabled = false,
            hasContactsPermission = false,
            hasNotificationPermission = false,
            internetAvailable = false,
            flashlightOn = false,
        )
    }
}

/**
 * How deeply the app can control a system feature on this device.
 *
 * Exists so the UI can be honest instead of showing a dead toggle: a control
 * that can only open a system panel must look and read differently from one
 * that actually flips a setting in place.
 */
enum class ControlAccessLevel {
    /** Direct API control — the switch works right here. */
    DIRECT,

    /** Opens the quick-settings panel; the user taps once. */
    PANEL,

    /** Opens a full settings screen; the user does everything. */
    SCREEN,
}

/**
 * Identifies a controllable device feature the assistant can query or change.
 */
enum class DeviceFeature {
    WIFI,
    BLUETOOTH,
    BRIGHTNESS,
    VOLUME,
    FLASHLIGHT,
    LOCATION,
    CONTACTS,
    NOTIFICATIONS
}
