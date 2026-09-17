package com.my.amali.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.blend
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import java.util.Calendar

// ════════════════════════════════════════════════════════════
//  ENUM: Визуальный стиль
// ════════════════════════════════════════════════════════════

enum class AmaliaVisualTheme(val displayName: String) {
    LIQUID_GLASS("Liquid Glass"),
    BIOPHILIC("Биофильная релаксация"),
}

enum class DarkModePreference {
    SYSTEM,
    ALWAYS_DARK,
    ALWAYS_LIGHT,
}

enum class BioTimeOfDay(val hourRange: IntRange) {
    MORNING(6..11),
    DAY(12..17),
    EVENING(18..22),
    NIGHT(23..5);

    companion object {
        fun fromHour(hour: Int): BioTimeOfDay =
            entries.firstOrNull { hour in it.hourRange } ?: NIGHT
    }
}

// ════════════════════════════════════════════════════════════
//  COLOR SCHEMES
// ════════════════════════════════════════════════════════════

private val GlassDarkScheme = darkColorScheme(
    primary = GlassAccentDim,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = GlassAccentGlow,
    onPrimaryContainer = GlassTextPrimary,
    inversePrimary = GlassAccentSoft,
    secondary = GlassAccentSoft,
    onSecondary = GlassBgBase,
    secondaryContainer = GlassBgSurfaceHigh,
    onSecondaryContainer = GlassTextPrimary,
    tertiary = GlassAccentMist,
    onTertiary = GlassBgBase,
    background = GlassBgBase,
    onBackground = GlassTextPrimary,
    surface = GlassBgSurface,
    onSurface = GlassTextPrimary,
    surfaceVariant = GlassBgSurfaceHigh,
    onSurfaceVariant = GlassTextSecondary,
    surfaceTint = GlassAccentDim,
    surfaceContainerHighest = GlassBgSurfaceHigh,
    surfaceContainerHigh = GlassBgGlass,
    surfaceContainer = GlassBgSurface,
    surfaceContainerLow = GlassBgBase,
    surfaceContainerLowest = GlassBgBase,
    outline = GlassBgStroke,
    outlineVariant = GlassBgStroke.copy(alpha = 0.55f),
    scrim = Color(0x00000000),
    error = GlassStateError,
    onError = GlassBgBase,
    errorContainer = GlassStateError.copy(alpha = 0.16f),
    onErrorContainer = GlassStateError,
)

private val BioLightScheme = lightColorScheme(
    primary = BioAccentPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = BioAccentPrimary.copy(alpha = 0.16f),
    onPrimaryContainer = BioTextPrimary,
    secondary = BioAccentSecondary,
    onSecondary = BioTextPrimary,
    secondaryContainer = BioBgSurfaceHigh,
    onSecondaryContainer = BioTextPrimary,
    tertiary = BioAccentTertiary,
    onTertiary = Color(0xFFFFFFFF),
    background = BioBgBase,
    onBackground = BioTextPrimary,
    surface = BioBgSurface,
    onSurface = BioTextPrimary,
    surfaceVariant = BioBgSurfaceHigh,
    onSurfaceVariant = BioTextSecondary,
    surfaceTint = BioAccentPrimary,
    surfaceContainerHighest = BioBgSurfaceHigh,
    surfaceContainerHigh = BioBgGlass,
    surfaceContainer = BioBgSurface,
    surfaceContainerLow = BioBgBase,
    surfaceContainerLowest = BioBgBase,
    outline = BioBgStroke,
    outlineVariant = BioBgStroke.copy(alpha = 0.55f),
    error = BioStateError,
    onError = Color(0xFFFFFFFF),
)

private val BioDarkScheme = darkColorScheme(
    primary = BioAccentSecondary,
    onPrimary = BioDarkBgBase,
    primaryContainer = BioAccentSecondary.copy(alpha = 0.18f),
    onPrimaryContainer = BioDarkTextPrimary,
    secondary = BioAccentPrimary,
    onSecondary = BioDarkTextPrimary,
    secondaryContainer = BioDarkBgSurfaceHigh,
    onSecondaryContainer = BioDarkTextPrimary,
    tertiary = BioTerracotta,
    onTertiary = BioDarkTextPrimary,
    background = BioDarkBgBase,
    onBackground = BioDarkTextPrimary,
    surface = BioDarkBgSurface,
    onSurface = BioDarkTextPrimary,
    surfaceVariant = BioDarkBgSurfaceHigh,
    onSurfaceVariant = BioDarkTextSecondary,
    surfaceTint = BioAccentSecondary,
    surfaceContainerHighest = BioDarkBgSurfaceHigh,
    surfaceContainerHigh = BioDarkBgGlass,
    surfaceContainer = BioDarkBgSurface,
    surfaceContainerLow = BioDarkBgBase,
    surfaceContainerLowest = BioDarkBgBase,
    outline = BioDarkBgStroke,
    outlineVariant = BioDarkBgStroke.copy(alpha = 0.55f),
    error = BioStateError,
    onError = BioDarkBgBase,
)

