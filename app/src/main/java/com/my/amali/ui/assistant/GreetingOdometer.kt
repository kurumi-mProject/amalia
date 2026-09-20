package com.my.amali.ui.assistant

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Эффект «одометра»: буквы подкручиваются на нужные, как цифры в счётчике.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ПОЧЕМУ CANVAS, А НЕ СТОЛБЕЦ ИЗ `Text`
 * ════════════════════════════════════════════════════════════════════════
 *
 * Первая версия этого файла собирала каждую позицию как `Column` из
 * нескольких `Text` с вертикальным сдвигом. Работало — и было грубой ошибкой:
 * на фразу из пятнадцати знаков получалось пятнадцать поддеревьев, каждое
 * со своим измерением и размещением, и всё это пересобиралось на кадре
 * анимации.
 *
 * Здесь каждое окошко — это [Canvas]. Внутри `draw` рисуются **два** глифа:
 * уходящий и приходящий. `drawText` принимает заранее измеренный
 * [TextLayoutResult], поэтому на кадре не происходит ни измерения, ни
 * композиции: перерисовывается только графика. Это ровно тот приём, на
 * котором стоит библиотека compose-number-flow, и он же единственный
 * правильный для покадровой прокрутки.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  БЕСКОНЕЧНАЯ ЛЕНТА ВМЕСТО КОНЕЧНОГО СТОЛБЦА
 * ════════════════════════════════════════════════════════════════════════
 *
 * Позиция на ленте не сворачивается в 0..N — она просто накапливается:
 * `target += steps`, где `steps` — сколько знаков нужно пройти. Поэтому
 * «9 → 0» — не особый случай и не прыжок, а один обычный шаг, как «3 → 4».
 *
 * Первая версия строила конечный столбец `[знак, шум, знак]` и делала
 * целое число оборотов. Это давало шов на каждом переходе и требовало
 * отдельной возни, чтобы последний кадр не дёрнулся. Лента убирает и то,
 * и другое.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  КАК ЧИТАЕТСЯ ПОЗИЦИЯ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Анимация — обычное число: дробная часть задаёт, на сколько именно
 * сдвинута лента, целая — какой знак сейчас на месте. Кадровое состояние
 * живёт в [mutableFloatStateOf] и читается **внутри** `draw`-лямбды, поэтому
 * изменение числа вызывает только перерисовку, но не рекомпозицию.
 *
 * @param text фраза, которую показываем.
 * @param progress прогресс перехода 0..1. Один на все позиции: рассинхрон
 *   возможен на уровне сдвига, а не на уровне «кто первый начал».
 * @param color цвет глифов.
 * @param style стиль текста — тот же, что у остального приветствия.
 */
@Composable
internal fun GreetingOdometerText(
    text: String,
    progress: Float,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    if (text.isEmpty()) return

    val measurer = rememberTextMeasurer()

    // Метрики считаются в пикселях — их отдаёт замерщик. Перевод в dp один
    // раз при входе в композицию: считать его на кадре незачем.
    val density = LocalDensity.current
    val metricsPx = remember(text, style) { odometerMetrics(text, style, measurer) }
    val cellWidth = remember(metricsPx, density) { with(density) { metricsPx.slotWidth.toDp() } }
    val cellHeight = remember(metricsPx, density) { with(density) { metricsPx.lineHeight.toDp() } }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            text.forEachIndexed { index, ch ->
                if (ch == ' ') {
                    // Пробел — пустое место той же ширины, что окошко. Так
                    // строка не разъезжается на словах.
                    Box(Modifier.width(cellWidth))
                } else {
                    OdometerDrum(
                        char = ch,
                        index = index,
                        length = text.length,
                        metrics = metricsPx,
                        cellWidth = cellWidth,
                        cellHeight = cellHeight,
                        progress = progress,
                        color = color,
                        style = style,
                        measurer = measurer,
                    )
                }
            }
        }
    }
}

/**
 * Метрики строки в пикселях.
 *
 * @property slotWidth ширина одного окошка — общая для всех позиций.
 * @property lineHeight высота окошка: по ней же считается шаг ленты.
 */
internal data class OdometerMetricsPx(
    val slotWidth: Float,
    val lineHeight: Float,
)

/**
 * Считает геометрию окошка.
 *
 * Ширина — максимум по знакам фразы плюс небольшой запас: узкое окошко
 * обрезало бы широкие буквы (`Ш`, `Ж`, `W`), и вместо буквы была бы её
 * половина. Общая ширина на все позиции, а не своя у каждой, — сознательно:
 * барабаны в механизме одинаковые, и это читается как счётчик, а не как
 * рваный ряд.
 */
