package com.my.amali.ui.theme

import androidx.compose.ui.graphics.Color

// ════════════════════════════════════════════════════════════
//  ТЕМА 1: MINIMALIST LIQUID GLASS — тёмная, холодная, стеклянная
//  Биология: тёмный фон, низкий chroma, минимум синего спектра
//  в вечернее время (управляется BiotimeManager)
// ════════════════════════════════════════════════════════════

// --- Акценты (приглушённый сине-фиолет, не кислотный) ---
val GlassAccentDim = Color(0xFF6D5FE0)
val GlassAccentSoft = Color(0xFF8478F0)
val GlassAccentGlow = Color(0xFF5D50D8)

// --- Состояния (приглушённые) ---
val GlassStateIdle = Color(0xFF6D5FE0)
val GlassStateListening = Color(0xFF8478F0)
val GlassStateThinking = Color(0xFF7B7FA0)
val GlassStateSpeaking = Color(0xFF9388F5)
val GlassStateError = Color(0xFFB06565)

// --- Фоны: глубокий тёмный с лёгким тёплым уклоном ---
val GlassBgBase = Color(0xFF0B0B0F)
val GlassBgSurface = Color(0xFF15151C)
val GlassBgSurfaceHigh = Color(0xFF1D1D26)
val GlassBgGlass = Color(0xFF1A1A23)
val GlassBgStroke = Color(0xFF2A2A35)

// --- Текст ---
val GlassTextPrimary = Color(0xFFEDEDF0)
val GlassTextSecondary = Color(0xFF9B9BA8)
val GlassTextFaint = Color(0xFF5E5E6E)

// --- Glass overlay ---
val GlassOverlay = Color(0xFFFFFFFF)
val GlassOverlayAlpha = 0.04f
val GlassBorderAlpha = 0.08f

// ════════════════════════════════════════════════════════════
//  ТЕМА 2: БИОФИЛЬНАЯ РЕЛАКСАЦИЯ — нежные градиенты
//  Биология: OKLCH-конвертированные цвета, 555nm пик,
//  меланопсин-безопасные, низкий chroma
// ════════════════════════════════════════════════════════════

// --- Палитра 1: "Утренняя Листва" (день / фокус) ---
// oklch(75% 0.06 145) → Sage
val BioSage = Color(0xFFB7C9B0)
// oklch(85% 0.05 160) → Mint Fog
val BioMintFog = Color(0xFFD4E0CC)
// oklch(88% 0.04 90) → Warm Sand
val BioWarmSand = Color(0xFFE8DFD0)
// oklch(30% 0.05 140) → Deep Olive (text)
val BioDeepOlive = Color(0xFF3A4038)

// --- Палитра 2: "Персиковый Шёлк" (день / творчество) ---
// oklch(78% 0.07 20) → Dusty Rose
val BioDustyRose = Color(0xFFE0BFB8)
// oklch(90% 0.03 70) → Creamy Latte
val BioCreamyLatte = Color(0xFFF5E8D8)
// oklch(82% 0.08 35) → Pale Coral
val BioPaleCoral = Color(0xFFF0CABF)

// --- Палитра 3: "Вечерний Туман" (вечер / подготовка ко сну) ---
// oklch(25% 0.02 60) → Warm Graphite
val BioWarmGraphite = Color(0xFF3A342C)
// oklch(40% 0.08 45) → Terracotta Shadow
val BioTerracotta = Color(0xFF8B5E4A)
// oklch(35% 0.05 80) → Amber Coal
val BioAmberCoal = Color(0xFF7A6142)
// oklch(85% 0.03 80) → Warm Milk (text)
val BioWarmMilk = Color(0xFFE8D8C0)

// --- Палитра 4: "Лавандовая Дымка" (ночь / медитация) ---
val BioLavenderMist = Color(0xFFC8C8D8)
val BioLavenderDeep = Color(0xFF9090A8)
val BioLavenderBg = Color(0xFF1C1A24)
val BioLavenderText = Color(0xFFD0D0DC)

