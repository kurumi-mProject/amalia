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
 * Язык не должен ни ронять приложение, ни требовать ручных действий от
 * пользователя: выбрал в настройках — запомнилось, работает до перезапуска
 * и после него. Ниже — почему сделано именно так и какие способы были
 * отброшены после того, как они сломали сборку.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ОТБРОШЕННЫЙ СПОСОБ: мгновенная подмена контекста в композиции
 * ════════════════════════════════════════════════════════════════════════
 *
 * Идея была красивая: обернуть интерфейс в
 * `CompositionLocalProvider(LocalContext provides localizedContext)`, и язык
 * менялся бы в следующем кадре — без перезапуска, без мигания, с
 * сохранением позиции прокрутки.
 *
 * На практике это валит приложение на старте. Подмена `LocalContext`
 * подменяет и **все локалы, вычисляемые из него**, а среди них:
 *
 *  — `LocalActivityResultRegistryOwner` — владелец реестра результатов;
 *  — `LocalLifecycleOwner` — владелец жизненного цикла;
 *  — `LocalSavedStateRegistryOwner` — владелец сохранённого состояния.
 *
 * Первый же экран с системным запросом падал:
 *
 *   java.lang.IllegalStateException: No ActivityResultRegistryOwner was
 *   provided via LocalActivityResultRegistryOwner
 *     at rememberLauncherForActivityResult
 *     at AssistantScreen
 *
 * Ассистент запрашивает микрофон через `rememberLauncherForActivityResult`,
 * и этот запрос оставался без владельца реестра. Пять падений подряд в
 * одном логе, все одинаковые — приложение не открывалось вообще.
 *
 * Вывод: контекст — это не «просто локаль строк». Это корень цепочки, на
 * которой держатся результаты активити, жизненный цикл и сохранение
 * состояния. Подменять его в дереве композиции нельзя.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  КАК СДЕЛАНО СЕЙЧАС
 * ════════════════════════════════════════════════════════════════════════
 *
 * Локаль задаётся контексту активити в [attachBaseContext] — до того, как
 * система построит активити и выставит все производные локалы. Ничего
 * подменять в дереве не нужно: `stringResource` читают строки из контекста,
 * который уже локализован, а владельцы реестров и жизненного цикла
 * расставляются системой как обычно.
 *
 * Смена языка — это смена конфигурации, поэтому активити пересоздаётся
 * ([applyLanguageChoice]). Порядок такой, что цикл перезапусков невозможен:
 *
 *  1. [attachBaseContext] синхронно читает язык из DataStore и строит
 *     активити сразу на нужной локали, запоминая её в [appliedLanguage];
 *  2. при пересоздании читается **то же самое** значение, поэтому
 *     [onLanguageChanged] выходит на первой строке и второй раз
 *     пересоздание не запрашивает.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ЧТО БЫЛО ИСПРАВЛЕНО ПО ХОДУ
 * ════════════════════════════════════════════════════════════════════════
 *
 *  1. **`Locale.setDefault()` внутри композиции.** Глобальная локаль
 *     процесса менялась во время отрисовки кадра, и `Locale.getDefault()`
 *     расходился с `Configuration` активити. На таком расхождении ресурсы
 *     Android бросают исключение. Глобальная локаль не трогается вообще.
 *
 *  2. **`AppCompatDelegate` на старте.** Вызванный в `onCreate`, он просил
 *     пересоздание раньше, чем активити успевала прочитать DataStore:
 *     видела там `SYSTEM`, просила смену заново — цикл до ANR. Теперь
 *     вызывается только по действию пользователя.
 *
 *  3. **`configChanges="locale"` в манифесте.** Активити заявляла, что
 *     обработает смену локали сама, а `AppCompat` ждал пересоздания;
 *     система не присылала ни того, ни другого, и состояние локали
 *     расходилось. Флаг убран — теперь пересоздание приходит нормально.
 *
 * ## Итог
 *
 *  — **По умолчанию** — язык системы: пользователь ничего не выбирает.
 *  — **Выбор** хранится в DataStore (`pref_app_language`) и переживает
 *    перезапуск.
 *  — **Смена** применяется за одно пересоздание активити, без цикла.
 *  — **Первого кадра на чужом языке не бывает**: локаль готова до создания
 *    активити.
 *  — **Системное меню Android 13+** видит выбор благодаря
 *    `AppLocalesMetadataHolderService` с `autoStoreLocales`.
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
            // Язык применяется контекстом активити, а его нельзя поменять
            // на живом окне: смена локали — это смена конфигурации, и
            // Android для неё пересоздаёт активити.
            //
            // Раньше здесь стояла мгновенная подмена через
            // `CompositionLocalProvider(LocalContext provides ...)`, и это
            // валило приложение: подмена LocalContext заодно подменяет
            // производные локалы, среди которых
            // `LocalActivityResultRegistryOwner`. Первый же экран с
            // `rememberLauncherForActivityResult` (запрос микрофона в
            // ассистенте) падал с «No ActivityResultRegistryOwner was
            // provided». Пересоздание лишено этого класса ошибок целиком:
            // контекст формируется в attachBaseContext до создания активити,
            // и все локалы выставляются самой системой.
            if (language == appliedLanguage) return
            appliedLanguage = language
            applyLocaleToSystem(language)
            recreate()
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

            // Язык НЕ применяется здесь: активити уже построена с локалью
            // из attachBaseContext. Вызов AppCompatDelegate на старте
            // добавил бы лишний рестарт — и ровно он раньше превращался в
            // бесконечный цикл, когда активити не успевала прочитать язык.
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
     * Выравнивает системную локаль приложения под выбор пользователя.
     *
     * ════════════════════════════════════════════════════════════════════════
     *  ЗАЧЕМ ЭТО, ЕСЛИ АКТИВИТИ И ТАК ПЕРЕСОЗДАЁТСЯ
     * ════════════════════════════════════════════════════════════════════════
     *
     * Локаль контекста активити задаётся в [attachBaseContext] и покрывает
     * весь интерфейс приложения. Но есть места за его пределами, куда
     * контекст активити не достаёт:
     *
     *  — системные диалоги и меню, которые Android строит сам
     *    («Открыть в другом приложении», диалоги разрешений);
     *  — строка «Язык приложения» в системных настройках Android 13+;
     *  — уведомления и foreground-сервисы, работающие вне активити.
     *
     * Их язык берётся из локали приложения, а не из контекста активити,
     * поэтому её нужно синхронизировать отдельно. Делается это ровно по
     * действию пользователя — из [onLanguageChanged], а не при каждом
     * создании активити: вызов `AppCompatDelegate.setApplicationLocales` в
     * onCreate раньше приводил к бесконечному пересозданию, потому что
     * новая активити не успевала прочитать сохранённый язык и просила
     * смену заново.
     */
    private fun applyLocaleToSystem(language: AppLanguage) {
        runCatching {
            // SYSTEM означает «как в телефоне»: пустой список снимает
            // приложение-специфичную локаль и возвращает системную.
            val localeList = if (language.isSystem) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language.code)
            }
            AppCompatDelegate.setApplicationLocales(localeList)
        }
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
     * Применяет выбранный язык: системная локаль + пересоздание активити.
     *
     * ════════════════════════════════════════════════════════════════════
     *  ПОЧЕМУ ПЕРЕСОЗДАНИЕ, А НЕ МГНОВЕННАЯ ПОДМЕНА КОНТЕКСТА
     * ════════════════════════════════════════════════════════════════════
     *
     * Соблазн был большой: подменить контекст прямо в композиции через
     * `CompositionLocalProvider(LocalContext provides localized)`, чтобы
     * язык менялся в следующем кадре без единого перезапуска. Так и было
     * сделано — и это валило приложение на старте:
     *
     *   No ActivityResultRegistryOwner was provided via
     *   LocalActivityResultRegistryOwner
     *   at rememberLauncherForActivityResult
     *   at AssistantScreen
     *
     * Подмена `LocalContext` подменяет и все локалы, вычисляемые из него:
     * `LocalActivityResultRegistryOwner`, `LocalLifecycleOwner`,
     * `LocalSavedStateRegistryOwner`. Первый же экран с системным запросом
     * (ассистент просит микрофон) оставался без владельца реестра и падал.
     *
     * Пересоздание этой проблемы не имеет вовсе: контекст формируется в
     * [attachBaseContext] до создания активити, и все производные локалы
     * выставляет сама система.
     *
     * ## Почему цикл перезапусков не вернётся
     *
     * Раньше бесконечный цикл возникал из-за гонки: новая активити
     * пересоздавалась быстрее, чем успевала прочитать DataStore, видела там
     * `SYSTEM` и просила смену заново. Сейчас порядок другой:
     *
     *  1. `attachBaseContext` синхронно читает язык из DataStore и сразу
     *     строит активити на нужной локали, запоминая его в [appliedLanguage];
     *  2. при пересоздании этот же метод читает уже **новое** значение,
     *     поэтому [onLanguageChanged] видит `language == appliedLanguage`
     *     и выходит на первой строке — пересоздавать больше нечего.
     *
     * То есть язык применяется ровно один раз на действие пользователя.
     *
     * @param language выбор из настроек приложения.
     */
    fun applyLanguageChoice(language: AppLanguage) {
        if (language == appliedLanguage) return
        appliedLanguage = language
        applyLocaleToSystem(language)
        recreate()
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
