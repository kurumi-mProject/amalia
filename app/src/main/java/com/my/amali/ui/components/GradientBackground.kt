package com.my.amali.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.my.amali.ui.theme.BioTimeOfDay
import com.my.amali.ui.theme.currentGradientPalette
import java.util.Calendar
import kotlin.math.sin

/**
 * GradientBackground — живой дышащий фон Амалии.
 *
 * Берёт текущую [GradientPalette] (Liquid Glass или биофильная по времени
 * суток) и мягко сдвигает радиальные градиенты по синусоиде с периодом
 * 60 секунд — «дыхание» фона. Также добавляет медленное глобальное
 * масштабирование, чтобы фон ощущался живым, но не отвлекал.
 *
 * Фон рисуется на Canvas — стоимость анимации предсказуема и мала.
 *
 * @param visualTheme активная визуальная тема.
 * @param darkModePref влияет на биофильные тёмные палитры.
 * @param useBioTime включает адаптацию по времени суток.
 */
@Composable
fun GradientBackground(
    visualTheme: com.my.amali.ui.theme.AmaliaVisualTheme,
    darkModePref: com.my.amali.ui.theme.DarkModePreference,
    useBioTime: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = currentGradientPalette(visualTheme, darkModePref, useBioTime)

    val breath = rememberInfiniteTransition(label = "backgroundBreath")
    val phase by breath.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60_000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "breathPhase",
    )

    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Базовая заливка — нижний стоп палитры.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = palette.stops.map { it.color },
                    startY = 0f,
                    endY = h,
                ),
            )

            // Два медленно дрейфующих радиальных «света».
            palette.stops.take(3).forEachIndexed { index, stop ->
                val drift = sin(phase * (0.7f + index * 0.35f) + index * 2.1f)
                val cx = w * (0.28f + 0.22f * index) + drift * w * 0.06f
                val cy = h * (0.22f + 0.28f * ((index + 1) % 2)) - drift * h * 0.05f
                val radius = kotlin.math.min(w, h) * (0.55f + 0.1f * drift)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            stop.color.copy(alpha = 0.55f),
                            Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(cx, cy),
                )
            }

            // Тонкая верхняя вуаль для глубины.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (palette.isDark) 0.02f else 0.05f),
                        Color.Transparent,
                    ),
                    startY = 0f,
                    endY = h * 0.35f,
                ),
            )
        }
    }
}

/** Перегрузка для быстрого использования внутри уже настроенной темы. */
@Composable
fun GradientBackground(
    visualTheme: com.my.amali.ui.theme.AmaliaVisualTheme,
    modifier: Modifier = Modifier,
) {
    val hour = rememberHourOfDay()
    val timeOfDay = BioTimeOfDay.fromHour(hour)
    GradientBackground(
        visualTheme = visualTheme,
        darkModePref = when (timeOfDay) {
            BioTimeOfDay.EVENING, BioTimeOfDay.NIGHT ->
                com.my.amali.ui.theme.DarkModePreference.ALWAYS_DARK
            else -> com.my.amali.ui.theme.DarkModePreference.ALWAYS_LIGHT
        },
        useBioTime = visualTheme == com.my.amali.ui.theme.AmaliaVisualTheme.BIOPHILIC,
        modifier = modifier,
    )
}

@Composable
private fun rememberHourOfDay(): Int {
    val hour = androidx.compose.runtime.remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    return hour
}
