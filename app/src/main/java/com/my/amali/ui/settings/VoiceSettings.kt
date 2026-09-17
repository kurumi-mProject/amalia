package com.my.amali.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.my.amali.R
import com.my.amali.core.di.ServiceLocator
import com.my.amali.ui.components.AmaliaBadge
import com.my.amali.ui.components.AmaliaScreen
import com.my.amali.ui.components.GlassCard
import com.my.amali.ui.components.GlassDivider
import com.my.amali.ui.components.GlassGroup
import com.my.amali.ui.components.GlassSlider
import com.my.amali.ui.components.SectionTitle
import com.my.amali.ui.components.SettingsToggleRow
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing

/**
 * Экран «Голос и речь»: скорость и тон синтеза, авто-слушание,
 * ключевое слово, текущий движок TTS.
 *
 * Значения применяются мгновенно и сразу сохраняются в DataStore,
 * поэтому кнопки «Сохранить» здесь нет — и не нужно.
 */
@Composable
fun VoiceSettings(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()

    AmaliaScreen(
        title = stringResource(R.string.settings_voice),
        subtitle = stringResource(R.string.settings_voice_desc),
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
            SectionTitle(stringResource(R.string.voice_speech_rate))

            GlassSlider(
                label = stringResource(R.string.voice_speech_rate),
                valueText = "×%.1f".format(settings.speechRate),
                value = settings.speechRate,
                valueRange = 0.5f..2f,
                onValueChange = { vm.setSpeechRate(it) },
            )

            Spacer(Modifier.height(Spacing.listGap))

            GlassSlider(
                label = stringResource(R.string.voice_speech_pitch),
                valueText = "×%.1f".format(settings.speechPitch),
                value = settings.speechPitch,
                valueRange = 0.5f..2f,
                onValueChange = { vm.setSpeechPitch(it) },
            )

            SectionTitle(stringResource(R.string.settings_voice))

            GlassGroup {
                SettingsToggleRow(
                    icon = Icons.Rounded.Hearing,
                    title = stringResource(R.string.voice_auto_listen),
                    subtitle = stringResource(R.string.voice_auto_listen_desc),
                    checked = settings.autoListen,
                    onCheckedChange = { vm.setAutoListen(it) },
                )
                GlassDivider()
                SettingsToggleRow(
                    icon = Icons.Rounded.RecordVoiceOver,
                    title = stringResource(R.string.voice_wake_word),
                    subtitle = stringResource(R.string.voice_wake_word_desc),
                    checked = settings.wakeWordEnabled,
                    onCheckedChange = { vm.setWakeWordEnabled(it) },
                )
            }

            SectionTitle(stringResource(R.string.voice_tts_engine))

            TtsEngineCard()

            Spacer(Modifier.height(96.dp))
        }
    }
}

/**
 * Карточка «чем говорит Амалия».
 *
 * ## Почему она перестала быть статичной
 *
 * Раньше здесь были захардкожены `voice_tts_engine_mock` и бейдж «DEMO» —
 * независимо от того, что реально работает. При зашитых ключах Deepgram,
 * Groq и Fish Audio пользователь всё равно видел «Демо (заглушка)»: экран
 * врал о состоянии системы, и по нему невозможно было понять, почему голос
 * звучит не так, как ожидалось.
 *
 * Теперь карточка читает [ServiceLocator.hasLiveKeys] — то же условие, по
 * которому собирается конвейер, — и честно показывает:
 *  — какой движок синтеза подключён (LIVE) и что у него есть страховка;
 *  — либо что работает демо-заглушка (DEMO).
 */
@Composable
private fun TtsEngineCard(modifier: Modifier = Modifier) {
    // Читаем флаг в remember: BuildConfig не меняется в течение жизни процесса.
    val live = remember { runCatching { ServiceLocator.hasLiveKeys }.getOrDefault(false) }

    GlassCard(modifier = modifier, cornerRadius = Radius.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(22.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (live) {
                        // Название продукта — не переводится: это бренд движка.
                        "Fish Audio s2.1-pro"
                    } else {
                        stringResource(R.string.voice_tts_engine_mock)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (live) {
                        stringResource(R.string.voice_tts_fallback_note)
                    } else {
                        stringResource(R.string.ai_engine_not_connected)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            AmaliaBadge(text = if (live) "LIVE" else "DEMO")
        }
    }
}
