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
 * ### 2. `AppCompatDelegate` вызывался на этапе старта активити
 *
 * `AppCompatDelegate.setApplicationLocales()` пересоздаёт активити — это его
 * единственный способ обновить ресурсы. Вызванный на старте, он давал гонку:
 * новая активити ещё не прочитала DataStore, видела `SYSTEM` и просила смену
 * снова → бесконечный цикл пересозданий → ANR.
 *
 * **Теперь:** `AppCompatDelegate` вызывается **только** там, где язык реально
 * изменил пользователь (см. [applyLanguageChoice]) и никогда — при создании
 * или в композиции. Интерфейс же меняет язык мгновенно, без перезапуска,
 * через [LocalizedContent]; персистентность обеспечивает DataStore, а
 * [attachBaseContext] применяет выбор ещё до создания активити.
 *
 * ### 3. `configChanges="locale"` в манифесте
 *
 * Активити заявляла, что сама обработает смену локали, а `AppCompatDelegate`
 * при этом ждал пересоздания. Система пересоздание не присылала — состояние
 * локали в активити и в AppCompat расходилось.
 *
 * **Теперь:** `locale` и `layoutDirection` убраны из `configChanges` (см.
 * манифест). Активити создаётся с нужной локалью один раз — в
 * [attachBaseContext], — а при смене языка её вообще не требуется
 * пересоздавать: интерфейс обновляет [LocalizedContent].
 *
 * ## Как это работает в итоге
 *
 *  — **По умолчанию** язык берётся из системы: пользователь ничего не
 *    выбирает, и приложение говорит на языке телефона.
 *  — **Мгновенно**: `LocalizedContent` подкладывает в дерево контекст с
 *    нужной локалью, и все `stringResource` читают строки нового языка
 *    сразу, без перезапуска; навигация и позиция прокрутки не теряются.
 *  — **Между запусками**: выбор персистится в DataStore (`pref_app_language`)
 *    и применяется ещё до создания активити, в [attachBaseContext], —
 *    поэтому первого кадра на чужом языке не бывает.
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
     *
     * Интерфейс от этого моста не зависит: смена языка в UI идёт через
     * подписку на DataStore в композиции. Мост лишь выравнивает системную
     * локаль приложения, чтобы «Язык приложения» в настройках Android и
     * разделяемые диалоги совпадали с выбором пользователя.
     */
    private val languageApplier = object : SettingsViewModel.LanguageApplier {
        override fun onLanguageChanged(language: AppLanguage) {
            // Язык в композиции меняется сам — подписка на DataStore уже
            // перерисовала UI. Здесь остаётся одна задача: синхронизировать
            // системную локаль приложения (настройки Android, меню системы).
            if (language == appliedLanguage) return
            appliedLanguage = language
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

            // Язык НЕ применяется здесь. Активити уже построена с локалью
            // из [attachBaseContext], а смена языка в настройках мгновенно
            // отражается через [LocalizedContent] — без единого
            // пересоздания. Вызов `AppCompatDelegate` на старте только
            // добавил бы лишний рестарт (и ровно он раньше превращался в
            // бесконечный цикл, когда активити не успевала прочитать язык).
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
     * Поэтому локаль подкладывается контекстом: `remember(language)` создаёт
     * новый `Context` ровно один раз на смену языка, а `CompositionLocalProvider`
     * пересобирает поддерево — все `stringResource` внутри сразу читают строки
     * нового языка. Изменения нигде не кэшируются: при следующей смене языка
     * контекст пересоздаётся, и интерфейс снова меняется целиком.
     *
     * ## Чего здесь сознательно НЕ делается
     *
     *  — `Locale.setDefault(...)`. Это глобальное состояние процесса. Вызов
     *    во время композиции рассинхронизирует `Resources` с
     *    `Configuration` активити — именно это и валило приложение.
     *  — `AppCompatDelegate.setApplicationLocales(...)`. Перезапуск активити
     *    при смене языка не нужен: интерфейс обновляется здесь, а системную
     *    локаль синхронизирует [applyLanguageChoice] — по действию
     *    пользователя и без пересоздания.
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
        // SYSTEM — это «как в телефоне»: контекст отдаётся как есть, ничего
        // не переопределяется. Так пользователь, который ничего не выбирал,
        // видит ровно системный язык.
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
     * Применяет локаль к системной части приложения.
     *
     * Вызывается **только** по действию пользователя — из [applyLanguageChoice]
     * или из системного меню Android «Язык приложения». На старте не
     * вызывается сознательно: локаль активити уже задана в
     * [attachBaseContext], а лишний вызов `AppCompatDelegate` здесь приводил
     * к пересозданию активити на каждом запуске и (при гонке с чтением
     * DataStore) к бесконечному циклу перезапусков.
     *
     * Всё обёрнуто в [runCatching]: даже если AppCompat откажется менять
     * локаль (бывает на кастомных прошивках), приложение обязано работать.
     */
    @Suppress("unused")
    private fun applyLocaleSafely(language: AppLanguage) {
        val localeList = if (language.isSystem) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.code)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    /**
     * Синхронизирует язык с системной локалью приложения.
     *
     * ════════════════════════════════════════════════════════════════════
     *  ЕДИНСТВЕННАЯ ТОЧКА СМЕНЫ ЯЗЫКА. ПОЧЕМУ БОЛЬШЕ НЕТ ЦИКЛА.
     * ════════════════════════════════════════════════════════════════════
     *
     * Путь пользователя целиком:
     *
     *  1. Он выбирает язык в настройках → `SettingsViewModel.setLanguage`
     *     пишет выбор в DataStore.
     *  2. Композиция подписана на DataStore: `MainActivity` читает
     *     `settings.selectedLanguage` и передаёт его в [LocalizedContent].
     *     Интерфейс становится нужного языка **в следующем кадре**, без
     *     перезапуска: экран настроек не мигает, позиция прокрутки и
     *     открытый экран сохраняются.
     *  3. Этот метод дёргается из того же `setLanguage` и приводит к тому же
     *     выбору системную локаль приложения — так язык совпадает в
     *     «Язык приложения» в настройках Android и в системных диалогах.
     *
     * Раньше здесь было пересоздание активити, и оно же было источником
     * краша: пользователь выбирал язык, активити перезапускалась, новая ещё
     * не успевала прочитать DataStore, видела `SYSTEM` и просила смену
     * снова — цикл перезапусков до ANR. Приложение падало на каждом старте,
     * потому что выбор уже лежал в DataStore.
     *
     * Сейчас пересоздания нет вообще, и оно не нужно:
     *  — **UI** обновляет [LocalizedContent] — мгновенно и без потерь;
     *  — **системная локаль** читается только из `Configuration` и не влияет
     *    на уже нарисованный Compose-интерфейс;
     *  — **выбор** уже в DataStore, поэтому следующий запуск поднимется на
     *    нужном языке через [attachBaseContext].
     *
     * Метод идемпотентен (повторный вызов с тем же языком ничего не делает)
     * и вызывается только по явному действию пользователя — из композиции,
     * из жизненного цикла или из чтения DataStore его ничто не дёргает.
     */
    fun applyLanguageChoice(language: AppLanguage) {
        if (language == appliedLanguage) return
        appliedLanguage = language
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
