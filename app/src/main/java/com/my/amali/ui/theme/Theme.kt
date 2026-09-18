package com.my.amali.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import java.util.Calendar

// ════════════════════════════════════════════════════════════
//  ENUM: визуальный стиль
// ════════════════════════════════════════════════════════════

enum class AmaliaVisualTheme(val displayName: String) {
    LIQUID_GLASS("Liquid Glass"),
    BIOPHILIC("Биофильная релаксация"),
}

enum class DarkModePreference {
    SYSTEM,
    ALWAYS_DARK,
    ALWAYS_LIGHT,
}

/**
 * Фазы суток для **ручного** выбора, когда адаптация по времени выключена.
 *
 * Это не «основной» механизм: основная адаптация идёт через [CircadianEngine]
 * с непрерывной CCT. Здесь — грубые интервалы, нужные только чтобы пользователь
 * мог зафиксировать свет, если не хочет автоматику.
 */
enum class BioTimeOfDay(val hourRange: IntRange) {
    MORNING(6..11),
    DAY(12..17),
    EVENING(18..22),
    NIGHT(23..5);

    companion object {
        fun fromHour(hour: Int): BioTimeOfDay =
            entries.firstOrNull { hour in it.hourRange } ?: NIGHT
    }
}

// ════════════════════════════════════════════════════════════
//  COLOR SCHEMES
// ════════════════════════════════════════════════════════════

/**
 * Все схемы строятся из одного источника — палитры фона и параметров стекла,
 * а не задаются заранее «на память».
 *
 * Почему так: раньше схема была фиксированной константой, а адаптация
 * подмешивала в неё цвет постфактум ([old tintScheme]). Из-за этого тёмная
 * тема всегда выглядела наполовину перекрашенной: фон уезжал в вечер,
 * а акценты, тени и границы оставались на прежнем времени суток.
 * Здесь схема **выводится** из палитры, поэтому разъехаться не может.
 */