// ════════════════════════════════════════════════════════════
//  THEME COMPOSABLE
// ════════════════════════════════════════════════════════════

/**
 * Главная тема приложения.
 *
 * Ключевое решение: акцентные цвета **следуют за палитрой фона**, а не живут
 * отдельно. Раньше «время суток» меняло только градиент за спиной, из-за чего
 * интерфейс выглядел перекрашенным наполовину: тёмная амура + всегда один и
 * тот же индиго акцент. Теперь `primary/secondary/tertiary` подмешивают себе
 * aurora-тона текущей палитры, поэтому и текст, и иконки, и волна, и кнопки
 * дышат одним временем суток.
 *
 * Подмешивание умеренное (≈30%) и обязательно калибруется по контрасту:
 * на светлой подложке слишком светлый акцент был бы нечитаем, поэтому для
 * светлых схем тон затемняется.
 */
@Composable
fun AmaliaTheme(
    darkModePref: DarkModePreference = DarkModePreference.SYSTEM,
    visualTheme: AmaliaVisualTheme = AmaliaVisualTheme.LIQUID_GLASS,
    useBioTime: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (darkModePref) {
        DarkModePreference.SYSTEM -> systemDark
        DarkModePreference.ALWAYS_DARK -> true
        DarkModePreference.ALWAYS_LIGHT -> false
    }

    val hour = currentTimeHour(useBioTime)
    val bioTime = if (useBioTime) BioTimeOfDay.fromHour(hour) else null

    val effectiveDark = when (visualTheme) {
        AmaliaVisualTheme.LIQUID_GLASS -> true
        AmaliaVisualTheme.BIOPHILIC -> bioTime?.let {
            it == BioTimeOfDay.EVENING || it == BioTimeOfDay.NIGHT
        } ?: isDark
    }

    val palette = currentGradientPalette(visualTheme, darkModePref, useBioTime, hour)

    val baseScheme = when (visualTheme) {
        AmaliaVisualTheme.LIQUID_GLASS -> GlassDarkScheme
        AmaliaVisualTheme.BIOPHILIC -> if (effectiveDark) BioDarkScheme else BioLightScheme
    }
    val colorScheme = tintScheme(baseScheme, palette, effectiveDark)

    // Стекло тёмной темы — дымчатое и контурное; светлой — молочное и мягкое.
    val glassStyle = when {
        visualTheme == AmaliaVisualTheme.LIQUID_GLASS -> GlassStyle(
            fill = 0.52f,
            border = 0.13f,
            highlight = 0.11f,
            glow = 0.16f,
            light = false,
        )
        effectiveDark -> GlassStyle(
            fill = 0.60f,
            border = 0.11f,
            highlight = 0.08f,
            glow = 0.14f,
            light = false,
        )
        else -> GlassStyle(
            fill = 0.74f,
            border = 0.55f,
            highlight = 0.55f,
            glow = 0.10f,
            light = true,
        )
    }

    val typography = when (visualTheme) {
        AmaliaVisualTheme.LIQUID_GLASS -> GlassTypography
        AmaliaVisualTheme.BIOPHILIC -> BioTypography
    }

    val shapes = when (visualTheme) {
        AmaliaVisualTheme.LIQUID_GLASS -> GlassShapes
        AmaliaVisualTheme.BIOPHILIC -> BioShapes
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !effectiveDark
                isAppearanceLightNavigationBars = !effectiveDark
            }
        }
    }

    CompositionLocalProvider(
        LocalGlassStyle provides glassStyle,
        LocalAmaliaPalette provides palette,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}

/**
 * Час, который использует тема.
 *
 * Значение опрашивается раз в минуту и обновляется только когда час реально
 * сменился — иначе каждая минута перезапускала бы всю композицию экрана.
 * Когда адаптация по времени выключена, таймер не заводится вовсе.
 */
@Composable
private fun currentTimeHour(enable: Boolean): Int {
    val tracked by produceState(initialValue = hourNow(), key1 = enable) {
        if (!enable) return@produceState
        while (true) {
            delay(MINUTE_POLL_MS)
            val next = hourNow()
            if (next != value) value = next
        }
    }
    return if (enable) tracked else hourNow()
}