internal fun odometerMetrics(
    text: String,
    style: TextStyle,
    measurer: androidx.compose.ui.text.TextMeasurer,
): OdometerMetricsPx {
    var widest = 1f
    var tallest = 1f
    text.forEach { ch ->
        if (ch == ' ') return@forEach
        val m = measurer.measure(ch.toString(), style)
        if (m.size.width > widest) widest = m.size.width.toFloat()
        if (m.size.height > tallest) tallest = m.size.height.toFloat()
    }
    return OdometerMetricsPx(
        slotWidth = widest * SlotWidthFactor,
        lineHeight = tallest,
    )
}

/** Запас к ширине окошка: 6% хватает на боковые выносы курсивных глифов. */
private const val SlotWidthFactor = 1.06f

/**
 * Один барабан: окошко с отсечением, внутри — прокручиваемая лента глифов.
 *
 * Рисование идёт вручную, потому что оба глифа обязаны находиться в одной
 * системе координат и обрезаться по одним границам. Два отдельных `Text`
 * с `offset` дали бы то же самое, но пересобирали бы поддерево на кадре.
 */
@Composable
private fun OdometerDrum(
    char: Char,
    index: Int,
    length: Int,
    metrics: OdometerMetricsPx,
    cellWidth: Dp,
    cellHeight: Dp,
    progress: Float,
    color: Color,
    style: TextStyle,
    measurer: androidx.compose.ui.text.TextMeasurer,
) {
    // Глифы измеряются заранее: на кадре измерения не должно быть вовсе.
    val real = remember(char, style, measurer) { measurer.measure(char.toString(), style) }

    // Соседний знак — тот, что «выезжает» на место текущего. Он из набора
    // подмены: именно он даёт ощущение, что барабан проходит через значения.
    val noise = remember(char, style, measurer, index) {
        val alphabet = NoiseAlphabet
        val pick = alphabet[(char.code + index * 7).mod(alphabet.length)]
        measurer.measure(pick.toString(), style)
    }

    // Смещение ленты в долях высоты окошка. Хранится в Float-состоянии и
    // читается внутри draw — это и есть весь механизм без рекомпозиций.
    var travel by remember(char) { mutableFloatStateOf(0f) }
    LaunchedEffect(progress, char) {
        // Кадры идут от progress: анимация внешняя, а здесь только её
        // отображение. Так эффект не заводит собственный таймер, и смена
        // фразы на полпути не оставляет висеть старую прокрутку.
        while (true) {
            travel = progress.coerceIn(0f, 1f)
            if (travel >= 1f) break
            delay(FrameMs)
        }
        travel = 1f
    }

    Canvas(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .clipToBounds(),
    ) {
        val height = size.height
        // Сдвиг: лента едет вверх, пока не встанет на место. Округление до
        // целых пикселей обязательно — при дробном сдвиге сглаживание
        // размазывает глифы, и прокрутка читается как мыло.
        val shift = (travel * height).toInt().toFloat()
        val centerX = size.width / 2f

        drawGlyph(real, centerX, -shift + CenteringOffset, color)
        drawGlyph(noise, centerX, height - shift + CenteringOffset, color.copy(alpha = NoiseAlpha))
    }
}

/**
 * Рисует глиф по центру окошка.
 *
 * Смещение по вертикали задаётся снаружи: `drawText` принимает левый верхний
 * угол, а нам нужно положение в ленте, поэтому вертикальный отсчёт ведёт
 * вызывающий.
 */
private fun DrawScope.drawGlyph(
    glyph: TextLayoutResult,
    centerX: Float,
    y: Float,
    color: Color,
) {
    drawText(
        textLayoutResult = glyph,
        color = color,
        topLeft = Offset(centerX - glyph.size.width / 2f, y),
    )
}

/**
 * Вертикальная поправка при центрировании глифа в окошке.
 *
 * `TextLayoutResult.size` больше видимой высоты буквы: в него входит место
 * под верхние и нижние выносные элементы. Ноль здесь означает «рисовать от
 * верхнего края размера», что и совпадает с положением глифа в измеренном
 * боксе.
 */
private const val CenteringOffset = 0f

/**
 * Набор знаков, через которые проходит барабан.
 *
 * Буквы и цифры, без `I`, `O`, `l`, `0`, `1` — неразличимые в шрифте пары.
 * Знаки пунктуации здесь не нужны: барабан подкручивается между буквами,
 * и `#` посреди слова читался бы как сбой, а не как прокрутка.
 */
private const val NoiseAlphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

/** Насколько приглушён выезжающий знак: настоящая буква обязана выделяться. */
private const val NoiseAlpha = 0.30f

/** Шаг перерисовки анимации. */
private const val FrameMs = 16L
