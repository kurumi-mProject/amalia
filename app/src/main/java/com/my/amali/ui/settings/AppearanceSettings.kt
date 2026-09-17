package com.my.amali.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.my.amali.R
import com.my.amali.ui.components.AmaliaScreen
import com.my.amali.ui.components.GlassDivider
import com.my.amali.ui.components.MotifSwatch
import com.my.amali.ui.components.GlassGroup
import com.my.amali.ui.components.GlassSlider
import com.my.amali.ui.components.SectionTitle
import com.my.amali.ui.components.SettingsToggleRow
import com.my.amali.ui.theme.AmaliaMotif
import com.my.amali.ui.theme.AmaliaVisualTheme
import com.my.amali.ui.theme.BioGradientMorning
import com.my.amali.ui.theme.DarkModePreference
import com.my.amali.ui.theme.GlassGradientPalette
import com.my.amali.ui.theme.GradientPalette
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.accentGlow
import com.my.amali.ui.theme.glassSurface
import com.my.amali.ui.theme.previewColors

/**
 * Экран «Внешний вид».
 *
 * Главное решение: тема выбирается не радиокнопкой в списке, а
 * визуальной превью-карточкой — пользователь видит палитру до
 * применения. Ниже — сегментированный переключатель тёмности,
 * bio-time и регулятор интенсивности стекла с живым эффектом.
 */
@Composable
fun AppearanceSettings(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()

    AmaliaScreen(
        title = stringResource(R.string.settings_appearance),
        subtitle = stringResource(R.string.settings_appearance_desc),
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
            SectionTitle(stringResource(R.string.appearance_theme))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                ThemeCard(
                    palette = GlassGradientPalette,
                    label = stringResource(R.string.appearance_theme_glass),
                    accent = MaterialTheme.colorScheme.primary,
                    selected = settings.visualTheme == AmaliaVisualTheme.LIQUID_GLASS,
                    onClick = { vm.setVisualTheme(AmaliaVisualTheme.LIQUID_GLASS) },
                    modifier = Modifier.weight(1f),
                )
                ThemeCard(
                    palette = BioGradientMorning,
                    label = stringResource(R.string.appearance_theme_bio),
                    accent = MaterialTheme.colorScheme.tertiary,
                    selected = settings.visualTheme == AmaliaVisualTheme.BIOPHILIC,
                    onClick = { vm.setVisualTheme(AmaliaVisualTheme.BIOPHILIC) },
                    modifier = Modifier.weight(1f),
                )
            }

            SectionTitle(stringResource(R.string.appearance_dark_mode))

            SegmentedDarkMode(
                selected = settings.darkModePref,
                enabled = settings.visualTheme == AmaliaVisualTheme.BIOPHILIC,
                onSelect = { vm.setDarkModePreference(it) },
            )

            if (settings.visualTheme == AmaliaVisualTheme.LIQUID_GLASS) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.appearance_glass_always_dark),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xxs),
                )
            }

            SectionTitle(stringResource(R.string.appearance_bio_time))

            GlassGroup {
                SettingsToggleRow(
                    icon = Icons.Rounded.Schedule,
                    title = stringResource(R.string.appearance_bio_time),
                    subtitle = stringResource(R.string.appearance_bio_time_desc),
                    checked = settings.useBioTime,
                    onCheckedChange = { vm.setUseBioTime(it) },
                )
            }

            SectionTitle(stringResource(R.string.appearance_motif))

            Text(
                text = stringResource(R.string.appearance_motif_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = Spacing.xxs,
                    bottom = Spacing.xs,
                ),
            )

            // Живой фон этого экрана перекрашивается сразу: выбор мотива
            // видно не в миниатюре, а на всём интерфейсе.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(AmaliaMotif.choices, key = { it.name }) { motif ->
                    MotifChip(
                        motif = motif,
                        selected = settings.motif == motif,
                        onClick = { vm.setMotif(motif) },
                    )
                }
            }

            if (settings.motif != AmaliaMotif.OFF) {
                Spacer(Modifier.height(Spacing.xs))
                GlassSlider(
                    label = stringResource(R.string.appearance_motif_density),
                    description = stringResource(R.string.appearance_motif_desc),
                    valueText = "${(settings.motifDensity * 100).toInt()}%",
                    value = settings.motifDensity,
                    onValueChange = { vm.setMotifDensity(it) },
                )
            }

            SectionTitle(stringResource(R.string.appearance_glass_intensity))

            GlassSlider(
                label = stringResource(R.string.appearance_glass_intensity),
                description = stringResource(R.string.appearance_glass_blur),
                valueText = "${(settings.glassIntensity * 100).toInt()}%",
                value = settings.glassIntensity,
                onValueChange = { vm.setGlassIntensity(it) },
            )

            Spacer(Modifier.height(96.dp))
        }
    }
}

