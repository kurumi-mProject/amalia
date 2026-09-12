package com.my.amali.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.my.amali.BuildConfig
import com.my.amali.R
import com.my.amali.ui.components.AmaliaBadge
import com.my.amali.ui.components.AmaliaScreen
import com.my.amali.ui.components.GlassCard
import com.my.amali.ui.components.GlassDivider
import com.my.amali.ui.components.GlassGroup
import com.my.amali.ui.components.SectionTitle
import com.my.amali.ui.components.SettingsActionRow
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.accentGlow

/**
 * Экран «О приложении»: hero-блок с логотипом и версией,
 * ссылки на разработчика, политику и лицензии.
 *
 * Версия берётся из BuildConfig, а не хардкодится, — цифры на
 * экране всегда совпадают с собранным APK.
 */
@Composable
fun AboutSettings(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionLine = remember {
        "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    AmaliaScreen(
        title = stringResource(R.string.settings_about),
        subtitle = stringResource(R.string.settings_about_desc),
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
            // === Hero ===
            GlassCard(cornerRadius = Radius.lg, elevated = true) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .accentGlow(
                                color = MaterialTheme.colorScheme.primary,
                                alpha = 0.35f,
                                spread = 1.6f,
                            )
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${stringResource(R.string.about_version)} $versionLine",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AmaliaBadge(text = if (BuildConfig.DEBUG) "DEBUG" else "RELEASE")
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(R.string.about_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            SectionTitle(stringResource(R.string.about_developer))

            GlassGroup {
                SettingsActionRow(
                    icon = Icons.Rounded.MailOutline,
                    title = stringResource(R.string.about_developer_name),
                    subtitle = stringResource(R.string.about_contact),
                    onClick = { openUri(context, "mailto:amalia.assistant@proton.me") },
                )
            }

            SectionTitle(stringResource(R.string.about_licenses))

            GlassGroup {
                SettingsActionRow(
                    icon = Icons.Rounded.Shield,
                    title = stringResource(R.string.about_privacy_policy),
                    subtitle = stringResource(R.string.settings_privacy_desc),
                    onClick = { openUri(context, "https://amalia.app/privacy") },
                )
                GlassDivider()
                SettingsActionRow(
                    icon = Icons.Rounded.Description,
                    title = stringResource(R.string.about_terms),
                    subtitle = stringResource(R.string.about_terms),
                    onClick = { openUri(context, "https://amalia.app/terms") },
                )
                GlassDivider()
                SettingsActionRow(
                    icon = Icons.Rounded.Gavel,
                    title = stringResource(R.string.about_licenses),
                    subtitle = "Jetpack Compose · Material 3 · Kotlin",
                    onClick = { openUri(context, "https://amalia.app/licenses") },
                )
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}

/** Открывает внешнюю ссылку; при отсутствии обработчика — тихо игнорирует. */
private fun openUri(context: android.content.Context, uri: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(uri),
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
