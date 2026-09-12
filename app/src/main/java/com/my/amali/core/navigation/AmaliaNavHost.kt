package com.my.amali.core.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.my.amali.ui.assistant.AssistantScreen
import com.my.amali.ui.history.ConversationDetailScreen
import com.my.amali.ui.history.ConversationListScreen
import com.my.amali.ui.onboarding.OnboardingScreen
import com.my.amali.ui.permissions.PermissionsScreen
import com.my.amali.ui.settings.AboutSettings
import com.my.amali.ui.settings.AppearanceSettings
import com.my.amali.ui.settings.DeviceControlSettings
import com.my.amali.ui.settings.LanguageSettings
import com.my.amali.ui.settings.NotificationSettings
import com.my.amali.ui.settings.PrivacySettings
import com.my.amali.ui.settings.SettingsScreen
import com.my.amali.ui.settings.VoiceSettings

/**
 * Корневой NavHost Амалии.
 *
 * Вместо Material-Scaffold используется Box: нижняя навигация —
 * плавающая стеклянная панель, которая лежит ПОВЕРХ контента, поэтому
 * фон-аурора виден на всю высоту экрана и интерфейс читается как
 * единое стекло, а не как набор прямоугольных блоков.
 *
 * @param startOnOnboarding true при первом запуске — стартуем с онбординга.
 */
@Composable
fun AmaliaNavHost(
    startOnOnboarding: Boolean = false,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NavHost(
            navController = navController,
            startDestination = if (startOnOnboarding) {
                Destinations.Onboarding.route
            } else {
                Destinations.Assistant.route
            },
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                fadeIn(tween(240)) + slideInHorizontally(tween(280)) { it / 14 }
            },
            exitTransition = {
                fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 0.99f)
            },
            popEnterTransition = {
                fadeIn(tween(220)) + scaleIn(tween(240), initialScale = 0.995f)
            },
            popExitTransition = {
                fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { it / 14 }
            },
        ) {
            composable(Destinations.Onboarding.route) {
                OnboardingScreen(
                    onNavigateToAssistant = {
                        navController.navigate(Destinations.Assistant.route) {
                            popUpTo(Destinations.Onboarding.route) { inclusive = true }
                        }
                    },
                )
            }

            composable(Destinations.Assistant.route) {
                AssistantScreen(
                    onNavigateToHistory = {
                        navController.navigate(Destinations.History.route) {
                            popUpTo(Destinations.Assistant.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Destinations.Settings.route) {
                            popUpTo(Destinations.Assistant.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            composable(Destinations.History.route) {
                ConversationListScreen(
                    onOpenConversation = { id ->
                        navController.navigate(Destinations.Conversation.createRoute(id))
                    },
                )
            }

            composable(Destinations.Settings.route) {
                SettingsScreen(
                    onOpenAppearance = { navController.navigate("${Destinations.Settings.route}/appearance") },
                    onOpenLanguage = { navController.navigate("${Destinations.Settings.route}/language") },
                    onOpenVoice = { navController.navigate("${Destinations.Settings.route}/voice") },
                    onOpenDevice = { navController.navigate("${Destinations.Settings.route}/device") },
                    onOpenPrivacy = { navController.navigate("${Destinations.Settings.route}/privacy") },
                    onOpenNotifications = { navController.navigate("${Destinations.Settings.route}/notifications") },
                    onOpenAbout = { navController.navigate("${Destinations.Settings.route}/about") },
                    onOpenPermissions = { navController.navigate(Destinations.Permissions.route) },
                )
            }

            composable(Destinations.Permissions.route) {
                PermissionsScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Destinations.Conversation.route,
                arguments = listOf(
                    navArgument(Destinations.Conversation.ARG_ID) { type = NavType.StringType },
                ),
            ) { entry ->
                val conversationId = entry.arguments
                    ?.getString(Destinations.Conversation.ARG_ID)
                    .orEmpty()
                ConversationDetailScreen(
                    conversationId = conversationId,
                    onBack = { navController.popBackStack() },
                )
            }

            composable("${Destinations.Settings.route}/appearance") {
                AppearanceSettings(onBack = { navController.popBackStack() })
            }
            composable("${Destinations.Settings.route}/language") {
                LanguageSettings(onBack = { navController.popBackStack() })
            }
            composable("${Destinations.Settings.route}/voice") {
                VoiceSettings(onBack = { navController.popBackStack() })
            }
            composable("${Destinations.Settings.route}/device") {
                DeviceControlSettings(onBack = { navController.popBackStack() })
            }
            composable("${Destinations.Settings.route}/privacy") {
                PrivacySettings(
                    onBack = { navController.popBackStack() },
                    onOpenPermissions = { navController.navigate(Destinations.Permissions.route) },
                )
            }
            composable("${Destinations.Settings.route}/notifications") {
                NotificationSettings(onBack = { navController.popBackStack() })
            }
            composable("${Destinations.Settings.route}/about") {
                AboutSettings(onBack = { navController.popBackStack() })
            }
        }

        // Плавающая навигация поверх контента — только на вкладочных экранах.
        BottomNavBar(
            navController = navController,
            currentRoute = currentRoute,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Нижний отступ контента на вкладочных экранах, чтобы последний
 * элемент списка не уезжал под плавающую навигацию.
 */
val BottomBarContentInset = 96