private fun hourNow(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

private const val MINUTE_POLL_MS = 60_000L

/**
 * Подмешивает aurora-тона палитры в акценты схемы.
 *
 * @param scheme базовая схема темы.
 * @param palette палитра, чьи цвета тянем в акценты.
 * @param isDark тёмная ли схема — влияет на направление коррекции контраста.
 */
private fun tintScheme(
    scheme: androidx.compose.material3.ColorScheme,
    palette: GradientPalette,
    isDark: Boolean,
): androidx.compose.material3.ColorScheme {
    val tones = palette.auroras.ifEmpty { palette.stops.map { it.color } }
    if (tones.isEmpty()) return scheme

    val warm = tones.getOrElse(0) { scheme.primary }
    val cool = tones.getOrElse(1 % tones.size) { scheme.secondary }
    val spark = tones.getOrElse(2 % tones.size) { scheme.tertiary }

    fun fit(accent: Color): Color {
        val mixed = scheme.primary.blend(accent, TINT_RATIO)
        return if (isDark) {
            if (mixed.luminance() < MIN_DARK_LUMINANCE) {
                mixed.blend(Color.White, LIFT_RATIO)
            } else {
                mixed
            }
        } else {
            if (mixed.luminance() > MAX_LIGHT_LUMINANCE) {
                mixed.blend(Color.Black, DEEPEN_RATIO)
            } else {
                mixed
            }
        }
    }

    val primary = fit(warm)
    val secondary = fit(cool)
    val tertiary = fit(spark)

    return scheme.copy(
        primary = primary,
        primaryContainer = primary.blend(scheme.surface, 0.72f),
        onPrimaryContainer = scheme.onSurface,
        secondary = secondary,
        secondaryContainer = secondary.blend(scheme.surface, 0.82f),
        onSecondary = scheme.background,
        tertiary = tertiary,
        onTertiary = scheme.background,
        surfaceTint = secondary,
    )
}

// ════════════════════════════════════════════════════════════
//  УТИЛИТЫ
// ════════════════════════════════════════════════════════════

/**
 * Палитра живого фона под текущие настройки.
 *
 * [hour] передаётся явно, чтобы тема и фон расходились одним источником
 * времени: иначе фон и акценты могли «разъехаться» на границу часа.
 */
fun currentGradientPalette(
    visualTheme: AmaliaVisualTheme,
    darkModePref: DarkModePreference = DarkModePreference.SYSTEM,
    useBioTime: Boolean = false,
    hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
): GradientPalette {
    val timeOfDay = if (useBioTime) BioTimeOfDay.fromHour(hour) else null

    if (visualTheme == AmaliaVisualTheme.LIQUID_GLASS) {
        return when (timeOfDay) {
            BioTimeOfDay.MORNING -> GlassGradientMorning
            BioTimeOfDay.DAY -> GlassGradientDay
            BioTimeOfDay.EVENING -> GlassGradientEvening
            BioTimeOfDay.NIGHT -> GlassGradientPalette
            null -> GlassGradientPalette
        }
    }

    if (timeOfDay != null) {
        return when (timeOfDay) {
            BioTimeOfDay.MORNING -> BioGradientMorning
            BioTimeOfDay.DAY -> BioGradientDay
            BioTimeOfDay.EVENING -> BioGradientEvening
            BioTimeOfDay.NIGHT -> BioGradientNight
        }
    }

    return when (darkModePref) {
        DarkModePreference.ALWAYS_DARK -> BioGradientNight
        DarkModePreference.ALWAYS_LIGHT -> BioGradientDay
        DarkModePreference.SYSTEM -> BioGradientDay
    }
}

/** Палитра, под которую сейчас нарисованы фон и акценты. */
val LocalAmaliaPalette = staticCompositionLocalOf<GradientPalette> { GlassGradientPalette }

/** Актуальная палитра — для иконок, аватаров и превью. */
val currentPalette: GradientPalette
    @Composable @ReadOnlyComposable
    get() = LocalAmaliaPalette.current

/**
 * Цвет подсветки под иконкой/аватаром: следует за палитрой, а не за
 * «вечным» primary, поэтому стеклянные кнопки не выглядят приклеенными
 * к фону другого времени суток.
 */
@Composable
@ReadOnlyComposable
fun iconAccent(): Color {
    val palette = LocalAmaliaPalette.current
    val tone = palette.motifAccent.takeIf { it != Color.Unspecified }
        ?: palette.auroras.firstOrNull()
        ?: MaterialTheme.colorScheme.primary
    return tone.blend(MaterialTheme.colorScheme.primary, if (palette.isDark) 0.35f else 0.5f)
}

private const val TINT_RATIO = 0.30f
private const val MIN_DARK_LUMINANCE = 0.42f
private const val MAX_LIGHT_LUMINANCE = 0.62f
private const val LIFT_RATIO = 0.28f
private const val DEEPEN_RATIO = 0.30f
