package com.my.amali.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.my.amali.R
import com.my.amali.ui.onboarding.ArrowDirection
import com.my.amali.ui.onboarding.GuideStep
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.accentGlow
import com.my.amali.ui.theme.glassSurface

/**
 * GuideOverlay — интерактивная подсказка поверх интерфейса.
 *
 * Затемнение делает фон менее контрастным, но не «выключает» его —
 * пользователь продолжает видеть элемент, о котором идёт речь.
 * Стрелка анимированно «дышит» в сторону подсвечиваемой зоны, поэтому
 * связь подсказки с элементом читается без линий и выносок.
 *
 * @param step текущий шаг гайда.
 * @param isLastStep последний шаг — кнопка меняется на «Готово».
 */
@Composable
fun GuideOverlay(
    step: GuideStep,
    isLastStep: Boolean,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "guideArrow")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.50f),
                        Color.Black.copy(alpha = 0.72f),
                    ),
                ),
            )
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .glassSurface(
                    shape = RoundedCornerShape(Radius.lg),
                    elevated = true,
                    fillAlpha = 0.95f,
                )
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (step.arrowDirection != ArrowDirection.NONE) {
                val icon = when (step.arrowDirection) {
                    ArrowDirection.UP -> Icons.Rounded.ArrowUpward
                    ArrowDirection.DOWN -> Icons.Rounded.ArrowDownward
                    ArrowDirection.LEFT -> Icons.AutoMirrored.Rounded.ArrowBack
                    ArrowDirection.RIGHT -> Icons.AutoMirrored.Rounded.ArrowForward
                    ArrowDirection.NONE -> Icons.Rounded.ArrowUpward
                }
                val shift = (drift * 6f).dp
                Box(
                    modifier = Modifier
                        .offset(
                            x = when (step.arrowDirection) {
                                ArrowDirection.LEFT -> -shift
                                ArrowDirection.RIGHT -> shift
                                else -> 0.dp
                            },
                            y = when (step.arrowDirection) {
                                ArrowDirection.UP -> -shift
                                ArrowDirection.DOWN -> shift
                                else -> 0.dp
                            },
                        )
                        .size(48.dp)
                        .accentGlow(
                            color = MaterialTheme.colorScheme.primary,
                            alpha = 0.30f + drift * 0.12f,
                            spread = 1.6f,
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.height(Spacing.md))
            }

            Text(
                text = stringResource(step.titleResId),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(step.descriptionResId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GhostButton(
                    text = stringResource(R.string.guide_skip_all),
                    onClick = onDismiss,
                )
                PrimaryButton(
                    text = stringResource(
                        if (isLastStep) R.string.onboarding_finish else R.string.onboarding_next,
                    ),
                    onClick = if (isLastStep) onDismiss else onNext,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