private fun buildScheme(
    palette: GradientPalette,
    isDark: Boolean,
    glass: GlassStyle,
): ColorScheme {
    val base = palette.stops.firstOrNull()?.color ?: if (isDark) GlassBgBase else BioBgBase
    val surface = palette.stops.getOrNull(palette.stops.size / 2)?.color
        ?: if (isDark) GlassBgSurface else BioBgSurface
    val deep = palette.stops.lastOrNull()?.color ?: base

    // Акцент: ведущий тон ауроры палитры, приведённый к читаемому контрасту
    // на текущем фоне. Это и есть «один источник правды» для акцентов.
    val tones = palette.auroras.ifEmpty { palette.stops.map { it.color } }
    val warm = tones.getOrElse(0) { if (isDark) GlassAccentDim else BioAccentPrimary }
    val cool = tones.getOrElse(1 % tones.size) { warm }
    val spark = tones.getOrElse(2 % tones.size) { cool }

    val primary = CircadianEngine.ensureContrast(
        color = if (isDark) warm else blendTowards(warm, Color.Black, 0.18f),
        background = surface,
        minRatio = CircadianEngine.MIN_ACCENT_CONTRAST,
    )
    val secondary = CircadianEngine.ensureContrast(
        color = cool,
        background = surface,
        minRatio = CircadianEngine.MIN_ACCENT_CONTRAST,
    )
    val tertiary = CircadianEngine.ensureContrast(
        color = spark,
        background = surface,
        minRatio = CircadianEngine.MIN_ACCENT_CONTRAST * 0.9f,
    )

    // Текст: сначала берём базовый тон темы, потом ограничиваем светлоту
    // (off-white в тёмной теме) и добиваем контраст до целевого значения.
    val textSeed = when {
        !isDark -> BioTextPrimary
        palette.cct <= 2900 -> BioDarkTextPrimary
        else -> GlassTextPrimary
    }
    val bodyText = CircadianEngine.ensureContrast(
        color = CircadianEngine.softenText(textSeed, isDark),
        background = surface,
        minRatio = CircadianEngine.TARGET_BODY_CONTRAST,
    )
    val secondaryText = CircadianEngine.ensureContrast(
        color = if (isDark) GlassTextSecondary else BioTextSecondary,
        background = surface,
        minRatio = CircadianEngine.MIN_SECONDARY_CONTRAST,
    )
    val faintText = CircadianEngine.ensureContrast(
        color = if (isDark) GlassTextFaint else BioTextFaint,
        background = surface,
        minRatio = CircadianEngine.MIN_ACCENT_CONTRAST,
    )

    // Фон никогда не чистый чёрный — это защита от halation.
    val background = CircadianEngine.liftBackground(deep, isDark)
    val surfaceLifted = CircadianEngine.liftBackground(surface, isDark)

    val outline = if (isDark) GlassBgStroke else BioBgStroke
    val error = if (isDark) GlassStateError else BioStateError
    val onText = if (isDark) deep else Color.White

    return if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onText,
            primaryContainer = lerp(primary, surfaceLifted, 0.74f),
            onPrimaryContainer = bodyText,
            inversePrimary = secondary,
            secondary = secondary,
            onSecondary = onText,
            secondaryContainer = lerp(secondary, surfaceLifted, 0.80f),
            onSecondaryContainer = bodyText,
            tertiary = tertiary,
            onTertiary = onText,
            tertiaryContainer = lerp(tertiary, surfaceLifted, 0.80f),
            onTertiaryContainer = bodyText,
            background = background,
            onBackground = bodyText,
            surface = surfaceLifted,
            onSurface = bodyText,
            surfaceVariant = lerp(surfaceLifted, Color.White, 0.05f),
            onSurfaceVariant = secondaryText,
            surfaceTint = primary,
            surfaceContainerHighest = lerp(surfaceLifted, Color.White, 0.06f),
            surfaceContainerHigh = lerp(surfaceLifted, Color.White, 0.03f),
            surfaceContainer = surfaceLifted,
            surfaceContainerLow = background,
            surfaceContainerLowest = background,
            outline = outline,
            outlineVariant = outline.copy(alpha = 0.55f),
            scrim = Color(0x00000000),
            error = error,
            onError = onText,
            errorContainer = error.copy(alpha = 0.16f),
            onErrorContainer = error,
            inverseSurface = bodyText,
            inverseOnSurface = background,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.16f),
            onPrimaryContainer = bodyText,
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = lerp(secondary, surfaceLifted, 0.82f),
            onSecondaryContainer = bodyText,
            tertiary = tertiary,
            onTertiary = Color.White,
            tertiaryContainer = lerp(tertiary, surfaceLifted, 0.84f),
            onTertiaryContainer = bodyText,
            background = background,
            onBackground = bodyText,
            surface = surfaceLifted,
            onSurface = bodyText,
            surfaceVariant = lerp(surfaceLifted, Color.Black, 0.04f),
            onSurfaceVariant = secondaryText,
            surfaceTint = primary,
            surfaceContainerHighest = lerp(surfaceLifted, Color.Black, 0.05f),
            surfaceContainerHigh = lerp(surfaceLifted, Color.Black, 0.025f),
            surfaceContainer = surfaceLifted,
            surfaceContainerLow = background,
            surfaceContainerLowest = background,
            outline = outline,
            outlineVariant = outline.copy(alpha = 0.55f),
            scrim = Color(0x00000000),
            error = error,
            onError = Color.White,
            errorContainer = error.copy(alpha = 0.14f),
            onErrorContainer = error,
            inverseSurface = bodyText,
            inverseOnSurface = background,
        )
    }
}

/**
 * Блендинг с сохранением «плотности» цвета — нужен, чтобы светлая схема
 * получала достаточно насыщенный акцент (простое [lerp] к чёрному
 * высветляет и вымывает тон вместо затемнения).
 */
private fun blendTowards(color: Color, target: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = color.red * (1f - t) + target.red * t,
        green = color.green * (1f - t) + target.green * t,
        blue = color.blue * (1f - t) + target.blue * t,
        alpha = color.alpha,
    )
}

