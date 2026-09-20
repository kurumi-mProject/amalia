package com.my.amali.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.my.amali.R
import com.my.amali.domain.entity.WaveSettings
import com.my.amali.ui.components.AmaliaButton
import com.my.amali.ui.components.AmaliaScreen
import com.my.amali.ui.components.AmaliaWaveform
import com.my.amali.ui.components.GlassCard
import com.my.amali.ui.components.GlassSlider
import com.my.amali.ui.components.SectionTitle
import com.my.amali.ui.components.SettingsToggleRow
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Экран «Волна»: геометрия и поведение живого индикатора звука.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ЗАЧЕМ ЭТОТ ЭКРАН СУЩЕСТВУЕТ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Волна — единственный объект, который пользователь видит каждую секунду
 * разговора. «Правильной» её формы не существует: кому-то нужны девять
 * широких полос, кому-то двадцать одна тонкая; у кого-то тихий микрофон,
 * и без поднятой чувствительности полосы почти не двигаются. Это вопрос
 * привычки и железа, а не дизайна, поэтому он отдан сюда.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ГЛАВНОЕ РЕШЕНИЕ: ЖИВОЕ ПРЕВЬЮ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Настройка индикатора без индикатора — это подбор числа вслепую. Поэтому
 * сверху стоит **настоящий** [AmaliaWaveform] с **той же** моделью данных,
 * что и на главном экране. Не отдельная «превьюшка с синусоидой», а сам
 * компонент: что видно здесь, то и будет в разговоре, вплоть до пикселя.
 *
 * Чтобы превью не было мёртвым, уровень громкости имитируется [previewLevel]:
 * медленная волна с редкими всплесками — так видно и поведение в тишине,
 * и реакцию на громкий слог.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ПОЧЕМУ НЕТ КНОПКИ «СОХРАНИТЬ»
 * ════════════════════════════════════════════════════════════════════════
 *
 * Каждое движение ползунка сразу уходит в DataStore и немедленно видно в
 * превью. Кнопка сохранения заставила бы держать черновик, сравнивать его
 * с сохранённым и объяснять пользователю разницу между «настроил» и
 * «применил» — три состояния вместо одного.
 */
