package com.my.amali.system

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings as SystemSettings
import com.my.amali.data.model.DeviceFeature
import com.my.amali.data.model.DeviceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Хаб всех системных контроллеров. Один объект агрегирует проверки
 * состояний и переключения Wi-Fi, Bluetooth, яркости, громкости,
 * локации, уведомлений и контактов.
 *
 * Дизайн: класс не кэширует разрешения сам — статус читается напрямую
 * из системы в момент вызова, поэтому UI всегда видит актуальное состояние.
 * Изменения состояний пушатся в [status] StateFlow для реактивного UI.
 */
class SystemControllerHub(private val context: Context) {

    private val wifiManager: WifiManager? =
        context.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val notificationManager: NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val _status = MutableStateFlow(DeviceStatus.Offline)

    /** Реактивный снимок состояния всех управляемых фич. */
    val status: StateFlow<DeviceStatus> = _status.asStateFlow()

    // ══════════════════════════════════════════════════════════════════
    //  Снимок состояния
    // ══════════════════════════════════════════════════════════════════

    /**
     * Читает актуальное состояние всех фич и обновляет [status].
     * Вызывается при открытии экрана управления устройством.
     */
    suspend fun refresh(): DeviceStatus = withContext(Dispatchers.Default) {
        val wifi = runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(false)
        val bt = runCatching {
            bluetoothManager?.adapter?.isEnabled == true
        }.getOrDefault(false)
        val brightness = runCatching {
            SystemSettings.System.getInt(
                context.contentResolver,
                SystemSettings.System.SCREEN_BRIGHTNESS,
                DEFAULT_BRIGHTNESS
            )
        }.getOrDefault(DEFAULT_BRIGHTNESS)
        val volume = runCatching {
            audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)
                ?.coerceAtLeast(0) ?: DEFAULT_VOLUME
        }.getOrDefault(DEFAULT_VOLUME)
        
        // Battery
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        val batteryLevel = runCatching {
            batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        }.getOrDefault(0)
        val isCharging = runCatching {
            val intentFilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val status = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
        }.getOrDefault(false)
        
        // Time
        val currentTime = runCatching {
            val cal = java.util.Calendar.getInstance()
            "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
        }.getOrDefault("")
        
