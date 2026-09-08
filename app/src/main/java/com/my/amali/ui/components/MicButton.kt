package com.my.amali.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
androidx.compose.material.icons.filled.Mic
androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.my.amali.domain.entity.VoiceState

/**
 * MicButton — центральная кнопка микрофона Амалии.
 *
 * 76dp круглая кнопка с фирменным градиентом. В активном состоянии
 * ([isActive]) вокруг кнопки расходятся пульсирующие кольца, иконка
 * меняется на Stop. Полная семантика TalkBack через [stateLabel].
 *
 * @param isActive идёт ли сейчас запись/разговор.
 * @param stateLabel человекочитаемое состояние для TalkBack
 *   (например, stringResource(R.string.assistant_listening)).
 */
@Composable
fun MicButton(
    isActive: Boolean,
    stateLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val pulse = rememberInfiniteTransition(label = "micPulse")
    val ringProgress by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringProgress",
    )

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Box(
        modifier = modifier.size(76.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isActive) {
            // Два расходящихся кольца со сдвигом по фазе.
            Ring(
                progress = ringProgress,
                color = primary,
            )
            Ring(
                progress = (ringProgress + 0.5f) % 1f,
                color = tertiary,
            )
        }
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = androidx.compose.ui.graphics.Color.Transparent,
            shadowElevation = if (enabled) 6.dp else 0.dp,
            modifier = Modifier
                .size(76.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = stateLabel
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(primary, tertiary),
                        ),
                    )
                    .border(1.dp, outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                    tint = onPrimary,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

@Composable
private fun Ring(
    progress: Float,
    color: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .graphicsLayer {
                scaleX = 1f + progress * 0.7f
                scaleY = 1f + progress * 0.7f
                alpha = (1f - progress) * 0.45f
            }
            .border(
                width = 2.dp,
                color = color,
                shape = CircleShape,
            ),
    )
}
