package com.my.amali.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.my.amali.ui.components.GlassCard
import com.my.amali.ui.components.GlassDivider
import com.my.amali.ui.components.GlassSlider
import com.my.amali.ui.components.MotifLayer
import com.my.amali.ui.components.MotifSwatch
import com.my.amali.ui.components.GlassGroup
import com.my.amali.ui.components.SectionTitle
import com.my.amali.ui.components.SettingsToggleRow
import com.my.amali.ui.theme.AmaliaMotif
import com.my.amali.ui.theme.AmaliaVisualTheme
import com.my.amali.ui.theme.BioGradientDay
import com.my.amali.ui.theme.CircadianEngine
import com.my.amali.ui.theme.DarkModePreference
import com.my.amali.ui.theme.GlassGradientPalette
import com.my.amali.ui.theme.GradientPalette
import com.my.amali.ui.theme.LocalLightProfile
import com.my.amali.ui.theme.stringRes
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.paletteChip
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.accentGlow
import com.my.amali.ui.theme.bodyTextContrast
import com.my.amali.ui.theme.currentPalette
import com.my.amali.ui.theme.glassSurface
import com.my.amali.ui.theme.iconAccent
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
                    palette = BioGradientDay,
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

            // ── Свет интерфейса: что именно рассчитал движок ──────────
            //
            // Раньше адаптация была невидимой: палитра менялась, а пользователь
            // не знал ни почему, ни «какой сейчас свет». Здесь показываются
            // ровно те величины, которые посчитал [CircadianEngine], — значит
            // индикация физически не может разойтись с фактическим фоном.
            if (settings.useBioTime) {
                SectionTitle(stringResource(R.string.appearance_light_title))
                CircadianLightCard()
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

            // Живая миниатюра выбранного мотива во всю ширину.
            //
            // Статичные свотчи в чипах отвечают на вопрос «что это за форма»,
            // но не на вопрос «как оно двигается». А движение — половина
            // впечатления: лепестки падают медленно и качаются, снег идёт
            // ровно, светлячки блуждают. Здесь мотив показан в его настоящей
            // анимации, поэтому выбор перестаёт быть лотереей.
            MotifPreviewStrip(
                motif = settings.motif,
                density = settings.motifDensity,
            )
            Spacer(Modifier.height(Spacing.sm))

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

            // Когда именно выбранный мотив появляется сам.
            //
            // Без этой подписи «сакура» выглядит как произвольная картинка,
            // и пользователь не понимает, что она привязана к свету: выбрал
            // снег — и он идёт круглый год. Здесь честно сказано, к какой
            // фазе суток мотив привязан, а к какому — не привязан вовсе.
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(
                    R.string.appearance_motif_when,
                    stringResource(motifPeriodRes(settings.motif)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xxs),
            )

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

            // Ползунок «интенсивность стекла» убран: пользователь не обязан
            // тюнить силуэт фона — это работа дизайн-системы. Само значение
            // в настройках осталось (его читает [AmaliaVisuals]) и берётся
            // из сохранённого; просто рычага для случайной поломки больше нет.
            Spacer(Modifier.height(64.dp))        }
    }
}

/**
 * Превью-карточка темы: живой градиент палитры, три точки-акцента
 * (имитация волны) и подпись. Выбранная карточка получает акцентный
 * контур, свечение и галочку.
 *
 * Высота зоны подписи фиксирована ([LabelZone]): «Биофильная релаксация»
 * занимает две строки, «Liquid Glass» — одну. Без фиксированной высоты
 * длинная подпись продавливала свой столбец вниз, и пара карточек
 * стояла криво — правая ниже левой на целую строку.
 */
private val LabelZone = 44.dp

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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .height(LabelZone)
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

/**
 * ════════════════════════════════════════════════════════════════════════
 *  КАРТОЧКА «СВЕТ ИНТЕРФЕЙСА»
 * ════════════════════════════════════════════════════════════════════════
 *
 * Показывает пользователю то, что раньше происходило «под капотом» молча.
 * Это не декоративная сводка: все три величины приходят из одного
 * [LocalLightProfile], то есть из того же расчёта, что управляет фоном,
 * акцентами и тенями. Разойтись они не могут по построению.
 *
 * ## Что и зачем показано
 *
 * 1. **Цветовая температура в кельвинах** — пользователь видит, какой
 *    именно свет выбран прямо сейчас. Это снимает главную претензию к
 *    адаптивным темам: «цвет сам меняется, и непонятно, так задумано или
 *    это баг».
 *
 * 2. **Вклад в подавление мелатонина** — доля от меланопического максимума
 *    (по mel-DER). Вечером полоса короткая и подписана «сон под защитой»:
 *    именно ради этого весь механизм и существует. Цифра честная: она
 *    считается из той же CCT, что задаёт фон.
 *
 * 3. **Контраст текста** — фактическое отношение контраста основного текста
 *    к поверхности, посчитанное той же функцией WCAG, которой пользуется
 *    тема. Если оно ниже целевого (7:1 для тела текста — компенсация
 *    halation), карточка честно скажет об этом, а не промолчит.
 *
 * Отдельная строка про астигматизм — не «сноска для галочки»: примерно у
 * трети людей светлые буквы на тёмном читаются хуже даже при формально
 * проходящем контрасте, и им нужна именно светлая тема. Об этом дешевле
 * сказать в интерфейсе, чем оставить человека думать, что приложение
 * «неудобное».
 */
