package com.my.amali.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ════════════════════════════════════════════════════════════
//  ДИЗАЙН-СИСТЕМА «LIQUID GLASS»
//  Единая сетка отступов, радиусов и стеклянных поверхностей.
//  Все экраны собираются только из этих значений — отсюда
//  берётся визуальное единство приложения.
// ════════════════════════════════════════════════════════════

/** Сетка отступов, кратная 4dp. */
object Spacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val huge: Dp = 40.dp

    /** Боковой отступ контента на всех экранах. */
    val screen: Dp = 20.dp

    /** Зазор между карточками в списках и группах настроек. */
    val listGap: Dp = 10.dp
}

/** Радиусы: одна шкала на всё приложение. */
object Radius {
    val chip: Dp = 100.dp
    /** Малый «хвостик» пузыря сообщения. */
    val bubbleTail: Dp = 6.dp
    val xs: Dp = 12.dp
    val sm: Dp = 16.dp
    val md: Dp = 22.dp
    val lg: Dp = 28.dp
    val xl: Dp = 34.dp
}

/**
 * Параметры стекла текущей темы. Подставляются в [AmaliaTheme] и читаются
 * всеми стеклянными компонентами, чтобы карточка в тёмной теме была
 * «дымчатой», а в светлой — «молочной».
 *
 * @param fill базовая непрозрачность заливки поверхности.
 * @param border непрозрачность светового контура.
 * @param highlight сила верхнего блика (имитация преломления).
 * @param glow сила цветного свечения акцента внутри стекла.
 * @param light true для светлой темы: блики темнее фона, а не светлее.
 */
data class GlassStyle(
    val fill: Float,
    val border: Float,
    val highlight: Float,
    val glow: Float,
    val light: Boolean,
)

val LocalGlassStyle = staticCompositionLocalOf {
    GlassStyle(fill = 0.55f, border = 0.12f, highlight = 0.10f, glow = 0.14f, light = false)
}

/**
 * Визуальный контекст приложения: какую палитру фона рисовать, насколько
 * сильным делать стекло и какие декорации сыпать поверх. Провайдится один
 * раз в MainActivity из настроек пользователя, поэтому любой экран может
 * нарисовать корректный фон, не получая настройки через параметры.
 *
 * @property motif декоративный слой поверх ауроры ([AmaliaMotif.AUTO] — по
 *   времени суток, [AmaliaMotif.OFF] — чистый фон).
 * @property motifDensity 0..1 — густота декораций; привязана к интенсивности
 *   стекла, чтобы «тихая» тема оставалась тихой целиком.
 */
data class AmaliaVisuals(
    val visualTheme: AmaliaVisualTheme = AmaliaVisualTheme.LIQUID_GLASS,
    val darkModePref: DarkModePreference = DarkModePreference.SYSTEM,
    val useBioTime: Boolean = false,
    val glassIntensity: Float = 0.75f,
    val motif: AmaliaMotif = AmaliaMotif.AUTO,
    val motifDensity: Float = 1f,
)

val LocalAmaliaVisuals = staticCompositionLocalOf { AmaliaVisuals() }

val MaterialGlass: GlassStyle
    @Composable @ReadOnlyComposable
    get() = LocalGlassStyle.current

