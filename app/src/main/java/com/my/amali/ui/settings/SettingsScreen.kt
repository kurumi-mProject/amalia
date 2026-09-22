package com.my.amali.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.my.amali.R
import com.my.amali.ui.icons.AmaliaWave
import com.my.amali.ui.components.AmaliaScreen
import com.my.amali.ui.components.GlassCard
import com.my.amali.ui.components.GlassDivider
import com.my.amali.ui.components.GlassGroup
import com.my.amali.ui.components.SectionTitle
import com.my.amali.ui.components.SettingsActionRow
import com.my.amali.ui.theme.AmaliaVisualTheme
import com.my.amali.ui.theme.DarkModePreference
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing

/**
 * Главный экран настроек.
 *
 * Структура: карточка-профиль текущей конфигурации сверху (тема,
 * язык, голос — одним взглядом), затем три группы настроек в
 * стеклянных блоках с разделителями. Группы отделены мелкими
 * разреженными заголовками, а не пустотой, — список читается быстро.
 */
@Composable
fun SettingsScreen(
    onOpenApiKeys: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenWave: () -> Unit,
    onOpenDevice: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()

    AmaliaScreen(
        title = stringResource(R.string.settings_title),
        subtitle = stringResource(R.string.settings_appearance_desc),
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
        ) {
            // === Сводка конфигурации ===
            GlassCard(cornerRadius = Radius.lg, elevated = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = settings.visualTheme.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = darkModeLabel(settings.darkModePref),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SummaryChip(
                        text = when (settings.selectedLanguage.isSystem) {
                            true -> stringResource(R.string.language_follow_system)
                            false -> settings.selectedLanguage.nativeName
                        },
                    )
                    SummaryChip(text = "×%.1f".format(settings.speechRate))
                    SummaryChip(
                        text = if (settings.wakeWordEnabled) {
                            stringResource(R.string.voice_wake_word)
                        } else {
                            stringResource(R.string.device_status_off)
                        },
                    )
                }
            }

            // === Интерфейс ===
            SectionTitle(stringResource(R.string.settings_appearance))
            GlassGroup {
                SettingsActionRow(
                    icon = Icons.Rounded.Palette,
                    title = stringResource(R.string.appearance_theme),
                    subtitle = stringResource(R.string.settings_appearance_desc),
                    value = settings.visualTheme.displayName,
                    onClick = onOpenAppearance,
                )
                GlassDivider()
                SettingsActionRow(
                    icon = Icons.Rounded.Language,
                    title = stringResource(R.string.settings_language),
                    subtitle = stringResource(R.string.settings_language_desc),
                    value = if (settings.selectedLanguage.isSystem) {
                        null
                    } else {
                        settings.selectedLanguage.code.uppercase()
                    },
                    onClick = onOpenLanguage,
                )
                GlassDivider()
                // Строка волны стоит в группе интерфейса: она настраивает
                // не голос, а его отображение на главном экране.
                SettingsActionRow(
                    icon = AmaliaWave,
                    title = stringResource(R.string.settings_wave),
                    subtitle = stringResource(R.string.settings_wave_desc),
                    value = stringResource(
                        R.string.settings_wave_value,
                        settings.wave.spikeCount,
                    ),
                    onClick = onOpenWave,
                )
                GlassDivider()
                SettingsActionRow(
                    icon = Icons.Rounded.Key,
                    title = stringResource(R.string.settings_api_title),
                    subtitle = stringResource(R.string.settings_api_desc),
                    // Значение-сводка: сколько провайдеров переведено на свои
                    // ключи. Пусто, пока не настроен ни один, — тогда строка
                    // выглядит как обычный переход, а не как «почти настроено».
                    value = settings.api.configuredProviders
                        .takeIf { it > 0 }
                        ?.let { "$it/3" },
                    onClick = onOpenApiKeys,
                )
            }
            // === Устройство ===
            SectionTitle(stringResource(R.string.settings_device))
            GlassGroup {
                SettingsActionRow(
                    icon = Icons.Rounded.PhoneAndroid,
                    title = stringResource(R.string.settings_device),
                    subtitle = stringResource(R.string.settings_device_desc),
                    onClick = onOpenDevice,
                )
                GlassDivider()
                SettingsActionRow(
                    icon = Icons.Rounded.Apps,
                    title = stringResource(R.string.settings_apps),
                    subtitle = stringResource(R.string.settings_apps_desc),
                    onClick = onOpenApps,
                )
                GlassDivider()
                SettingsActionRow(
                    icon = Icons.Rounded.Notifications,
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(R.string.settings_notifications_desc),
                    onClick = onOpenNotifications,
                )
            }

            // === Данные и приватность ===
            SectionTitle(stringResource(R.string.settings_privacy))
            GlassGroup {
                SettingsActionRow(
                    icon = Icons.Rounded.Security,
                    title = stringResource(R.string.privacy_permissions),
                    subtitle = stringResource(R.string.permission_rationale_title),
                    onClick = onOpenPermissions,
                )
                GlassDivider()
                SettingsActionRow(
                    icon = Icons.Rounded.DarkMode,
                    title = stringResource(R.string.settings_privacy),
                    subtitle = stringResource(R.string.settings_privacy_desc),
                    value = retentionLabel(settings.dataRetentionDays),
                    onClick = onOpenPrivacy,
                )
            }

            // === О приложении ===
            SectionTitle(stringResource(R.string.settings_about))
            GlassGroup {
                SettingsActionRow(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.settings_about),
                    subtitle = stringResource(R.string.settings_about_desc),
                    value = "1.0.0",
                    onClick = onOpenAbout,
                )
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}

/** Мелкий чип-факт в карточке-сводке. */
@Composable
private fun SummaryChip(text: String) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(Radius.chip))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun darkModeLabel(pref: DarkModePreference): String = stringResource(
    when (pref) {
        DarkModePreference.SYSTEM -> R.string.appearance_dark_system
        DarkModePreference.ALWAYS_DARK -> R.string.appearance_dark_always
        DarkModePreference.ALWAYS_LIGHT -> R.string.appearance_dark_never
    },
)

@Composable
private fun retentionLabel(days: Int): String = stringResource(
    when (days) {
        7 -> R.string.privacy_retention_7
        30 -> R.string.privacy_retention_30
        90 -> R.string.privacy_retention_90
        else -> R.string.privacy_retention_forever
    },
)
