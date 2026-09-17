package com.my.amali.ui.theme

import androidx.compose.ui.graphics.Color

// ════════════════════════════════════════════════════════════
//  ТЕМА 1: LIQUID GLASS — минимализм, тёмное стекло, один акцент
//  Принцип: почти чёрный холодный фон, низкий chroma, светится
//  только то, что важно (волна, активная кнопка, выбранный пункт).
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

// --- Фоны: глубокий графит с синим уклоном ---
val GlassBgBase = Color(0xFF07070B)
val GlassBgSurface = Color(0xFF101017)
val GlassBgSurfaceHigh = Color(0xFF17171F)
val GlassBgGlass = Color(0xFF13131B)
val GlassBgStroke = Color(0xFF2C2C38)

// --- Текст ---
val GlassTextPrimary = Color(0xFFF2F2F6)
val GlassTextSecondary = Color(0xFF9E9EAE)
val GlassTextFaint = Color(0xFF60606F)

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
val BioTextSecondary = Color(0xFF6B6960)
val BioTextFaint = Color(0xFF9B988E)

// --- Биофильные фоны (вечер/ночь) ---
val BioDarkBgBase = Color(0xFF211D19)
val BioDarkBgSurface = Color(0xFF2C2721)
val BioDarkBgSurfaceHigh = Color(0xFF373029)
val BioDarkBgGlass = Color(0xFF302A24)
val BioDarkBgStroke = Color(0xFF473E34)
val BioDarkTextPrimary = Color(0xFFEDE0CC)
val BioDarkTextSecondary = Color(0xFFB3A896)
val BioDarkTextFaint = Color(0xFF827764)

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
 * @property auroras цвета дрейфующих пятен-аурор.
 * @property motif декорации, которые автоматически «прилетают» под этот
 *   настрой: у персикового дня — лепестки сакуры, у лавандовой ночи — звёзды.
 *   Это не «ещё один слой», а визуальная причина, почему цвет вообще
 *   меняется: пользователю очевидно, что тема следует за временем, а не глючит.
 * @property motifAccent основной цвет частиц; если не задан — берётся
 *   последний aurora-акцент.
 */
data class GradientPalette(
    val name: String,
    val stops: List<GradientStop>,
    val isDark: Boolean,
    /** Цвета «аурора»-пятен, которые медленно дрейфуют поверх базы. */
    val auroras: List<Color> = emptyList(),
    val motif: AmaliaMotif = AmaliaMotif.OFF,
    val motifAccent: Color = Color.Unspecified,
    val motifAccentAlt: Color = Color.Unspecified,
)

/**
 * Палитры живого фона.
 *
 * У каждой — свой [GradientPalette.motif]: палитра и декорации решаются
 * ОДНИМ значением, поэтому фон и «что летает поверх» физически не могут
 * разъехаться (было бы странно ронять снежинки на персиковое утро).
 */

// ── LIQUID GLASS: тёмная база, акценты меняются по времени суток ────────

private val glassStops = listOf(
    GradientStop(Color(0xFF08080C), 0f),
    GradientStop(Color(0xFF0D0D14), 0.55f),
    GradientStop(Color(0xFF06060A), 1f),
)

/** Ночь/база: индиго + лёд. */
val GlassGradientPalette = GradientPalette(
    name = "Liquid Glass",
    stops = glassStops,
    isDark = true,
    auroras = listOf(
        Color(0xFF6E63F2),
        Color(0xFF3E6FA8),
        Color(0xFF79C7E8),
    ),
    motif = AmaliaMotif.STARS,
    motifAccent = StarCool,
    motifAccentAlt = StarWarm,
)

