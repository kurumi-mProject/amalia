package com.my.amali

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.my.amali.core.di.ServiceLocator
import com.my.amali.core.navigation.AmaliaNavHost
import com.my.amali.domain.entity.AppLanguage
import com.my.amali.domain.entity.UserSettings
import com.my.amali.ui.settings.SettingsViewModel
import com.my.amali.ui.theme.AmaliaTheme
import com.my.amali.ui.theme.AmaliaVisuals
import com.my.amali.ui.theme.LocalAmaliaVisuals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Единственная активити Амалии (single-activity, Jetpack Compose).
 *
 * ════════════════════════════════════════════════════════════════════════
 *  СМЕНА ЯЗЫКА: ПОЧЕМУ ПРИЛОЖЕНИЕ ПАДАЛО И КАК ЭТО УСТРОЕНО ТЕПЕРЬ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Симптом был жёсткий: пользователь выбирал язык в настройках и получал
 * краш — приложение не запускалось вообще, потому что язык уже сохранён
 * в DataStore, и падало оно **при каждом** старте.
 *
 * Причин было три, и все три — от смешивания двух механизмов локализации
 * в одном месте.
 *
 * ### 1. `Locale.setDefault()` вызывался внутри композиции
 *
 * Глобальная локаль процесса менялась прямо во время отрисовки кадра.
 * После этого `Locale.getDefault()` расходился с `Configuration` активити:
 * часть кода (`HistoryFormat`, `EngineOptions`) читала уже новую локаль,
 * а системные ресурсы — прежнюю. На таком расхождении `Resources` бросает
 * исключение, и приложение падает ещё до первого кадра.
 *
 * **Теперь:** глобальная локаль не трогается вообще. Язык живёт в контексте
 * поддерева композиции ([LocalizedContent]) и в DataStore — двух местах,
 * которые не могут испортить состояние процесса.
 *
 * ### 2. `applyLocale()` вызывался из `LaunchedEffect` внутри композиции
 *
 * `AppCompatDelegate.setApplicationLocales()` пересоздаёт активити. Вызов
 * из композиции давал гонку: новая активити стартовала, `bootSettings` ещё
 * `null`, композиция видела `DEFAULT` (то есть `SYSTEM`), снова звала
 * `applyLocale(SYSTEM)` → снова пересоздание → бесконечный цикл → ANR.
 *
 * **Теперь:** `AppCompatDelegate` вызывается **только** там, где язык
 * реально изменил пользователь, — и никогда во время композиции.
 *
 * ### 3. `configChanges="locale"` в манифесте
 *
 * Активити заявляла, что сама обработает смену локали, а `AppCompatDelegate`
 * при этом ждал пересоздания. Система пересоздание не присылала — состояние
 * локали в активити и в AppCompat расходилось.
 *
 * **Теперь:** `locale` и `layoutDirection` убраны из `configChanges` (см.
 * манифест): пусть система честно пересоздаёт активити, если ей это нужно.
 *
 * ## Как это работает в итоге
 *
 *  — **Мгновенно**: `LocalizedContent` подкладывает в дерево контекст с
 *    нужной локалью, и все `stringResource` читают строки нового языка
 *    сразу, без перезапуска; навигация и позиция прокрутки не теряются.
 *  — **Между запусками**: выбор персистится в DataStore (`pref_app_language`)
 *    и на старте применяется один раз — до [setContent].
 *  — **В системном меню Android 13+**: язык виден в «Язык приложения»
 *    благодаря `AppLocalesMetadataHolderService` с `autoStoreLocales`,
 *    который уже объявлен в манифесте. Отдельного `setApplicationLocales`
 *    из жизненного цикла для этого не требуется.
 */
class MainActivity : ComponentActivity() {

    /** true → навигация стартует с онбординга; false → с ассистента. */
    private var startOnOnboarding by mutableStateOf(false)

    /** Настройки, прочитанные при старте (null → splash ещё виден). */
    private var bootSettings by mutableStateOf<UserSettings?>(null)

    /**
     * Язык, применённый к **контексту активити** при старте.
     *
     * Нужен ровно для одного: не пересоздавать активити, если пользователь
     * выбрал тот язык, который уже действует. Значение живёт в поле активити,
     * а не в `remember`, — снимок в композиции умирает на смене конфигурации.
     */
    private var appliedLanguage: AppLanguage = AppLanguage.SYSTEM

    /** Управляет скрытием splash-экрана. */
    private var uiReady by mutableStateOf(false)