// ════════════════════════════════════════════════════════════
//  THEME COMPOSABLE
// ════════════════════════════════════════════════════════════

/**
 * Главная тема приложения.
 *
 * ## Что здесь изменилось по сравнению со «сломанной» версией
 *
 * 1. **Время стало дробным и непрерывным.** Раньше тема опрашивала
 *    `Calendar.HOUR_OF_DAY` с точностью в час и переключала палитру `when`-ом
 *    по четырём интервалам: в 11:59 был один экран, в 12:00 — другой, рывком.
 *    Теперь считается [CircadianEngine.profileAt] с дробным часом, а палитра
 *    интерполируется между соседними по CCT — свет едет плавно, вспышек нет.
 *
 * 2. **Один источник правды.** [currentGradientPalette] и `MaterialTheme` —
 *    это одна и та же палитра. Раньше фон считался отдельно от схемы, и на
 *    стыке возникало расхождение: тёмная амура + вечный индиго-акцент.
 *
 * 3. **Тени и стекло следуют за светом.** [GlassStyle] и [LocalShadow] теперь
 *    выводятся из CCT: тёплый вечер получает тёплое стекло и тёплую тень,
 *    холодное утро — холодные. Иначе последний слой ломает всю адаптацию.
 *
 * 4. **Контраст гарантирован, а не «на глаз».** Каждый текстовый и акцентный
 *    цвет проходит через [CircadianEngine.ensureContrast] с целевым 7:1 для
 *    тела текста: это компенсация halation, из-за которой белый текст на
 *    тёмном фоне «расплывается» даже при формально проходящем AA 4.5:1.
 *
 * @param darkModePref выбор пользователя: системная / всегда тёмная / светлая.
 * @param visualTheme визуальный стиль приложения.
 * @param useBioTime включена ли адаптация по времени суток и свету.
 * @param userHourOverride ручное время для превью и настроек (null — текущее).
 */
@Composable
fun AmaliaTheme(
    darkModePref: DarkModePreference = DarkModePreference.SYSTEM,
    visualTheme: AmaliaVisualTheme = AmaliaVisualTheme.LIQUID_GLASS,
    useBioTime: Boolean = false,
    userHourOverride: Float? = null,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val hour = userHourOverride ?: currentTimeHour(useBioTime)
    val profile = CircadianEngine.profileAt(hour)

    val isDark = when (darkModePref) {
        DarkModePreference.SYSTEM -> systemDark
        DarkModePreference.ALWAYS_DARK -> true
        DarkModePreference.ALWAYS_LIGHT -> false
    }

    // Liquid Glass — тёмный по определению: стекло и свечение работают
    // только на глубоком фоне. Биофильная тема честно слушает систему.
    val effectiveDark = when (visualTheme) {
        AmaliaVisualTheme.LIQUID_GLASS -> true
        AmaliaVisualTheme.BIOPHILIC -> isDark
    }

    val palette = currentGradientPalette(
        visualTheme = visualTheme,
        darkModePref = darkModePref,
        useBioTime = useBioTime,
        hour = hour,
    )

    val glassStyle = glassStyleFor(palette, effectiveDark)
    val scheme = buildScheme(palette, effectiveDark, glassStyle)

    val typography = when (visualTheme) {
        AmaliaVisualTheme.LIQUID_GLASS -> GlassTypography
        AmaliaVisualTheme.BIOPHILIC -> BioTypography
    }

    val shapes = when (visualTheme) {
        AmaliaVisualTheme.LIQUID_GLASS -> GlassShapes
        AmaliaVisualTheme.BIOPHILIC -> BioShapes
    }

    // Тень — часть системы света: у тёплого вечера она тёплая, у холодного
    // утра холодная. Это последний слой, который обычно забывают, и тогда
    // адаптация «не читается»: фон поменялся, а глубина осталась чужой.
    val shadow = AmaliaShadow(
        color = palette.shadow,
        warm = palette.isWarm,
        ambient = CircadianEngine.shadowFor(
            base = scheme.surface,
            warmTone = palette.auroras.firstOrNull() ?: scheme.primary,
            darkUi = palette.isDark,
        ),
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Контекст может быть не активити (ContextThemeWrapper у части
            // OEM-оболочек, превью-хосты, инструментальные обёртки). Прямой
            // cast здесь давал ClassCastException и убивал процесс целиком —
            // а без статус-бар-контроллера приложение прекрасно живёт.
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !effectiveDark
                isAppearanceLightNavigationBars = !effectiveDark
            }
        }
    }

    CompositionLocalProvider(
        LocalGlassStyle provides glassStyle,
        LocalAmaliaPalette provides palette,
        LocalAmaliaShadow provides shadow,
        LocalLightProfile provides profile,
    ) {
        // Тема вызывается по имени пакета, а не через импорт.
        //
        // Импорт `MaterialTheme` (объект-компаньон со вложенной composable
        // `MaterialTheme(...)`) в одном файле с собственными компонентами
        // регулярно разрешается в **свой** член вместо нужной функции — и
        // сборка падает на «No value passed for parameter 'content'» в
        // совершенно невинном месте. Полное имя убирает эту двусмысленность
        // раз и навсегда.
        androidx.compose.material3.MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = shapes,
        ) {
            content()
        }
    }
}

