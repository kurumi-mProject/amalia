package com.my.amali.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Декоративный мотив фона.
 *
 * ## Зачем это нужно
 *
 * Смена палитры по времени суток сам по себе выглядит как глюк: цвет
 * «просто другой», и непонятно почему. Мотив даёт подсказку-ассоциацию:
 * розовый день → лепестки сакуры, вечер → светлячки, ночь → звёзды/снег.
 * Экран перестаёт быть «перекрашенным» и становится живым временем суток.
 *
 * [AUTO] берёт мотив у текущей палитры. [OFF] — полностью чистый фон: никаких
 * частиц, ровно то, что получит пользователь, которому декорации не понравились.
 */
enum class AmaliaMotif(val displayName: String) {
    OFF("Без декораций"),
    AUTO("По времени суток"),
    SAKURA("Сакура"),
    MAPLE("Клены"),
    FIREFLY("Светлячки"),
    SNOW("Снег"),
    STARS("Звёзды");

    /** Нужно ли рисовать частицы вообще. */
    val hasParticles: Boolean get() = this != OFF


    companion object {
        /** Варианты для пользователя: [AUTO] плюс все ручные мотивы. */
        val choices: List<AmaliaMotif> = listOf(AUTO, SAKURA, MAPLE, FIREFLY, SNOW, STARS)

        fun fromName(stored: String?): AmaliaMotif =
            entries.firstOrNull { it.name == stored } ?: AUTO
    }
}

/**
 * Что именно рисовать для мотива и как себя ведут частицы.
 *
 * Все параметры — «физика» анимации, а не цвета: цвета приходят из палитры,
 * поэтому один и тот же мотив одинаково работает в тёмной и светлой темах.
 *
 * @param falling падает ли частица вниз (лепесток, лист, снег) или висит
 *   и пульсирует (звезда, светлячок).
 * @param spin скорость собственного вращения, оборотов за цикл.
 * @param sway амплитуда бокового дрейфа, в долях ширины экрана.
 * @param count базовое число частиц при полной интенсивности.
 * @param size диапазон размера частицы, dp-ish (пересчитывается от плотности).
 * @param pulse для «висячих» мотивов: сила мерцания.
 * @param drift для светлячков: насколько далеко они гуляют по экрану.
 */
data class MotifBehavior(
    val falling: Boolean,
    val spin: Float,
    val sway: Float,
    val count: Int,
    val minSize: Float,
    val maxSize: Float,
    val pulse: Float = 0f,
    val drift: Float = 0f,
    /**
     * Множитель скорости падения.
     *
     * Раньше вертикальная скорость задавалась только случайным разбросом
     * внутри [MotifLayer] и была одинаковой для всех мотивов — снег и
     * лепестки падали с одной средней скоростью, и разницы между ними не
     * ощущалось. Теперь у каждого мотива свой характер: лист тяжелее
     * лепестка почти вдвое, и это видно сразу.
     */
    val fallSpeedScale: Float = 1f,
)

/**
 * Поведение по мотиву.
 *
 * ## Почему физика у каждого мотива своя, а не «одни и те же настройки»
 *
 * Лепесток, лист, снежинка и светлячок ведут себя в реальности по-разному,
 * и именно разница в движении делает мотив узнаваемым — форма на 8 dp
 * читается плохо, а **скорость и характер падения** считываются мгновенно:
 *
 *  — **сакура** падает медленно и сильно качается: лепесток лёгкий и
 *    цепляется за воздух;
 *  — **лист** тяжелее: падает быстрее, кувыркается активнее, качается
 *    меньше, зато разворачивается вокруг своей оси;
 *  — **снег** идёт плотно и ровно, почти без вращения, но с мелким дрожанием;
 *  — **звёзды** висят и мерцают (не падают вовсе) — это единственный
 *    «статичный» мотив, и он специально не отвлекает;
 *  — **светлячки** блуждают: у них есть [MotifBehavior.drift], то есть
 *    горизонтальное гуляние, которого нет ни у кого больше.
 *
 * [AmaliaMotif.OFF] и [AmaliaMotif.AUTO] возвращают null: OFF выключает слой,
 * AUTO разрешается в конкретный мотив через палитру до вызова этого метода.
 */
