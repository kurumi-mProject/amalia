package com.my.amali.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// ════════════════════════════════════════════════════════════
//  ТЕМА 1: LIQUID GLASS — минимализм, тёмное стекло, один акцент
//  Принцип: почти чёрный холодный фон, низкий chroma, светится
//  только то, что важно (волна, активная кнопка, выбранный пункт).
//
//  ВАЖНО: ни один фон здесь не является чистым #000000.
//  Минимальная светлота задана CircadianEngine.MIN_BG_LIGHTNESS —
//  это защита от halation (расплывание светлого текста на чёрном).
// ════════════════════════════════════════════════════════════

// --- Акцент: ледяной сине-фиолетовый, один на всё приложение ---
val GlassAccentDim = Color(0xFF6E63F2)      // база акцента
val GlassAccentSoft = Color(0xFF9A90FF)     // подсветка, текст-акцент
val GlassAccentGlow = Color(0xFF4F44C9)     // глубокая тень акцента
val GlassAccentMist = Color(0xFF79C7E8)     // холодный второй тон для градиента волны

// --- Состояния ---
val GlassStateIdle = Color(0xFF8E8AA8)
val GlassStateListening = Color(0xFF9A90FF)
val GlassStateThinking = Color(0xFF8AA0C8)
val GlassStateSpeaking = Color(0xFF79C7E8)
val GlassStateError = Color(0xFFE08A8A)

// --- Фоны: глубокий графит с синим уклоном (off-black, не #000) ---
val GlassBgBase = Color(0xFF0A0B11)
val GlassBgSurface = Color(0xFF12131B)
val GlassBgSurfaceHigh = Color(0xFF191A24)
val GlassBgGlass = Color(0xFF15161F)
val GlassBgStroke = Color(0xFF2E2F3D)

// --- Текст: off-white, а не #FFFFFF (компенсация halation) ---
val GlassTextPrimary = Color(0xFFE8E7F0)
val GlassTextSecondary = Color(0xFFA4A4B6)
val GlassTextFaint = Color(0xFF6A6A7C)

// --- Стеклянные слои ---
val GlassOverlay = Color(0xFFFFFFFF)
val GlassOverlayAlpha = 0.05f
val GlassBorderAlpha = 0.10f

// ════════════════════════════════════════════════════════════
//  ТЕМА 2: БИОФИЛЬНАЯ РЕЛАКСАЦИЯ — мягкий свет, тёплое стекло
// ════════════════════════════════════════════════════════════

val BioSage = Color(0xFFB7C9B0)
val BioMintFog = Color(0xFFD4E0CC)
val BioWarmSand = Color(0xFFE8DFD0)
val BioDeepOlive = Color(0xFF3A4038)

val BioDustyRose = Color(0xFFE0BFB8)
val BioCreamyLatte = Color(0xFFF5E8D8)
val BioPaleCoral = Color(0xFFF0CABF)

val BioWarmGraphite = Color(0xFF322D26)
val BioTerracotta = Color(0xFF8B5E4A)
val BioAmberCoal = Color(0xFF7A6142)
val BioWarmMilk = Color(0xFFEDE0CC)

val BioLavenderMist = Color(0xFFC8C8D8)
val BioLavenderDeep = Color(0xFF9090A8)
val BioLavenderBg = Color(0xFF17161E)
val BioLavenderText = Color(0xFFD0D0DC)

// --- Биофильные акценты ---
val BioAccentPrimary = Color(0xFF6F9A76)
val BioAccentSecondary = Color(0xFFD9A97F)
val BioAccentTertiary = Color(0xFF8FA6D6)

// --- Биофильные состояния ---
val BioStateIdle = Color(0xFF6F9A76)
val BioStateListening = Color(0xFF8FBE97)
val BioStateThinking = Color(0xFFA8A09B)
val BioStateSpeaking = Color(0xFFD9A97F)
val BioStateError = Color(0xFFC08585)

