package com.my.amali.data.model

/**
 * Snapshot of device-level features and permissions the assistant can
 * inspect or toggle on behalf of the user.
 *
 * @property wifiEnabled whether Wi-Fi is currently on.
 * @property bluetoothEnabled whether Bluetooth is currently on.
 * @property brightnessLevel display brightness in the [0, 255] range.
 * @property volumeLevel media volume as a percentage in the [0, 100] range.
 *   Normalised rather than raw, because the per-device stream maximum varies
 *   (7, 10, 15, 25, 30): a percentage is the only portable unit.
 * @property batteryLevel battery charge percentage [0, 100].
 * @property isCharging whether the device is charging.
 * @property currentTime current time as "HH:mm" string.
 * @property locationEnabled whether location services are on.
 * @property hasContactsPermission whether READ_CONTACTS was granted.
 * @property hasNotificationPermission whether notifications are enabled
 *   (runtime grant *and* system toggle on API 33+).
 * @property internetAvailable whether a validated network is up right now.
 * @property flashlightOn whether the torch is believed to be on.
 * @property wifiAccess how deeply Wi-Fi can be controlled on this device.
 * @property bluetoothAccess how deeply Bluetooth can be controlled.
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
) {
    /** Normalized brightness in the [0.0, 1.0] range for UI sliders. */
    val brightnessFraction: Float
        get() = brightnessLevel.coerceIn(0, 255) / 255f

    /** Normalized volume in the [0.0, 1.0] range for UI sliders. */
    val volumeFraction: Float
        get() = volumeLevel.coerceIn(0, 100) / 100f

    /** Returns a copy with [level] clamped to the valid [0, 255] brightness range. */
    fun withBrightness(level: Int): DeviceStatus =
        copy(brightnessLevel = level.coerceIn(0, 255))

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
        else -> ControlAccessLevel.DIRECT
    }

    companion object {
        /** Snapshot with every feature reported off and defaults zeroed. */
        val Offline: DeviceStatus = DeviceStatus(
            wifiEnabled = false,
            bluetoothEnabled = false,
            brightnessLevel = 0,
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
