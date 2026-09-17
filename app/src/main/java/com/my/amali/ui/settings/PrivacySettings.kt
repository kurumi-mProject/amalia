package com.my.amali.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.my.amali.ui.components.AmaliaScreen
import com.my.amali.ui.components.GlassDialog
import com.my.amali.ui.components.GlassDivider
import com.my.amali.ui.components.GlassGroup
import com.my.amali.ui.components.SectionTitle
import com.my.amali.ui.components.SettingsActionRow
import com.my.amali.ui.components.SettingsToggleRow
import com.my.amali.ui.components.SettingsValueRow
import com.my.amali.ui.theme.Spacing

/**
 * Экран «Приватность и разрешения»: срок хранения истории,
 * необратимая очистка с подтверждением, переход к разрешениям.
 *
 * Деструктивное действие визуально отделено от остальных и требует
 * подтверждения в стеклянном диалоге — случайно удалить историю нельзя.
 */
@Composable
fun PrivacySettings(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    AmaliaScreen(
        title = stringResource(R.string.settings_privacy),
        subtitle = stringResource(R.string.settings_privacy_desc),
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
            SectionTitle(stringResource(R.string.privacy_data_retention))

            Text(
                text = stringResource(R.string.privacy_data_retention_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = Spacing.xxs,
                    bottom = Spacing.xs,
                ),
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                UserSettings.RETENTION_OPTIONS.forEach { days ->
                    SettingsValueRow(
                        title = retentionTitle(days),
                        subtitle = null,
                        selected = settings.dataRetentionDays == days,
                        onClick = { vm.setDataRetentionDays(days) },
                    )
                }
            }

            SectionTitle(stringResource(R.string.privacy_memory))

            GlassGroup {
                SettingsToggleRow(
                    icon = Icons.Rounded.History,
                    title = stringResource(R.string.privacy_resume_session),
                    subtitle = stringResource(R.string.privacy_resume_session_desc),
                    checked = settings.resumeLastSession,
                    onCheckedChange = { vm.setResumeLastSession(it) },
                )
            }

            SectionTitle(stringResource(R.string.privacy_permissions))

            GlassGroup {
                SettingsActionRow(
                    icon = Icons.Rounded.Security,
                    title = stringResource(R.string.privacy_permissions),
                    subtitle = stringResource(R.string.permission_rationale_title),
                    onClick = onOpenPermissions,
                )
                GlassDivider()
                SettingsActionRow(
                    icon = Icons.Rounded.DeleteSweep,
                    title = stringResource(R.string.privacy_clear_history),
                    subtitle = stringResource(R.string.privacy_clear_history_confirm_desc),
                    onClick = { showClearDialog = true },
                )
            }

            Spacer(Modifier.height(96.dp))
        }
    }

    if (showClearDialog) {
        GlassDialog(
            title = stringResource(R.string.privacy_clear_history_confirm),
            message = stringResource(R.string.privacy_clear_history_confirm_desc),
            confirmLabel = stringResource(R.string.privacy_clear),
            dismissLabel = stringResource(R.string.privacy_cancel),
            destructive = true,
            onConfirm = {
                showClearDialog = false
                vm.clearHistory()
            },
            onDismiss = { showClearDialog = false },
        )
    }
}

/** Человекочитаемая подпись срока хранения. */
@Composable
private fun retentionTitle(days: Int): String = stringResource(
    when (days) {
        7 -> R.string.privacy_retention_7
        30 -> R.string.privacy_retention_30
        90 -> R.string.privacy_retention_90
        else -> R.string.privacy_retention_forever
    },
)