// --- Биофильные фоны (день) ---
val BioBgBase = Color(0xFFF7F4EE)
val BioBgSurface = Color(0xFFFFFCF6)
val BioBgSurfaceHigh = Color(0xFFF1EBDF)
val BioBgGlass = Color(0xFFFBF7EF)
val BioBgStroke = Color(0xFFDCD5C6)
val BioTextPrimary = Color(0xFF33382F)
val BioTextSecondary = Color(0xFF615F57)
val BioTextFaint = Color(0xFF918E84)

// --- Биофильные фоны (вечер/ночь) ---
val BioDarkBgBase = Color(0xFF231F1B)
val BioDarkBgSurface = Color(0xFF2E2923)
val BioDarkBgSurfaceHigh = Color(0xFF39322B)
val BioDarkBgGlass = Color(0xFF322C26)
val BioDarkBgStroke = Color(0xFF4A4136)
val BioDarkTextPrimary = Color(0xFFE8DCC9)
val BioDarkTextSecondary = Color(0xFFB8AD9B)
val BioDarkTextFaint = Color(0xFF8A7F6C)

// ════════════════════════════════════════════════════════════
//  Светлая нейтральная (fallback)
// ════════════════════════════════════════════════════════════
val LightBg = Color(0xFFF4F4F7)
val LightSurface = Color(0xFFFFFFFF)
val LightTextPrimary = Color(0xFF16161C)
val LightTextSecondary = Color(0xFF6B6B7A)

// ════════════════════════════════════════════════════════════
//  Градиентные палитры живого фона
// ════════════════════════════════════════════════════════════

data class GradientStop(
    val color: Color,
    val position: Float,
)

/**
 * Палитра живого фона.
 *
 * Каждая палитра описывает **свет**, а не «набор красивых цветов»:
 * поэтому у неё есть [cct] (цветовая температура в кельвинах) и
 * [melanopicDer] — это позволяет проверять палитры на соответствие
 * времени суток и не давать «ледяной ночи» или «янтарного полдня».
 *
 * @property cct цветовая температура света палитры в кельвинах.
 *   Пользователь видит её в настройках, и она же участвует в расчёте
 *   циркадного вклада — одна цифра вместо двух независимых правд.
 * @property melanopicDer меланопическая эффективность относительно D65.
 * @property stops вертикальные стопы базового градиента.
 * @property auroras цвета дрейфующих пятен-аурор.
 * @property motif декорации, которые автоматически «прилетают» под этот
 *   настрой: у персикового дня — лепестки сакуры, у лавандовой ночи — звёзды.
 *   Это не «ещё один слой», а визуальная причина, почему цвет вообще
 *   меняется: пользователю очевидно, что тема следует за временем, а не глючит.
 * @property motifAccent основной цвет частиц; если не задан — берётся
 *   последний aurora-акцент.
 * @property shadow тень, которой палитра обязана пользоваться вместе с фоном.
 *   Тёплый вечер не может отбрасывать ледяную тень — иначе вся работа по
 *   адаптации света разваливается на последнем слое.
 */
data class GradientPalette(
    val name: String,
    val stops: List<GradientStop>,
    val isDark: Boolean,
    val cct: Int,
    val melanopicDer: Float,
    /** Цвета «аурора»-пятен, которые медленно дрейфуют поверх базы. */
    val auroras: List<Color> = emptyList(),
    val motif: AmaliaMotif = AmaliaMotif.OFF,
    val motifAccent: Color = Color.Unspecified,
    val motifAccentAlt: Color = Color.Unspecified,
    /** Цвет тени для этой палитры. */
    val shadow: Color = Color(0x75000000),
    /** Насколько сильно стекло «ловит» свет сверху (0..1). */
    val sheen: Float = 0.10f,
) {
    /** Тёплая ли палитра — для выбора направления тонировки стекла. */
    val isWarm: Boolean get() = cct <= 3400
}

