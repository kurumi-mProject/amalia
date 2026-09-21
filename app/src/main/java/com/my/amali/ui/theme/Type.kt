package com.my.amali.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
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

/**
 * Оптический трекинг для крупного кегля.
 *
 * Пропорциональный шрифт при увеличении кегля выглядит всё более разреженным:
 * межбуквенные пробелы растут вместе с буквами, а глаз читает это как «текст
 * разъехался». Типографы компенсируют это отрицательным трекингом, и он должен
 * быть пропорционален размеру, а не одинаковым у 52 и у 27 sp — иначе на
 * заголовках экранов буквы начнут слипаться.
 *
 * Формула: −0.021 em (≈ −2.1% от кегля). Значения:
 *  • 52 sp → −1.09 sp   • 42 sp → −0.88 sp   • 34 sp → −0.71 sp
 *  • 27 sp → −0.57 sp   • 22 sp → −0.46 sp   • 20 sp → −0.26 sp (лёгкая)
 */
private fun opticalTracking(fontSize: Float, share: Float = 0.021f): androidx.compose.ui.unit.TextUnit =
    (fontSize * share).sp

/**
 * Высота страницы крупного текста: целое число строк плюс запас на выносные
 * элементы (`у`, `р`, `Д`). Одна строка даёт кегль плюс 12% в интерлиньяже, а
 * запас нужен потому, что одна из трёх фраз фазы иногда переносится на вторую
 * строку, и без запаса низкие буквы обрезались бы отсечением барабана.
 */
fun greetingTextPages(
    fontSize: Float,
    lineHeight: Float,
    lineCount: Int,
): androidx.compose.ui.unit.Dp = (lineHeight * lineCount + fontSize * 0.27f).dp

// ── LIQUID GLASS: строго, холодно, с сильной иерархией ──────────────
//
// ВАЖНО про интерлиньяж: 1.12 для display-стилей. Это уже воздушно (обычный
// набор — 1.0–1.05), но ещё не развалено: главная фраза экрана почти всегда
// занимает одну строку, и её высота — это ровно её кегль плюс 12%, а не 46–56
// пикселей воздуха, из-за которых крупный текст выглядел «уехавшим» от края.
val GlassTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Light,
        fontSize = 52.sp,
        lineHeight = 58.sp,
        letterSpacing = opticalTracking(52f),
        lineHeightStyle = CenteredLines,
    ),
    displayMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Light,
        fontSize = 41.sp,
        lineHeight = 46.sp,
        letterSpacing = opticalTracking(41f),
        lineHeightStyle = CenteredLines,
    ),
    displaySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Light,
        fontSize = 33.sp,
        lineHeight = 38.sp,
        letterSpacing = opticalTracking(33f),
        lineHeightStyle = CenteredLines,
    ),
    headlineLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 29.sp,
        lineHeight = 35.sp,
        letterSpacing = opticalTracking(29f, 0.017f),
        lineHeightStyle = CenteredLines,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        letterSpacing = opticalTracking(26f, 0.017f),
        lineHeightStyle = CenteredLines,
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = opticalTracking(22f),
        lineHeightStyle = CenteredLines,
    ),
    titleLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = opticalTracking(20f, 0.013f),
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
//
// Отрицательный трекинг наследуется от стекла (см. [opticalTracking]):
// он — свойство пропорционального шрифта, а не «характера» темы. Меняем
// только кегль и интерлиньяж: здесь текст стоит на тёплом фоне, и ему нужно
// больше воздуха между строками, но никак не больше воздуха между буквами.
val BioTypography = Typography(
    displayLarge = GlassTypography.displayLarge.copy(
        fontSize = 48.sp,
        lineHeight = 58.sp,
    ),
    displayMedium = GlassTypography.displayMedium.copy(
        fontSize = 39.sp,
        lineHeight = 48.sp,
    ),
    displaySmall = GlassTypography.displaySmall.copy(
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineLarge = GlassTypography.headlineLarge.copy(
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = GlassTypography.headlineMedium.copy(
        fontSize = 25.sp,
        lineHeight = 33.sp,
    ),
    headlineSmall = GlassTypography.headlineSmall.copy(
        fontSize = 21.sp,
        lineHeight = 29.sp,
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