fun AmaliaMotif.behavior(): MotifBehavior? = when (this) {
    AmaliaMotif.SAKURA -> MotifBehavior(
        falling = true,
        spin = 0.55f,
        sway = 0.090f,
        count = 16,
        minSize = 7f,
        maxSize = 15f,
        // Медленное падение + сильное качание = «лепесток в воздухе».
        fallSpeedScale = 0.72f,
    )
    AmaliaMotif.MAPLE -> MotifBehavior(
        falling = true,
        spin = 1.15f,
        sway = 0.050f,
        count = 11,
        minSize = 9f,
        maxSize = 19f,
        // Тяжелее: падает быстрее и почти не качается.
        fallSpeedScale = 1.25f,
    )
    AmaliaMotif.SNOW -> MotifBehavior(
        falling = true,
        spin = 0.15f,
        sway = 0.035f,
        count = 30,
        minSize = 3f,
        maxSize = 8f,
        // Плотный ровный снегопад.
        fallSpeedScale = 0.95f,
    )
    AmaliaMotif.STARS -> MotifBehavior(
        falling = false,
        spin = 0f,
        sway = 0f,
        count = 34,
        minSize = 1.6f,
        maxSize = 4.4f,
        // Самое сильное мерцание: звёзды должны «дышать» заметно,
        // иначе статичный мотив выглядит как застывший экран.
        pulse = 1f,
    )
    AmaliaMotif.FIREFLY -> MotifBehavior(
        falling = false,
        spin = 0f,
        sway = 0.04f,
        count = 12,
        minSize = 3.5f,
        maxSize = 7.5f,
        pulse = 1f,
        // Единственный мотив с блужданием: светлячки гуляют по экрану.
        drift = 0.075f,
    )
    AmaliaMotif.OFF, AmaliaMotif.AUTO -> null
}

/**
 * Разрешение [AmaliaMotif.AUTO] в конкретный мотив по времени суток.
 *
 * Используется и для палитр, и для превью в настройках, чтобы «авто»
 * выглядело одинаково везде.
 *
 * ## Почему по дробному часу, а не по четырём интервалам
 *
 * Раньше здесь стоял `BioTimeOfDay.fromHour(hour)` — тот самый грубый
 * `when` по четырём интервалам, из-за которого палитра переключалась
 * рывком. Мотиву это вредило ещё заметнее, чем цвету: в 11:59 падали
 * лепестки, в 12:00 они мгновенно исчезали и начинали падать листья.
 * Выглядело как глюк.
 *
 * Теперь границы заданы **по высоте солнца**, и мотив меняется там же, где
 * меняется свет. Резкость перехода никуда не девается — мотив нельзя
 * смешать, как цвет, — но она попадает на момент, когда экран и так
 * перестраивается (рассвет/закат), а не на «круглую» цифру на часах.
 *
 * @param hour дробный час: 14.5 = 14:30.
 */
fun autoMotifAt(hour: Float): AmaliaMotif {
    val elevation = CircadianEngine.solarElevation(hour)
    return when {
        // Ночь: солнце глубоко под горизонтом — звёзды.
        elevation <= -0.35f -> AmaliaMotif.STARS
        // Сумерки: светлячки появляются вместе с первыми тенями.
        elevation <= 0f -> AmaliaMotif.FIREFLY
        // Рассвет/золотой час: солнце низко — листопад.
        elevation <= 0.42f -> AmaliaMotif.MAPLE
        // Полный день: лепестки.
        else -> AmaliaMotif.SAKURA
    }
}


/** Мотив текущей палитры с учётом выбора пользователя. */
@Composable
@ReadOnlyComposable
fun resolveMotif(requested: AmaliaMotif, palette: GradientPalette): AmaliaMotif = when (requested) {
    AmaliaMotif.OFF -> AmaliaMotif.OFF
    AmaliaMotif.AUTO -> palette.motif.takeIf { it.hasParticles }
        ?: autoMotifAt(CircadianEngine.nowFractionalHour())
    else -> requested
}

/**
 * Палитра-репрезентация мотива для превью в настройках: два акцентных цвета,
 * из которых рисуется миниатюра.
 */
fun AmaliaMotif.previewColors(): Pair<Color, Color> = when (this) {
    AmaliaMotif.SAKURA -> SakuraPetal to SakuraDeep
    AmaliaMotif.MAPLE -> MapleAmber to MapleRose
    AmaliaMotif.SNOW -> SnowPale to SnowCold
    AmaliaMotif.STARS -> StarWarm to StarCool
    AmaliaMotif.FIREFLY -> FireflyLime to FireflyGold
    AmaliaMotif.OFF -> Color(0xFF6E63F2) to Color(0xFF79C7E8)
    AmaliaMotif.AUTO -> SakuraPetal to StarCool
}

// ── Палитры самих частиц ───────────────────────────────────────────────

/** Лепестки сакуры: от почти белого к глубокому розовому. */
val SakuraPetal = Color(0xFFFFD9E4)
val SakuraPetalSoft = Color(0xFFFFEAF0)
val SakuraDeep = Color(0xFFF3A8C0)

/** Кленовые листья: медь, охра, лёгкий багрянец. */
val MapleAmber = Color(0xFFE7A65B)
val MapleRose = Color(0xFFC96A4F)
val MapleGreen = Color(0xFFAFC189)

/** Снег: холодное серебро. */
val SnowPale = Color(0xFFEFF4FF)
val SnowCold = Color(0xFFBFD2EA)

/** Звёзды: тёплое золото + холодный сирень. */
val StarWarm = Color(0xFFFFE9BD)
val StarCool = Color(0xFFCBD5FF)

/** Светлячки: лаймовое свечение с янтарным ядром. */
val FireflyLime = Color(0xFFCBF27A)
val FireflyGold = Color(0xFFF7D778)