/** Утро в стекле: холодный циан + шалфей. */
val GlassGradientMorning = GradientPalette(
    name = "Liquid Glass · Утро",
    stops = listOf(
        GradientStop(Color(0xFF07080C), 0f),
        GradientStop(Color(0xFF0C1216), 0.55f),
        GradientStop(Color(0xFF06070A), 1f),
    ),
    isDark = true,
    auroras = listOf(
        Color(0xFF6FA8A0),
        Color(0xFF79C7E8),
        Color(0xFF9AB06E),
    ),
    motif = AmaliaMotif.MAPLE,
    motifAccent = MapleGreen,
    motifAccentAlt = MapleAmber,
)

/** День в стекле: тот самый розовый — значит сакура. */
val GlassGradientDay = GradientPalette(
    name = "Liquid Glass · День",
    stops = listOf(
        GradientStop(Color(0xFF09070C), 0f),
        GradientStop(Color(0xFF130C16), 0.55f),
        GradientStop(Color(0xFF07060A), 1f),
    ),
    isDark = true,
    auroras = listOf(
        Color(0xFFE5A0BF),
        Color(0xFF6E63F2),
        Color(0xFFF3C6D6),
    ),
    motif = AmaliaMotif.SAKURA,
    motifAccent = SakuraPetal,
    motifAccentAlt = SakuraDeep,
)

/** Вечер в стекле: медь и янтарь → светлячки. */
val GlassGradientEvening = GradientPalette(
    name = "Liquid Glass · Вечер",
    stops = listOf(
        GradientStop(Color(0xFF0A0708), 0f),
        GradientStop(Color(0xFF140D0C), 0.55f),
        GradientStop(Color(0xFF070506), 1f),
    ),
    isDark = true,
    auroras = listOf(
        Color(0xFFB4794A),
        Color(0xFF7A6142),
        Color(0xFFD9A97F),
    ),
    motif = AmaliaMotif.FIREFLY,
    motifAccent = FireflyLime,
    motifAccentAlt = FireflyGold,
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
    auroras = listOf(
        Color(0xFFB7C9B0),
        Color(0xFFD9E3CE),
        Color(0xFFE8DFD0),
    ),
    motif = AmaliaMotif.MAPLE,
    motifAccent = MapleGreen,
    motifAccentAlt = MapleAmber,
)

val BioGradientDay = GradientPalette(
    name = "Персиковый Шёлк",
    stops = listOf(
        GradientStop(Color(0xFFFBF3E7), 0f),
        GradientStop(Color(0xFFF6E4DC), 0.5f),
        GradientStop(Color(0xFFF4EBDD), 1f),
    ),
    isDark = false,
    auroras = listOf(
        Color(0xFFF0CABF),
        Color(0xFFE0BFB8),
        Color(0xFFF5E8D8),
    ),
    motif = AmaliaMotif.SAKURA,
    motifAccent = SakuraPetal,
    motifAccentAlt = SakuraDeep,
)

val BioGradientEvening = GradientPalette(
    name = "Вечерний Туман",
    stops = listOf(
        GradientStop(Color(0xFF2A251F), 0f),
        GradientStop(Color(0xFF332A22), 0.55f),
        GradientStop(Color(0xFF241F1A), 1f),
    ),
    isDark = true,
    auroras = listOf(
        Color(0xFF8B5E4A),
        Color(0xFF7A6142),
        Color(0xFFD9A97F),
    ),
    motif = AmaliaMotif.FIREFLY,
    motifAccent = FireflyLime,
    motifAccentAlt = FireflyGold,
)

val BioGradientNight = GradientPalette(
    name = "Лавандовая Дымка",
    stops = listOf(
        GradientStop(Color(0xFF15141B), 0f),
        GradientStop(Color(0xFF1C1A24), 0.55f),
        GradientStop(Color(0xFF121118), 1f),
    ),
    isDark = true,
    auroras = listOf(
        Color(0xFF9090A8),
        Color(0xFF6E63F2),
        Color(0xFFC8C8D8),
    ),
    motif = AmaliaMotif.SNOW,
    motifAccent = SnowPale,
    motifAccentAlt = SnowCold,
)