// --- Биофильный акцент ---
val BioAccentPrimary = Color(0xFF7A9E7B)     // мягкий зелёный (555nm пик)
val BioAccentSecondary = Color(0xFFE0B89A)  // тёплый персиковый
val BioAccentTertiary = Color(0xFF8B9EDA)   // выбеленный голубой (для дня)

// --- Биофильные состояния ---
val BioStateIdle = Color(0xFF7A9E7B)
val BioStateListening = Color(0xFF9DBE9E)
val BioStateThinking = Color(0xFFA8A09B)
val BioStateSpeaking = Color(0xFFC8A88A)
val BioStateError = Color(0xFFC08585)

// --- Биофильные фоны ---
val BioBgBase = Color(0xFFF5F2EC)           // светлый тёплый (день)
val BioBgSurface = Color(0xFFEFEAE0)
val BioBgSurfaceHigh = Color(0xFFE8E2D4)
val BioBgGlass = Color(0xFFF0EBE0)
val BioBgStroke = Color(0xFFD4CFC0)
val BioTextPrimary = Color(0xFF3A4038)
val BioTextSecondary = Color(0xFF6B6960)
val BioTextFaint = Color(0xFF9B988E)

// --- Тёмная версия биофильной темы (вечер/ночь) ---
val BioDarkBgBase = Color(0xFF2A2420)        // тёплый графит
val BioDarkBgSurface = Color(0xFF332D28)
val BioDarkBgSurfaceHigh = Color(0xFF3D3630)
val BioDarkBgGlass = Color(0xFF353029)
val BioDarkBgStroke = Color(0xFF4A4036)
val BioDarkTextPrimary = Color(0xFFE8D8C0)  // тёплое молоко
val BioDarkTextSecondary = Color(0xFFB0A698)
val BioDarkTextFaint = Color(0xFF807665)

// ════════════════════════════════════════════════════════════
//  ОБЩИЕ: Светлая тема (fallback)
// ════════════════════════════════════════════════════════════
val LightBg = Color(0xFFF5F5F7)
val LightSurface = Color(0xFFFFFFFF)
val LightTextPrimary = Color(0xFF1A1A20)
val LightTextSecondary = Color(0xFF6B6B7A)

// ════════════════════════════════════════════════════════════
//  Градиентные наборы (для живого фона)
// ════════════════════════════════════════════════════════════
data class GradientStop(
    val color: Color,
    val position: Float,
)

data class GradientPalette(
    val name: String,
    val stops: List<GradientStop>,
    val isDark: Boolean,
)

// Glass тема — градиенты
val GlassGradientPalette = GradientPalette(
    name = "Liquid Glass",
    stops = listOf(
        GradientStop(Color(0xFF0B0B0F), 0f),
        GradientStop(Color(0xFF12121A), 0.5f),
        GradientStop(Color(0xFF0E0E14), 1f),
    ),
    isDark = true,
)

// Биофильные градиенты по времени суток
val BioGradientMorning = GradientPalette(
    name = "Утренняя Листва",
    stops = listOf(
        GradientStop(Color(0xFFE8E8E0), 0f),
        GradientStop(Color(0xFFDDE8D4), 0.4f),
        GradientStop(Color(0xFFE0DDD0), 1f),
    ),
    isDark = false,
)

val BioGradientDay = GradientPalette(
    name = "Персиковый Шёлк",
    stops = listOf(
        GradientStop(Color(0xFFF5E8D8), 0f),
        GradientStop(Color(0xFFF0D8D0), 0.5f),
        GradientStop(Color(0xFFEDE0D0), 1f),
    ),
    isDark = false,
)

val BioGradientEvening = GradientPalette(
    name = "Вечерний Туман",
    stops = listOf(
        GradientStop(Color(0xFF3A342C), 0f),
        GradientStop(Color(0xFF453830), 0.5f),
        GradientStop(Color(0xFF3A2D24), 1f),
    ),
    isDark = true,
)

val BioGradientNight = GradientPalette(
    name = "Лавандовая Дымка",
    stops = listOf(
        GradientStop(Color(0xFF1C1A24), 0f),
        GradientStop(Color(0xFF232030), 0.5f),
        GradientStop(Color(0xFF1A1820), 1f),
    ),
    isDark = true,
)