/**
 * Превью-карточка темы: живой градиент палитры, три точки-акцента
 * (имитация волны) и подпись. Выбранная карточка получает акцентный
 * контур, свечение и галочку.
 */
@Composable
private fun ThemeCard(
    palette: GradientPalette,
    label: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.md)
    Column(
        modifier = modifier
            .then(if (selected) Modifier.accentGlow(accent, alpha = 0.30f, spread = 1.2f) else Modifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .border(
                width = if (selected) 1.4.dp else 0.8.dp,
                color = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = label
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops = palette.stops
                            .map { it.position to it.color }
                            .toTypedArray(),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(5) { index ->
                    val height = when (index) {
                        2 -> 26.dp
                        1, 3 -> 18.dp
                        else -> 10.dp
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = height)
                            .clip(RoundedCornerShape(Radius.chip))
                            .background(accent.copy(alpha = if (index == 2) 1f else 0.5f)),
                    )
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.xs)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        )
    }
}

/**
 * Chip выбора декора фона: миниатюра мотива + подпись.
 *
 * Миниатюра статична ([MotifSwatch]) — шесть анимированных канвасов в списке
 * съели бы кадры ради превью, которое смотрят две секунды.
 */
@Composable
private fun MotifChip(
    motif: AmaliaMotif,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(motifLabelRes(motif))
    val (from, to) = remember(motif) { motif.previewColors() }
    val shape = RoundedCornerShape(Radius.md)
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.97f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "motifChip",
    )

    Column(
        modifier = modifier
            .width(96.dp)
            .scale(scale)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(from.copy(alpha = 0.22f), to.copy(alpha = 0.10f)),
                ),
            )
            .border(
                width = if (selected) 1.4.dp else 0.8.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(top = Spacing.xs)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MotifSwatch(
            motif = motif,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
        )
        Spacer(Modifier.height(Spacing.xxs))
        Row(
            modifier = Modifier.padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
    }
}

@StringRes
private fun motifLabelRes(motif: AmaliaMotif): Int = when (motif) {
    AmaliaMotif.OFF -> R.string.motif_off
    AmaliaMotif.AUTO -> R.string.motif_auto
    AmaliaMotif.SAKURA -> R.string.motif_sakura
    AmaliaMotif.MAPLE -> R.string.motif_maple
    AmaliaMotif.FIREFLY -> R.string.motif_firefly
    AmaliaMotif.SNOW -> R.string.motif_snow
    AmaliaMotif.STARS -> R.string.motif_stars
}

/**
 * Сегментированный переключатель тёмности: три варианта в одной
 * стеклянной пилюле. Выбранный сегмент подсвечивается акцентом —
 * это быстрее и компактнее трёх отдельных строк-радиокнопок.
 */
@Composable
private fun SegmentedDarkMode(
    selected: DarkModePreference,
    enabled: Boolean,
    onSelect: (DarkModePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        DarkModePreference.SYSTEM to R.string.appearance_dark_system,
        DarkModePreference.ALWAYS_DARK to R.string.appearance_dark_always,
        DarkModePreference.ALWAYS_LIGHT to R.string.appearance_dark_never,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(shape = RoundedCornerShape(Radius.chip))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (option, labelRes) ->
            val isSelected = option == selected
            val label = stringResource(labelRes)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(Radius.chip))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                    )
                    .clickable(enabled = enabled) { onSelect(option) }
                    .semantics {
                        role = Role.RadioButton
                        this.selected = isSelected
                        contentDescription = label
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
