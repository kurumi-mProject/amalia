package com.my.amali.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.my.amali.R
import com.my.amali.core.di.ServiceLocator
import com.my.amali.data.model.DeviceFeature
import com.my.amali.ui.components.GlassCard
import com.my.amali.ui.components.SettingsHeader
import com.my.amali.ui.components.SettingsToggleRow
import kotlinx.coroutines.launch

/**
 * Экран «Управление устройством»: Wi-Fi, Bluetooth, яркость, громкость.
 *
 * Состояние фич читается напрямую из системы через [SystemControllerHub].
 * На Android 13+ Wi-Fi/BT переключаются переходом в системные настройки
 * (политика платформы), на более старых — напрямую. Яркость требует
 * системного WRITE_SETTINGS — при отсутствии открывается системный экран.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_device)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f),
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // Wi-Fi
            SettingsHeader(title = stringResource(R.string.device_wifi))
            DeviceToggleCard(
                title = stringResource(R.string.device_wifi),
                enabled = status.wifiEnabled,
                directToggle = hub.isDirectToggleSupported(DeviceFeature.WIFI),
                onToggle = { requested ->
                    scope.launch {
                        if (hub.isDirectToggleSupported(DeviceFeature.WIFI)) {
                            hub.setWifiEnabled(requested)
                            status = hub.refresh()
                        } else {
                            openWifiSettings()
                        }
                    }
                },
            )

            // Bluetooth
            SettingsHeader(title = stringResource(R.string.device_bluetooth))
            DeviceToggleCard(
                title = stringResource(R.string.device_bluetooth),
                enabled = status.bluetoothEnabled,
                directToggle = hub.isDirectToggleSupported(DeviceFeature.BLUETOOTH),
                onToggle = { requested ->
                    scope.launch {
                        if (hub.isDirectToggleSupported(DeviceFeature.BLUETOOTH)) {
                            hub.setBluetoothEnabled(requested)
                            status = hub.refresh()
                        } else {
                            openBluetoothSettings()
                        }
                    }
                },
            )

            // Яркость
            SettingsHeader(title = stringResource(R.string.device_brightness))
            GlassCard {
                Text(
                    text = stringResource(R.string.device_brightness),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = status.brightnessFraction,
                    onValueChange = { fraction ->
                        status = status.withBrightness((fraction * 255).toInt())
                    },
                    onValueChangeFinished = {
                        scope.launch {
                            val ok = hub.setBrightness(status.brightnessLevel)
                            if (!ok) hub.openBrightnessSettingsScreen()
                            status = hub.refresh()
                        }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Text(
                    text = "${status.brightnessLevel}/255",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Громкость
            SettingsHeader(title = stringResource(R.string.device_volume))
            GlassCard {
                Text(
                    text = stringResource(R.string.device_volume),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Slider(
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
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Text(
                    text = "${status.volumeLevel}/$volumeMax",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Локация — только переход в системные настройки.
            SettingsHeader(title = stringResource(R.string.device_location))
            GlassCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.device_location),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(
                                if (status.locationEnabled) R.string.device_status_on
                                else R.string.device_status_off
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.locationEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            },
                        )
                    }
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { hub.openLocationSettings() },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.permission_open_settings))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Переключатель Wi-Fi/BT в едином стиле glass-карточки. */
@Composable
private fun DeviceToggleCard(
    title: String,
    enabled: Boolean,
    directToggle: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    GlassCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        if (enabled) R.string.device_status_on else R.string.device_status_off
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    },
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                enabled = true,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

private fun openWifiSettings() {
    // Переход в системные настройки Wi-Fi (Android 13+).
    try {
        val context = ServiceLocator.appContextValue
        val intent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

private fun openBluetoothSettings() {
    try {
        val context = ServiceLocator.appContextValue
        val intent = android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