@Composable
private fun CircadianLightCard() {
    val light = LocalLightProfile.current
    val contrast = bodyTextContrast()

    // Доля подавления мелатонина: mel-DER нормирован к D65 (1.0), поэтому
    // 0.62 при 3500 K честно читается как «62% от дневного вклада».
    val melatoninPercent = (light.melanopicDer * 100).toInt().coerceIn(0, 100)
    val protected = light.circadianStimulus < 0f

    GlassCard(cornerRadius = Radius.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .paletteChip(shape = CircleShape, strength = 1f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.WbTwilight,
                    contentDescription = null,
                    tint = iconAccent(),
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.appearance_light_now,
                        stringResource(light.lightLabel.stringRes),
                        light.cct,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.appearance_light_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        // ── Полоса меланопического вклада ─────────────────────────────
        MelatoninBar(
            percent = melatoninPercent,
            protected = protected,
        )

        Spacer(Modifier.height(Spacing.xs))

        Text(
            text = stringResource(R.string.appearance_melatonin_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        )

        Spacer(Modifier.height(Spacing.md))
        GlassDivider()
        Spacer(Modifier.height(Spacing.md))

        // ── Контраст текста ───────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Contrast,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = stringResource(R.string.appearance_eye_contrast, contrast),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (contrast >= CircadianEngine.TARGET_BODY_CONTRAST) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.appearance_eye_ok),
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.height(Spacing.xxs))

        Text(
            text = stringResource(R.string.appearance_eye_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.xs))

        Text(
            text = stringResource(R.string.appearance_eye_astigmatism),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

/**
 * Полоса «сколько света сейчас подмешивается в циркадную систему».
 *
 * Цвет полосы не декоративный: он меняется с тёплого на холодный ровно по
 * той же границе ([LightProfile.isWarm]), что и сама палитра. Когда вклад
 * мал и свет тёплый, полоса дополнительно подписана — так пользователь
 * понимает, что механизм работает, а не «просто нарисована шкала».
 */
@Composable
private fun MelatoninBar(percent: Int, protected: Boolean) {
    val fraction = (percent / 100f).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(520),
        label = "melatoninBar",
    )

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.appearance_melatonin),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.appearance_melatonin_value, percent),
                style = MaterialTheme.typography.labelLarge,
                color = if (protected) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
            )
        }
        Spacer(Modifier.height(Spacing.xxs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(Radius.chip))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(8.dp)
                    .clip(RoundedCornerShape(Radius.chip))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.primary,
                            ),
                        ),
                    ),
            )
        }
    }
}

/**
 * Полоса-превью выбранного мотива в натуральную величину и в движении.
 *
 * ## Зачем это, если рядом уже есть чипы с миниатюрами
 *
 * Свотч в чипе статичен и размером с ноготь: он отвечает только на вопрос
 * «какая форма». Пользователь выбирает мотив не по форме, а по **впечатлению
 * от движения** — лепестки должны падать медленно и качаться, снег идти
 * ровной стеной, светлячки блуждать. Пока этого не видно, выбор превращается
 * в лотерею: человек ставит снег в июле, не понимая, что снег не сезонный,
 * а просто один из пяти вариантов.
 *
 * Полоса использует **тот же** [MotifLayer], что и главный экран, поэтому
 * показанное движение — не отдельная демонстрация, а ровно то, что будет
 * видно в приложении. Разойтись они не могут.
 *
 * Плотность берётся из пользовательской настройки: если человек убавил
 * густоту, превью обязано показать именно его вариант, а не «полный».
 */
@Composable
private fun MotifPreviewStrip(
    motif: AmaliaMotif,
    density: Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.md)
    val palette = currentPalette

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colorStops = palette.stops.map { it.position to it.color }.toTypedArray(),
                ),
            )
            .border(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = shape,
            ),
    ) {
        // Живой слой: те же частицы, та же физика, та же палитра.
        MotifLayer(
            motif = motif,
            density = density.coerceIn(0.25f, 1f),
            modifier = Modifier.fillMaxSize(),
        )

        // Подпись поверх — чтобы полоса не выглядела просто картинкой.
        Text(
            text = stringResource(R.string.appearance_motif_live),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Spacing.xs),
        )

        if (motif == AmaliaMotif.OFF) {
            // OFF не рисует ничего — объясняем это, а не оставляем пустой бокс.
            Text(
                text = stringResource(R.string.appearance_motif_off_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(Spacing.md),
            )
        }
    }
}

/** Ресурс с описанием фазы суток, к которой привязан мотив. */
@StringRes
private fun motifPeriodRes(motif: AmaliaMotif): Int = when (motif) {
    AmaliaMotif.OFF -> R.string.motif_period_off
    AmaliaMotif.AUTO -> R.string.motif_period_auto
    AmaliaMotif.SAKURA -> R.string.motif_period_sakura
    AmaliaMotif.MAPLE -> R.string.motif_period_maple
    AmaliaMotif.FIREFLY -> R.string.motif_period_firefly
    AmaliaMotif.SNOW -> R.string.motif_period_snow
    AmaliaMotif.STARS -> R.string.motif_period_stars
}
