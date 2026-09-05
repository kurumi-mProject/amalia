package com.my.amali.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AmaliaDarkScheme = darkColorScheme(
    primary = AuroraViolet,
    onPrimary = ColorTokens.White,
    primaryContainer = AuroraViolet.copy(alpha = 0.22f),
    onPrimaryContainer = ColorTokens.NearWhite,
    secondary = AuroraCyan,
    onSecondary = ColorTokens.Deep,
    secondaryContainer = AuroraCyan.copy(alpha = 0.16f),
    onSecondaryContainer = ColorTokens.NearWhite,
    tertiary = AuroraPink,
    onTertiary = ColorTokens.Deep,
    background = DeepNavy,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerHighest = SurfaceHigh,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainer = SurfaceDark,
    surfaceContainerLow = DeepNavy,
    surfaceContainerLowest = DeepNavy,
    outline = SurfaceStroke,
    outlineVariant = SurfaceStroke.copy(alpha = 0.6f),
    error = ListenRed,
)

private val AmaliaLightScheme = lightColorScheme(
    primary = AuroraViolet,
    onPrimary = ColorTokens.White,
    primaryContainer = AuroraViolet.copy(alpha = 0.16f),
    onPrimaryContainer = ColorTokens.Deep,
    secondary = ColorTokens.IndigoDeep,
    onSecondary = ColorTokens.White,
    secondaryContainer = AuroraCyan.copy(alpha = 0.25f),
    onSecondaryContainer = ColorTokens.Deep,
    tertiary = AuroraPink,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = ColorTokens.LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = ColorTokens.LightOutline,
)

/**
 * Тема Амалии. Продукт тёмный по своей природе («амбиент»): по умолчанию
 * всегда тёмная схема. Когда доступны динамические цвета Material You
 * (Android 12+), берём их как основу — приложение подстраивается под обои.
 */
@Composable
fun AmaliaTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
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

/** Внутренние служебные цвета (конкретные места, не схема целиком). */
private object ColorTokens {
    val White = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val NearWhite = androidx.compose.ui.graphics.Color(0xFFF4F5FB)
    val Deep = androidx.compose.ui.graphics.Color(0xFF0B1020)
    val IndigoDeep = androidx.compose.ui.graphics.Color(0xFF4F46E5)
    val LightSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFE9EAF5)
    val LightOutline = androidx.compose.ui.graphics.Color(0xFFC9CCE0)
}
