package com.my.amali.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

// ════════════════════════════════════════════════════════════
//  ТИПОГРАФИКА
//  Принципы:
//   • крупные заголовки — лёгкое начертание и отрицательный трекинг
//     (так набирают премиальные продукты: воздушно, но плотно);
//   • тело — Normal 15–16sp с межстрочным 1.5 для комфортного чтения;
//   • лейблы — Medium с положительным трекингом, чтобы мелкий текст
//     не «слипался»;
//   • выравнивание строк по центру глифов: текст не «плавает» в
//     контейнерах фиксированной высоты.
// ════════════════════════════════════════════════════════════

/** Центрирование строки внутри её высоты — убирает визуальный сдвиг текста. */
private val CenteredLines = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private val Sans = FontFamily.SansSerif

// ── LIQUID GLASS: строго, холодно, с сильной иерархией ──────────────
val GlassTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Light,
        fontSize = 52.sp,
        lineHeight = 56.sp,
        letterSpacing = (-1.4).sp,
        lineHeightStyle = CenteredLines,
    ),
    displayMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Light,
        fontSize = 42.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1).sp,
        lineHeightStyle = CenteredLines,
    ),
    displaySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.6).sp,
        lineHeightStyle = CenteredLines,
    ),
    headlineLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
        lineHeightStyle = CenteredLines,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 27.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.4).sp,
        lineHeightStyle = CenteredLines,
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
        lineHeightStyle = CenteredLines,
    ),
    titleLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.1).sp,
        lineHeightStyle = CenteredLines,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp,
        lineHeightStyle = CenteredLines,
    ),
    titleSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        lineHeightStyle = CenteredLines,
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
        lineHeightStyle = CenteredLines,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.15.sp,
        lineHeightStyle = CenteredLines,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
        lineHeightStyle = CenteredLines,
    ),
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
        lineHeightStyle = CenteredLines,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
        lineHeightStyle = CenteredLines,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.45.sp,
        lineHeightStyle = CenteredLines,
    ),
)

// ── БИОФИЛЬНАЯ: мягче, воздушнее, больше межстрочного ──────────────
val BioTypography = Typography(
    displayLarge = GlassTypography.displayLarge.copy(
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.6).sp,
    ),
    displayMedium = GlassTypography.displayMedium.copy(
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.4).sp,
    ),
    displaySmall = GlassTypography.displaySmall.copy(
        fontSize = 32.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineLarge = GlassTypography.headlineLarge.copy(
        fontSize = 29.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineMedium = GlassTypography.headlineMedium.copy(
        fontSize = 26.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.1).sp,
    ),
    headlineSmall = GlassTypography.headlineSmall.copy(
        fontSize = 21.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = GlassTypography.titleLarge.copy(
        fontSize = 19.sp,
        lineHeight = 27.sp,
    ),
    titleMedium = GlassTypography.titleMedium.copy(
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = GlassTypography.titleSmall.copy(
        fontSize = 13.5.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = GlassTypography.bodyLarge.copy(
        fontSize = 15.5.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = GlassTypography.bodyMedium.copy(
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = GlassTypography.bodySmall.copy(
        fontSize = 12.5.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = GlassTypography.labelLarge,
    labelMedium = GlassTypography.labelMedium,
    labelSmall = GlassTypography.labelSmall,
)
