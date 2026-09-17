package com.my.amali.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.my.amali.R
import com.my.amali.core.di.ServiceLocator
import com.my.amali.data.model.ControlAccessLevel
import com.my.amali.ui.components.AmaliaScreen
import com.my.amali.ui.components.GlassCard
import com.my.amali.ui.components.GlassDivider
import com.my.amali.ui.components.GlassGroup
import com.my.amali.ui.components.GlassSlider
import com.my.amali.ui.components.SecondaryButton
import com.my.amali.ui.components.SectionTitle
import com.my.amali.ui.components.SettingsStatusRow
import com.my.amali.ui.components.SettingsToggleRow
import com.my.amali.system.ControlResult
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Экран «Управление устройством».
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ГЛАВНОЕ РЕШЕНИЕ: честный тумблер вместо мёртвого
 * ════════════════════════════════════════════════════════════════════════
 *
 * Раньше на Android 13+ тумблер Wi-Fi и Bluetooth выглядел как обычный
 * переключатель, но при нажатии открывал настройки — и это читалось как
 * «кнопка сломана». Пользователь не понимал, почему одно работает, а другое
 * нет.
 *
 * Теперь глубина доступа ([ControlAccessLevel]) видна заранее по подписи
 * строки:
 *
 *  — [ControlAccessLevel.DIRECT] → «Включено» / «Выключено». Тумблер работает
 *    здесь и сейчас;
 *  — [ControlAccessLevel.PANEL] → «Откроется панель: один тап». Пользователь
 *    заранее знает, что приложение не переключит само, но доведёт до места;
 *  — [ControlAccessLevel.SCREEN] → «Откроются настройки». Самый слабый
 *    доступ, и он тоже заявлен прямо, а не спрятан за молчанием.
 *
 * Это соответствует правилу из [SystemControllerHub]: ни один метод
 * управления не возвращает «просто отказ» — каждый сообщает, **что именно**
 * произошло. UI обязан передать это пользователю теми же словами.
 *
 * Второе решение: результат действия не проглатывается. Если прямое
 * переключение не сработало (например, включена авто-яркость и она
 * перебивает ручную установку), пользователь видит пояснение внизу экрана,
 * а не думает, что приложение врёт.
 */
