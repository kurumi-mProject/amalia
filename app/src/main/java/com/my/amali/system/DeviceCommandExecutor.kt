package com.my.amali.system

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.provider.AlarmClock
import com.my.amali.data.model.DeviceCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Выполняет список [DeviceCommand] полученных от LLM.
 *
 * Возвращает список результатов: для каждой команды — строка
 * что произошло (для логирования / обратной связи в промпт).
 */
class DeviceCommandExecutor(
    private val context: Context,
    private val hub: SystemControllerHub,
) {

    suspend fun execute(commands: List<DeviceCommand>): List<String> =
        commands.map { cmd -> runCatching { dispatch(cmd) }.getOrElse { "ошибка: ${it.message}" } }

    private suspend fun dispatch(cmd: DeviceCommand): String = when (cmd.action) {

        DeviceCommand.Action.SET_WIFI -> {
            val on = cmd.boolValue()
            val ok = hub.setWifiEnabled(on)
            if (ok) "wifi ${if (on) "включён" else "выключен"}"
            else "wifi: не удалось (Android 13+ требует системного экрана)"
        }

        DeviceCommand.Action.SET_BLUETOOTH -> {
            val on = cmd.boolValue()
            val ok = hub.setBluetoothEnabled(on)
            if (ok) "bluetooth ${if (on) "включён" else "выключен"}"
            else "bluetooth: не удалось (Android 12+ требует системного экрана)"
        }

        DeviceCommand.Action.SET_BRIGHTNESS -> {
            val pct = cmd.intValue().coerceIn(0, 100)
            val level = (pct * 255) / 100
            hub.setBrightness(level)
            "яркость → $pct%"
        }

        DeviceCommand.Action.SET_VOLUME -> {
            val pct = cmd.intValue().coerceIn(0, 100)
            val max = hub.mediaVolumeMax()
            val level = (pct * max) / 100
            hub.setVolume(level)
            "громкость → $pct%"
        }

        DeviceCommand.Action.SET_FLASHLIGHT -> {
            val on = cmd.boolValue()
            toggleFlashlight(on)
            "фонарик ${if (on) "включён" else "выключен"}"
        }

        DeviceCommand.Action.SET_TIMER -> {
            val seconds = cmd.intValue()
            val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "таймер на ${seconds / 60} мин"
        }

        DeviceCommand.Action.SET_ALARM -> {
            val time = cmd.value?.toString() ?: "07:00"
            val parts = time.split(":").mapNotNull { it.toIntOrNull() }
            val hour = parts.getOrElse(0) { 7 }
            val minute = parts.getOrElse(1) { 0 }
            val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "будильник на $time"
        }

        DeviceCommand.Action.OPEN_APP -> {
            val pkg = cmd.value?.toString() ?: return "open_app: не указан пакет"
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?: Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("market://details?id=$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "открыто $pkg"
        }

        DeviceCommand.Action.OPEN_SETTINGS -> {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "открыты настройки"
        }

        DeviceCommand.Action.WEB_SEARCH -> {
            val query = cmd.value?.toString() ?: return "web_search: нет запроса"
            val intent = Intent(Intent.ACTION_WEB_SEARCH)
                .putExtra(android.app.SearchManager.QUERY, query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "поиск: $query"
        }
    }

    private suspend fun toggleFlashlight(on: Boolean) = withContext(Dispatchers.IO) {
        runCatching {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cm.cameraIdList.firstOrNull() ?: return@runCatching
            cm.setTorchMode(cameraId, on)
        }
    }

    // ── Хелперы для извлечения значений ──────────────────────────────────

    private fun DeviceCommand.boolValue(): Boolean = when (val v = value) {
        is Boolean -> v
        is String -> v.lowercase() in setOf("true", "on", "вкл", "включить", "1")
        is Number -> v.toInt() != 0
        else -> false
    }

    private fun DeviceCommand.intValue(): Int = when (val v = value) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: 0
        else -> 0
    }
}
