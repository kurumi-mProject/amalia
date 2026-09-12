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
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.my.amali.R
import com.my.amali.core.di.ServiceLocator
import com.my.amali.data.model.DeviceFeature
import com.my.amali.ui.components.AmaliaScreen
import com.my.amali.ui.components.GlassCard
import com.my.amali.ui.components.GlassDivider
import com.my.amali.ui.components.GlassGroup
import com.my.amali.ui.components.GlassSlider
import com.my.amali.ui.components.SecondaryButton
import com.my.amali.ui.components.SectionTitle
import com.my.amali.ui.components.SettingsStatusRow
import com.my.amali.ui.components.SettingsToggleRow
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Экран «Управление устройством»: Wi-Fi, Bluetooth, яркость, громкость,
 * локация.
 *
 * Дизайн-решение: переключатели собраны в одну стеклянную группу
 * (это системные тумблеры — им не нужны отдельные карточки), а
 * регуляторы вынесены в отдельные карточки-слайдеры, потому что
 * требуют точного жеста и визуального пространства.
 *
 * На Android 13+ прямое переключение Wi-Fi/BT запрещено политикой
 * платформы, поэтому в этом случае тумблер ведёт в системные настройки —
 * и подпись строки честно об этом сообщает.
 */
@Composable
fun DeviceControlSettings(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hub = remember { ServiceLocator.systemControllers }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(hub.status.value) }
    var volumeMax by remember { mutableStateOf(hub.mediaVolumeMax()) }

    LaunchedEffect(Unit) {
        status = hub.refresh()
        volumeMax = hub.mediaVolumeMax()
    }

    val wifiDirect = remember { hub.isDirectToggleSupported(DeviceFeature.WIFI) }
    val btDirect = remember { hub.isDirectToggleSupported(DeviceFeature.BLUETOOTH) }
    val systemHint = stringResource(R.string.permission_open_settings)

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
            SectionTitle(stringResource(R.string.settings_device))

            GlassGroup {
                SettingsToggleRow(
                    icon = Icons.Rounded.Wifi,
                    title = stringResource(R.string.device_wifi),
                    subtitle = if (wifiDirect) {
                        stringResource(
                            if (status.wifiEnabled) R.string.device_status_on
                            else R.string.device_status_off,
                        )
                    } else {
                        systemHint
                    },
                    checked = status.wifiEnabled,
                    onCheckedChange = { requested ->
                        scope.launch {
                            if (wifiDirect) {
                                hub.setWifiEnabled(requested)
                                status = hub.refresh()
                            } else {
                                openSystemSettings(android.provider.Settings.ACTION_WIFI_SETTINGS)
                            }
                        }
                    },
                )
                GlassDivider()
                SettingsToggleRow(
                    icon = Icons.Rounded.Bluetooth,
                    title = stringResource(R.string.device_bluetooth),
                    subtitle = if (btDirect) {
                        stringResource(
                            if (status.bluetoothEnabled) R.string.device_status_on
                            else R.string.device_status_off,
                        )
                    } else {
                        systemHint
                    },
                    checked = status.bluetoothEnabled,
                    onCheckedChange = { requested ->
                        scope.launch {
                            if (btDirect) {
                                hub.setBluetoothEnabled(requested)
                                status = hub.refresh()
                            } else {
                                openSystemSettings(
                                    android.provider.Settings.ACTION_BLUETOOTH_SETTINGS,
                                )
                            }
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
            }

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
                        val applied = hub.setBrightness(status.brightnessLevel)
                        if (!applied) hub.openBrightnessSettingsScreen()
                        status = hub.refresh()
                    }
                },
            )

            Spacer(Modifier.height(Spacing.listGap))

            GlassSlider(
                label = stringResource(R.string.device_volume),
                valueText = "${status.volumeLevel}/$volumeMax",
                value = status.volumeFraction,
                onValueChange = { fraction ->
                    status = status.withVolume((fraction * volumeMax).toInt())
                },
                onValueChangeFinished = {
                    scope.launch {
                        hub.setVolume(status.volumeLevel)
                        status = hub.refresh()
                    }
                },
            )

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
                    icon = Icons.Rounded.OpenInNew,
                    onClick = {
                        openSystemSettings(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            withPackage = true,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}

/**
 * Открывает системный экран настроек по [action].
 * При [withPackage] добавляет URI пакета — нужно для экрана «О приложении».
 */
private fun openSystemSettings(action: String, withPackage: Boolean = false) {
    runCatching {
        val context = ServiceLocator.appContextValue
        val intent = android.content.Intent(action)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (withPackage) {
            intent.data = android.net.Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }
}
