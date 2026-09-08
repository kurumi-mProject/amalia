package com.my.amali.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.my.amali.R
import com.my.amali.domain.entity.UserSettings
import com.my.amali.ui.components.GlassCard
import com.my.amali.ui.components.SettingsActionRow
import com.my.amali.ui.components.SettingsHeader
import com.my.amali.ui.components.SettingsValueRow

/**
 * Экран «Приватность»: срок хранения истории, полная очистка истории,
 * переход на экран разрешений.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettings(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_privacy)) },
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
            SettingsHeader(title = stringResource(R.string.privacy_data_retention))

            UserSettings.RETENTION_OPTIONS.forEachIndexed { index, days ->
                val title = when (days) {
                    7 -> stringResource(R.string.privacy_retention_7)
                    30 -> stringResource(R.string.privacy_retention_30)
                    90 -> stringResource(R.string.privacy_retention_90)
                    else -> stringResource(R.string.privacy_retention_forever)
                }
                SettingsValueRow(
                    title = title,
                    subtitle = null,
                    selected = settings.dataRetentionDays == days,
                    onClick = { vm.setDataRetentionDays(days) },
                )
                if (index < UserSettings.RETENTION_OPTIONS.size - 1) {
                    Spacer(Modifier.height(8.dp))
                }
            }

            SettingsHeader(title = stringResource(R.string.privacy_clear_history))

            SettingsActionRow(
                title = stringResource(R.string.privacy_clear),
                subtitle = stringResource(R.string.privacy_clear_history_confirm_desc),
                onClick = { showClearDialog = true },
            )

            Spacer(Modifier.height(8.dp))

            SettingsActionRow(
                title = stringResource(R.string.privacy_permissions),
                subtitle = stringResource(R.string.settings_privacy_desc),
                onClick = onOpenPermissions,
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.privacy_clear_history_confirm)) },
            text = { Text(stringResource(R.string.privacy_clear_history_confirm_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        vm.clearHistory()
                    },
                ) {
                    Text(
                        stringResource(R.string.privacy_clear),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.privacy_cancel))
                }
            },
        )
    }
}