/**
 * Стеклянная поверхность: заливка + световой контур + верхний блик +
 * мягкое цветное свечение. Работает на любом API — эффект собран
 * из градиентов, а не из RenderEffect, поэтому одинаков на всех
 * устройствах и дешёв по кадрам.
 *
 * @param shape форма поверхности.
 * @param tint цветовое свечение внутри стекла (по умолчанию — акцент).
 * @param elevated усиленное стекло для «плавающих» панелей.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(Radius.md),
    tint: Color = MaterialTheme.colorScheme.primary,
    elevated: Boolean = false,
    fillAlpha: Float? = null,
): Modifier {
    val style = MaterialGlass
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val fill = fillAlpha ?: if (elevated) style.fill + 0.18f else style.fill
    val lightSource = if (style.light) Color.White else Color.White
    val highlightAlpha = if (elevated) style.highlight * 1.6f else style.highlight
    val glowAlpha = if (elevated) style.glow * 1.4f else style.glow

    return this
        .clip(shape)
        .background(surface.copy(alpha = fill.coerceIn(0f, 1f)))
        .drawWithCache {
            // Диагональный блик — «преломление» на верхней грани стекла.
            val sheen = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to lightSource.copy(alpha = highlightAlpha),
                    0.42f to lightSource.copy(alpha = highlightAlpha * 0.22f),
                    1f to Color.Transparent,
                ),
                start = Offset.Zero,
                end = Offset(size.width * 0.9f, size.height * 1.4f),
            )
            // Цветное свечение снизу — «жидкость» внутри стекла.
            val glow = Brush.radialGradient(
                colors = listOf(tint.copy(alpha = glowAlpha), Color.Transparent),
                center = Offset(size.width * 0.82f, size.height * 1.05f),
                radius = maxOf(size.width, size.height) * 0.85f,
            )
            onDrawBehind {
                drawRect(glow)
                drawRect(sheen)
            }
        }
        .border(
            width = if (elevated) 1.dp else 0.8.dp,
            brush = Brush.verticalGradient(
                listOf(
                    lightSource.copy(alpha = style.border * if (elevated) 2.2f else 1.6f),
                    outline.copy(alpha = style.border * 0.9f),
                ),
            ),
            shape = shape,
        )
}

/**
 * Мягкое цветное свечение под элементом (акцентная кнопка, активная волна).
 * Рисуется как размытое радиальное пятно за границами компонента,
 * поэтому не требует тени Material и не даёт «серой рамки».
 */
fun Modifier.accentGlow(
    color: Color,
    alpha: Float = 0.35f,
    spread: Float = 1.6f,
): Modifier = this.drawWithCache {
    val radius = maxOf(size.width, size.height) * spread / 2f
    val brush = Brush.radialGradient(
        colors = listOf(color.copy(alpha = alpha), Color.Transparent),
        center = Offset(size.width / 2f, size.height / 2f),
        radius = radius,
    )
    onDrawBehind {
        drawCircle(
            brush = brush,
            radius = radius,
            center = Offset(size.width / 2f, size.height / 2f),
        )
    }
}

/** Текущая тема светлая? Нужно для выбора направления бликов. */
@Composable
@ReadOnlyComposable
fun isLightSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() > 0.45f

/**
 * Подложка под иконкой/аватаром: мягкий градиент из двух акцентных тонов
 * ТЕКУЩЕЙ палитры плюс тонкий контур тем же тоном.
 *
 * Нужна ровно для того, чтобы кругляши кнопок и аватары не оставались
 * «вечное стекло», когда фон и текст уже уехали в вечер или ночь: иконка
 * начинает принадлежать той же палитре, что и всё остальное.
 *
 * @param shape форма подложки.
 * @param strength сила тонировки (1 — нормально, больше — для активных).
 * @param outlined рисовать ли световой контур.
 */
@Composable
@ReadOnlyComposable
fun Modifier.paletteChip(
    shape: Shape,
    strength: Float = 1f,
    outlined: Boolean = true,
): Modifier {
    val palette = LocalAmaliaPalette.current
    val tones = palette.auroras.ifEmpty { palette.stops.map { it.color } }
    val from = tones.getOrElse(0) { MaterialTheme.colorScheme.primary }
    val to = tones.getOrElse(1 % tones.size) { from }
    val a = strength.coerceIn(0f, 1.6f)

    val filled = background(
        Brush.linearGradient(
            listOf(
                from.copy(alpha = 0.26f * a),
                to.copy(alpha = 0.11f * a),
            ),
        ),
        shape,
    )
    return if (!outlined) {
        filled
    } else {
        filled.border(0.7.dp, from.copy(alpha = 0.30f * a), shape)
    }
}
