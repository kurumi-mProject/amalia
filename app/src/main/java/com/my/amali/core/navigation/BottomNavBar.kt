package com.my.amali.core.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.accentGlow
import com.my.amali.ui.theme.glassSurface

/** Иконка вкладки. В минимализме одна иконка на состояние — без «залитых» дублей. */
private val tabIcons: Map<Destinations, ImageVector> = mapOf(
    Destinations.Assistant to Icons.AutoMirrored.Rounded.Chat,
    Destinations.History to Icons.Rounded.History,
    Destinations.Settings to Icons.Rounded.Settings,
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
 * Одна вкладка: круглая иконка, при выборе — акцентная подложка,
 * свечение и появляющаяся подпись. Тач-зона 56dp.
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
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(240),
        label = "pillIconColor",
    )
    val indicatorSize by animateDpAsState(
        targetValue = if (selected) 40.dp else 34.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillIndicator",
    )
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .width(96.dp)
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
                .size(indicatorSize)
                .scale(scale)
                .then(
                    if (selected) {
                        Modifier
                            .accentGlow(color = accent, alpha = 0.45f, spread = 1.8f)
                            .clip(CircleShape)
                            .background(accent)
                    } else {
                        Modifier.clip(CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(120)),
        ) {
            Column {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
