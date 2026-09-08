package com.my.amali.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
            values().firstOrNull { hour in it.hourRange } ?: NIGHT
    }
}

// ════════════════════════════════════════════════════════════
//  COLOR SCHEMES
// ════════════════════════════════════════════════════════════

// --- Glass Dark ---
private val GlassDarkScheme = darkColorScheme(
    primary = GlassAccentDim,
    onPrimary = GlassTextPrimary,
    primaryContainer = GlassAccentDim.copy(alpha = 0.14f),
    onPrimaryContainer = GlassTextPrimary,
    secondary = GlassAccentSoft,
    onSecondary = GlassTextPrimary,
    secondaryContainer = GlassAccentSoft.copy(alpha = 0.10f),
    onSecondaryContainer = GlassTextPrimary,
    tertiary = GlassAccentGlow,
    onTertiary = GlassTextPrimary,
    background = GlassBgBase,
    onBackground = GlassTextPrimary,
    surface = GlassBgSurface,
    onSurface = GlassTextPrimary,
    surfaceVariant = GlassBgSurfaceHigh,
    onSurfaceVariant = GlassTextSecondary,
    surfaceContainerHighest = GlassBgSurfaceHigh,
    surfaceContainerHigh = GlassBgSurfaceHigh,
    surfaceContainer = GlassBgSurface,
    surfaceContainerLow = GlassBgBase,
    surfaceContainerLowest = GlassBgBase,
    outline = GlassBgStroke,
    outlineVariant = GlassBgStroke.copy(alpha = 0.5f),
    error = GlassStateError,
    onError = GlassTextPrimary,
)

// --- Biophilic Light (день) ---
private val BioLightScheme = lightColorScheme(
    primary = BioAccentPrimary,
    onPrimary = BioTextPrimary,
    primaryContainer = BioAccentPrimary.copy(alpha = 0.12f),
    onPrimaryContainer = BioTextPrimary,
    secondary = BioAccentSecondary,
    onSecondary = BioTextPrimary,
    secondaryContainer = BioAccentSecondary.copy(alpha = 0.10f),
    onSecondaryContainer = BioTextPrimary,
    tertiary = BioAccentTertiary,
    onTertiary = BioTextPrimary,
    background = BioBgBase,
    onBackground = BioTextPrimary,
    surface = BioBgSurface,
    onSurface = BioTextPrimary,
    surfaceVariant = BioBgSurfaceHigh,
    onSurfaceVariant = BioTextSecondary,
    surfaceContainerHighest = BioBgSurfaceHigh,
    surfaceContainerHigh = BioBgSurfaceHigh,
    surfaceContainer = BioBgSurface,
    surfaceContainerLow = BioBgBase,
    surfaceContainerLowest = BioBgBase,
    outline = BioBgStroke,
    outlineVariant = BioBgStroke.copy(alpha = 0.5f),
    error = BioStateError,
)

// --- Biophilic Dark (вечер/ночь) ---
private val BioDarkScheme = darkColorScheme(
    primary = BioAccentPrimary,
    onPrimary = BioDarkTextPrimary,
    primaryContainer = BioAccentPrimary.copy(alpha = 0.14f),
    onPrimaryContainer = BioDarkTextPrimary,
    secondary = BioAccentSecondary,
    onSecondary = BioDarkTextPrimary,
    secondaryContainer = BioAccentSecondary.copy(alpha = 0.10f),
    onSecondaryContainer = BioDarkTextPrimary,
    tertiary = BioAccentTertiary,
    onTertiary = BioDarkTextPrimary,
    background = BioDarkBgBase,
    onBackground = BioDarkTextPrimary,
    surface = BioDarkBgSurface,
    onSurface = BioDarkTextPrimary,
    surfaceVariant = BioDarkBgSurfaceHigh,
    onSurfaceVariant = BioDarkTextSecondary,
    surfaceContainerHighest = BioDarkBgSurfaceHigh,
    surfaceContainerHigh = BioDarkBgSurfaceHigh,
    surfaceContainer = BioDarkBgSurface,
    surfaceContainerLow = BioDarkBgBase,
    surfaceContainerLowest = BioDarkBgBase,
    outline = BioDarkBgStroke,
    outlineVariant = BioDarkBgStroke.copy(alpha = 0.5f),
    error = BioStateError,
)

// --- Fallback Light ---
private val AmaliaLightScheme = lightColorScheme(
    primary = GlassAccentDim,
    onPrimary = LightSurface,
    primaryContainer = GlassAccentDim.copy(alpha = 0.12f),
    onPrimaryContainer = LightTextPrimary,
    secondary = GlassAccentSoft,
    onSecondary = LightSurface,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightBg,
    onSurfaceVariant = LightTextSecondary,
    outline = GlassBgStroke,
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

    // Bio-time: если включено, переопределяем тёмность по времени суток
    val bioTime = if (useBioTime && visualTheme == AmaliaVisualTheme.BIOPHILIC) {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        BioTimeOfDay.fromHour(hour)
    } else null

    val effectiveDark = bioTime?.let {
        it == BioTimeOfDay.EVENING || it == BioTimeOfDay.NIGHT
    } ?: isDark

    val colorScheme = when (visualTheme) {
        AmaliaVisualTheme.LIQUID_GLASS -> GlassDarkScheme
        AmaliaVisualTheme.BIOPHILIC -> {
            if (effectiveDark) BioDarkScheme else BioLightScheme
        }
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
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !effectiveDark
                isAppearanceLightNavigationBars = !effectiveDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = shapes,
        content = content,
    )
}

// ════════════════════════════════════════════════════════════
//  УТИЛИТЫ: получить текущую палитру градиента для фона
// ════════════════════════════════════════════════════════════

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

    val systemDark = darkModePref == DarkModePreference.ALWAYS_DARK ||
            (darkModePref == DarkModePreference.SYSTEM &&
                    android.content.res.Configuration.UI_MODE_NIGHT_YES ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES)
    return if (systemDark) BioGradientNight else BioGradientDay
}