/**
 * Палитры живого фона.
 *
 * У каждой — свой [GradientPalette.motif]: палитра и декорации решаются
 * ОДНИМ значением, поэтому фон и «что летает поверх» физически не могут
 * разъехаться (было бы странно ронять снежинки на персиковое утро).
 *
 * Порядок палитр — от холодного к тёплому, с указанием CCT. Это не
 * декоративные подписи: [currentGradientPalette] выбирает палитру по
 * рассчитанной CCT, а не по грубому `when (hour)`, поэтому переход между
 * ними непрерывен и не даёт вспышки на границе часа.
 */

// ── LIQUID GLASS: тёмная база, свет меняется по времени суток ──────────

private val glassStopsBase = listOf(
    GradientStop(Color(0xFF0A0B11), 0f),
    GradientStop(Color(0xFF10111A), 0.55f),
    GradientStop(Color(0xFF090A10), 1f),
)

/** Ночь/база: индиго + лёд. Самая холодная и самая «тихая» палитра. */
val GlassGradientPalette = GradientPalette(
    name = "Liquid Glass",
    stops = glassStopsBase,
    isDark = true,
    cct = 2400,
    melanopicDer = 0.11f,
    auroras = listOf(
        Color(0xFF6E63F2),
        Color(0xFF3E6FA8),
        Color(0xFF79C7E8),
    ),
    motif = AmaliaMotif.STARS,
    motifAccent = StarCool,
    motifAccentAlt = StarWarm,
    shadow = Color(0x8A05060A),
    sheen = 0.09f,
)

/** Утро в стекле: холодный циан + шалфей. */
val GlassGradientMorning = GradientPalette(
    name = "Liquid Glass · Утро",
    stops = listOf(
        GradientStop(Color(0xFF090B0F), 0f),
        GradientStop(Color(0xFF0E151A), 0.55f),
        GradientStop(Color(0xFF080A0D), 1f),
    ),
    isDark = true,
    cct = 4200,
    melanopicDer = 0.39f,
    auroras = listOf(
        Color(0xFF6FA8A0),
        Color(0xFF79C7E8),
        Color(0xFF9AB06E),
    ),
    motif = AmaliaMotif.MAPLE,
    motifAccent = MapleGreen,
    motifAccentAlt = MapleAmber,
    shadow = Color(0x8A06090C),
    sheen = 0.11f,
)

/** День в стекле: тот самый розовый — значит сакура. */
val GlassGradientDay = GradientPalette(
    name = "Liquid Glass · День",
    stops = listOf(
        GradientStop(Color(0xFF0B0810), 0f),
        GradientStop(Color(0xFF16101B), 0.55f),
        GradientStop(Color(0xFF0A080E), 1f),
    ),
    isDark = true,
    cct = 5600,
    melanopicDer = 0.72f,
    auroras = listOf(
        Color(0xFFE5A0BF),
        Color(0xFF6E63F2),
        Color(0xFFF3C6D6),
    ),
    motif = AmaliaMotif.SAKURA,
    motifAccent = SakuraPetal,
    motifAccentAlt = SakuraDeep,
    shadow = Color(0x8A0A0610),
    sheen = 0.13f,
)

/** Вечер в стекле: медь и янтарь → светлячки. */
val GlassGradientEvening = GradientPalette(
    name = "Liquid Glass · Вечер",
    stops = listOf(
        GradientStop(Color(0xFF0C0809), 0f),
        GradientStop(Color(0xFF17100E), 0.55f),
        GradientStop(Color(0xFF0A0708), 1f),
    ),
    isDark = true,
    cct = 2700,
    melanopicDer = 0.16f,
    auroras = listOf(
        Color(0xFFB4794A),
        Color(0xFF7A6142),
        Color(0xFFD9A97F),
    ),
    motif = AmaliaMotif.FIREFLY,
    motifAccent = FireflyLime,
    motifAccentAlt = FireflyGold,
    shadow = Color(0x8A0B0507),
    sheen = 0.10f,
)

// ── БИОФИЛЬНАЯ: мягкий свет → тёплый сумрак ────────────────────────────

