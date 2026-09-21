package com.my.amali.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.iconAccent

/**
 * Черта под фразой приветствия.
 *
 * Это не украшение. Работает **эффект изоляции**: слабый, но различимый
 * маркер рядом с главным объектом удерживает на нём взгляд, не борясь
 * с ним за внимание. Черта обозначает, что текст «стоит на чём-то»,
 * а не висит в пустоте — и именно поэтому она должна быть почти незаметной:
 * если её видно сразу, она превращается в подчёркивание и начинает спорить
 * с текстом.
 *
 * Геометрия и цвета оставлены такими, какими были до правок: 44 × 2.5 dp,
 * скругление по [Radius.chip], градиент от основного акцента к вторичному.
 * Менять их не за чем — черта уже работала.
 *
 * @param breath фаза дыхания экрана 0..1, общая с волной и лампой. Свой
 *   период у черты был бы четвёртым независимым ритмом, а четыре ритма
 *   глаз читает как шум, а не как покой.
 */
@Composable
internal fun GreetingUnderline(breath: Float, modifier: Modifier = Modifier) {
    // 0.5..1.0: в нижней точке черта не исчезает, потому что исчезающая линия
    // читается как мигание светодиода индикатора. Тонкая пульсация без
    // провала в ноль — это дыхание, а не сигнал.
    val barAlpha = 0.5f + 0.5f * breath
    val accent = iconAccent()

    Box(
        modifier = modifier
            .size(width = UnderlineWidth, height = UnderlineThickness)
            .clip(RoundedCornerShape(Radius.chip))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        accent.copy(alpha = 0.55f * barAlpha),
                        accent.copy(alpha = 0.9f * barAlpha),
                        accent.copy(alpha = 0.55f * barAlpha),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

/** Ширина черты: заметно уже фразы, чтобы читаться маркером, а не подчёркиванием. */
private val UnderlineWidth = 56.dp

/** Толщина черты: тоньше полутора пикселей на плотных экранах пропадает. */
private val UnderlineThickness = 2.dp
