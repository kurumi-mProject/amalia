package com.my.amali.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.my.amali.R
import com.my.amali.ui.components.GlassCard
import com.my.amali.ui.components.SettingsActionRow
import com.my.amali.ui.components.SettingsHeader

/**
 * Главный экран настроек: шесть секций + прямой переход на разрешения.
 * Каждая секция — отдельный детальный экран (см. AppearanceSettings и др.).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAppearance: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenDevice: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SettingsViewModel = viewModel()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            GlassCard {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_appearance_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            SettingsHeader(title = stringResource(R.string.settings_appearance))

            SettingsActionRow(
                icon = Icons.Filled.Palette,
                title = stringResource(R.string.appearance_theme),
                subtitle = stringResource(R.string.settings_appearance_desc),
                onClick = onOpenAppearance,
            )
            SettingsActionRow(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.settings_language),
                subtitle = stringResource(R.string.settings_language_desc),
                onClick = onOpenLanguage,
            )
            SettingsActionRow(
                icon = Icons.Filled.Mic,
                title = stringResource(R.string.settings_voice),
                subtitle = stringResource(R.string.settings_voice_desc),
                onClick = onOpenVoice,
            )

            SettingsHeader(title = stringResource(R.string.settings_device))

            SettingsActionRow(
                icon = Icons.Filled.PhoneAndroid,
                title = stringResource(R.string.settings_device),
                subtitle = stringResource(R.string.settings_device_desc),
                onClick = onOpenDevice,
            )
            SettingsActionRow(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.settings_notifications),
                subtitle = stringResource(R.string.settings_notifications_desc),
                onClick = onOpenNotifications,
            )

            SettingsHeader(title = stringResource(R.string.settings_privacy))

            SettingsActionRow(
                icon = Icons.Filled.Security,
                title = stringResource(R.string.privacy_permissions),
                subtitle = stringResource(R.string.privacy_permissions),
                onClick = onOpenPermissions,
            )
            SettingsActionRow(
                icon = Icons.Filled.DarkMode,
                title = stringResource(R.string.settings_privacy),
                subtitle = stringResource(R.string.settings_privacy_desc),
                onClick = onOpenPrivacy,
            )

            SettingsHeader(title = stringResource(R.string.settings_about))

            SettingsActionRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_about_desc),
                onClick = onOpenAbout,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
