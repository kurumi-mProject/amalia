package com.my.amali

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.my.amali.core.di.ServiceLocator
import com.my.amali.core.navigation.AmaliaNavHost
import com.my.amali.domain.entity.AppLanguage
import com.my.amali.domain.entity.UserSettings
import com.my.amali.ui.theme.AmaliaTheme
import com.my.amali.ui.theme.AmaliaVisuals
import com.my.amali.ui.theme.LocalAmaliaVisuals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Единственная активити Амалии (single-activity, Jetpack Compose).
 *
 * Последовательность запуска:
 * 1. Splash держится, пока DataStore не отдаст первые настройки.
 * 2. Локаль применяется через [AppCompatDelegate.setApplicationLocales] ещё
 *    до [setContent] — чтобы ресурсы загрузились сразу на нужном языке.
 * 3. [AmaliaTheme] строится из пользовательских настроек.
 * 4. [LocalAmaliaVisuals] раздаёт всем экранам параметры фона и стекла,
 *    поэтому любой экран рисует корректную аурору без проброса настроек
 *    через параметры композаблов.
 * 5. [AmaliaNavHost] стартует с онбординга при первом запуске.
 */
class MainActivity : ComponentActivity() {

    /** true → навигация стартует с онбординга; false → с ассистента. */
    private var startOnOnboarding by mutableStateOf(false)

    /** Настройки, прочитанные при старте (null → splash ещё виден). */
    private var bootSettings by mutableStateOf<UserSettings?>(null)

    /** Управляет скрытием splash-экрана. */
    private var uiReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { !uiReady }

        val settingsRepository = ServiceLocator.settingsRepository
        val onboardingKey =
            androidx.datastore.preferences.core.booleanPreferencesKey("onboarding_completed")

        lifecycleScope.launch {
            val first = runCatching { settingsRepository.settings.first() }
                .getOrDefault(UserSettings.DEFAULT)
            val completed = runCatching {
                ServiceLocator.dataStore.data.first()[onboardingKey] ?: false
            }.getOrDefault(false)

            // Применяем сохранённый язык до показа UI, чтобы ресурсы
            // загрузились сразу на нужном языке (без перерисовки).
            applyLocale(first.selectedLanguage)

            bootSettings = first
            startOnOnboarding = !completed
            uiReady = true
        }

        setContent {
            val settings by if (bootSettings != null) {
                settingsRepository.settings.collectAsStateWithLifecycle(
                    initialValue = bootSettings ?: UserSettings.DEFAULT,
                )
            } else {
                remember { mutableStateOf(UserSettings.DEFAULT) }
            }

            // Реактивно применяем язык при каждом изменении настройки —
            // AppCompatDelegate перезапустит активити, если locale изменился.
            LaunchedEffect(settings.selectedLanguage) {
                applyLocale(settings.selectedLanguage)
            }

            AmaliaTheme(
                darkModePref = settings.darkModePref,
                visualTheme = settings.visualTheme,
                useBioTime = settings.useBioTime,
            ) {
                CompositionLocalProvider(
                    LocalAmaliaVisuals provides AmaliaVisuals(
                        visualTheme = settings.visualTheme,
                        darkModePref = settings.darkModePref,
                        useBioTime = settings.useBioTime,
                        // Интенсивность фона следует настройке стекла, но
                        // никогда не гаснет полностью: минимум 35% свечения.
                        glassIntensity = 0.35f + settings.glassIntensity * 0.65f,
                        motif = settings.motif,
                        // Густота декораций следует за стеклом, но не гаснет
                        // вместе с ним: «тихая» тема остаётся тихой целиком.
                        motifDensity = (0.35f + settings.motifDensity * 0.65f) *
                            (0.6f + settings.glassIntensity * 0.4f),
                    ),
                ) {
                    AmaliaNavHost(startOnOnboarding = startOnOnboarding)
                }
            }
        }
    }

    /**
     * Применяет локаль через [AppCompatDelegate.setApplicationLocales].
     *
     * При [AppLanguage.SYSTEM] отдаём пустой список — система сама выберет
     * язык устройства. При конкретном языке передаём его BCP-47 код.
     *
     * AppCompatDelegate сам определяет, изменилась ли локаль, и только
     * тогда перезапускает активити — лишних рестартов не будет.
     */
    private fun applyLocale(language: AppLanguage) {
        val localeList = if (language.isSystem) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.code)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}
