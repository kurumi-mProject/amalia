package com.my.amali.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Минималистичная тёмная палитра Амалии.
 * Однотонная база + один акцент (приглушённый фиолет).
 * Без неоновой радуги. Спокойный, дорогой, стабильный.
 */

// --- Единый акцент ---
val AccentDim = Color(0xFF7C6FE0)        // приглушённый фиолет — основной акцент
val AccentSoft = Color(0xFF9388F5)      // чуть светлее для тонких моментов
val AccentGlow = Color(0xFF6D5FEF)      // для индикатора активности (точка пульса)

// --- Состояния (тоже приглушённые, не кислотные) ---
val StateListening = Color(0xFF7C6FE0)
val StateThinking = Color(0xFF8B8FB0)
val StateSpeaking = Color(0xFF9388F5)
val StateError = Color(0xFFB06565)

// --- Фоны: глубокий тёмный с лёгким тёплым уклоном ---
val BgBase = Color(0xFF0E0E12)          // основной фон — почти чёрный, не синий
val BgSurface = Color(0xFF16161C)       // карточки/поверхности
val BgSurfaceHigh = Color(0xFF1E1E26)   // приподнятые элементы
val BgGlass = Color(0xFF1A1A22)        // для glass-эффекта (полупрозрачный слой)
val BgStroke = Color(0xFF2A2A33)       // тонкие границы

// --- Текст ---
val TextPrimary = Color(0xFFEDEDF0)     // основной — почти белый, мягкий
val TextSecondary = Color(0xFF9B9BA8)  // вторичный — серый
val TextFaint = Color(0xFF5E5E6E)      // блёклый — timestamps, hints

// --- Светлая тема (будущее) ---
val LightBg = Color(0xFFF5F5F7)
val LightSurface = Color(0xFFFFFFFF)
val LightTextPrimary = Color(0xFF1A1A20)
val LightTextSecondary = Color(0xFF6B6B7A)
