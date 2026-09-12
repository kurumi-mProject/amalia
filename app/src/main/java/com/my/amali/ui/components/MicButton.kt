package com.my.amali.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.my.amali.ui.theme.accentGlow

/**
 * MicButton — главный CTA приложения: стеклянная капля с микрофоном.
 *
 * Конструкция (снизу вверх):
 *  1. живое свечение акцента под кнопкой — усиливается в активном режиме;
 *  2. два расходящихся кольца-пульса (только когда идёт разговор);
 *  3. тонкое вращающееся кольцо-«орбита» — показывает, что система живая;
 *  4. стеклянное тело: радиальный градиент + верхний блик + контур;
 *  5. иконка Mic/Stop с плавной подменой.
 *
 * Тач-зона 88dp — заметно больше минимума 48dp, попадать большим пальцем
 * легко даже одной рукой.
 *
 * @param isActive идёт запись/разговор.
 * @param stateLabel человекочитаемое состояние для TalkBack.
 * @param level уровень звука 0..1 — слегка «раздувает» кнопку в такт голосу.
 */
@Composable
fun MicButton(
    isActive: Boolean,
    stateLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    level: Float = 0f,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val pulse = rememberInfiniteTransition(label = "micPulse")
    val ring by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_200, easing = LinearEasing)),
        label = "ring",
    )
    val orbit by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7_000, easing = LinearEasing)),
        label = "orbit",
    )
    val breath by pulse.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            tween(5_000, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "breath",
    )

    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.94f
            isActive -> 1.03f + level.coerceIn(0f, 1f) * 0.05f
            else -> breath
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "micScale",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.42f else 0.22f,
        animationSpec = tween(420),
        label = "micGlow",
    )

    val primary = MaterialTheme.colorScheme.primary
    val accentSoft = MaterialTheme.colorScheme.secondary
    val cool = MaterialTheme.colorScheme.tertiary
    val onAccent = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = modifier
            .size(88.dp)
            .semantics {
                role = Role.Button
                contentDescription = stateLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        // 1. Свечение под кнопкой.
        Box(
            modifier = Modifier
                .size(88.dp)
                .accentGlow(color = primary, alpha = glowAlpha, spread = 2.1f),
        )

        // 2. Пульсирующие кольца в активном состоянии.
        if (isActive) {
            PulseRing(progress = ring, color = accentSoft)
            PulseRing(progress = (ring + 0.5f) % 1f, color = cool)
        }

        // 3. Орбита: тонкая дуга, медленно вращается.
        Canvas(modifier = Modifier.size(84.dp)) {
            val stroke = 1.4f
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color.Transparent,
                        primary.copy(alpha = if (isActive) 0.85f else 0.35f),
                        Color.Transparent,
                        Color.Transparent,
                    ),
                ),
                startAngle = orbit,
                sweepAngle = 110f,
                useCenter = false,
                style = Stroke(width = stroke * 2f, cap = StrokeCap.Round),
            )
        }

        // 4–5. Стеклянное тело и иконка.
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(scale)
                .background(
                    brush = Brush.radialGradient(
                        colors = if (isActive) {
                            listOf(accentSoft, primary, primary.copy(alpha = 0.92f))
                        } else {
                            listOf(
                                primary.copy(alpha = 0.96f),
                                primary.copy(alpha = 0.80f),
                                primary.copy(alpha = 0.62f),
                            )
                        },
                        center = Offset(28f, 20f),
                        radius = 150f,
                    ),
                    shape = CircleShape,
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.42f),
                            Color.White.copy(alpha = 0.05f),
                        ),
                    ),
                    shape = CircleShape,
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Верхний стеклянный блик внутри капли.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.White.copy(alpha = 0.22f),
                                0.45f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.10f),
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )
            AnimatedContent(
                targetState = isActive,
                transitionSpec = {
                    (fadeIn(tween(160)) + scaleIn(initialScale = 0.7f)) togetherWith
                        (fadeOut(tween(120)) + scaleOut(targetScale = 0.7f))
                },
                label = "micIcon",
            ) { active ->
                Icon(
                    imageVector = if (active) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                    tint = onAccent,
                    modifier = Modifier.size(if (active) 26.dp else 30.dp),
                )
            }
        }
    }
}

/** Одно расходящееся кольцо-пульс. */
@Composable
private fun PulseRing(progress: Float, color: Color) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .scale(1f + progress * 0.55f)
            .border(
                width = (1.5f - progress).coerceAtLeast(0.6f).dp,
                color = color.copy(alpha = (1f - progress) * 0.38f),
                shape = CircleShape,
            ),
    )
}