val BioGradientMorning = GradientPalette(
    name = "Утренняя Листва",
    stops = listOf(
        GradientStop(Color(0xFFF3F2EA), 0f),
        GradientStop(Color(0xFFE6EEDE), 0.5f),
        GradientStop(Color(0xFFEFEBDF), 1f),
    ),
    isDark = false,
    cct = 4400,
    melanopicDer = 0.43f,
    auroras = listOf(
        Color(0xFFB7C9B0),
        Color(0xFFD9E3CE),
        Color(0xFFE8DFD0),
    ),
    motif = AmaliaMotif.MAPLE,
    motifAccent = MapleGreen,
    motifAccentAlt = MapleAmber,
    shadow = Color(0x2A4A4436),
    sheen = 0.16f,
)

val BioGradientDay = GradientPalette(
    name = "Персиковый Шёлк",
    stops = listOf(
        GradientStop(Color(0xFFFBF3E7), 0f),
        GradientStop(Color(0xFFF6E4DC), 0.5f),
        GradientStop(Color(0xFFF4EBDD), 1f),
    ),
    isDark = false,
    cct = 5300,
    melanopicDer = 0.64f,
    auroras = listOf(
        Color(0xFFF0CABF),
        Color(0xFFE0BFB8),
        Color(0xFFF5E8D8),
    ),
    motif = AmaliaMotif.SAKURA,
    motifAccent = SakuraPetal,
    motifAccentAlt = SakuraDeep,
    shadow = Color(0x2E5A4A3C),
    sheen = 0.18f,
)

val BioGradientEvening = GradientPalette(
    name = "Вечерний Туман",
    stops = listOf(
        GradientStop(Color(0xFF2C2620), 0f),
        GradientStop(Color(0xFF362C24), 0.55f),
        GradientStop(Color(0xFF26211B), 1f),
    ),
    isDark = true,
    cct = 2500,
    melanopicDer = 0.13f,
    auroras = listOf(
        Color(0xFF8B5E4A),
        Color(0xFF7A6142),
        Color(0xFFD9A97F),
    ),
    motif = AmaliaMotif.FIREFLY,
    motifAccent = FireflyLime,
    motifAccentAlt = FireflyGold,
    shadow = Color(0x8A1A0F08),
    sheen = 0.09f,
)

val BioGradientNight = GradientPalette(
    name = "Лавандовая Дымка",
    stops = listOf(
        GradientStop(Color(0xFF18171F), 0f),
        GradientStop(Color(0xFF1F1D28), 0.55f),
        GradientStop(Color(0xFF15141C), 1f),
    ),
    isDark = true,
    cct = 2100,
    melanopicDer = 0.07f,
    auroras = listOf(
        Color(0xFF9090A8),
        Color(0xFF6E63F2),
        Color(0xFFC8C8D8),
    ),
    motif = AmaliaMotif.SNOW,
    motifAccent = SnowPale,
    motifAccentAlt = SnowCold,
    shadow = Color(0x8A0E0C14),
    sheen = 0.08f,
)

// ════════════════════════════════════════════════════════════
//  ВЫБОР ПАЛИТРЫ ПО СВЕТУ (непрерывно, без скачков на границе часа)
// ════════════════════════════════════════════════════════════

/**
 * Все палитры темы, упорядоченные по CCT (от самой тёплой к самой холодной).
 *
 * Именно этот список — единственный источник правды о том, какие палитры
 * существуют и как они соотносятся по свету. Добавление новой палитры
 * не требует правок ни в теме, ни в фоне: достаточно положить её сюда с
 * корректной [GradientPalette.cct].
 */
val glassPaletteLadder: List<GradientPalette> = listOf(
    GlassGradientPalette,   // 2400 K ночь
    GlassGradientEvening,   // 2700 K вечер
    GlassGradientMorning,   // 4200 K утро
    GlassGradientDay,       // 5600 K день
).sortedBy { it.cct }

/** Биофильная лестница: светлые палитры дня и тёплые тёмные вечера. */
val bioPaletteLadder: List<GradientPalette> = listOf(
    BioGradientNight,       // 2100 K
    BioGradientEvening,     // 2500 K
    BioGradientMorning,     // 4400 K
    BioGradientDay,         // 5300 K
).sortedBy { it.cct }

