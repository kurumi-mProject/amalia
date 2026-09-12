package com.my.amali.ui.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.my.amali.R
import com.my.amali.ui.components.AmaliaScreen
import com.my.amali.ui.components.GlassCard
import com.my.amali.ui.components.PermissionCard
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing

/** Иконка группы разрешений. */
internal fun AmaliaPermission.icon(): ImageVector = when (this) {
    AmaliaPermission.MICROPHONE -> Icons.Rounded.Mic
    AmaliaPermission.NOTIFICATIONS -> Icons.Rounded.Notifications
    AmaliaPermission.LOCATION_FINE, AmaliaPermission.LOCATION_COARSE -> Icons.Rounded.LocationOn
    AmaliaPermission.CONTACTS -> Icons.Rounded.Contacts
    AmaliaPermission.BLUETOOTH -> Icons.Rounded.BluetoothAudio
    AmaliaPermission.CAMERA -> Icons.Rounded.PhotoCamera
    AmaliaPermission.PHONE -> Icons.Rounded.Call
    AmaliaPermission.MEDIA_AUDIO -> Icons.Rounded.MusicNote
    AmaliaPermission.MEDIA_IMAGES, AmaliaPermission.MEDIA_VIDEO -> Icons.Rounded.Collections
}

/**
 * Экран управления разрешениями.
 *
 * Сверху — прогресс-полоса «выдано N из M»: пользователь видит, сколько
 * осталось, и это превращает скучный список в понятную задачу.
 * Дальше — карточки разрешений, где невыданные идут первыми: то, что
 * требует действия, всегда наверху.
 */
@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }

    val permissions = remember(refreshTick) { AmaliaPermission.requiredForCurrentDevice() }
    val statuses = remember(refreshTick) {
        permissions.associateWith { it.isGranted(context) }
    }
    // Невыданные — вверх: сначала то, что требует действия.
    val ordered = remember(refreshTick) {
        permissions.sortedBy { statuses[it] == true }
    }
    val grantedCount = statuses.values.count { it }

    AmaliaScreen(
        title = stringResource(R.string.privacy_permissions),
        subtitle = "$grantedCount / ${permissions.size}",
        onBack = onBack,
        backLabel = stringResource(R.string.common_back),
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = Spacing.xxs,
                bottom = 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.listGap),
        ) {
            item {
                GlassCard(cornerRadius = Radius.md, elevated = true) {
                    Text(
                        text = stringResource(R.string.permission_rationale_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = stringResource(R.string.settings_privacy_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    PermissionProgress(
                        granted = grantedCount,
                        total = permissions.size,
                    )
                }
            }
            items(ordered, key = { it.name }) { permission ->
                PermissionCardItem(
                    permission = permission,
                    granted = statuses[permission] == true,
                    onGrant = { refreshTick++ },
                )
            }
        }
    }
}

/** Прогресс выданных разрешений: полоса + подпись. */
@Composable
private fun PermissionProgress(granted: Int, total: Int) {
    val fraction = if (total == 0) 0f else granted / total.toFloat()
    val label = "$granted / $total"

    Column(modifier = Modifier.semantics { contentDescription = label }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(Radius.chip))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(Radius.chip))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                            ),
                        ),
                    ),
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(Spacing.xs))
            Text(
                text = stringResource(R.string.privacy_permissions).lowercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Карточка разрешения с реальным системным запросом. */
@Composable
private fun PermissionCardItem(
    permission: AmaliaPermission,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { onGrant() }

    PermissionCard(
        icon = permission.icon(),
        title = stringResource(rationaleTitleFor(permission)),
        rationale = stringResource(rationaleFor(permission)),
        granted = granted,
        grantLabel = stringResource(R.string.permission_grant),
        grantedLabel = stringResource(R.string.device_status_on),
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
private fun rationaleFor(permission: AmaliaPermission): Int = when (permission) {
    AmaliaPermission.MICROPHONE -> R.string.permission_mic_rationale
    AmaliaPermission.NOTIFICATIONS -> R.string.permission_notifications_rationale
    AmaliaPermission.LOCATION_FINE, AmaliaPermission.LOCATION_COARSE ->
        R.string.permission_location_rationale
    AmaliaPermission.CONTACTS -> R.string.permission_contacts_rationale
    AmaliaPermission.BLUETOOTH -> R.string.permission_bluetooth_rationale
    else -> R.string.permission_storage_rationale
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