@Composable
fun DeviceControlSettings(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hub = remember { ServiceLocator.systemControllers }
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf(hub.status.value) }
    var volumeMax by remember { mutableIntStateOf(hub.mediaVolumeMax()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var flashlightOn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        status = hub.refresh()
        volumeMax = hub.mediaVolumeMax()
    }

    // Тексты уровней доступа берём из ресурсов: подписи — часть интерфейса,
    // а не сообщения об ошибке, поэтому обязаны локализоваться.
    val directLabel = stringResource(R.string.device_access_direct)
    val panelLabel = stringResource(R.string.device_access_panel)
    val screenLabel = stringResource(R.string.device_access_screen)

    AmaliaScreen(
        title = stringResource(R.string.settings_device),
        subtitle = stringResource(R.string.settings_device_desc),
        onBack = onBack,
        backLabel = stringResource(R.string.common_back),
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
        ) {
            // ── Тумблеры с указанием доступной глубины управления ──────
            SectionTitle(stringResource(R.string.settings_device))

            GlassGroup {
                ToggleRowForFeature(
                    icon = Icons.Rounded.Wifi,
                    title = stringResource(R.string.device_wifi),
                    enabled = status.wifiEnabled,
                    access = status.wifiAccess,
                    directLabel = directLabel,
                    panelLabel = panelLabel,
                    screenLabel = screenLabel,
                    onToggle = { requested ->
                        scope.launch {
                            notice = hub.setWifiEnabled(requested).userNotice()
                            status = hub.refresh()
                        }
                    },
                )
                GlassDivider()
                ToggleRowForFeature(
                    icon = Icons.Rounded.Bluetooth,
                    title = stringResource(R.string.device_bluetooth),
                    enabled = status.bluetoothEnabled,
                    access = status.bluetoothAccess,
                    directLabel = directLabel,
                    panelLabel = panelLabel,
                    screenLabel = screenLabel,
                    onToggle = { requested ->
                        scope.launch {
                            notice = hub.setBluetoothEnabled(requested).userNotice()
                            status = hub.refresh()
                        }
                    },
                )
                GlassDivider()
                SettingsToggleRow(
                    icon = Icons.Rounded.FlashlightOn,
                    title = stringResource(R.string.device_flashlight),
                    subtitle = stringResource(
                        if (flashlightOn) R.string.device_status_on
                        else R.string.device_status_off,
                    ),
                    checked = flashlightOn,
                    onCheckedChange = { requested ->
                        scope.launch {
                            val result = hub.setFlashlight(requested)
                            if (result is ControlResult.Applied) {
                                flashlightOn = requested
                            }
                            notice = result.userNotice()
                        }
                    },
                )
                GlassDivider()
                SettingsStatusRow(
                    icon = Icons.Rounded.LocationOn,
                    title = stringResource(R.string.device_location),
                    status = stringResource(
                        if (status.locationEnabled) R.string.device_status_on
                        else R.string.device_status_off,
                    ),
                    active = status.locationEnabled,
                    onClick = { hub.openLocationSettings() },
                )
                GlassDivider()
                SettingsStatusRow(
                    icon = Icons.Rounded.Notifications,
                    title = stringResource(R.string.device_notifications),
                    status = stringResource(
                        if (status.hasNotificationPermission) R.string.device_status_on
                        else R.string.device_status_off,
                    ),
                    active = status.hasNotificationPermission,
                    onClick = { hub.openNotificationSettings() },
                )
            }

            // ── Яркость ────────────────────────────────────────────────
            SectionTitle(stringResource(R.string.device_brightness))

            GlassSlider(
                label = stringResource(R.string.device_brightness),
                valueText = "${(status.brightnessFraction * 100).toInt()}%",
                value = status.brightnessFraction,
                onValueChange = { fraction ->
                    status = status.withBrightness((fraction * 255).toInt())
                },
                onValueChangeFinished = {
                    scope.launch {
                        notice = hub.setBrightness(status.brightnessLevel).userNotice()
                        status = hub.refresh()
                    }
                },
            )

            Spacer(Modifier.height(Spacing.listGap))

            // ── Громкость ──────────────────────────────────────────────
            GlassSlider(
                label = stringResource(R.string.device_volume),
                valueText = "${(status.volumeFraction * 100).toInt()}%",
                value = status.volumeFraction,
                onValueChange = { fraction ->
                    status = status.withVolume((fraction * 100).toInt())
                },
                onValueChangeFinished = {
                    scope.launch {
                        notice = hub.setVolumePercent(status.volumeLevel).userNotice()
                        status = hub.refresh()
                    }
                },
            )

            Spacer(Modifier.height(Spacing.listGap))

            // Пояснение, почему громкость в процентах, а не в «шагах»:
            // у разных телефонов шкала разная, и это снимает вопрос «почему 15, а не 100».
            Text(
                text = stringResource(R.string.device_volume_note, volumeMax),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(
                    start = Spacing.xxs,
                    top = Spacing.xs,
                    end = Spacing.xxs,
                ),
            )

            // ── Батарея и фоновое слушание ─────────────────────────────
            SectionTitle(stringResource(R.string.device_battery))

            GlassCard(cornerRadius = Radius.md) {
                val percent = status.batteryLevel
                Text(
                    text = stringResource(
                        R.string.device_battery_level,
                        percent,
                        stringResource(
                            if (status.isCharging) R.string.device_battery_charging
                            else R.string.device_battery_discharging,
                        ),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = stringResource(R.string.device_battery_rationale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                SecondaryButton(
                    text = stringResource(R.string.device_battery_exempt),
                    icon = Icons.Rounded.BatteryFull,
                    onClick = {
                        if (hub.isBatteryOptimizationIgnored()) {
                            hub.openAppInfo(ServiceLocator.appContextValue.packageName)
                        } else {
                            hub.requestIgnoreBatteryOptimizations()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Честный результат последнего действия ──────────────────
            notice?.let { message ->
                Spacer(Modifier.height(Spacing.listGap))
                GlassCard(cornerRadius = Radius.md, tint = MaterialTheme.colorScheme.tertiary) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            SectionTitle(stringResource(R.string.settings_privacy))

            GlassCard(cornerRadius = Radius.md) {
                Text(
                    text = stringResource(R.string.permission_bluetooth_rationale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                SecondaryButton(
                    text = stringResource(R.string.permission_open_settings),
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = {
                        runCatching {
                            val context = ServiceLocator.appContextValue
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            )
                                .setData(
                                    android.net.Uri.fromParts(
                                        "package",
                                        context.packageName,
                                        null,
                                    ),
                                )
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}

/**
 * Строка-тумблер, чья подпись заранее объясняет глубину доступа.
 *
 * Ключевая деталь: **подпись сообщает, что произойдёт при нажатии**, а не
 * только текущее состояние. Для [ControlAccessLevel.DIRECT] это «Включено»,
 * для [PANEL]/[SCREEN] — «Откроется панель» / «Откроются настройки».
 * Благодаря этому переход в системный UI не воспринимается как поломка.
 */
@Composable
private fun ToggleRowForFeature(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    enabled: Boolean,
    access: ControlAccessLevel,
    directLabel: String,
    panelLabel: String,
    screenLabel: String,
    onToggle: (Boolean) -> Unit,
) {
    val subtitle = when (access) {
        ControlAccessLevel.DIRECT -> "$directLabel · ${onOff(enabled)}"
        ControlAccessLevel.PANEL -> panelLabel
        ControlAccessLevel.SCREEN -> screenLabel
    }
    SettingsToggleRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        checked = enabled,
        onCheckedChange = onToggle,
    )
}

@Composable
private fun onOff(enabled: Boolean): String = stringResource(
    if (enabled) R.string.device_status_on else R.string.device_status_off,
)

/**
 * Превращает [ControlResult] в текст для пользователя.
 *
 * Возвращает null, когда объяснять нечего (прямой успех без оговорок) —
 * иначе экран зарастал бы сообщениями на каждое действие.
 */
private fun ControlResult.userNotice(): String? = when (this) {
    is ControlResult.Applied -> hint
    is ControlResult.Delegated -> hint
    is ControlResult.Unsupported -> hint
}
