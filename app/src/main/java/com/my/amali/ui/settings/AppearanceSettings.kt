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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.my.amali.R
import com.my.amali.ui.components.GlassCard
import com.my.amali.ui.components.SettingsHeader
import com.my.amali.ui.components.SettingsToggleRow
import com.my.amali.ui.components.SettingsValueRow
import com.my.amali.ui.theme.AmaliaVisualTheme
import com.my.amali.ui.theme.DarkModePreference

/**
 * Экран «Внешний вид»: выбор визуальной темы (Liquid Glass / Биофильная),
 * режима тёмности, bio-time-адаптации и интенсивности glass-эффекта.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_appearance)) },
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
            SettingsHeader(title = stringResource(R.string.appearance_theme))

            SettingsValueRow(
                title = stringResource(R.string.appearance_theme_glass),
                subtitle = stringResource(R.string.appearance_theme_glass),
                selected = settings.visualTheme == AmaliaVisualTheme.LIQUID_GLASS,
                onClick = { vm.setVisualTheme(AmaliaVisualTheme.LIQUID_GLASS) },
            )
            Spacer(Modifier.height(8.dp))
            SettingsValueRow(
                title = stringResource(R.string.appearance_theme_bio),
                subtitle = stringResource(R.string.appearance_theme_bio),
                selected = settings.visualTheme == AmaliaVisualTheme.BIOPHILIC,
                onClick = { vm.setVisualTheme(AmaliaVisualTheme.BIOPHILIC) },
            )

            SettingsHeader(title = stringResource(R.string.appearance_dark_mode))

            SettingsValueRow(
                title = stringResource(R.string.appearance_dark_system),
                subtitle = null,
                selected = settings.darkModePref == DarkModePreference.SYSTEM,
                onClick = { vm.setDarkModePreference(DarkModePreference.SYSTEM) },
            )
            Spacer(Modifier.height(8.dp))
            SettingsValueRow(
                title = stringResource(R.string.appearance_dark_always),
                subtitle = null,
                selected = settings.darkModePref == DarkModePreference.ALWAYS_DARK,
                onClick = { vm.setDarkModePreference(DarkModePreference.ALWAYS_DARK) },
            )
            Spacer(Modifier.height(8.dp))
            SettingsValueRow(
                title = stringResource(R.string.appearance_dark_never),
                subtitle = null,
                selected = settings.darkModePref == DarkModePreference.ALWAYS_LIGHT,
                onClick = { vm.setDarkModePreference(DarkModePreference.ALWAYS_LIGHT) },
            )

            SettingsHeader(title = stringResource(R.string.appearance_bio_time))

            SettingsToggleRow(
                title = stringResource(R.string.appearance_bio_time),
                subtitle = stringResource(R.string.appearance_bio_time_desc),
                checked = settings.useBioTime,
                onCheckedChange = { vm.setUseBioTime(it) },
                enabled = settings.visualTheme == AmaliaVisualTheme.BIOPHILIC,
            )

            Spacer(Modifier.height(20.dp))
            GlassCard {
                Text(
                    text = stringResource(R.string.appearance_glass_intensity),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.appearance_glass_blur),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(10.dp))
                Slider(
                    value = settings.glassIntensity,
                    onValueChange = { vm.setGlassIntensity(it) },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Text(
                    text = "${(settings.glassIntensity * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
