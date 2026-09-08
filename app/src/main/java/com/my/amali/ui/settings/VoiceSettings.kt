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

/**
 * Экран «Голос»: скорость и высота речи TTS, автопрослушивание,
 * wake-word (заготовка под будущий детектор), тест голоса.
 * Все значения применяются мгновенно и сохраняются в DataStore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettings(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_voice)) },
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
            GlassCard {
                Text(
                    text = stringResource(R.string.voice_speech_rate),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = settings.speechRate,
                    onValueChange = { vm.setSpeechRate(it) },
                    valueRange = 0.5f..2f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Text(
                    text = "×" + String.format("%.1f", settings.speechRate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(12.dp))

            GlassCard {
                Text(
                    text = stringResource(R.string.voice_speech_pitch),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = settings.speechPitch,
                    onValueChange = { vm.setSpeechPitch(it) },
                    valueRange = 0.5f..2f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Text(
                    text = "×" + String.format("%.1f", settings.speechPitch),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            SettingsHeader(title = stringResource(R.string.settings_voice))

            SettingsToggleRow(
                title = stringResource(R.string.voice_auto_listen),
                subtitle = stringResource(R.string.voice_auto_listen_desc),
                checked = settings.autoListen,
                onCheckedChange = { vm.setAutoListen(it) },
            )
            Spacer(Modifier.height(8.dp))
            SettingsToggleRow(
                title = stringResource(R.string.voice_wake_word),
                subtitle = stringResource(R.string.voice_wake_word_desc),
                checked = settings.wakeWordEnabled,
                onCheckedChange = { vm.setWakeWordEnabled(it) },
            )

            Spacer(Modifier.height(16.dp))
            GlassCard {
                Text(
                    text = stringResource(R.string.voice_tts_engine),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.voice_tts_engine_mock),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