        val location = runCatching {
            SystemSettings.Secure.getInt(
                context.contentResolver,
                SystemSettings.Secure.LOCATION_MODE,
                SystemSettings.Secure.LOCATION_MODE_OFF
            ) != SystemSettings.Secure.LOCATION_MODE_OFF
        }.getOrDefault(false)
        val contacts = runCatching {
            context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val notifications = runCatching {
            notificationManager?.areNotificationsEnabled() ?: false
        }.getOrDefault(false)

        DeviceStatus(
            wifiEnabled = wifi,
            bluetoothEnabled = bt,
            brightnessLevel = brightness,
            volumeLevel = volume,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            currentTime = currentTime,
            locationEnabled = location,
            hasContactsPermission = contacts,
            hasNotificationPermission = notifications,
        ).also { snapshot -> _status.update { snapshot } }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Wi-Fi
    // ══════════════════════════════════════════════════════════════════

    /**
     * Wi-Fi: состояние читается всегда; переключение разрешено только
     * на API ≤ 32 (на Android 13+ системный экран настроек открывает пользователь).
     */
    suspend fun setWifiEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.Default) {
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.S_V2) {
            return@withContext false // требует системного экрана настроек
        }
        runCatching {
            @Suppress("DEPRECATION")
            wifiManager?.isWifiEnabled = enabled
        }.getOrDefault(false)
        refresh().wifiEnabled
    }

    // ══════════════════════════════════════════════════════════════════
    //  Bluetooth
    // ══════════════════════════════════════════════════════════════════

    /**
     * Bluetooth: состояние читается всегда; переключение разрешено только
     * на API ≤ 32 (BLUETOOTH_CONNECT на Android 12+ требует BLUETOOTH_CONNECT
     * и не даёт менять состояние из приложения).
     */
    suspend fun setBluetoothEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.Default) {
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.S_V2) {
            return@withContext false
        }
        runCatching {
            @Suppress("DEPRECATION")
            bluetoothManager?.adapter?.enable()
            @Suppress("DEPRECATION")
            if (!enabled) bluetoothManager?.adapter?.disable()
        }
        refresh().bluetoothEnabled
    }

    // ══════════════════════════════════════════════════════════════════
    //  Яркость
    // ══════════════════════════════════════════════════════════════════

    /**
     * Устанавливает яркость экрана [0, 255]. Требует
     * Settings.System.canWrite(context) — иначе возвращает false
     * и UI предлагает открыть системный экран разрешения.
     */
    suspend fun setBrightness(level: Int): Boolean = withContext(Dispatchers.Default) {
        if (!SystemSettings.System.canWrite(context)) return@withContext false
        runCatching {
            SystemSettings.System.putInt(
                context.contentResolver,
                SystemSettings.System.SCREEN_BRIGHTNESS,
                level.coerceIn(0, 255)
            )
        }.getOrDefault(false)
        refresh()
        true
    }

    /** Проверяет, выдано ли системное разрешение на запись настроек яркости. */
    fun canWriteBrightness(): Boolean = SystemSettings.System.canWrite(context)

    /**
     * Открывает системный экран «Изменение настроек» (WRITE_SETTINGS),
     * который нужен для управления яркостью.
     */
    fun openBrightnessSettingsScreen() {
        val intent = android.content.Intent(
            SystemSettings.ACTION_MANAGE_WRITE_SETTINGS,
            android.net.Uri.parse("package:${context.packageName}")
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Громкость
    // ══════════════════════════════════════════════════════════════════

    /** Устанавливает громкость мультимедиа [0..max]. Возвращает новое значение. */
    suspend fun setVolume(level: Int): Int = withContext(Dispatchers.Default) {
        val max = mediaVolumeMax()
        val clamped = level.coerceIn(0, max)
        runCatching {
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
        }
        clamped
    }

    /** Максимум громкости мультимедиа на устройстве. */
    fun mediaVolumeMax(): Int =
        runCatching { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }
            .getOrDefault(15)

    /** Текущая громкость мультимедиа [0..max]. */
    fun currentVolume(): Int =
        runCatching { audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0 }
            .getOrDefault(0)

    // ══════════════════════════════════════════════════════════════════
    //  Локация (только чтение)
    // ══════════════════════════════════════════════════════════════════

    /** Читает состояние служб геолокации (без переключения — только системные настройки). */
    fun isLocationEnabled(): Boolean = runCatching {
        SystemSettings.Secure.getInt(
            context.contentResolver,
            SystemSettings.Secure.LOCATION_MODE,
            SystemSettings.Secure.LOCATION_MODE_OFF
        ) != SystemSettings.Secure.LOCATION_MODE_OFF
    }.getOrDefault(false)

    /** Открывает системные настройки локации приложения. */
    fun openLocationSettings() {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Уведомления
    // ══════════════════════════════════════════════════════════════════

    /** Разрешены ли уведомления приложения. */
    fun areNotificationsEnabled(): Boolean =
        notificationManager?.areNotificationsEnabled() ?: false

    /** Открывает системный экран настроек уведомлений приложения. */
    fun openNotificationSettings() {
        val intent = android.content.Intent(
            SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS
        ).putExtra(SystemSettings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Фичи (общий статус по всем контроллерам)
    // ══════════════════════════════════════════════════════════════════

    /** Проверяет, выдано ли [feature] разрешение/доступно ли состояние. */
    fun isFeatureAvailable(feature: DeviceFeature): Boolean {
        val snapshot = _status.value
        return snapshot.isEnabled(feature)
    }

    /** Признак, что переключение фичи возможно без системного экрана. */
    fun isDirectToggleSupported(feature: DeviceFeature): Boolean =
        when (feature) {
            DeviceFeature.WIFI, DeviceFeature.BLUETOOTH ->
                android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.S_V2
            DeviceFeature.BRIGHTNESS -> canWriteBrightness()
            DeviceFeature.VOLUME -> true
            else -> false
        }

    private companion object {
        const val DEFAULT_BRIGHTNESS = 128
        const val DEFAULT_VOLUME = 5
    }
}
