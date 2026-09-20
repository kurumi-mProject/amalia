package com.my.amali.core.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.my.amali.ui.icons.AmaliaHistory
import com.my.amali.ui.icons.AmaliaSettings
import com.my.amali.ui.icons.AmaliaVoice
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.accentGlow
import com.my.amali.ui.theme.amaliaShadow
import com.my.amali.ui.theme.glassSurface

/** Иконка вкладки. Своя семья глифов — см. [com.my.amali.ui.icons]. */
private val tabIcons: Map<Destinations, ImageVector> = mapOf(
    Destinations.Assistant to AmaliaVoice,
    Destinations.History to AmaliaHistory,
    Destinations.Settings to AmaliaSettings,
)

/**
 * BottomNavBar — плавающая стеклянная «пилюля» с тремя вкладками.
 *
 * Отличия от системного NavigationBar: панель не прилипает к краю
 * экрана, а висит над ним со отступами, имеет стеклянную заливку,
 * световой контур и подсвеченную акцентом активную вкладку. Подпись
 * показывается только у активной вкладки — это убирает визуальный шум
 * и делает переключение читаемым.
 *
 * @param currentRoute текущий маршрут для определения активной вкладки.
 */
@Composable
fun BottomNavBar(
    navController: NavHostController,
    currentRoute: String?,
    modifier: Modifier = Modifier,
) {
    val visible = AmaliaBottomTabs.any { it.destination.route == currentRoute }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { it },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { it },
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    // Тень под панелью: до этого у навбара её не было вовсе —
                    // было только внутреннее свечение стекла. Без опоры
                    // панель «висела» в воздухе, а раз она плавающая,
                    // отсутствие тени читалось как дефект. Цвет тени берётся
                    // из палитры времени суток (тёплая вечером, холодная
                    // утром), поэтому она никогда не выглядит чёрной кляксой
                    // на тёплом фоне.
                    .amaliaShadow(elevation = 0.48f, shape = RoundedCornerShape(Radius.lg))
                    .glassSurface(
                        shape = RoundedCornerShape(Radius.lg),
                        elevated = true,
                        fillAlpha = 0.88f,
                    )
                    .padding(horizontal = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                AmaliaBottomTabs.forEach { tab ->
                    val selected = currentRoute == tab.destination.route
                    NavPill(
                        icon = tabIcons.getValue(tab.destination),
                        label = stringResource(tab.labelRes),
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(tab.destination.route) {
                                    popUpTo(Destinations.Assistant.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Одна вкладка: своя иконка, под ней — точка-индикатор.
 *
 * ## Почему не залитый круг
 *
 * Прежняя активная вкладка была сплошным непрозрачным кругом цвета primary
 * с иконкой цвета onPrimary. Внутри стекла непрозрачная масса невозможна —
 * это ломает саму метафору: стекло пропускает свет, а не перекрывает его.
 * Поэтому активное состояние собрано **гибридно** из трёх слабых сигналов,
 * которые вместе читаются сильнее любого из них:
 *
 *  1. **цвет иконки** — акцент вместо приглушённого onSurfaceVariant;
 *  2. **мягкое свечение** под иконкой (круглое, ограничено её радиусом);
 *  3. **точка-индикатор** под иконкой, появляющаяся пружиной.
 *
 * Ни один сигнал не кричит в одиночку, но их сумма однозначна.
 *
 * ## Почему тень больше не «искажена»
 *
 * Прежний `accentGlow` рисовался от **прямоугольника** композиции и
 * пересчитывался каждый кадр, пока размер подложки анимировался
 * (34dp → 40dp). На узкой панели это давало размазанное пятно, которое
 * заезжало под соседние вкладки. Теперь свечение рисуется строго по кругу
 * фиксированного радиуса и его размер не анимируется — анимируется только
 * сама иконка.
 */
@Composable
private fun NavPill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillPress",
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        },
        animationSpec = tween(240),
        label = "pillIconColor",
    )
    // Точка-индикатор: своё состояние видно не только цветом — это важно
    // для тех, кто не различает оттенки.
    val dotAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(220),
        label = "pillDot",
    )
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.2f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pillDotScale",
    )
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .width(88.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(Radius.md))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .scale(scale),
            contentAlignment = Alignment.Center,
        ) {
            // Свечение — круглый градиент ровно по габариту подложки.
            // Радиус фиксирован, размер не анимируется, поэтому пятно
            // не «плывёт» и не выходит за границы своей вкладки.
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .accentGlow(color = accent, alpha = 0.34f, spread = 1.6f),
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        // Точка-индикатор активной вкладки. Подписи в навбаре не показываем:
        // три широкие пилюли с текстом спорили за внимание с главным экраном,
        // а роль подписи для TalkBack выполняет contentDescription выше.
        Box(
            modifier = Modifier
                .size(4.dp)
                .scale(dotScale)
                .alpha(dotAlpha)
                .clip(CircleShape)
                .background(accent),
        )
    }
}
