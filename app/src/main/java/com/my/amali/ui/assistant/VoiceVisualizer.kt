package com.my.amali.ui.assistant

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

enum class VoiceState(val label: String, val color: Color) {
    Idle("на связи", Color(0xFF7C6FE0)),
    Listening("слушаю", Color(0xFF7C6FE0)),
    Thinking("думаю", Color(0xFF8B8FB0)),
    Speaking("говорю", Color(0xFF9388F5)),
    Error("ошибка", Color(0xFFB06565)),
}

/**
 * Минималистичный голосовой визуализатор.
 * Вместо орба — чистые полосы-волны, которые реагируют на аудио-уровень.
 * В будущем подключается к реальному аудио-стриму — пока симуляция.
 */
@Composable
fun VoiceVisualizer(
    state: VoiceState,
    audioLevel: Float = 0f,
    modifier: Modifier = Modifier,
    barCount: Int = 5,
) {
    val transition = rememberInfiniteTransition(label = "voice")

    val animatedLevel by animateFloatAsState(
        targetValue = when (state) {
            VoiceState.Listening -> audioLevel.coerceIn(0f, 1f)
            VoiceState.Speaking -> audioLevel.coerceIn(0f, 1f)
            VoiceState.Thinking -> 0.15f
            VoiceState.Idle -> 0.06f
            VoiceState.Error -> 0.02f
        },
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "level"
    )

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    VoiceState.Listening -> 1100
                    VoiceState.Speaking -> 900
                    VoiceState.Thinking -> 2400
                    else -> 3200
                },
                easing = LinearEasing
            )
        ),
        label = "phase"
    )

    val barColor = state.color

    Row(
        modifier = modifier
            .height(72.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val spacing = 10.dp
        val barWidth = 4.dp
        val totalWidth = barCount * barWidth.value + (barCount - 1) * spacing.value
        val maxWidthDp = barWidth

        repeat(barCount) { i ->
            val center = (barCount - 1) / 2f
            val distance = abs(i - center) / center
            val wave = sin(phase * 3.14f * 2 + i * 0.8f) * 0.5f + 0.5f
            val envelope = 1f - distance * 0.3f
            val amplitude = animatedLevel * envelope * (0.4f + wave * 0.6f)

            val targetHeight = when (state) {
                VoiceState.Idle -> 6.dp + (amplitude * 14f).dp
                VoiceState.Thinking -> 4.dp + (amplitude * 8f).dp
                VoiceState.Error -> 4.dp
                else -> 10.dp + (amplitude * 58f).dp
            }

            val animatedHeight by animateFloatAsState(
                targetValue = targetHeight.value,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "bar_$i"
            )

            Canvas(
                modifier = Modifier
                    .width(barWidth)
                    .height(animatedHeight.dp)
                    .padding(horizontal = (spacing / 2))
            ) {
                drawRoundRect(
                    color = barColor.copy(
                        alpha = when (state) {
                            VoiceState.Idle -> 0.4f
                            VoiceState.Error -> 0.5f
                            else -> 0.85f
                        }
                    ),
                    size = Size(size.width, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        size.width / 2,
                        size.width / 2
                    )
                )
            }
        }
    }
}
