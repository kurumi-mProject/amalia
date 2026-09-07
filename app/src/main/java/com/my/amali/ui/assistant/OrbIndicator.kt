package com.my.amali.ui.assistant

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * Расширенная палитра Аурора для живого орба.
 */
private val AuroraViolet = Color(0xFF8B5CF6)
private val AuroraIndigo = Color(0xFF6366F1)
private val AuroraCyan = Color(0xFF22D3EE)
private val AuroraTeal = Color(0xFF2DD4BF)
private val DeepNavy = Color(0xFF0B1020)
private val SoftWhite = Color(0xFFE8EBF5)

/**
 * Состояния орба с эмоциональной палитрой.
 */
enum class OrbState(val label: String) {
    Idle("на связи"),
    Listening("слушаю"),
    Thinking("думаю"),
    Speaking("говорю"),
    Error("ошибка"),
}

/**
 * Супер-премиальный живой орб Амалии.
 *
 * Особенности:
 * - Многослойный: внешнее свечение, энергетическое кольцо, основная сфера, внутренний блик, суб-частицы
 * - Состояние полностью меняет поведение, скорость, цвет, масштаб
 * - При listening — живая волна амплитуды (имитация голоса)
 * - При speaking — пульсация + бегущие кольца
 * - При thinking — медленное вращение + "дыхание"
 * - При idle — спокойное, элегантное дыхание
 * - Полностью векторный, производительный
 */
