package com.my.amali.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.amaliaShadow
import com.my.amali.ui.theme.glassSurface

/**
 * GlassCard — базовая стеклянная карточка Амалии.
 *
 * Собрана из [glassSurface]: полупрозрачная заливка, световой контур,
 * диагональный блик и мягкое свечение акцента снизу. Одинаково работает
 * на всех API, включая API 26, потому что не использует RenderEffect.
 *
 * ## Почему к стеклу добавлена тень ([amaliaShadow])
 *
 * Раньше все стеклянные поверхности лежали на фоне **в одной плоскости**:
 * ни одна карточка не отбрасывала тень, поэтому интерфейс выглядел
 * наклеенным слоем, а не набором предметов. Системная Material-тень здесь
 * непригодна — она всегда чёрная, а на тёплом янтарном вечере чёрное пятно
 * под карточкой разрушает всю работу по адаптации света.
 *
 * [amaliaShadow] берёт цвет из текущей палитры и тонируется её ведущим
 * тоном: тёплый вечер получает тёплую тень, холодное утро — холодную.
 *
 * @param cornerRadius радиус скругления (по умолчанию — общий Radius.md).
 * @param contentPadding внутренний отступ контента.
 * @param elevated усиленное стекло для акцентных/плавающих блоков;
 *   заодно поднимает и тень — иначе «парящая» карточка выглядит плоской.
 * @param tint цвет внутреннего свечения.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = Radius.md,
    contentPadding: Dp = Spacing.lg,
    elevated: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .amaliaShadow(elevation = if (elevated) 0.62f else 0.34f, shape = shape)
            .glassSurface(
                shape = shape,
                tint = tint,
                elevated = elevated,
            )
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Нажимаемая стеклянная карточка с пружинным «утапливанием».
 * Используется для строк настроек, элементов истории и карточек выбора.
 *
 * @param selected визуально выделяет карточку акцентным контуром.
 */
@Composable
fun GlassCardButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = Radius.md,
    contentPadding: Dp = Spacing.md,
    selected: Boolean = false,
    enabled: Boolean = true,
    role: Role = Role.Button,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.978f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "cardPress",
    )
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .glassSurface(
                shape = RoundedCornerShape(cornerRadius),
                tint = accent,
                elevated = selected,
                fillAlpha = if (selected) 0.78f else null,
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = role,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(contentPadding),
        content = content,
    )
}

/** Тонкий стеклянный разделитель внутри групп. */
@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    androidx.compose.material3.HorizontalDivider(
        modifier = modifier.padding(horizontal = Spacing.md),
        thickness = 0.7.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
    )
}
