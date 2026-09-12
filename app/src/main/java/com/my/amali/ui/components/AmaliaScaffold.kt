package com.my.amali.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.my.amali.ui.theme.LocalAmaliaVisuals
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.glassSurface

/**
 * AmaliaScreen — единая оболочка любого экрана приложения.
 *
 * Отвечает за три вещи, которые раньше делались по-разному на каждом
 * экране и ломали визуальное единство:
 *  1. живой аурора-фон под контентом (берётся из [LocalAmaliaVisuals]);
 *  2. крупный заголовок в стиле «большой типографики», а не серый AppBar;
 *  3. корректные системные отступы (status bar, нижняя навигация).
 *
 * @param title заголовок экрана. Пустая строка — заголовок не рисуется.
 * @param subtitle необязательная подпись под заголовком.
 * @param onBack если задан — слева появляется стеклянная кнопка «назад».
 * @param actions иконки действий справа от заголовка.
 * @param content контент экрана; получает отступы для скролла.
 */
@Composable
fun AmaliaScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Назад",
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val visuals = LocalAmaliaVisuals.current

    Box(modifier = modifier.fillMaxSize()) {
        GradientBackground(
            visualTheme = visuals.visualTheme,
            darkModePref = visuals.darkModePref,
            useBioTime = visuals.useBioTime,
            intensity = visuals.glassIntensity,
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.fillMaxSize()) {
            AmaliaHeader(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                backLabel = backLabel,
                actions = actions,
            )
            Box(Modifier.weight(1f)) {
                content(
                    PaddingValues(
                        start = Spacing.screen,
                        end = Spacing.screen,
                        top = Spacing.xs,
                        bottom = Spacing.huge,
                    ),
                )
            }
        }
    }
}

/**
 * Шапка экрана: кнопка назад, крупный заголовок, подпись, действия.
 * Заголовок — displaySmall/headlineMedium, чтобы держать сильную
 * вертикальную иерархию вместо мелкого центрированного AppBar.
 */
@Composable
fun AmaliaHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Назад",
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = Spacing.screen,
                end = Spacing.screen,
                top = Spacing.sm,
                bottom = Spacing.md,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = backLabel,
                    onClick = onBack,
                )
                Spacer(Modifier.width(Spacing.sm))
            }
            Column(Modifier.weight(1f)) {
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
            }
        }
    }
}

/**
 * GlassIconButton — круглая стеклянная кнопка-иконка 44dp внутри
 * тач-зоны 48dp. Используется в шапках вместо системных IconButton,
 * чтобы кнопки не «висели в воздухе» на прозрачном фоне.
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    badge: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "iconPress",
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .scale(scale)
                .glassSurface(shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
        if (badge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/**
 * SectionTitle — заголовок группы внутри экрана.
 * Мелкий, разреженный, приглушённый: структурирует список, но не
 * конкурирует за внимание с основным контентом.
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        modifier = modifier.padding(
            start = Spacing.xxs,
            top = Spacing.lg,
            bottom = Spacing.xs,
        ),
    )
}

/**
 * GlassGroup — визуальная группа строк настроек в одном стеклянном блоке
 * с разделителями. Заменяет «плавающие» карточки-строки: список
 * выглядит собранным и дорогим.
 */
@Composable
fun GlassGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(shape = RoundedCornerShape(Radius.md)),
    ) {
        content()
    }
}

/**
 * Плавающая стеклянная панель для «прилипающего» контента у края экрана
 * (нижняя навигация, панель действий). Отличается усиленным контуром и
 * более плотной заливкой, чтобы контент под ней читался как «под стеклом».
 */
@Composable
fun FloatingGlassBar(
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = Radius.lg,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 60.dp)
            .glassSurface(
                shape = RoundedCornerShape(cornerRadius),
                elevated = true,
                fillAlpha = 0.86f,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Тонкая градиентная «тень» под плавающей панелью — мягкий отрыв от фона. */
@Composable
fun BarShadow(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                    ),
                ),
            ),
    )
}
