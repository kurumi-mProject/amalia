package com.my.amali.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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

    val bioTime = if (useBioTime && visualTheme == AmaliaVisualTheme.BIOPHILIC) {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        BioTimeOfDay.fromHour(hour)
    } else {
        null
    }

    val effectiveDark = when (visualTheme) {
        AmaliaVisualTheme.LIQUID_GLASS -> true
        AmaliaVisualTheme.BIOPHILIC -> bioTime?.let {
            it == BioTimeOfDay.EVENING || it == BioTimeOfDay.NIGHT
        } ?: isDark
    }

    val colorScheme = when (visualTheme) {
        AmaliaVisualTheme.LIQUID_GLASS -> GlassDarkScheme
        AmaliaVisualTheme.BIOPHILIC -> if (effectiveDark) BioDarkScheme else BioLightScheme
    }

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

    CompositionLocalProvider(LocalGlassStyle provides glassStyle) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}

// ════════════════════════════════════════════════════════════
//  УТИЛИТЫ
// ════════════════════════════════════════════════════════════

/** Палитра живого фона под текущие настройки. */
fun currentGradientPalette(
    visualTheme: AmaliaVisualTheme,
    darkModePref: DarkModePreference = DarkModePreference.SYSTEM,
    useBioTime: Boolean = false,
): GradientPalette {
    if (visualTheme == AmaliaVisualTheme.LIQUID_GLASS) return GlassGradientPalette

    if (useBioTime) {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (BioTimeOfDay.fromHour(hour)) {
            BioTimeOfDay.MORNING -> BioGradientMorning
            BioTimeOfDay.DAY -> BioGradientDay
            BioTimeOfDay.EVENING -> BioGradientEvening
            BioTimeOfDay.NIGHT -> BioGradientNight
        }
    }

    return when (darkModePref) {
        DarkModePreference.ALWAYS_DARK -> BioGradientNight
        else -> BioGradientDay
    }
}
