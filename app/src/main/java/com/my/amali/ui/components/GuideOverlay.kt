package com.my.amali.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
androidx.compose.material.icons.automirrored.filled.ArrowBack
androidx.compose.material.icons.automirrored.filled.ArrowForward
androidx.compose.material.icons.filled.ArrowDownward
androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.my.amali.R
import com.my.amali.ui.onboarding.ArrowDirection
import com.my.amali.ui.onboarding.GuideStep

/**
 * GuideOverlay — интерактивный гайд со стрелками после онбординга.
 *
 * Затемняет экран и показывает пузырь с заголовком/подписью текущего шага.
 * Направление стрелки берётся из [GuideStep.arrowDirection] и указывает
 * на подсвечиваемую зону. На последнем шаге кнопка меняется на «Готово».
 *
 * @param step текущий шаг гайда (из GuideStep.guideSteps).
 * @param isLastStep true, когда показывается последний шаг.
 * @param onNext переход к следующему шагу.
 * @param onDismiss закрыть гайд (последний шаг или кнопка пропуска).
 */
@Composable
fun GuideOverlay(
    step: GuideStep,
    isLastStep: Boolean,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(180)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.93f),
                            ),
                        ),
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (step.arrowDirection != ArrowDirection.NONE) {
                    Icon(
                        imageVector = when (step.arrowDirection) {
                            ArrowDirection.UP -> Icons.Filled.ArrowUpward
                            ArrowDirection.DOWN -> Icons.Filled.ArrowDownward
                            ArrowDirection.LEFT -> Icons.AutoMirrored.Filled.ArrowBack
                            ArrowDirection.RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
                            ArrowDirection.NONE -> Icons.Filled.ArrowUpward
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .size(34.dp)
                            .scale(1.15f),
                    )
                }
                Text(
                    text = stringResource(step.titleResId),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(step.descriptionResId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            stringResource(R.string.guide_skip_all),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                    TextButton(onClick = if (isLastStep) onDismiss else onNext) {
                        Text(
                            stringResource(if (isLastStep) R.string.onboarding_finish else R.string.onboarding_next),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
