package com.my.amali.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ════════════════════════════════════════════════════════════
//  Скругления — единая система радиусов
//  Принцип: мягкие, не резкие, одинаковая сетка
// ════════════════════════════════════════════════════════════

val AmaliaShapes = Shapes(
    // Extra small — чипы, теги, маленькие элементы
    extraSmall = RoundedCornerShape(8.dp),

    // Small — текстовые поля, маленькие кнопки
    small = RoundedCornerShape(12.dp),

    // Medium — карточки, диалоги (основной радиус)
    medium = RoundedCornerShape(20.dp),

    // Large — крупные карточки, панели
    large = RoundedCornerShape(28.dp),

    // Extra large — полноэкранные листы, hero-блоки
    extraLarge = RoundedCornerShape(36.dp),
)

// Glass-специфичные скругления (чуть больше для "жидкого" ощущения)
val GlassShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(38.dp),
)

// Биофильные скругления (ещё мягче, более органичные)
val BioShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(40.dp),
)
