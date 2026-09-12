package com.my.amali.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.accentGlow
import com.my.amali.ui.theme.glassSurface

/**
 * Состояния экранов: загрузка, пусто, ошибка, статус-баннер.
 * Все четыре собраны в одном стеклянном языке, поэтому переход между
 * ними не «дёргает» интерфейс.
 */

/**
 * Скелетон-плейсхолдер: стеклянная плашка с бегущим световым блеском.
 * Блеск идёт слева направо за 1.2 с — это читается как загрузка,
 * а не как сломанный элемент.
 */
@Composable
fun SkeletonPlaceholder(
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 8.dp,
) {
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label = "shimmer",
    )
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val sheen = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(base)
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        (progress - 0.25f).coerceIn(0f, 1f) to Color.Transparent,
                        progress.coerceIn(0f, 1f) to sheen,
                        (progress + 0.25f).coerceIn(0f, 1f) to Color.Transparent,
                    ),
                ),
            ),
    )
}

/** Скелетон списка: [items] стеклянных строк с плашками текста. */
@Composable
fun SkeletonList(
    items: Int = 5,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.listGap),
    ) {
        repeat(items) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(shape = RoundedCornerShape(Radius.md))
                    .padding(Spacing.md),
            ) {
                SkeletonPlaceholder(
                    modifier = Modifier.size(40.dp),
                    cornerRadius = Radius.xs,
                )
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    SkeletonPlaceholder(
                        Modifier
                            .fillMaxWidth(0.58f)
                            .height(13.dp),
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    SkeletonPlaceholder(
                        Modifier
                            .fillMaxWidth(0.34f)
                            .height(10.dp),
                    )
                }
            }
        }
    }
}

/**
 * Пустое состояние: стеклянный медальон с иконкой, заголовок,
 * пояснение и необязательное действие. Никогда не выглядит как
 * «сломанный экран» — всегда объясняет, что делать дальше.
 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            val transition = rememberInfiniteTransition(label = "emptyBreath")
            val breath by transition.animateFloat(
                initialValue = 0.85f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(3_400, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "breath",
            )
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .accentGlow(
                        color = MaterialTheme.colorScheme.primary,
                        alpha = 0.18f * breath,
                        spread = 1.5f,
                    )
                    .clip(CircleShape)
                    .glassSurface(shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    modifier = Modifier.size(38.dp),
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.lg))
            SecondaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** Состояние ошибки: мягкий красный акцент и явное действие повтора. */
@Composable
fun ErrorState(
    title: String,
    description: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .accentGlow(
                    color = MaterialTheme.colorScheme.error,
                    alpha = 0.18f,
                    spread = 1.4f,
                )
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.lg))
        SecondaryButton(text = retryLabel, onClick = onRetry)
    }
}

/** Центрированный индикатор загрузки с подписью. */
@Composable
fun LoadingState(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            color = color,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(34.dp),
        )
        if (label.isNotBlank()) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * StatusBanner — компактный баннер состояния системного параметра.
 *
 * Состояние кодируется тремя независимыми каналами: иконка, цвет и
 * текст. Пользователь с нарушением цветовосприятия поймёт статус по
 * иконке и подписи, поэтому баннер соответствует требованиям доступности.
 */
@Composable
fun StatusBanner(
    icon: ImageVector,
    title: String,
    message: String,
    positive: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = if (positive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                shape = RoundedCornerShape(Radius.md),
                tint = accent,
            )
            .padding(Spacing.md)
            .semantics { contentDescription = "$title: $message" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = accent,
            )
        }
    }
}
