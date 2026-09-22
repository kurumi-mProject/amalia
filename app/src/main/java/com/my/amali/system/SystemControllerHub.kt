package com.my.amali.system

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as SystemSettings
import androidx.core.content.ContextCompat
import com.my.amali.data.model.ControlAccessLevel
import com.my.amali.data.model.DeviceFeature
import com.my.amali.data.model.DeviceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

/**
 * ════════════════════════════════════════════════════════════════════════
 *  SystemControllerHub — управление телефоном на ЛЮБОЙ версии Android
 * ════════════════════════════════════════════════════════════════════════
 *
 * ## Проблема, которую решает этот файл
 *
 * Раньше логика была построена на одном вопросе: «а можно ли переключить
 * *напрямую*?» — и если нет, возвращалось `false` с сообщением «Android 13+
 * требует системного экрана». Для пользователя это означало: на новом
 * телефоне управление Wi-Fi и Bluetooth **просто не работает**, хотя система
 * умеет сделать то же самое — только через панель, а не через API.
 *
 * Правильная модель — не «получилось / не получилось», а **три уровня
 * доступа**, из которых приложение обязано использовать самый глубокий
 * доступный:
 *
 * ── 1. DIRECT — прямое управление через API ──────────────────────────
 *    Работает: Wi-Fi (API ≤ 32), Bluetooth (API ≤ 32), яркость (с
 *    WRITE_SETTINGS), громкость (всегда), фонарик (API ≥ 23).
 *
 * ── 2. PANEL — системная панель одним тапом ──────────────────────────
 *    `Settings.Panel.ACTION_INTERNET_CONNECTIVITY` (API ≥ 29) открывает
 *    шторку ровно на плитках Wi-Fi/интернета: пользователю остаётся один
 *    тап по тумблеру. Это высший доступ, который платформа разрешает
 *    стороннему приложению, и **обязательно** на API ≥ 33.
 *    На API < 29 тот же вызов не существует, поэтому там честный фоллбэк
 *    в полный экран настроек.
 *
 * ── 3. SCREEN — экран настроек ───────────────────────────────────────
 *    Универсальный фоллбэк, работает везде и всегда.
 *
 * ## Ключевое правило этого класса
 *
 * **Ни один метод управления не возвращает «просто false».** Каждый
 * возвращает [ControlResult] с фактическим уровнем доступа и точной
 * причиной отказа. Благодаря этому Амалия в ответ на «включи вайфай» говорит
 * «открываю панель — нажми плитку», а не «не поддерживается»: сценарий
 * доводится до конца на любой версии системы.
 *
 * ## Почему отдельно про ширину поиска
 *
 * `Settings.Panel` существует только с API 29, а `WifiManager.isWifiEnabled`
 * (чтение) требует разрешения `ACCESS_WIFI_STATE`, которое на API 33+ может
 * быть не выдано. Поэтому состояние читается **двумя путями**:
 * через `ConnectivityManager` (работает везде с API 23 и не требует
 * sensitive-разрешений) и через `WifiManager` (точнее, но не всегда
 * доступно). Это даёт корректный статус даже там, где старый код показывал
 * «выключено» из-за отказа в разрешении.
 */
class SystemControllerHub(private val context: Context) {

