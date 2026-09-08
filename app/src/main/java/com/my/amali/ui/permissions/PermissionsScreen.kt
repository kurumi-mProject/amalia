package com.my.amali.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.my.amali.R
import com.my.amali.ui.components.GlassCard

/** Иконка, описывающая группу разрешений. */
internal fun AmaliaPermission.icon(): ImageVector = when (this) {
    AmaliaPermission.MICROPHONE -> Icons.Filled.Mic
    AmaliaPermission.NOTIFICATIONS -> Icons.Filled.Notifications
    AmaliaPermission.LOCATION_FINE, AmaliaPermission.LOCATION_COARSE -> Icons.Filled.LocationOn
    AmaliaPermission.CONTACTS -> Icons.Filled.Contacts
    AmaliaPermission.BLUETOOTH -> Icons.Filled.BluetoothAudio
    AmaliaPermission.CAMERA -> Icons.Filled.PhotoCamera
    AmaliaPermission.PHONE -> Icons.Filled.Call
    AmaliaPermission.MEDIA_AUDIO -> Icons.Filled.MusicNote
    AmaliaPermission.MEDIA_IMAGES, AmaliaPermission.MEDIA_VIDEO -> Icons.Filled.Collections
}

/**
 * Полный экран управления разрешениями.
 *
 * Показывает все группы разрешений Амалии с человекочитаемым обоснованием.
 * Кнопка «Разрешить» открывает системный диалог (через rememberLauncherForActivityResult),
 * при повторном отказе предлагает открыть системные настройки приложения.
 *
 * @param onBack возврат на предыдущий экран.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Локальный счётчик-триггер: перечитывать статусы после возврата из системного диалога.
    var refreshTick by remember { mutableStateOf(0) }

    val permissions = remember(refreshTick) { AmaliaPermission.requiredForCurrentDevice() }
    val statuses = remember(refreshTick) {
        permissions.associateWith { it.isGranted(context) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.privacy_permissions)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 20.dp, vertical = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GlassCard {
                    Text(
                        text = stringResource(R.string.permission_rationale_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_privacy_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
            }
            items(permissions, key = { it.name }) { permission ->
                PermissionCardItem(
                    permission = permission,
                    granted = statuses[permission] == true,
                    onGrant = { refreshTick++ },
                )
            }
        }
    }
}

@Composable
private fun rationaleFor(permission: AmaliaPermission): Int = when (permission) {
    AmaliaPermission.MICROPHONE -> R.string.permission_mic_rationale
    AmaliaPermission.NOTIFICATIONS -> R.string.permission_notifications_rationale
    AmaliaPermission.LOCATION_FINE, AmaliaPermission.LOCATION_COARSE -> R.string.permission_location_rationale
    AmaliaPermission.CONTACTS -> R.string.permission_contacts_rationale
    AmaliaPermission.BLUETOOTH -> R.string.permission_bluetooth_rationale
    else -> R.string.permission_storage_rationale
}

/**
 * Карточка одного разрешения с реальным системным запросом.
 * Оборачивает [com.my.amali.ui.components.PermissionCard], подключая
 * rememberLauncherForActivityResult к кнопке «Разрешить».
 */
@Composable
private fun PermissionCardItem(
    permission: AmaliaPermission,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    val context = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { onGrant() }

    val title = stringResource(rationaleTitleFor(permission))
    val rationale = stringResource(rationaleFor(permission))
    val grantLabel = stringResource(R.string.permission_grant)
    val grantedLabel = stringResource(R.string.device_status_on)

    com.my.amali.ui.components.PermissionCard(
        icon = permission.icon(),
        title = title,
        rationale = rationale,
        granted = granted,
        grantLabel = grantLabel,
        grantedLabel = grantedLabel,
        onGrant = {
            val manifest = permission.manifestPermission
            if (manifest != null && !granted) {
                launcher.launch(manifest)
            } else {
                onGrant()
            }
        },
    )
}

@Composable
private fun rationaleTitleFor(permission: AmaliaPermission): Int = when (permission) {
    AmaliaPermission.MICROPHONE -> R.string.privacy_microphone
    AmaliaPermission.NOTIFICATIONS -> R.string.device_notifications
    AmaliaPermission.LOCATION_FINE, AmaliaPermission.LOCATION_COARSE -> R.string.device_location
    AmaliaPermission.CONTACTS -> R.string.device_contacts
    AmaliaPermission.BLUETOOTH -> R.string.privacy_bluetooth
    AmaliaPermission.CAMERA -> R.string.privacy_storage
    AmaliaPermission.PHONE -> R.string.privacy_permissions
    AmaliaPermission.MEDIA_AUDIO, AmaliaPermission.MEDIA_IMAGES, AmaliaPermission.MEDIA_VIDEO ->
        R.string.privacy_storage
}