@Composable
fun WaveSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val wave = settings.wave

    /**
     * Уровень для превью.
     *
     * Считается в композиции, а не в корутине: значение нужно только для
     * отрисовки, и держать ради него отдельный поток — лишняя сущность.
     * [previewLevel] получает время и возвращает 0..1, поэтому анимация
     * живёт от обычной рекомпозиции по кадрам.
     */
    var previewLevel by remember { mutableFloatStateOf(0.35f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = (now - start) / 1_000_000_000f
            previewLevel = previewLevelFor(seconds)
        }
    }

    AmaliaScreen(
        title = stringResource(R.string.settings_wave),
        subtitle = stringResource(R.string.settings_wave_desc),
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Spacer(Modifier.height(Spacing.xs))

            // ── Превью ──────────────────────────────────────────────────
            GlassCard(cornerRadius = Radius.lg) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = stringResource(R.string.settings_wave_preview),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(wave.maxHeight.dp.coerceAtLeast(56.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AmaliaWaveform(
                            level = previewLevel,
                            settings = wave,
                            color = MaterialTheme.colorScheme.primary,
                            isActive = true,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.settings_wave_size,
                            wave.spikeCount,
                            wave.totalWidthDp.roundToInt(),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // ── Геометрия ───────────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_wave_section_shape))

            GlassSlider(
                label = stringResource(R.string.settings_wave_count),
                valueText = "${wave.spikeCount}",
                value = wave.spikeCount.toFloat(),
                valueRange = WaveSettings.COUNT_MIN.toFloat()..WaveSettings.COUNT_MAX.toFloat(),
                description = stringResource(R.string.settings_wave_count_desc),
                onValueChange = { value ->
                    vm.setWave(wave.copy(spikeCount = value.roundToInt()))
                },
            )

            GlassSlider(
                label = stringResource(R.string.settings_wave_width),
                valueText = "${wave.spikeWidth.roundToInt()} dp",
                value = wave.spikeWidth,
                valueRange = WaveSettings.WIDTH_MIN..WaveSettings.WIDTH_MAX,
                description = stringResource(R.string.settings_wave_width_desc),
                onValueChange = { value -> vm.setWave(wave.copy(spikeWidth = value)) },
            )

            GlassSlider(
                label = stringResource(R.string.settings_wave_gap),
                valueText = "${wave.spikeGap.roundToInt()} dp",
                value = wave.spikeGap,
                valueRange = WaveSettings.GAP_MIN..WaveSettings.GAP_MAX,
                description = stringResource(R.string.settings_wave_gap_desc),
                onValueChange = { value -> vm.setWave(wave.copy(spikeGap = value)) },
            )

            GlassSlider(
                label = stringResource(R.string.settings_wave_height),
                valueText = "${wave.maxHeight.roundToInt()} dp",
                value = wave.maxHeight,
                valueRange = WaveSettings.HEIGHT_MIN..WaveSettings.HEIGHT_MAX,
                description = stringResource(R.string.settings_wave_height_desc),
                onValueChange = { value -> vm.setWave(wave.copy(maxHeight = value)) },
            )

            GlassSlider(
                label = stringResource(R.string.settings_wave_radius),
                valueText = String.format(java.util.Locale.US, "%.1f dp", wave.cornerRadius),
                value = wave.cornerRadius,
                valueRange = WaveSettings.RADIUS_MIN..WaveSettings.RADIUS_MAX,
                description = stringResource(R.string.settings_wave_radius_desc),
                onValueChange = { value -> vm.setWave(wave.copy(cornerRadius = value)) },
            )

            // ── Поведение ───────────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_wave_section_react))

            GlassSlider(
                label = stringResource(R.string.settings_wave_sensitivity),
                valueText = String.format(java.util.Locale.US, "×%.1f", wave.sensitivity),
                value = wave.sensitivity,
                valueRange = WaveSettings.SENSITIVITY_MIN..WaveSettings.SENSITIVITY_MAX,
                description = stringResource(R.string.settings_wave_sensitivity_desc),
                onValueChange = { value -> vm.setWave(wave.copy(sensitivity = value)) },
            )

            GlassSlider(
                label = stringResource(R.string.settings_wave_smoothing),
                valueText = "${(wave.smoothing * 100).roundToInt()}%",
                value = wave.smoothing,
                valueRange = WaveSettings.SMOOTHING_MIN..WaveSettings.SMOOTHING_MAX,
                description = stringResource(R.string.settings_wave_smoothing_desc),
                onValueChange = { value -> vm.setWave(wave.copy(smoothing = value)) },
            )

            SettingsToggleRow(
                title = stringResource(R.string.settings_wave_filled),
                subtitle = stringResource(R.string.settings_wave_filled_desc),
                checked = wave.filled,
                onCheckedChange = { value -> vm.setWave(wave.copy(filled = value)) },
            )

            Spacer(Modifier.height(Spacing.xs))

            AmaliaButton(
                text = stringResource(R.string.settings_wave_reset),
                onClick = { vm.setWave(WaveSettings()) },
                primary = false,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/**
 * Уровень громкости для превью: медленная волна с редкими всплесками.
 *
 * Три наложенных колебания с разными периодами (7, 2.3 и 0.7 секунды) дают
 * неповторяющийся рисунок: за то время, что пользователь крутит ползунок,
 * волна успевает показать и тихую речь, и громкий слог. Один синус выглядел
 * бы метрономом — по нему нельзя судить, как настройка ведёт себя на живом
 * голосе.
 */
private fun previewLevelFor(seconds: Float): Float {
    val slow = (sin(seconds * 2.0 * Math.PI / 7.0) + 1.0) / 2.0
    val medium = (sin(seconds * 2.0 * Math.PI / 2.3 + 1.1) + 1.0) / 2.0
    val fast = (sin(seconds * 2.0 * Math.PI / 0.7 + 2.4) + 1.0) / 2.0
    val mixed = slow * 0.55 + medium * 0.30 + fast * 0.15
    // Лёгкая нелинейность: тихие места должны быть действительно тихими,
    // иначе превью «кипит» постоянно и по нему не видно разницы настроек.
    return (mixed * mixed).toFloat().coerceIn(0.02f, 1f)
}
