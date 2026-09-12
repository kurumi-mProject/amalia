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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.my.amali.ui.theme.AmaliaVisualTheme
import com.my.amali.ui.theme.BioTimeOfDay
import com.my.amali.ui.theme.DarkModePreference
import com.my.amali.ui.theme.GradientPalette
import com.my.amali.ui.theme.currentGradientPalette
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * GradientBackground — тихий «аурора»-фон под всем интерфейсом.
 *
 * База — вертикальный градиент палитры. Поверх неё три крупных
 * размытых пятна акцентных цветов, которые дрейфуют по эллиптическим
 * траекториям с разными периодами (90/70/110 с). Движение настолько
 * медленное, что не отвлекает, но экран перестаёт быть «мёртвым».
 *
 * Сверху добавляется зернистая вуаль-виньетка: она приглушает края и
 * заставляет контент в центре читаться лучше.
 *
 * @param visualTheme активная визуальная тема.
 * @param darkModePref влияет на выбор биофильной палитры.
 * @param useBioTime включает адаптацию палитры по времени суток.
 * @param intensity 0..1 — общая сила свечения (настройка «интенсивность стекла»).
 */
@Composable
fun GradientBackground(
    visualTheme: AmaliaVisualTheme,
    darkModePref: DarkModePreference,
    useBioTime: Boolean,
    modifier: Modifier = Modifier,
    intensity: Float = 0.75f,
) {
    val palette = currentGradientPalette(visualTheme, darkModePref, useBioTime)
    AuroraCanvas(palette = palette, intensity = intensity, modifier = modifier)
}

/** Перегрузка: фон по текущему времени суток, без явных настроек. */
@Composable
fun GradientBackground(
    visualTheme: AmaliaVisualTheme,
    modifier: Modifier = Modifier,
    intensity: Float = 0.75f,
) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val timeOfDay = BioTimeOfDay.fromHour(hour)
    GradientBackground(
        visualTheme = visualTheme,
        darkModePref = when (timeOfDay) {
            BioTimeOfDay.EVENING, BioTimeOfDay.NIGHT -> DarkModePreference.ALWAYS_DARK
            else -> DarkModePreference.ALWAYS_LIGHT
        },
        useBioTime = visualTheme == AmaliaVisualTheme.BIOPHILIC,
        modifier = modifier,
        intensity = intensity,
    )
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
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(90_000, easing = LinearEasing)),
        label = "p1",
    )
    val p2 by drift.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(70_000, easing = LinearEasing)),
        label = "p2",
    )
    val p3 by drift.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(110_000, easing = LinearEasing)),
        label = "p3",
    )

    val strength = intensity.coerceIn(0f, 1f)
    val auroras = palette.auroras.ifEmpty { palette.stops.map { it.color } }

    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // База.
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
                val alpha = (if (palette.isDark) 0.24f else 0.32f) *
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
                    colors = listOf(Color.Transparent, edge.copy(alpha = if (palette.isDark) 0.55f else 0.16f)),
                    center = Offset(w / 2f, h * 0.42f),
                    radius = maxOf(w, h) * 0.78f,
                ),
            )

            // Верхняя световая вуаль — «стекло ловит свет сверху».
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (palette.isDark) 0.035f else 0.10f),
                        Color.Transparent,
                    ),
                    startY = 0f,
                    endY = h * 0.32f,
                ),
            )
        }
    }
}