/**
 * Стиль стекла под текущий свет.
 *
 * В тёмной теме стекло «дымчатое» и контурное: фон должен просвечивать,
 * иначе пропадает смысл ауроры. В светлой — «молочное»: там важнее
 * контур и мягкость, а не прозрачность.
 *
 * `fill` слегка растёт к ночи: чем темнее свет, тем плотнее стекло, чтобы
 * текст на нём оставался читаемым.
 */
private fun glassStyleFor(palette: GradientPalette, dark: Boolean): GlassStyle {
    val nightBoost = ((2800 - palette.cct).coerceAtLeast(0) / 2800f) * 0.10f
    return if (dark) {
        GlassStyle(
            fill = (0.58f + nightBoost).coerceAtMost(0.74f),
            border = 0.12f,
            highlight = 0.09f,
            glow = 0.15f,
            light = false,
        )
    } else {
        GlassStyle(
            fill = 0.72f,
            border = 0.42f,
            highlight = 0.42f,
            glow = 0.10f,
            light = true,
        )
    }
}

/**
 * Час (дробный), который использует тема.
 *
 * Опрашивается раз в 30 секунд и обновляется, только если значение реально
 * изменилось больше чем на [HOUR_EPSILON]. Это важно: CCT считается из
 * дробного часа, и без порога каждая итерация таймера пересобирала бы всю
 * композицию экрана ради изменения на тысячную долю кельвина.
 *
 * Когда адаптация выключена, таймер не заводится вовсе — тема берёт время
 * один раз и больше не тратит кадры.
 */
@Composable
private fun currentTimeHour(enable: Boolean): Float {
    val tracked by produceState(initialValue = hourNow(), key1 = enable) {
        if (!enable) return@produceState
        while (true) {
            delay(TIME_POLL_MS)
            val next = hourNow()
            if (kotlin.math.abs(next - value) >= HOUR_EPSILON) value = next
        }
    }
    return if (enable) tracked else hourNow()
}

private fun hourNow(): Float = CircadianEngine.nowFractionalHour()

private const val TIME_POLL_MS = 30_000L
private const val HOUR_EPSILON = 0.05f

// ════════════════════════════════════════════════════════════
//  ПАЛИТРА
// ════════════════════════════════════════════════════════════

/**
 * Палитра живого фона под текущие настройки.
 *
 * При включённой адаптации CCT берётся у [CircadianEngine] и по ней
 * интерполируется палитра — непрерывно между соседями по лестнице.
 * При выключенной — фиксированные палитры по режиму тёмности, чтобы
 * пользователь, отключивший автоматику, получил предсказуемый вид.
 */
