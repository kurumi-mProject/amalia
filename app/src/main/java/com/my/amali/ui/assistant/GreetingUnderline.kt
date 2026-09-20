package com.my.amali.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    // Диапазон 0.35..1.0 — прежний. Он и есть та незаметность, о которой
    // речь: в нижней точке черта почти растворяется, в верхней — чуть
    // ярче фона, но всё ещё не бросается в глаза.
    val barAlpha = 0.35f + 0.65f * breath
    val accent = iconAccent()

    Box(
        modifier = modifier
            .size(width = 44.dp, height = 2.5.dp)
            .clip(RoundedCornerShape(Radius.chip))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = 0.95f * barAlpha),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f * barAlpha),
                    ),
                ),
            ),
    )
}
