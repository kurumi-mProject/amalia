package com.my.amali.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AmaliaDarkScheme = darkColorScheme(
    primary = AccentDim,
    onPrimary = TextPrimary,
    primaryContainer = AccentDim.copy(alpha = 0.14f),
    onPrimaryContainer = TextPrimary,
    secondary = AccentSoft,
    onSecondary = TextPrimary,
    secondaryContainer = AccentSoft.copy(alpha = 0.10f),
    onSecondaryContainer = TextPrimary,
    tertiary = AccentGlow,
    onTertiary = TextPrimary,
    background = BgBase,
    onBackground = TextPrimary,
    surface = BgSurface,
    onSurface = TextPrimary,
    surfaceVariant = BgSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainerHighest = BgSurfaceHigh,
    surfaceContainerHigh = BgSurfaceHigh,
    surfaceContainer = BgSurface,
    surfaceContainerLow = BgBase,
    surfaceContainerLowest = BgBase,
    outline = BgStroke,
    outlineVariant = BgStroke.copy(alpha = 0.5f),
    error = StateError,
)

private val AmaliaLightScheme = lightColorScheme(
    primary = AccentDim,
    onPrimary = LightSurface,
    primaryContainer = AccentDim.copy(alpha = 0.12f),
    onPrimaryContainer = LightTextPrimary,
    secondary = AccentSoft,
    onSecondary = LightSurface,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightBg,
    onSurfaceVariant = LightTextSecondary,
    outline = BgStroke,
)

@Composable
fun AmaliaTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AmaliaDarkScheme
        else -> AmaliaLightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AmaliaTypography,
        content = content,
    )
}
