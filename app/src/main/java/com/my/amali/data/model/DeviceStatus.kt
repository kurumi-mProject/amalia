package com.my.amali.data.model

/**
 * Snapshot of device-level features and permissions the assistant can
 * inspect or toggle on behalf of the user.
 *
 * @property wifiEnabled whether Wi-Fi is currently on.
 * @property bluetoothEnabled whether Bluetooth is currently on.
 * @property brightnessLevel display brightness in the [0, 255] range.
 * @property volumeLevel media volume in the [0, 100] range.
 * @property locationEnabled whether location services are on.
 * @property hasContactsPermission whether READ_CONTACTS was granted.
 * @property hasNotificationPermission whether POST_NOTIFICATIONS was granted.
 */
data class DeviceStatus(
    val wifiEnabled: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val brightnessLevel: Int = 128,
    val volumeLevel: Int = 50,
    val locationEnabled: Boolean = false,
    val hasContactsPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false
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
    }

    companion object {
        /** Snapshot with every feature reported off and defaults zeroed. */
        val Offline: DeviceStatus = DeviceStatus(
            wifiEnabled = false,
            bluetoothEnabled = false,
            brightnessLevel = 0,
            volumeLevel = 0,
            locationEnabled = false,
            hasContactsPermission = false,
            hasNotificationPermission = false
        )
    }
}

/**
 * Identifies a controllable device feature the assistant can query or change.
 */
enum class DeviceFeature {
    WIFI,
    BLUETOOTH,
    BRIGHTNESS,
    VOLUME,
    LOCATION,
    CONTACTS,
    NOTIFICATIONS
}
