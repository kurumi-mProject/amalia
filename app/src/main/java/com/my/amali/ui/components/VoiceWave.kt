package com.my.amali.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.my.amali.domain.entity.VoiceState
import kotlin.math.sin

/**
 * VoiceWave — био-синхронизированная волна Амалии.
 *
 * Заменяет старый орб (VoiceVisualizer). Семь вертикальных полос:
 * - Idle: дыхание с периодом 10 с (≈6 дыханий в минуту — темп спокойного
 *   человеческого дыхания из исследований вариабельности ритма сердца);
 * - Listening: мгновенно реагирует на [audioLevel] пружиной;
 * - Thinking: медленная бегущая волна (0.1 Гц);
 * - Speaking: ритмичная активность под синтез речи;
 * - Error: почти неподвижна и приглушена.
 *
 * Цвет полос — вертикальный градиент по схеме (Oklab-подобный мягкий
 * переход через androidx.compose.ui.graphics.lerp в линейном пространстве
 * не требуется: verticalGradient даёт эквивалентное восприятие).
 */
@Composable
fun VoiceWave(
    state: VoiceState,
    audioLevel: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 7,
) {
    // Базовая временная шкала: полный оборот фазы за 10 секунд → дыхание 6/мин.
    val breath = rememberInfiniteTransition(label = "waveBreath")
    val breathPhase by breath.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(10_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "breathPhase",
    )

    // Быстрая фаза для речевой/вопросительной активности: 1.4 с.
    val active = rememberInfiniteTransition(label = "waveActive")
    val activePhase by active.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "activePhase",
    )

    // Плавная пружина на громкость микрофона.
    val level by animateFloatAsState(
        targetValue = audioLevel.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 80f,
        ),
        label = "waveLevel",
    )

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val description = state.label

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .semantics { contentDescription = description },
    ) {
        val barWidth = size.width * 0.052f
        val gap = (size.width - barWidth * barCount) / (barCount - 1).coerceAtLeast(1)
        val centerY = size.height / 2f
        val maxHalf = size.height / 2f - size.height * 0.05f

        repeat(barCount) { index ->
            val phase = index * 0.85f
            val amplitude = when (state) {
                VoiceState.Idle ->
                    // Спокойное дыхание: 6 циклов в минуту.
                    0.18f + 0.10f * sin(breathPhase + phase)

                VoiceState.Listening -> {
                    val wobble = 0.55f + 0.45f * sin(activePhase * 1.6f + phase)
                    0.20f + 0.78f * level * wobble
                }

                VoiceState.Thinking ->
                    // Медленная бегущая волна ~0.1 Гц.
                    0.32f + 0.26f * sin(phase - breathPhase * 1.1f)

                VoiceState.Speaking -> {
                    val rhythm = 0.5f + 0.5f * sin(activePhase * 2.4f + phase * 1.35f)
                    0.30f + 0.62f * rhythm
                }

                VoiceState.Error -> 0.10f
            }.coerceIn(0.06f, 1f)

            val x = index * (barWidth + gap)
            val half = maxHalf * amplitude

            val topColor = when (state) {
                VoiceState.Error -> errorColor
                else -> tertiary
            }
            val bottomColor = when (state) {
                VoiceState.Error -> errorColor.copy(alpha = 0.65f)
                else -> primary
            }

            // Внешнее свечение.
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(topColor.copy(alpha = 0.18f), Color.Transparent),
                    startY = centerY - half,
                    endY = centerY + half,
                ),
                topLeft = Offset(x - barWidth * 0.45f, centerY - half * 1.35f),
                size = Size(barWidth * 1.9f, half * 2.7f),
                cornerRadius = CornerRadius(barWidth, barWidth),
            )

            // Основная полоса.
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(topColor, bottomColor),
                    startY = centerY - half,
                    endY = centerY + half,
                ),
                topLeft = Offset(x, centerY - half),
                size = Size(barWidth, half * 2f),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

/** Утилита рисования зеркальной полосы от центра — используется волной. */
private fun DrawScope.drawCenteredBar(
    x: Float,
    barWidth: Float,
    half: Float,
    centerY: Float,
    topColor: Color,
    bottomColor: Color,
) {
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(topColor, bottomColor),
            startY = centerY - half,
            endY = centerY + half,
        ),
        topLeft = Offset(x, centerY - half),
        size = Size(barWidth, half * 2f),
        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
    )
}