@Composable
fun OrbIndicator(
    state: OrbState,
    audioLevel: Float = 0f, // 0f..1f — уровень "голоса" при прослушивании
    modifier: Modifier = Modifier,
    orbSize: Dp = 260.dp,
) {
    val transition = rememberInfiniteTransition(label = "orbMaster")

    // Основное дыхание (всегда)
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    OrbState.Listening -> 900
                    OrbState.Thinking -> 2100
                    OrbState.Speaking -> 1100
                    else -> 2800
                },
                easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    // Вращение энергетических слоёв
    val rotationSlow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
        ),
        label = "rotSlow"
    )

    val rotationFast by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    OrbState.Listening -> 1400
                    OrbState.Speaking -> 1600
                    OrbState.Thinking -> 3800
                    else -> 9200
                },
                easing = LinearEasing
            )
        ),
        label = "rotFast"
    )

    // Амплитуда для listening (реалистичный voice level)
    val listeningAmplitude by animateFloatAsState(
        targetValue = if (state == OrbState.Listening) (0.6f + audioLevel * 1.1f) else 0f,
        animationSpec = tween(90),
        label = "amplitude"
    )

    // Цветовая палитра в зависимости от состояния
    val (primaryColor, secondaryColor, accentColor) = when (state) {
        OrbState.Idle -> Triple(AuroraViolet, AuroraIndigo, AuroraCyan.copy(alpha = 0.6f))
        OrbState.Listening -> Triple(Color(0xFFFF6B6B), Color(0xFFFFA07A), AuroraCyan)
        OrbState.Thinking -> Triple(Color(0xFFFBBF24), AuroraViolet, AuroraTeal)
        OrbState.Speaking -> Triple(AuroraCyan, AuroraTeal, AuroraViolet)
        OrbState.Error -> Triple(Color(0xFFEF4444), Color(0xFFF87171), Color(0xFF9CA3AF))
    }

    val baseScale = when (state) {
        OrbState.Idle -> 0.86f
        OrbState.Listening -> 1.05f + listeningAmplitude * 0.08f
        OrbState.Thinking -> 0.95f
        OrbState.Speaking -> 1.12f
        OrbState.Error -> 0.92f
    }
    val scale = baseScale * (0.95f + 0.10f * breath)

    Box(
        modifier = modifier.size(orbSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(orbSize)) {
            val d = size.minDimension
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = d * 0.5f

            // === 1. Внешнее мягкое свечение (атмосфера) ===
            val glowRadius = radius * (1.15f + (if (state == OrbState.Listening) listeningAmplitude * 0.12f else 0f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )

            // === 2. Энергетическое внешнее кольцо ===
            rotate(rotationSlow, center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            secondaryColor.copy(alpha = 0.0f),
                            secondaryColor.copy(alpha = 0.85f),
                            secondaryColor.copy(alpha = 0.0f)
                        )
                    ),
                    startAngle = rotationSlow * 0.6f,
                    sweepAngle = 95f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.96f, center.y - radius * 0.96f),
                    size = Size(radius * 1.92f, radius * 1.92f),
                    style = Stroke(width = d * 0.018f, cap = StrokeCap.Round)
                )
            }

            // === 3. Основная сфера — многослойный градиент ===
            val coreRadius = radius * 0.62f * scale

            // Глубокий фон сферы
            drawCircle(
                color = DeepNavy.copy(alpha = 0.6f),
                radius = coreRadius * 1.02f,
                center = center
            )

            // Главный живой градиент
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.98f),
                        secondaryColor,
                        DeepNavy.copy(alpha = 0.55f)
                    ),
                    center = Offset(center.x - coreRadius * 0.28f, center.y - coreRadius * 0.32f),
                    radius = coreRadius * 1.45f
                ),
                radius = coreRadius,
                center = center
            )

            // === 4. Внутренний блик (влажный premium свет) ===
            val highlightOffset = Offset(
                center.x - coreRadius * 0.32f,
                center.y - coreRadius * 0.38f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SoftWhite.copy(alpha = 0.92f),
                        Color.Transparent
                    ),
                    center = highlightOffset,
                    radius = coreRadius * 0.55f
                ),
                radius = coreRadius * 0.42f,
                center = highlightOffset
            )

            // === 5. Состояние-специфичные эффекты ===

            // Listening — динамическая волновая текстура вокруг сферы
            if (state == OrbState.Listening && listeningAmplitude > 0.05f) {
                val waveCount = 5
                for (i in 0 until waveCount) {
                    val phase = (System.currentTimeMillis() / 180f + i * 1.7f) % (2 * PI)
                    val amp = listeningAmplitude * (0.6f + sin(phase).toFloat() * 0.4f)
                    val waveR = coreRadius * (1.08f + i * 0.035f) + amp * 18f

                    drawCircle(
                        color = AuroraCyan.copy(alpha = 0.12f - i * 0.018f),
                        radius = waveR,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            // Speaking — пульсирующие концентрические кольца
            if (state == OrbState.Speaking) {
                val pulse = (0.5f + 0.5f * sin(breath * PI * 2).toFloat())
                for (k in 0..2) {
                    val r = coreRadius * (1.18f + k * 0.09f) * (0.92f + pulse * 0.08f)
                    drawCircle(
                        color = accentColor.copy(alpha = 0.18f - k * 0.05f),
                        radius = r,
                        center = center,
                        style = Stroke(width = (2.5f - k * 0.6f).dp.toPx())
                    )
                }
            }

            // Thinking — тонкое вращающееся внутреннее кольцо
            if (state == OrbState.Thinking) {
                rotate(rotationFast * 0.6f, center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                accentColor.copy(alpha = 0.0f),
                                accentColor.copy(alpha = 0.65f),
                                accentColor.copy(alpha = 0.0f)
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = 70f,
                        useCenter = false,
                        topLeft = Offset(center.x - coreRadius * 0.78f, center.y - coreRadius * 0.78f),
                        size = Size(coreRadius * 1.56f, coreRadius * 1.56f),
                        style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Error — лёгкая пульсация красным
            if (state == OrbState.Error) {
                val errPulse = 0.7f + 0.3f * breath
                drawCircle(
                    color = Color(0xFFEF4444).copy(alpha = 0.25f * errPulse),
                    radius = coreRadius * 1.05f,
                    center = center,
                    style = Stroke(width = 4.dp.toPx())
                )
            }

            // === 6. Финальный тонкий контур для премиум-ощущения ===
            drawCircle(
                color = SoftWhite.copy(alpha = 0.08f),
                radius = coreRadius * 0.995f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}