fun currentGradientPalette(
    visualTheme: AmaliaVisualTheme,
    darkModePref: DarkModePreference = DarkModePreference.SYSTEM,
    useBioTime: Boolean = false,
    hour: Float = CircadianEngine.nowFractionalHour(),
): GradientPalette {
    val profile = CircadianEngine.profileAt(hour)

    if (visualTheme == AmaliaVisualTheme.LIQUID_GLASS) {
        // Стекло всегда тёмное; меняется только свет.
        return if (useBioTime) {
            paletteForCct(glassPaletteLadder, profile.cct)
        } else {
            GlassGradientPalette
        }
    }

    // Биофильная тема: светлые палитры днём, тёмные вечером и ночью.
    val isDark = when (darkModePref) {
        DarkModePreference.ALWAYS_DARK -> true
        DarkModePreference.ALWAYS_LIGHT -> false
        DarkModePreference.SYSTEM -> profile.effectiveDark
    }
    return if (useBioTime) {
        paletteForCct(bioPaletteLadder, profile.cct)
    } else {
        if (isDark) BioGradientNight else BioGradientDay
    }
}

/** Палитра, под которую сейчас нарисованы фон и акценты. */
val LocalAmaliaPalette = staticCompositionLocalOf { GlassGradientPalette }

/** Световой профиль момента — доступен любому экрану без проброса параметров. */
val LocalLightProfile = staticCompositionLocalOf { CircadianEngine.profileAt(12f) }

/**
 * Тень текущей палитры.
 *
 * @property color основной цвет тени (уже с альфой).
 * @property ambient мягкая «рассеянная» тень для парящих поверхностей.
 * @property warm тёплая ли тень — влияет на направление тонировки бликов.
 */
data class AmaliaShadow(
    val color: Color,
    val ambient: Color,
    val warm: Boolean,
)

val LocalAmaliaShadow = staticCompositionLocalOf {
    AmaliaShadow(color = Color(0x8A05060A), ambient = Color(0x4A05060A), warm = false)
}

/** Актуальная палитра — для иконок, аватаров и превью. */
val currentPalette: GradientPalette
    @Composable @ReadOnlyComposable
    get() = LocalAmaliaPalette.current

/** Актуальный световой профиль — для подписей «какой сейчас свет». */
val currentLight: LightProfile
    @Composable @ReadOnlyComposable
    get() = LocalLightProfile.current

/**
 * Цвет подсветки под иконкой/аватаром: следует за палитрой, а не за
 * «вечным» primary, поэтому стеклянные кнопки не выглядят приклеенными
 * к фону другого времени суток.
 *
 * Обязательно проходит контраст-гард: иконка на стекле — это мелкая графика,
 * и она обязана читаться даже на самом светлом участке ауроры.
 */
@Composable
@ReadOnlyComposable
fun iconAccent(): Color {
    val palette = LocalAmaliaPalette.current
    val tone = palette.motifAccent.takeIf { it != Color.Unspecified }
        ?: palette.auroras.firstOrNull()
        ?: MaterialTheme.colorScheme.primary
    val mixed = lerp(tone, MaterialTheme.colorScheme.primary, if (palette.isDark) 0.35f else 0.5f)
    return CircadianEngine.ensureContrast(
        color = mixed,
        background = MaterialTheme.colorScheme.surface,
        minRatio = CircadianEngine.MIN_ACCENT_CONTRAST,
    )
}

/**
 * Отладочная проверка: минимальный контраст текста на поверхности.
 * Используется превью и тестами, чтобы «глазная» часть не деградировала
 * при будущих правках палитр.
 */
@Composable
@ReadOnlyComposable
fun bodyTextContrast(): Float = CircadianEngine.contrastRatio(
    MaterialTheme.colorScheme.onSurface,
    MaterialTheme.colorScheme.surface,
)

/** Проверка, что фон действительно не чистый чёрный (halation-гард). */
fun isBackgroundSafe(color: Color): Boolean = color.luminance() >= 0.002f