/**
 * Выбирает палитру под рассчитанную CCT **смешением двух соседей**.
 *
 * Раньше выбор был дискретным (`when` по четырём интервалам часа), и на
 * границе интервала экран перекрашивался рывком — это выглядело как глюк и
 * было главной претензией к «сломанной» адаптации. Теперь палитра — линейная
 * интерполяция между двумя ближайшими по CCT палитрами, поэтому свет едет
 * непрерывно, а вместе с ним непрерывно едут и акценты, и тени.
 *
 * Композиция «смешать» делается не через усреднение цветов в RGB (это
 * давало бы грязную муть из двух палитр), а через:
 *  — интерполяцию стопов градиента по позиции;
 *  — интерполяцию аурор по индексу (у всех палитр их три);
 *  — выбор мотива у ближайшей палитры (декорации не «растворяются»).
 *
 * @param ladder лестница палитр, отсортированная по возрастанию CCT.
 * @param cct целевая цветовая температура.
 */
fun paletteForCct(ladder: List<GradientPalette>, cct: Int): GradientPalette =
    blendLadder(ladder, cct)

/** Смешивает палитры лестницы под целевую CCT. */
private fun blendLadder(ladder: List<GradientPalette>, cct: Int): GradientPalette {
    if (ladder.isEmpty()) return GlassGradientPalette
    if (ladder.size == 1) return ladder.first()

    val target = cct.toFloat().coerceIn(
        ladder.first().cct.toFloat(),
        ladder.last().cct.toFloat(),
    )

    var lower = ladder.first()
    var upper = ladder.last()
    for (i in 0 until ladder.lastIndex) {
        if (target >= ladder[i].cct && target <= ladder[i + 1].cct) {
            lower = ladder[i]
            upper = ladder[i + 1]
            break
        }
    }

    if (lower === upper || lower.cct == upper.cct) return lower

    val t = ((target - lower.cct) / (upper.cct - lower.cct).toFloat()).coerceIn(0f, 1f)
    val anchor = if (t < 0.5f) lower else upper

    return GradientPalette(
        name = anchor.name,
        stops = blendStops(lower.stops, upper.stops, t),
        isDark = anchor.isDark,
        cct = cct,
        melanopicDer = lerp(lower.melanopicDer, upper.melanopicDer, t),
        auroras = blendColors(lower.auroras, upper.auroras, t),
        // Мотив берём у ближайшей палитры целиком: частично смешанный
        // «снег с лепестками» — это уже не мотив, а визуальный шум.
        motif = anchor.motif,
        motifAccent = anchor.motifAccent,
        motifAccentAlt = anchor.motifAccentAlt,
        shadow = lerp(lower.shadow, upper.shadow, t),
        sheen = lerp(lower.sheen, upper.sheen, t),
    )
}

/** Интерполирует стопы двух градиентов по совпадающим позициям. */
private fun blendStops(
    a: List<GradientStop>,
    b: List<GradientStop>,
    t: Float,
): List<GradientStop> {
    if (a.isEmpty()) return b
    if (b.isEmpty()) return a
    val count = maxOf(a.size, b.size)
    return (0 until count).map { i ->
        val sa = a.getOrElse(i) { a.last() }
        val sb = b.getOrElse(i) { b.last() }
        GradientStop(
            color = lerp(sa.color, sb.color, t),
            position = sa.position + (sb.position - sa.position) * t,
        )
    }
}

/** Интерполирует два списка цветов по индексу. */
private fun blendColors(a: List<Color>, b: List<Color>, t: Float): List<Color> {
    if (a.isEmpty()) return b
    if (b.isEmpty()) return a
    val count = maxOf(a.size, b.size)
    return (0 until count).map { i ->
        lerp(a.getOrElse(i) { a.last() }, b.getOrElse(i) { b.last() }, t)
    }
}