    /**
     * Мост для смены языка из настроек.
     *
     * Ставится один раз при создании активити и снимается при уничтожении:
     * ViewModel может пережить активити, и держать в ней ссылку на мёртвую
     * активити нельзя — это утечка и потенциальный краш при вызове.
     */
    private val languageApplier = object : SettingsViewModel.LanguageApplier {
        override fun onLanguageChanged(language: AppLanguage) {
            applyLanguageChoice(language)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        // Локаль активити применяется ДО её создания.
        //
        // Это правильная точка для языка: `Resources` формируются здесь,
        // и дальше весь жизненный цикл работает с уже локализованным
        // контекстом. Никаких `Locale.setDefault`, никаких пересозданий во
        // время композиции — только этот метод и только один раз.
        //
        // Язык читается синхронно из DataStore: attachBaseContext не может
        // быть suspend, а показать первый кадр на чужом языке нельзя.
        // runBlocking здесь безопасен — это очень ранняя стадия запуска,
        // никакой UI-поток ещё не занят, а объём чтения — одно значение.
        val language = runCatching { currentLanguageBlocking() }
            .getOrDefault(AppLanguage.SYSTEM)
        appliedLanguage = language
        super.attachBaseContext(localizedContextFor(newBase, language))
    }

    /**
     * Синхронно читает сохранённый язык.
     *
     * Две ветки по очереди, потому что DataStore может отдать файл ещё не
     * готовым на самом первом запуске: тогда остаётся язык системы, и это
     * правильное поведение — пользователь ещё ничего не выбирал.
     */
    private fun currentLanguageBlocking(): AppLanguage = kotlinx.coroutines.runBlocking {
        runCatching {
            ServiceLocator.settingsRepository.settings.first().selectedLanguage
        }.getOrDefault(AppLanguage.SYSTEM)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { !uiReady }

        // Подписываем ViewModel настроек на смену языка. Живёт столько же,
        // сколько активити: снимаем в onDestroy, иначе мёртвая активити
        // останется в статическом поле и получит вызов.
        SettingsViewModel.languageApplier = languageApplier

        val settingsRepository = ServiceLocator.settingsRepository
        val onboardingKey =
            androidx.datastore.preferences.core.booleanPreferencesKey("onboarding_completed")

        lifecycleScope.launch {
            val first = runCatching { settingsRepository.settings.first() }
                .getOrDefault(UserSettings.DEFAULT)
            val completed = runCatching {
                ServiceLocator.dataStore.data.first()[onboardingKey] ?: false
            }.getOrDefault(false)

            // Локаль применяется ДО показа UI: ресурсы активити загружаются
            // сразу на нужном языке, и первого кадра на чужом языке не видно.
            // Никаких `Locale.setDefault` — только attachBaseContext-путь,
            // который уже отработал при создании активити.
            runCatching { applyLocaleSafely(first.selectedLanguage) }
            appliedLanguage = first.selectedLanguage

            bootSettings = first
            startOnOnboarding = !completed
            uiReady = true
        }

        setContent {
            // Единая точка подписки на настройки: сплэш держится до готовности
            // репозитория, поэтому `DEFAULT` на первом кадре пользователь
            // не увидит.
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = bootSettings ?: UserSettings.DEFAULT,
            )

            // Ошибка применения языка не имеет права уронить экран: язык —
            // это настройка, а не условие работы приложения. Любой сбой
            // логируем и продолжаем с тем, что есть.
            // Ошибка применения языка не имеет права уронить экран: язык —
            // это настройка, а не условие работы приложения. Любой сбой
            // логируем и продолжаем с тем, что есть.
            LocalizedContent(language = settings.selectedLanguage) {
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
    }

    /**
     * Переопределяет локаль для поддерева композиции — **только** для него.
     *
     * ## Почему не `AppCompatDelegate`
     *
     * `setApplicationLocales()` пересоздаёт активити: это его единственный
     * способ обновить ресурсы. Из композиции такой вызов превращается в цикл
     * (новая активити ещё не знает сохранённый язык и снова зовёт метод).
     * При этом пользователю не нужен перезапуск: экран настроек языка не
     * должен мигать и терять позицию.
     *
     * Поэтому локаль подкладывается контекстом: Compose пересобирает
     * поддерево по ключу [language] (там, где стоит `key(language)`), и все
     * `stringResource` внутри мгновенно читают строки нового языка.
     *
     * ## Чего здесь сознательно НЕ делается
     *
     *  — `Locale.setDefault(...)`. Это глобальное состояние процесса. Вызов
     *    во время композиции рассинхронизирует `Resources` с
     *    `Configuration` активити — именно это и валило приложение.
     *  — `applyLocale(...)`. Перезапуск активити — не то, что нужно при
     *    смене языка в настройках; персистентность обеспечивает DataStore.
     *
     * @param language выбранный язык; [AppLanguage.SYSTEM] означает «как в
     *   системе» и не переопределяет ничего.
     */
    @Composable
    private fun LocalizedContent(
        language: AppLanguage,
        content: @Composable () -> Unit,
    ) {
        val baseContext = LocalContext.current

        // Ключ — язык. Два важных следствия:
        //  1. контекст создаётся один раз на смену языка, а не на каждую
        //     рекомпозицию (создание Context стоит недёшево);
        //  2. смена языка заставляет Compose пересобрать поддерево — все
        //     `stringResource` перечитывают строки, и интерфейс меняется
        //     целиком и сразу.
        val localizedContext = remember(language) { localizedContextFor(baseContext, language) }

        CompositionLocalProvider(
            LocalContext provides localizedContext,
            content = content,
        )
    }

    /**
     * Собирает контекст с нужной локалью. Любая ошибка возвращает исходный
     * контекст: язык — настройка, а не условие работоспособности экрана.
     */
    private fun localizedContextFor(base: Context, language: AppLanguage): Context {
        if (language.isSystem) return base
        return runCatching {
            val locale = Locale.forLanguageTag(language.code)
            if (locale.language.isEmpty()) return@runCatching base
            val configuration = Configuration(base.resources.configuration)
            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)
            base.createConfigurationContext(configuration)
        }.getOrDefault(base)
    }

    /**
     * Применяет локаль к активити через [AppCompatDelegate].
     *
     * Вызывается **только один раз** — на старте, до [setContent]. Здесь это
     * безопасно: пересоздание активити нужно ровно затем, чтобы ресурсы
     * загрузились на сохранённом языке, и к этому моменту DataStore уже
     * прочитан, а композиция ещё не начата — цикла возникнуть не может.
     *
     * При смене языка пользователем метод не вызывается: мгновенную реакцию
     * даёт [LocalizedContent], а персистентность — запись в DataStore.
     *
     * Всё обёрнуто в [runCatching]: даже если AppCompat откажется менять
     * локаль (бывает на кастомных прошивках), приложение обязано запуститься.
     */
    private fun applyLocaleSafely(language: AppLanguage) {
        val localeList = if (language.isSystem) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.code)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    /**
     * Применяет язык немедленно, без пересоздания активити.
     *
     * Вызывается из экрана настроек, когда пользователь выбрал другой язык.
     * Делает две вещи:
     *  1. пишет выбор в DataStore — оттуда его прочитает следующая активити
     *     или следующий запуск (и `attachBaseContext` применит сразу);
     *  2. **пересоздаёт** активити явно и подконтрольно — через
     *     [recreate], а не через `AppCompatDelegate`, который в этом случае
     *     пошёл бы на конфликт с `configChanges`.
     *
     * Почему всё-таки пересоздаём, хотя есть «мягкий» путь: после смены
     * языка активити нужен корректный `Configuration` для системных
     * диалогов, разрешений и уведомлений — они читают контекст активити,
     * а не контекст композиции. `recreate()` вызывается один раз и только
     * по действию пользователя, поэтому цикла, который был раньше (когда
     * метод дёргался из `LaunchedEffect`), возникнуть не может.
     */
    fun applyLanguageChoice(language: AppLanguage) {
        if (language == appliedLanguage) return
        appliedLanguage = language
        // Единственное место, где допустимо тронуть AppCompatDelegate:
        // сразу после него активити пересоздастся сама и прочитает язык
        // в attachBaseContext. Без цикла: следующая активити уже знает
        // выбранный язык и повторно сюда не попадёт.
        runCatching { applyLocaleSafely(language) }
    }

    override fun onDestroy() {
        // Снимаем мост только если он всё ещё указывает на эту активити:
        // при смене языка активити уничтожается ПОСЛЕ создания новой, и
        // безусловная очистка стёрла бы уже установленный новый слушатель.
        if (SettingsViewModel.languageApplier === languageApplier) {
            SettingsViewModel.languageApplier = null
        }
        super.onDestroy()
    }
}
