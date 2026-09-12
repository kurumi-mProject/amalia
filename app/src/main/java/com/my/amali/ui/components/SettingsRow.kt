package com.my.amali.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.glassSurface

/**
 * Строки настроек Амалии.
 *
 * Ключевое решение дизайна: строки НЕ являются отдельными карточками.
 * Они живут внутри одного стеклянного блока [GlassGroup] и разделяются
 * тонкой линией — так список читается как единая панель, а не как
 * набор «плиток». Каждая строка ≥ 56dp, что превышает минимум 48dp.
 */

/** Заголовок группы настроек. */
@Composable
fun SettingsHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    SectionTitle(text = title, modifier = modifier)
}

/** Цветная стеклянная плашка под иконку строки. */
@Composable
private fun RowIcon(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(Radius.xs))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
    }
}

/** Базовый layout строки: иконка + текст + trailing. */
@Composable
private fun RowBody(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            RowIcon(icon = icon, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.sm))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        trailing()
    }
}

/** Строка с переключателем. */
@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.45f
    RowBody(
        icon = icon,
        title = title,
        subtitle = subtitle,
        titleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        modifier = modifier
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .semantics {
                role = Role.Switch
                contentDescription = title
            },
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

/** Строка-переход на другой экран. */
@Composable
fun SettingsActionRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    value: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "rowPress",
    )

    RowBody(
        icon = icon,
        title = title,
        subtitle = subtitle,
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = title
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Строка выбора значения. Выбранный вариант получает акцентный контур
 * и галочку, а не только цвет: состояние читается без опоры на цвет,
 * что важно для доступности.
 */
@Composable
fun SettingsValueRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "valuePress",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .glassSurface(
                shape = RoundedCornerShape(Radius.md),
                elevated = selected,
                fillAlpha = if (selected) 0.80f else null,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = title
            },
    ) {
        RowBody(
            icon = icon,
            title = title,
            subtitle = subtitle,
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.6f),
                    exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.6f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(Radius.chip))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                if (!selected) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(Radius.chip))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    )
                }
            }
        }
    }
}

/**
 * Строка-статус: показывает состояние системного параметра
 * (Wi-Fi, Bluetooth и т.д.) точкой-индикатором и текстом.
 */
@Composable
fun SettingsStatusRow(
    title: String,
    status: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val accent = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    RowBody(
        icon = icon,
        title = title,
        subtitle = null,
        modifier = modifier.then(
            if (onClick != null) {
                Modifier
                    .clickable(onClick = onClick)
                    .semantics {
                        role = Role.Button
                        contentDescription = "$title: $status"
                    }
            } else {
                Modifier.semantics { contentDescription = "$title: $status" }
            },
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(Radius.chip))
                    .background(accent.copy(alpha = if (active) 1f else 0.4f)),
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
        }
    }
}