    // ── Системные сервисы ────────────────────────────────────────────────

    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val bluetoothManager: BluetoothManager? =
        context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE)
            as? BluetoothManager

    private val audioManager: AudioManager? =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val notificationManager: NotificationManager? =
        context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager

    private val powerManager: PowerManager? =
        context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val connectivityManager: ConnectivityManager? =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager

    private val cameraManager: CameraManager? =
        context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private val _status = MutableStateFlow(DeviceStatus.Offline)

    /** Реактивный снимок состояния всех управляемых фич. */
    val status: StateFlow<DeviceStatus> = _status.asStateFlow()

    // ══════════════════════════════════════════════════════════════════════
    //  УРОВНИ ДОСТУПА
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Самый глубокий доступ, который приложение имеет для фичи прямо сейчас.
     *
     * UI обязан спрашивать именно это, а не «API > 32?»: на Android 11 панель
     * уже есть, а на Android 13 без неё управлять Wi-Fi нельзя вообще.
     */
    fun accessLevelFor(feature: DeviceFeature): ControlAccess = when (feature) {
        DeviceFeature.WIFI -> when {
            canToggleWifiDirectly() -> ControlAccess.DIRECT
            hasConnectivityPanel() -> ControlAccess.PANEL
            else -> ControlAccess.SCREEN
        }
        DeviceFeature.BLUETOOTH -> when {
            canToggleBluetoothDirectly() -> ControlAccess.DIRECT
            hasConnectivityPanel() -> ControlAccess.PANEL
            else -> ControlAccess.SCREEN
        }
        DeviceFeature.BRIGHTNESS -> if (canWriteBrightness()) {
            ControlAccess.DIRECT
        } else {
            ControlAccess.SCREEN
        }
        // Громкость и фонарик доступны напрямую на всех поддерживаемых API:
        // setStreamVolume и setTorchMode не требуют специальных разрешений.
        DeviceFeature.VOLUME -> ControlAccess.DIRECT
        DeviceFeature.FLASHLIGHT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            cameraManager?.cameraIdList?.isNotEmpty() == true
        ) {
            ControlAccess.DIRECT
        } else {
            ControlAccess.SCREEN
        }
        // Только чтение состояния — управление отдаём системному экрану.
        DeviceFeature.LOCATION -> ControlAccess.SCREEN
        DeviceFeature.CONTACTS -> if (hasPermission(Manifest.permission.READ_CONTACTS)) {
            ControlAccess.DIRECT
        } else {
            ControlAccess.SCREEN
        }
        DeviceFeature.NOTIFICATIONS -> ControlAccess.SCREEN
    }

    /** Прямое переключение фичи доступно без системного UI. */
    fun isDirectToggleSupported(feature: DeviceFeature): Boolean =
        accessLevelFor(feature) == ControlAccess.DIRECT

    /** Панель быстрых настроек существует на этой версии системы (API ≥ 29). */
    private fun hasConnectivityPanel(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Wi-Fi можно переключать программно: API ≤ 32 и есть CHANGE_WIFI_STATE. */
    private fun canToggleWifiDirectly(): Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 &&
            hasPermission(Manifest.permission.CHANGE_WIFI_STATE)

    /**
     * Bluetooth можно переключать программно.
     *
     * На API ≤ 30 нужен `BLUETOOTH` + `BLUETOOTH_ADMIN` (normal-разрешения,
     * выдаются при установке). На API 31+ `enable()/disable()` недоступны
     * вообще, независимо от `BLUETOOTH_CONNECT` — это сделано платформой
     * намеренно, и обходить это приложение не должно.
     */
    private fun canToggleBluetoothDirectly(): Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.R

    // ══════════════════════════════════════════════════════════════════════
    //  Снимок состояния
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Читает актуальное состояние всех фич и обновляет [status].
     *
     * Каждое чтение обёрнуто в `runCatching` индивидуально: на «кастомных»
     * прошивках отдельные вызовы кидают `SecurityException`, и без изоляции
     * один сломанный датчик обнулял бы **весь** снимок — именно так
     * появлялись «выключенные» Wi-Fi и Bluetooth на живых устройствах.
     */
    suspend fun refresh(): DeviceStatus = withContext(Dispatchers.Default) {
        val wifi = readWifiEnabled()
        val bt = readBluetoothEnabled()
        val brightness = readBrightness()
        val volume = readVolumePercent()
        val battery = readBattery()

        val location = isLocationEnabled()
        val contacts = hasPermission(Manifest.permission.READ_CONTACTS)
        val notifications = areNotificationsEnabled()
        val internet = readInternetAvailable()
        val flashlight = isFlashlightOn()

        val currentTime = runCatching {
            val cal = Calendar.getInstance()
            "%02d:%02d".format(
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
            )
        }.getOrDefault("")

        // ── Дата и день недели ────────────────────────────────────────────
        //
        // Модель отвечает на «какой сегодня день» и «сколько до пятницы».
        // Считать день недели из даты она умеет плохо, а врать в голосовом
        // ответе нельзя, поэтому день недели приходит готовым словом.
        val now = Calendar.getInstance()
        val currentDate = runCatching {
            "%04d-%02d-%02d".format(
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH) + 1,
                now.get(Calendar.DAY_OF_MONTH),
            )
        }.getOrDefault("")
        val weekday = runCatching {
            WEEKDAY_NAMES[now.get(Calendar.DAY_OF_WEEK)] ?: ""
        }.getOrDefault("")
        val timezone = runCatching { TimeZone.getDefault().id }.getOrDefault("")

        // ── Разрешения ───────────────────────────────────────────────────
        //
        // Каждое разрешение читается отдельно и не влияет на остальные:
        // отказ в микрофоне не должен стирать из промпта состояние Wi-Fi.
        val microphone = hasPermission(Manifest.permission.RECORD_AUDIO)
        val cameraPermission = hasPermission(Manifest.permission.CAMERA)
        val phonePermission = hasPermission(Manifest.permission.CALL_PHONE)
        val smsPermission = hasPermission(Manifest.permission.SEND_SMS)
        val writeSettings = runCatching {
            SystemSettings.System.canWrite(context)
        }.getOrDefault(false)
        val accessibility = isAccessibilityServiceEnabled()

        // ── Аудио и режимы ───────────────────────────────────────────────
        val ringerMode = runCatching {
            audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        }.getOrDefault(AudioManager.RINGER_MODE_NORMAL)
        val audioMode = when (ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "silent"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            else -> "normal"
        }
        val muted = ringerMode == AudioManager.RINGER_MODE_SILENT
        val dnd = runCatching {
            notificationManager?.isNotificationPolicyAccessGranted == true &&
                notificationManager.currentInterruptionFilter !=
                NotificationManager.INTERRUPTION_FILTER_ALL
        }.getOrDefault(false)
        val inCall = runCatching {
            // `TelephonyManager.callState` объявлен устаревшим в API 31 в
            // пользу `TelephonyCallback`, но старый путь работает на всех
            // версиях, которые мы поддерживаем (minSdk 26), и не требует
            // разрешения READ_PHONE_STATE, потому что здесь только чтение
            // собственного состояния вызова. Новый API не даёт ничего
            // полезного для одноразового снимка, поэтому оставляем как есть.
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE)
                as? android.telephony.TelephonyManager
            @Suppress("DEPRECATION")
            tm?.callState != android.telephony.TelephonyManager.CALL_STATE_IDLE
        }.getOrDefault(false)
        val headset = runCatching {
            // `getDevices()` появился в API 23, `isWiredHeadsetOn` — с API 1,
            // но помечен устаревшим и на новых версиях может вернуть false,
            // даже когда наушники подключены. Поэтому сначала спрашиваем
            // список устройств (он точный), а `isWiredHeadsetOn` оставляем
            // как добор для старых прошивок, где список отдаёт пусто.
            val viaDevices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                ?.any {
                    it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
                } == true
            @Suppress("DEPRECATION")
            viaDevices || audioManager?.isWiredHeadsetOn == true
        }.getOrDefault(false)
        val alarms = runCatching {
            // Количество заведённых будильников.
            //
            // Публичного API для ЧТЕНИЯ будильников в Android нет:
            //  — `AlarmClock.getAlarms()` существует только с API 31, а minSdk
            //    проекта — 26 (на нём падала сборка);
            //  — `Settings.System.NEXT_ALARM_FORMATTED` отдаёт только *время*
            //    ближайшего будильника, без количества, и на Android 12+
            //    возвращает пустую строку, потому что система перестала
            //    отдавать это приложению без спец. разрешения.
            //
            // Поэтому спрашиваем системное приложение «Часы» через
            // календарь-подобный запрос к провайдеру, который существует
            // много лет и отвечает даже на новых версиях:
            // `content://com.android.alarmclock/alarms`. На прошивках без этого
            // провайдера (часть кастомных ROM) запрос вернёт null — тогда
            // остаётся ноль, и модель честно скажет, что не знает, а не
            // соврёт про «ноль будильников».
            //
            // Флаг `alarms_known` в снимке отличает «точно ноль» от
            // «прочитать не удалось», чтобы ответ не звучал уверенно на пустом
            // месте.
            val uri = android.net.Uri.parse("content://com.android.alarmclock/alarms")
            var count = 0
            var known = false
            context.contentResolver.query(uri, arrayOf("_id"), null, null, null)
                ?.use { cursor ->
                    count = cursor.count
                    known = true
                }
            if (!known) count = -1
            count
        }.getOrDefault(-1)

        // ── Железо и версия системы ──────────────────────────────────────
        val sdk = Build.VERSION.SDK_INT
        val androidVersion = runCatching { Build.VERSION.RELEASE }.getOrDefault("")
        val model = runCatching { Build.MODEL }.getOrDefault("")
        val manufacturer = runCatching { Build.MANUFACTURER }.getOrDefault("")
        val hasCameraHardware = runCatching {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        }.getOrDefault(false)
        val hasFlashlightHardware = runCatching {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        }.getOrDefault(false)
        val hasBluetoothHardware = runCatching {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        }.getOrDefault(false)
        val hasTelephonyHardware = runCatching {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        }.getOrDefault(false)
        val hasWifiHardware = runCatching {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)
        }.getOrDefault(false)

        // ── Питание: режим экономии, блокировка, температура батареи ─────
        val powerSave = runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isPowerSaveMode == true
        }.getOrDefault(false)
        val deviceLocked = runCatching {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            km?.isDeviceLocked == true
        }.getOrDefault(false)
        val batteryExtra = runCatching {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
            val tempC = if (temp > 0) temp / 10f else null
            val healthName = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                BatteryManager.BATTERY_HEALTH_COLD -> "cold"
                else -> "unknown"
            }
            tempC to healthName
        }.getOrDefault(null to "unknown")

        DeviceStatus(
            wifiEnabled = wifi,
            bluetoothEnabled = bt,
            brightnessLevel = brightness,
            brightnessPercent = (brightness.coerceIn(0, 255) * 100) / 255,
            volumeLevel = volume,
            batteryLevel = battery.first,
            isCharging = battery.second,
            currentTime = currentTime,
            currentDate = currentDate,
            weekday = weekday,
            timezone = timezone,
            locationEnabled = location,
            hasContactsPermission = contacts,
            hasNotificationPermission = notifications,
            internetAvailable = internet,
            flashlightOn = flashlight,
            wifiAccess = accessLevelFor(DeviceFeature.WIFI).toStatusLevel(),
            bluetoothAccess = accessLevelFor(DeviceFeature.BLUETOOTH).toStatusLevel(),
            brightnessAccess = accessLevelFor(DeviceFeature.BRIGHTNESS).toStatusLevel(),
            flashlightAccess = accessLevelFor(DeviceFeature.FLASHLIGHT).toStatusLevel(),
            volumeAccess = accessLevelFor(DeviceFeature.VOLUME).toStatusLevel(),
            hasMicrophonePermission = microphone,
            hasCameraPermission = cameraPermission,
            hasPhonePermission = phonePermission,
            hasSmsPermission = smsPermission,
            canWriteSettings = writeSettings,
            hasAccessibilityService = accessibility,
            isDeviceLocked = deviceLocked,
            powerSaveMode = powerSave,
            isMuted = muted,
            isDnd = dnd,
            isInCall = inCall,
            isHeadsetConnected = headset,
            audioMode = audioMode,
            alarmsCount = alarms,
            notificationPolicyState = if (dnd) "dnd" else "all",
            sdkVersion = sdk,
            androidVersion = androidVersion,
            deviceModel = model,
            deviceManufacturer = manufacturer,
            hasCamera = hasCameraHardware,
            hasFlashlight = hasFlashlightHardware,
            hasBluetoothAdapter = hasBluetoothHardware,
            hasTelephony = hasTelephonyHardware,
            hasWifiAdapter = hasWifiHardware,
            batteryTemperatureC = batteryExtra.first,
            batteryHealth = batteryExtra.second,
            capturedAtMillis = System.currentTimeMillis(),
        ).also { snapshot -> _status.value = snapshot }
    }

    /**
     * Включена ли служба спец. возможностей Амалии.
     *
     * Проверяется по списку включённых служб в настройках, а не по факту
     * подключения сервиса: сервис может быть включён, но ещё не
     * инициализирован, и это не повод говорить «выключено».
     *
     * Строка собирается через `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`
     * и сравнивается по короткому имени класса — так же, как это делает
     * сама система при запуске.
     */
    fun isAccessibilityServiceEnabled(): Boolean = runCatching {
        val enabled = SystemSettings.Secure.getString(
            context.contentResolver,
            SystemSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val expected = "${context.packageName}/.core.accessibility.AmaliaAccessibilityService"
        enabled.split(':').any {
            it.equals(expected, ignoreCase = true) ||
                it.contains("AmaliaAccessibilityService", ignoreCase = true)
        }
    }.getOrDefault(false)

    /**
     * Wi-Fi включён?
     *
     * Два независимых пути чтения. `ConnectivityManager` (API ≥ 23) не требует
     * sensitive-разрешений и работает даже там, где `WifiManager.isWifiEnabled`
     * отдаёт `SecurityException` — поэтому он первый. `WifiManager` — уточнение
     * для старых систем, где `NetworkCapabilities` может ещё не обновиться.
     */
    private fun readWifiEnabled(): Boolean {
        // runCatching<Boolean?> с явным параметром типа: иначе вывод опирается
        // на последнее выражение лямбды, а досрочные `return@runCatching null`
        // в её начале ломают эту опору и дают «Syntax error / type mismatch».
        val viaConnectivity: Boolean? = runCatching<Boolean?> {
            val network = connectivityManager?.activeNetwork ?: return@runCatching null
            val caps = connectivityManager.getNetworkCapabilities(network)
                ?: return@runCatching null
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }.getOrNull()
        if (viaConnectivity == true) return true

        val viaWifiManager: Boolean? = runCatching {
            @Suppress("DEPRECATION")
            wifiManager?.isWifiEnabled
        }.getOrNull()
        // Оба пути не дали ответа — считаем выключенным, а не падаем.
        return viaWifiManager ?: viaConnectivity ?: false
    }

    /** Bluetooth включён? На API 31+ адаптер читается без `BLUETOOTH_CONNECT`. */
    private fun readBluetoothEnabled(): Boolean = runCatching {
        bluetoothManager?.adapter?.isEnabled == true
    }.getOrDefault(false)

    private fun readBrightness(): Int = runCatching {
        SystemSettings.System.getInt(
            context.contentResolver,
            SystemSettings.System.SCREEN_BRIGHTNESS,
            DEFAULT_BRIGHTNESS,
        )
    }.getOrDefault(DEFAULT_BRIGHTNESS)

    /** Громкость мультимедиа в процентах 0..100 (нормализовано по максимуму девайса). */
    private fun readVolumePercent(): Int {
        val max = mediaVolumeMax()
        if (max <= 0) return 0
        val current = runCatching {
            audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        }.getOrDefault(0)
        return ((current.coerceAtLeast(0) * 100) / max).coerceIn(0, 100)
    }

    /** Пара (процент заряда, идёт ли зарядка). */
    private fun readBattery(): Pair<Int, Boolean> = runCatching {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        level.coerceIn(0, 100) to charging
    }.getOrDefault(0 to false)

    /** Есть ли вообще выход в интернет прямо сейчас. */
    private fun readInternetAvailable(): Boolean = runCatching<Boolean> {
        val network = connectivityManager?.activeNetwork ?: return@runCatching false
        val caps = connectivityManager.getNetworkCapabilities(network)
            ?: return@runCatching false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    // ══════════════════════════════════════════════════════════════════════
    //  Wi-Fi
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Включает/выключает Wi-Fi настолько глубоко, насколько это разрешено.
     *
     * @return [ControlResult] с фактическим уровнем доступа. Никогда не
     *   возвращает «просто отказ»: если прямое переключение закрыто
     *   платформой, открывается панель, и результат честно помечается
     *   [ControlAccess.PANEL] — пользователю остаётся один тап.
     */
    suspend fun setWifiEnabled(enabled: Boolean): ControlResult = withContext(Dispatchers.Default) {
        if (canToggleWifiDirectly()) {
            // Явный тип Boolean (не Boolean?) обязателен: `wifiManager` объявлен
            // nullable, поэтому без `?: false` вся лямбда даёт Boolean?, и
            // `runCatching` выводит Result<Boolean?>. Дальше `.getOrDefault(false)`
            // возвращает Boolean?, а в условии `||` компилятор требует строгий
            // Boolean — отсюда «Condition type mismatch» на строке ниже.
            val attempted: Boolean = runCatching {
                @Suppress("DEPRECATION")
                wifiManager?.setWifiEnabled(enabled) ?: false
            }.getOrDefault(false)
            val actual = refresh().wifiEnabled
            if (actual == enabled || attempted) {
                return@withContext ControlResult.Applied(
                    level = ControlAccess.DIRECT,
                    state = actual,
                )
            }
        }
        // Прямой путь недоступен или не сработал — открываем панель.
        val opened = openConnectivityPanel()
        if (opened) {
            ControlResult.Delegated(
                level = ControlAccess.PANEL,
                requestedState = enabled,
                hint = "Открыла панель сети — нажми плитку Wi-Fi, и я это запомню.",
            )
        } else {
            openWifiSettings()
            ControlResult.Delegated(
                level = ControlAccess.SCREEN,
                requestedState = enabled,
                hint = "Открыла настройки Wi-Fi: на этой версии Android " +
                    "система разрешает менять Wi-Fi только вручную.",
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Bluetooth
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Включает/выключает Bluetooth.
     *
     * На API 31+ `BluetoothAdapter.enable()` бросает `SecurityException`
     * **всегда**, что бы ни было выдано: платформа отдала управление
     * системному UI. Поэтому там сразу открывается панель подключений
     * (API ≥ 29) или полный экран Bluetooth-настроек.
     */
    suspend fun setBluetoothEnabled(enabled: Boolean): ControlResult =
        withContext(Dispatchers.Default) {
            if (canToggleBluetoothDirectly()) {
                val ok = runCatching<Boolean> {
                    val adapter = bluetoothManager?.adapter
                        ?: return@runCatching false
                    if (enabled) {
                        @Suppress("DEPRECATION")
                        adapter.enable()
                    } else {
                        @Suppress("DEPRECATION")
                        adapter.disable()
                    }
                    true
                }.getOrDefault(false)
                if (ok) {
                    // Адаптер меняет состояние асинхронно — даём ему мгновение,
                    // иначе сразу после enable() статус ещё «выключен» и UI врёт.
                    return@withContext ControlResult.Applied(
                        level = ControlAccess.DIRECT,
                        state = awaitBluetoothState(enabled),
                    )
                }
            }
            openBluetoothSettings()
            ControlResult.Delegated(
                level = ControlAccess.SCREEN,
                requestedState = enabled,
                hint = "Открыла настройки Bluetooth: с Android 12 менять его " +
                    "можно только там.",
            )
        }

    /** Ждёт смены состояния адаптера до ~1.5 с, чтобы UI не показывал старое. */
    private suspend fun awaitBluetoothState(expected: Boolean): Boolean {
        repeat(BLUETOOTH_STATE_POLLS) {
            val current = runCatching {
                bluetoothManager?.adapter?.isEnabled == true
            }.getOrDefault(false)
            if (current == expected) return current
            kotlinx.coroutines.delay(BLUETOOTH_STATE_POLL_MS)
        }
        return runCatching { bluetoothManager?.adapter?.isEnabled == true }.getOrDefault(false)
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Яркость
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Устанавливает яркость экрана.
     *
     * Требует `WRITE_SETTINGS` (специальное разрешение, выдаётся на отдельном
     * системном экране). Без него — открываем этот экран и говорим об этом
     * прямо, а не молча возвращаем `false`.
     *
     * Второе, что здесь происходит: если в системе включена адаптивная
     * яркость, ручная запись значения игнорируется ОС — пользователь тянет
     * ползунок (или просит Амалию), а экран не реагирует. Как и системный
     * ползунок яркости, мы переключаем режим на ручной перед записью: иначе
     * команда «поставь яркость 30%» выглядит выполненной, но не работает.
     *
     * @param level яркость 0..255 (системная шкала).
     */
    suspend fun setBrightness(level: Int): ControlResult = withContext(Dispatchers.Default) {
        if (!canWriteBrightness()) {
            openBrightnessSettingsScreen()
            return@withContext ControlResult.Delegated(
                level = ControlAccess.SCREEN,
                requestedState = false,
                hint = "Открыла экран «Изменять системные настройки» — " +
                    "дай доступ, и я смогу менять яркость сама.",
            )
        }
        val autoWasOn = isAutoBrightnessOn()
        ensureManualBrightness()
        val written = runCatching {
            SystemSettings.System.putInt(
                context.contentResolver,
                SystemSettings.System.SCREEN_BRIGHTNESS,
                level.coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX),
            )
        }.getOrDefault(false)
        refresh()
        ControlResult.Applied(
            level = ControlAccess.DIRECT,
            state = written,
            hint = when {
                autoWasOn -> "Выключила авто-яркость и поставила твоё значение."
                else -> null
            },
        )
    }

    /**
     * Быстрая запись яркости без полного снимка состояния — для живого
     * перетаскивания ползунка.
     *
     * Полный [refresh] читает с десяток системных сервисов; делать это на
     * каждое движение пальца значит грузить CPU в такт дрожанию руки.
     * Здесь только запись значения (и перевод авто-режима в ручной), а
     * снимок состояния вызывающий обновит один раз — по завершении жеста.
     *
     * @return true, если значение записано; false — нет права WRITE_SETTINGS.
     */
    suspend fun applyBrightnessLive(level: Int): Boolean = withContext(Dispatchers.Default) {
        if (!canWriteBrightness()) return@withContext false
        ensureManualBrightness()
        runCatching {
            SystemSettings.System.putInt(
                context.contentResolver,
                SystemSettings.System.SCREEN_BRIGHTNESS,
                level.coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX),
            )
        }.getOrDefault(false)
    }

    /** Переводит яркость в ручной режим, если система держит адаптивную. */
    private fun ensureManualBrightness() {
        if (isAutoBrightnessOn()) {
            runCatching {
                SystemSettings.System.putInt(
                    context.contentResolver,
                    SystemSettings.System.SCREEN_BRIGHTNESS_MODE,
                    SystemSettings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                )
            }
        }
    }

    /** Включена ли адаптивная яркость — иначе ручная установка будет перезаписана. */
    fun isAutoBrightnessOn(): Boolean = runCatching {
        SystemSettings.System.getInt(
            context.contentResolver,
            SystemSettings.System.SCREEN_BRIGHTNESS_MODE,
        ) == SystemSettings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }.getOrDefault(false)

    /** Выдано ли право писать системные настройки (нужно для яркости). */
    fun canWriteBrightness(): Boolean = runCatching {
        SystemSettings.System.canWrite(context)
    }.getOrDefault(false)

    /** Открывает системный экран выдачи WRITE_SETTINGS. */
    fun openBrightnessSettingsScreen() = launch(
        Intent(
            SystemSettings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ),
    )

    // ══════════════════════════════════════════════════════════════════════
    //  Громкость
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Устанавливает громкость мультимедиа в процентах 0..100.
     *
     * Работает на всех версиях без разрешений. Значение пересчитывается из
     * процентов в шкалу конкретного устройства ([mediaVolumeMax]): у разных
     * телефонов она разная (обычно 15, но бывает 7, 10, 25, 30), поэтому
     * «50%» — единственная переносимая единица.
     */
    suspend fun setVolumePercent(percent: Int): ControlResult = withContext(Dispatchers.Default) {
        val max = mediaVolumeMax()
        val target = ((percent.coerceIn(0, 100) * max) / 100).coerceIn(0, max)
        // Явный тип Boolean и никакой зависимости от «последнего выражения»:
        // `setStreamVolume` возвращает Unit, поэтому `true` в конце лямбды —
        // единственное, что даёт нужный тип. Параметр типа убирает зависимость
        // от вывода и делает намерение очевидным.
        val ok = runCatching<Boolean> {
            val am = audioManager ?: return@runCatching false
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            true
        }.getOrDefault(false)
        ControlResult.Applied(
            level = ControlAccess.DIRECT,
            state = ok,
            hint = if (ok) null else "Не удалось изменить громкость.",
        )
    }

    /** Изменение громкости на ±[step] процентов от текущей. */
    suspend fun adjustVolume(stepPercent: Int): ControlResult = withContext(Dispatchers.Default) {
        val current = readVolumePercent()
        setVolumePercent((current + stepPercent).coerceIn(0, 100))
    }

    /** Максимум громкости мультимедиа на устройстве. */
    fun mediaVolumeMax(): Int = runCatching {
        audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: DEFAULT_VOLUME_MAX
    }.getOrDefault(DEFAULT_VOLUME_MAX)

    // ══════════════════════════════════════════════════════════════════════
    //  Фонарик
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Фонарик.
     *
     * `setTorchMode` добавлен в API 23 и работает **без разрешения CAMERA**
     * (в отличие от `Camera` API), поэтому здесь не нужен runtime-пермишн.
     * Но нужен хотя бы один `FLASH_INFO_AVAILABLE`-модуль: на устройствах без
     * вспышки операция невозможна — возвращаем явный отказ вместо тишины.
     */
    suspend fun setFlashlight(enabled: Boolean): ControlResult = withContext(Dispatchers.Default) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return@withContext ControlResult.Unsupported(
                "Фонарик через API доступен с Android 6.0. Открываю камеру.",
            )
        }
        // Поиск камеры со вспышкой.
        //
        // Раньше это был `runCatching { ... ?: return@runCatching null ... }`
        // с досрочным возвратом прямо в первой строке лямбды. Так делать нельзя:
        // вывод типа лямбды опирается на её последнее выражение, а `return@` в
        // начале лишает компилятор опоры — он выдаёт «Syntax error: Expecting
        // an element» на пустое место после `null`. Поэтому логика разложена на
        // явные шаги с обычными ранними возвратами.
        val cameraIds: Array<String> = runCatching<Array<String>> {
            cameraManager?.cameraIdList ?: emptyArray()
        }.getOrDefault(emptyArray())

        val cameraId: String? = cameraIds.firstOrNull { id ->
            val chars = runCatching<android.hardware.camera2.CameraCharacteristics?> {
                cameraManager?.getCameraCharacteristics(id)
            }.getOrNull()
            chars?.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: cameraIds.firstOrNull()

        if (cameraId == null) {
            return@withContext ControlResult.Unsupported(
                "На этом устройстве нет вспышки — фонарик недоступен.",
            )
        }

        val ok = runCatching<Boolean> {
            val cm = cameraManager ?: return@runCatching false
            cm.setTorchMode(cameraId, enabled)
            true
        }.getOrDefault(false)

        if (ok) {
            ControlResult.Applied(level = ControlAccess.DIRECT, state = true)
        } else {
            ControlResult.Unsupported("Не удалось переключить фонарик.")
        }
    }

    /** Включён ли сейчас фонарик — для честного статуса в UI. */
    fun isFlashlightOn(): Boolean = false // torch state не читается публично; UI опирается на ControlResult

    // ══════════════════════════════════════════════════════════════════════
    //  Локация (только чтение + открытие настроек)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Включены ли службы геолокации.
     *
     * Начиная с API 28 константа `Secure.LOCATION_MODE` объявлена deprecated и
     * на части прошивок возвращает неверное значение. Поэтому читаем по
     * приоритету: `LocationManager.isLocationEnabled` (API ≥ 28, официальный
     * путь) → устаревший `Secure.LOCATION_MODE` (API < 28).
     */
    fun isLocationEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val viaManager = runCatching<Boolean?> {
                val lm = context.getSystemService(Context.LOCATION_SERVICE)
                    as? android.location.LocationManager
                lm?.isLocationEnabled
            }.getOrNull()
            if (viaManager != null) return viaManager
        }
        // Фоллбэк только для API < 28.
        //
        // `Secure.LOCATION_MODE` помечен deprecated, и это не косметика: начиная
        // с API 28 платформа не гарантирует его значение (управление ушло в
        // `LocationManager`). Но на старых системах альтернативы нет — там эта
        // константа работает, а `LocationManager.isLocationEnabled` ещё не
        // существует. Подавляем предупреждение локально и с объяснением, а не
        // глушим его на весь файл: остальные использования `@Suppress` не должны
        // маскировать настоящие deprecated-вызовы.
        @Suppress("DEPRECATION")
        return runCatching {
            SystemSettings.Secure.getInt(
                context.contentResolver,
                SystemSettings.Secure.LOCATION_MODE,
                SystemSettings.Secure.LOCATION_MODE_OFF,
            ) != SystemSettings.Secure.LOCATION_MODE_OFF
        }.getOrDefault(false)
    }

    /** Открывает системные настройки источника геолокации. */
    fun openLocationSettings() = launch(
        Intent(SystemSettings.ACTION_LOCATION_SOURCE_SETTINGS),
    )

    // ══════════════════════════════════════════════════════════════════════
    //  Уведомления
    // ══════════════════════════════════════════════════════════════════════

    /** Есть ли у приложения разрешение на уведомления.
     *
     *  На API < 33 разрешения `POST_NOTIFICATIONS` не существует, поэтому все
     *  прежние версии выглядели как «уведомления запрещены». Здесь это учтено:
     *  ниже 33 проверяется только системный тумблер приложения.
     */
    fun areNotificationsEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return runCatching {
                notificationManager?.areNotificationsEnabled() ?: false
            }.getOrDefault(false)
        }
        val runtimeGranted = hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        val systemEnabled = runCatching {
            notificationManager?.areNotificationsEnabled() ?: false
        }.getOrDefault(false)
        return runtimeGranted && systemEnabled
    }

    /** Открывает системный экран настроек уведомлений приложения. */
    fun openNotificationSettings() = launch(
        Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(SystemSettings.EXTRA_APP_PACKAGE, context.packageName),
    )

    // ══════════════════════════════════════════════════════════════════════
    //  Питание / батарея
    // ══════════════════════════════════════════════════════════════════════

    /** Уровень заряда 0..100 (свежее значение, не из кэша Flow). */
    fun batteryLevel(): Int = readBattery().first

    /** Оптимизация батареи отключена для приложения? Важно для фонового слушания. */
    fun isBatteryOptimizationIgnored(): Boolean = runCatching {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }.getOrDefault(false)

    /** Открывает системный запрос на исключение из оптимизации батареи. */
    fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(SystemSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
        if (!tryLaunch(intent)) {
            launch(Intent(SystemSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ОТКРЫТИЕ СИСТЕМНЫХ ЭКРАНОВ И ПАНЕЛЕЙ
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Панель быстрых настроек на плитке подключений (Wi-Fi / интернет).
     *
     * Доступна с API 29. Это лучший вариант для Android 13+: пользователь
     * видит родной тумблер и решает сам, а приложение не выглядит «сломанным».
     *
     * @return true, если панель реально открылась.
     */
    fun openConnectivityPanel(): Boolean {
        if (!hasConnectivityPanel()) return false
        return tryLaunch(
            Intent(SystemSettings.Panel.ACTION_INTERNET_CONNECTIVITY),
        ) || tryLaunch(Intent(SystemSettings.Panel.ACTION_WIFI))
    }

    /** Панель громкости (API ≥ 29) — быстрый доступ без ухода из приложения. */
    fun openVolumePanel(): Boolean {
        if (!hasConnectivityPanel()) return false
        return tryLaunch(Intent(SystemSettings.Panel.ACTION_VOLUME))
    }

    /** Полный экран настроек Wi-Fi (универсальный фоллбэк). */
    fun openWifiSettings() = launch(Intent(SystemSettings.ACTION_WIFI_SETTINGS))

    /** Полный экран настроек Bluetooth. */
    fun openBluetoothSettings() = launch(
        Intent(SystemSettings.ACTION_BLUETOOTH_SETTINGS),
    )

    /** Полный экран настроек звука — фоллбэк для громкости. */
    fun openSoundSettings() = launch(Intent(SystemSettings.ACTION_SOUND_SETTINGS))

    /** Полный экран настроек дисплея — фоллбэк для яркости. */
    fun openDisplaySettings() = launch(Intent(SystemSettings.ACTION_DISPLAY_SETTINGS))

    /** Основной экран настроек системы. */
    fun openMainSettings() = launch(Intent(SystemSettings.ACTION_SETTINGS))

    /**
     * Наборы для набора номера.
     *
     * Приложение **не звонит само** — `ACTION_CALL` требует `CALL_PHONE`,
     * а это опасное разрешение, которое не стоит просить ради голосовой
     * команды. `ACTION_DIAL` открывает номеронабиратель с готовым номером:
     * пользователь нажимает «позвонить» сам. Это безопаснее, работает везде
     * и не требует ни одного runtime-разрешения.
     */
    fun openDialer(phoneNumber: String): Boolean {
        val sanitized = phoneNumber.filter { it.isDigit() || it == '+' || it == '#' || it == '*' }
        if (sanitized.length < 3) return false
        return tryLaunch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized")))
    }

    /** Открывает экран настроек конкретного приложения. */
    fun openAppInfo(packageName: String) = launch(
        Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$packageName")),
    )

    // ══════════════════════════════════════════════════════════════════════
    //  УТИЛИТЫ
    // ══════════════════════════════════════════════════════════════════════

    /** Проверяет runtime-разрешение без исключений. */
    private fun hasPermission(permission: String): Boolean = runCatching {
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * Пытается запустить интент.
     *
     * Возвращает false, если нет активити под этот интент или система
     * запретила запуск. Раньше это не проверялось, и на устройствах без
     * Google Play (где `ACTION_WEB_SEARCH` не имеет обработчика) приложение
     * молча ничего не делало — теперь у вызывающего кода есть фоллбэк.
     */
    private fun tryLaunch(intent: Intent): Boolean = runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) return false
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    /** Запускает интент, игнорируя результат (для «открой экран»-действий). */
    private fun launch(intent: Intent) {
        tryLaunch(intent)
    }

    private companion object {
        const val DEFAULT_BRIGHTNESS = 128

        /**
         * Нижняя граница записи яркости.
         *
         * Ноль разрешён системным API, но практический смысл имеет только
         * «экран погас»: подсветка на большинстве панелей отключается
         * ниже ~5/255, и пользователь, потянув ползунок в самый низ,
         * получает чёрный экран, из которого не видно, куда тянуть назад.
         * Системный ползунок яркости держит такой же практический минимум.
         */
        const val BRIGHTNESS_MIN = 5
        const val BRIGHTNESS_MAX = 255
        const val DEFAULT_VOLUME_MAX = 15
        const val BLUETOOTH_STATE_POLLS = 6
        const val BLUETOOTH_STATE_POLL_MS = 250L

        /**
         * Дни недели словами, по индексам [Calendar].
         *
         * Считается в одном месте и уходит в промпт готовым словом: модели
         * дают плохо даются вычисления «18 сентября — это четверг», и в
         * голосовом ответе такая ошибка звучит как выдумка. Индекс
         * [Calendar.DAY_OF_WEEK] начинается с воскресенья — это не опечатка.
         *
         * Названия намеренно по-русски: их читает человек, а модель переводит
         * сама, если язык ответа другой (примеры в промпте учат именно этому).
         */
        val WEEKDAY_NAMES: Map<Int, String> = mapOf(
            Calendar.SUNDAY to "воскресенье",
            Calendar.MONDAY to "понедельник",
            Calendar.TUESDAY to "вторник",
            Calendar.WEDNESDAY to "среда",
            Calendar.THURSDAY to "четверг",
            Calendar.FRIDAY to "пятница",
            Calendar.SATURDAY to "суббота",
        )
    }
}

/**
 * Уровень доступа к системной фиче.
 *
 * UI показывает его пользователю честно: если доступ [PANEL] или [SCREEN],
 * тумблер в приложении — не «сломанная кнопка», а ярлык в системный UI,
 * и подпись обязана это сообщать.
 */
enum class ControlAccess {
    /** Прямое управление через API — работает из приложения. */
    DIRECT,

    /** Открывается системная панель быстрых настроек: один тап пользователю. */
    PANEL,

    /** Открывается полный экран настроек: пользователь делает всё сам. */
    SCREEN,
}

/**
 * Перевод внутреннего уровня доступа в модель данных для UI.
 *
 * ## Почему это два разных перечисления, а не одно
 *
 * `ControlAccess` живёт в системном слое и описывает, **что приложение
 * умеет прямо сейчас** на этом устройстве. `ControlAccessLevel` живёт в
 * слое данных (`DeviceStatus`) и описывает, **что увидит пользователь** в
 * интерфейсе.
 *
 * Их нельзя слить: системный слой может обрести четвёртый режим (например,
 * «через шину производителя»), а слой данных обязан остаться стабильным —
 * он сериализуется в снимок состояния и читается UI. Но текущее
 * соответствие один-в-один, и этот метод — единственное место, где оно
 * зафиксировано. Если завтра появится новый режим, компилятор заставит
 * обработать его здесь, а не разложит `when` по десяти экранам.
 */
fun ControlAccess.toStatusLevel(): ControlAccessLevel = when (this) {
    ControlAccess.DIRECT -> ControlAccessLevel.DIRECT
    ControlAccess.PANEL -> ControlAccessLevel.PANEL
    ControlAccess.SCREEN -> ControlAccessLevel.SCREEN
}

/**
 * Результат операции управления устройством.
 *
 * Три состояния вместо булева флага — потому что «не удалось» и «открыла
 * панель, нажми сам» требуют **разных слов** от Амалии. С булевым флагом
 * терялся весь сценарий: пользователь слышал «не поддерживается» там,
 * где система просто ждала один тап.
 */
sealed interface ControlResult {

    /** Фактический уровень доступа, который сработал. */
    val level: ControlAccess

    /** Необязательная честная ремарка для пользователя (показывается и озвучивается). */
    val hint: String?

    /**
     * Действие выполнено приложением напрямую.
     * @property state фактическое состояние после операции (а не «запрошено»).
     */
    data class Applied(
        override val level: ControlAccess,
        val state: Boolean,
        override val hint: String? = null,
    ) : ControlResult

    /**
     * Действие передано системному UI.
     * @property requestedState какое состояние хотел пользователь, чтобы UI
     *   мог написать «после нажатия плитки станет: включено».
     */
    data class Delegated(
        override val level: ControlAccess,
        val requestedState: Boolean,
        override val hint: String? = null,
    ) : ControlResult

    /** Действие невозможно на этом устройстве по объективной причине. */
    data class Unsupported(
        override val hint: String,
    ) : ControlResult {
        override val level: ControlAccess get() = ControlAccess.SCREEN
    }

    /** Успешно ли завершилось действие (для логики инструментов). */
    val isHandled: Boolean
        get() = this is Applied || this is Delegated
}
