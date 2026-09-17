package com.my.amali.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.my.amali.ui.theme.AmaliaMotif
import com.my.amali.ui.theme.GradientPalette
import com.my.amali.ui.theme.currentPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * GradientBackground — живой фон под всем интерфейсом.
 *
 * Складывается из двух слоёв:
 *  1. **аурора** — вертикальная база палитры + три крупных размытых пятна
 *     акцентных цветов, дрейфующих по эллиптическим траекториям с разными
 *     периодами (90/70/110 с). Движение настолько медленное, что не
 *     отвлекает, но экран перестаёт быть «мёртвым»;
 *  2. **мотив** ([MotifLayer]) — лепестки/листья/снег/звёзды/светлячки,
 *     которые объясняют смену палитры временем суток.
 *
 * Сверху — виньетка: приглушает края, чтобы контент в центре читался лучше.
 *
 * Палитра берётся из [currentPalette], то есть из темы. Это принципиально:
 * фон и акцентные цвета схемы (текст, иконки, волна) обязаны быть одним и тем
 * же временем суток, иначе интерфейс выглядит перекрашенным наполовину.
 *
 * @param intensity 0..1 — общая сила свечения (настройка «интенсивность стекла»).
 * @param motif декоративный слой; [AmaliaMotif.OFF] выключает его полностью.
 * @param motifDensity 0..1 — густота декораций.
 */
@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 0.75f,
    motif: AmaliaMotif = AmaliaMotif.AUTO,
    motifDensity: Float = 1f,
    palette: GradientPalette = currentPalette,
) {
    Box(modifier = modifier) {
        AuroraCanvas(palette = palette, intensity = intensity, modifier = Modifier.fillMaxSize())
        MotifLayer(
            motif = motif,
            density = motifDensity,
            modifier = Modifier.fillMaxSize(),
        )
        // Верхняя световая вуаль — «стекло ловит свет сверху»; рисуется поверх
        // мотивов, чтобы они не выглядели наклеенными на самый верх экрана.
        SheenCanvas(dark = palette.isDark, modifier = Modifier.fillMaxSize())
    }
}

/** Отрисовка ауроры: база + дрейфующие пятна + виньетка. */
@Composable
private fun AuroraCanvas(
    palette: GradientPalette,
    intensity: Float,
    modifier: Modifier = Modifier,
) {
    val drift = rememberInfiniteTransition(label = "aurora")
    val p1 by drift.animateFloat(
        initialValue = 0f,
        targetValue = TAU,
        animationSpec = infiniteRepeatable(tween(90_000, easing = LinearEasing)),
        label = "p1",
    )
    val p2 by drift.animateFloat(
        initialValue = 0f,
        targetValue = TAU,
        animationSpec = infiniteRepeatable(tween(70_000, easing = LinearEasing)),
        label = "p2",
    )
    val p3 by drift.animateFloat(
        initialValue = 0f,
        targetValue = TAU,
        animationSpec = infiniteRepeatable(tween(110_000, easing = LinearEasing)),
        label = "p3",
    )

    val strength = intensity.coerceIn(0f, 1f)
    val auroras = palette.auroras.ifEmpty { palette.stops.map { it.color } }

    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = palette.stops
                        .map { it.position to it.color }
                        .toTypedArray(),
                    startY = 0f,
                    endY = h,
                ),
            )

            // Три дрейфующих пятна. Позиции — эллипсы вокруг «якорей».
            val spots = listOf(
                Triple(p1, Offset(0.22f, 0.18f), 0.95f),
                Triple(p2, Offset(0.84f, 0.40f), 0.80f),
                Triple(p3, Offset(0.46f, 0.92f), 1.05f),
            )
            spots.forEachIndexed { index, (phase, anchor, scale) ->
                val color = auroras[index % auroras.size]
                val cx = w * (anchor.x + 0.09f * cos(phase + index))
                val cy = h * (anchor.y + 0.06f * sin(phase * 1.3f + index))
                val radius = maxOf(w, h) * 0.52f * scale
                val alpha = (if (palette.isDark) 0.26f else 0.34f) *
                    strength * (0.85f + 0.15f * sin(phase))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(cx, cy),
                )
            }

            // Виньетка: тёмные (или светлые) края, чистый центр.
            val edge = if (palette.isDark) Color.Black else Color(0xFF8E8A7E)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        edge.copy(alpha = if (palette.isDark) 0.55f else 0.16f),
                    ),
                    center = Offset(w / 2f, h * 0.42f),
                    radius = maxOf(w, h) * 0.78f,
                ),
            )
        }
    }
}

/** Верхний блик — отдельный слой, чтобы лежать поверх декораций. */
@Composable
private fun SheenCanvas(dark: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (dark) 0.035f else 0.10f),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = size.height * 0.32f,
            ),
        )
    }
}

private val TAU = (2 * PI).toFloat()
