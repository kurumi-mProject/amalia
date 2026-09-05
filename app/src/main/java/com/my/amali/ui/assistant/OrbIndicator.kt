package com.my.amali.ui.assistant

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/** Палитра «аурора» для орба — фирменный градиент Амалии. */
private val AuroraViolet = Color(0xFF8B5CF6)
private val AuroraIndigo = Color(0xFF6366F1)
private val AuroraCyan = Color(0xFF22D3EE)
private val DeepNavy = Color(0xFF0B1020)

private val AuroraGradient = listOf(AuroraViolet, AuroraIndigo, AuroraCyan)

/** Акцент состояния (психология цвета: тёплый = энергия, холодный = поток). */
private val StateAccent = mapOf(
    OrbState.Idle to Color(0xFF4B5678),   // спокойный, приглушённый
    OrbState.Listening to Color(0xFFF87171), // слушаю — тёплый, живой
    OrbState.Thinking to Color(0xFFFBBF24),  // думаю — тёплый янтарь
    OrbState.Speaking to Color(0xFF22D3EE),  // говорю — холодный циан
)

/**
 * Жизненный цикл Амалии. Определяет цвет, ритм и форму орба.
 * label — человекочитаемое состояние для статуса на экране.
 */
enum class OrbState(val label: String) {
    Idle("на связи"),
    Listening("слушаю"),
    Thinking("думаю"),
    Speaking("говорю"),
}

/**
 * Живой орб — визуальное «лицо» Амалии.
 *
 * Дизайн-принцип: у состояния никогда не бывает «пустоты». Каждое состояние
 * дышит в своём ритме, и глаз считывает фазу ещё до прочтения текста.
 */
@Composable
fun OrbIndicator(
    state: OrbState,
    modifier: Modifier = Modifier,
    orbSize: androidx.compose.ui.unit.Dp = 200.dp,
) {
    val palette = if (state == OrbState.Idle) {
        AuroraGradient
    } else {
        val accent = StateAccent[state] ?: AuroraViolet
        listOf(accent, AuroraIndigo)
    }

    val transition = rememberInfiniteTransition(label = "orb")

    // Плавное «дыхание» сферы
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = { it * it * (3f - 2f * it) }),
        ),
        label = "breath",
    )

    // Бегущее кольцо-энергия
    val ringProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    OrbState.Idle -> 7000
                    OrbState.Listening -> 1600
                    OrbState.Thinking -> 2800
                    OrbState.Speaking -> 2200
                },
            ),
        ),
        label = "ring",
    )

    // Медленное вращение градиента
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(14000, easing = LinearEasing)),
        label = "rotation",
    )

    val baseScale = when (state) {
        OrbState.Idle -> 0.84f
        OrbState.Listening -> 1.02f
        OrbState.Thinking -> 0.94f
        OrbState.Speaking -> 1.1f
    }
    val scale = baseScale * (0.94f + 0.12f * breath)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(orbSize)) {
            val d = size.minDimension
            val center = Offset(size.width / 2f, size.height / 2f)

            // Внешняя дымка-свечение
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.first().copy(alpha = 0.28f), Color.Transparent),
                    center = center,
                    radius = d * 0.6f,
                ),
                radius = d * 0.5f,
                center = center,
            )

            // Кольцо «энергии», бегущее по кругу
            rotate(rotation, center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            palette.last().copy(alpha = 0f),
                            palette.last(),
                            palette.last().copy(alpha = 0f),
                        ),
                    ),
                    startAngle = ringProgress * 360f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - d * 0.42f, center.y - d * 0.42f),
                    size = Size(d * 0.84f, d * 0.84f),
                    style = Stroke(width = d * 0.016f, cap = StrokeCap.Round),
                )
            }

            // Тело сферы — радиальный градиент «живого света»
            val r = d * 0.34f * scale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.last().copy(alpha = 0.95f),
                        palette.first(),
                        DeepNavy.copy(alpha = 0.3f),
                    ),
                    center = Offset(center.x - r * 0.3f, center.y - r * 0.35f),
                    radius = r * 1.25f,
                ),
                radius = r,
                center = center,
            )

            // Блик — «влажный» живой свет
            val highlight = Offset(center.x - r * 0.36f, center.y - r * 0.4f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.85f), Color.Transparent),
                    center = highlight,
                    radius = r * 0.42f,
                ),
                radius = r * 0.36f,
                center = highlight,
            )
        }
    }
}
