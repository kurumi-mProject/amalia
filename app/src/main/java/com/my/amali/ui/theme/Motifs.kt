package com.my.amali.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import java.util.Calendar

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
)

/** Поведение по мотиву. [AmaliaMotif.OFF] и [AmaliaMotif.AUTO] не рисуются. */
fun AmaliaMotif.behavior(): MotifBehavior? = when (this) {
    AmaliaMotif.SAKURA -> MotifBehavior(
        falling = true, spin = 0.55f, sway = 0.075f, count = 16,
        minSize = 7f, maxSize = 15f,
    )
    AmaliaMotif.MAPLE -> MotifBehavior(
        falling = true, spin = 0.95f, sway = 0.055f, count = 11,
        minSize = 9f, maxSize = 19f,
    )
    AmaliaMotif.SNOW -> MotifBehavior(
        falling = true, spin = 0.15f, sway = 0.03f, count = 30,
        minSize = 3f, maxSize = 8f,
    )
    AmaliaMotif.STARS -> MotifBehavior(
        falling = false, spin = 0f, sway = 0f, count = 34,
        minSize = 1.6f, maxSize = 4.4f, pulse = 0.85f,
    )
    AmaliaMotif.FIREFLY -> MotifBehavior(
        falling = false, spin = 0f, sway = 0.04f, count = 12,
        minSize = 3.5f, maxSize = 7.5f, pulse = 1f, drift = 0.05f,
    )
    AmaliaMotif.OFF, AmaliaMotif.AUTO -> null
}

/**
 * Разрешение [AmaliaMotif.AUTO] в конкретный мотив по времени суток.
 *
 * Используется и для палитр, и для превью в настройках, чтобы «авто»
 * выглядело одинаково везде.
 */
fun autoMotifAt(hour: Int): AmaliaMotif = when (BioTimeOfDay.fromHour(hour)) {
    BioTimeOfDay.MORNING -> AmaliaMotif.MAPLE
    BioTimeOfDay.DAY -> AmaliaMotif.SAKURA
    BioTimeOfDay.EVENING -> AmaliaMotif.FIREFLY
    BioTimeOfDay.NIGHT -> AmaliaMotif.STARS
}

/** Мотив текущей палитры с учётом выбора пользователя. */
@Composable
@ReadOnlyComposable
fun resolveMotif(requested: AmaliaMotif, palette: GradientPalette): AmaliaMotif = when (requested) {
    AmaliaMotif.OFF -> AmaliaMotif.OFF
    AmaliaMotif.AUTO -> palette.motif.takeIf { it.hasParticles }
        ?: autoMotifAt(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
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
