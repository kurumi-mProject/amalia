package com.my.amali.core.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.my.amali.ui.theme.AmaliaVisualTheme
import androidx.compose.material3.LocalContentColor

/** Пара иконок выбранной/невыбранной вкладки. */
private data class TabIcons(
    val selected: ImageVector,
    val unselected: ImageVector,
)

private val tabIcons: Map<Destinations, TabIcons> = mapOf(
    Destinations.Assistant to TabIcons(Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
    Destinations.History to TabIcons(Icons.Filled.History, Icons.Outlined.History),
    Destinations.Settings to TabIcons(Icons.Filled.Settings, Icons.Outlined.Settings),
)

/**
 * Нижняя навигация Амалии: три вкладки — Ассистент, История, Настройки.
 *
 * Прячет себя плавным слайдом на не-вкладочных экранах (детали разговора,
 * разрешения). Вкладки переключаются без добавления в бэкстек верхнего уровня,
 * сохраняя состояние каждой вкладки.
 *
 * @param navController общий контроллер навигации.
 * @param currentRoute текущий маршрут из [NavHostController.currentBackStackEntryAsState].
 */
@Composable
fun BottomNavBar(
    navController: NavHostController,
    currentRoute: String?,
    visualTheme: AmaliaVisualTheme,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = AmaliaBottomTabs.any { tab -> tab.destination.route == currentRoute },
        enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 3 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 3 },
        modifier = modifier,
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 0.dp,
        ) {
            AmaliaBottomTabs.forEach { tab ->
                val icons = tabIcons.getValue(tab.destination)
                val selected = currentRoute == tab.destination.route
                NavigationBarItem(
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
                    icon = {
                        Icon(
                            imageVector = if (selected) icons.selected else icons.unselected,
                            contentDescription = stringResource(tab.labelRes),
                        )
                    },
                    label = { Text(stringResource(tab.labelRes)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = LocalContentColor.current.copy(alpha = 0.55f),
                        unselectedTextColor = LocalContentColor.current.copy(alpha = 0.55f),
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    ),
                )
            }
        }
    }
}
