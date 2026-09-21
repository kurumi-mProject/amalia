package com.my.amali.ui.assistant

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    val density = LocalDensity.current

    // Разбивка на строки — замером, а не догадкой: строк в приветствии не
    // больше двух (см. [GreetingOdometerLines]), и позиции считаются по
    // строке, в которой знак реально стоит.
    val maxWidthPx = remember(density) { GreetingOdometerWidthDp * density.density }
    val lines = remember(text, style, maxWidthPx) {
        odometerLines(text, style, measurer, maxWidthPx)
    }

    // Метрики одного окошка. Высота — интерлиньяж стиля: это ровно шаг ленты
    // барабана, и ровно столько места нужно, чтобы пропустить мимо соседа.
    // По вертикали геометрия не подстраивается ни под кого: окно барабана
    // обязано быть одного размера у всех позиций, иначе строка «поедет».
    val lineHeightPx = remember(style) { with(density) { style.lineHeight.toPx() } }
    val cellHeight = remember(lineHeightPx, density) { with(density) { lineHeightPx.toDp() } }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        lines.forEach { line ->
            // Ширина окна — по самому широкому глифу строки плюс запас.
            // Общая на все позиции строки, а не своя у каждой: барабаны в
            // механизме одинаковые, и это читается как счётчик, а не как
            // рваный ряд.
            val slotWidthPx = remember(line, style) { odometerSlotWidth(line, style, measurer) }
            val cellWidth = remember(slotWidthPx, density) { with(density) { slotWidthPx.toDp() } }

            Row(verticalAlignment = Alignment.CenterVertically) {
                line.forEach { ch ->
                    if (ch == ' ') {
                        // Пробел — пустое место той же ширины, что окошко. Так
                        // строка не разъезжается на словах.
                        Box(Modifier.width(cellWidth))
                    } else {
                        OdometerDrum(
                            char = ch,
                            style = style,
                            cellWidth = cellWidth,
                            cellHeight = cellHeight,
                            progress = progress,
                            color = color,
                            measurer = measurer,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Как раскладывается фраза по строкам для одометра.
 *
 * Прошлая версия строила **один** `Row` на всю фразу. Пока фразы были
 * короткими, это сходило с рук; на «Кофе, потом всё остальное» (24 знака)
 * одометр выходил за пределы экрана целиком — в превью это выглядело как
 * текст, выехавший за правый край. Здесь строка разбивается тем же способом,
 * что обычный текст: по словам, и не больше [GreetingOdometerLines] строк.
 */
private fun odometerLines(
    text: String,
    style: TextStyle,
    measurer: TextMeasurer,
    widthLimitPx: Float,
): List<List<Char>> {
    val full = listOf(text.toList())
    if (text.length <= OdometerBreakThreshold) return full

    val words = text.split(' ')
    if (words.size < 2) return full

    val limit = widthLimitPx * OdometerBreakShare
    var current = StringBuilder()
    val lines = mutableListOf<List<Char>>()

    words.forEach { word ->
        val candidate = if (current.isEmpty()) word else "${current} $word"
        val width = measurer.measure(candidate, style, maxLines = 1, softWrap = false)
            .size.width.toFloat()
        if (width <= limit || current.isEmpty()) {
            current = StringBuilder(candidate)
        } else {
            lines += current.toString().toList()
            current = StringBuilder(word)
        }
    }
    if (current.isNotEmpty()) lines += current.toString().toList()

    // Третью строку не показываем: приветствие не бывает длиннее двух строк,
    // а лишняя строка — это выехавший за экран текст.
    return if (lines.size <= GreetingOdometerLines) lines else full
}

/**
 * Ширина окна барабана в пикселях.
 *
 * Считается по самому широкому глифу строки плюс запас. Максимум, а не
 * среднее: широкий настоящий знак (`Ш`, `Ж`, `W`) должен получить своё место,
 * иначе его обрежет собственное окно.
 */
private fun odometerSlotWidth(
    line: List<Char>,
    style: TextStyle,
    measurer: TextMeasurer,
): Float {
    var widest = 1f
    line.forEach { ch ->
        if (ch == ' ') return@forEach
        val w = measurer.measure(ch.toString(), style).size.width.toFloat()
        if (w > widest) widest = w
    }
    return widest * SlotWidthFactor
}

/**
 * Запас к ширине окошка.
 *
 * 1.55, а не 1.06, как было раньше. Запас обязан покрывать не только
 * настоящий знак, но и **шум подмены**: пусть запасной глиф («З», «Ю», «3»)
 * окажется шире настоящей буквы — тогда без запаса на кадре прокрутки он
 * вылезал бы за своё окно и налезал на соседний барабан. Ширина строки от
 * этого не страдает: окна считаются заранее и складываются в фиксированную
 * ширину, которая не зависит от того, что сейчас нарисовано внутри.
 */
private const val SlotWidthFactor = 1.55f

/**
 * Один барабан: окошко с отсечением, внутри — лента из двух глифов.
 *
 * Рисование идёт вручную, потому что оба глифа обязаны находиться в одной
 * системе координат и обрезаться по одним границам. Два отдельных `Text`
 * с `offset` дали бы то же самое, но пересобирали бы поддерево на кадре.
 *
 * @param char настоящая буква этой позиции. С неё начинается отсчёт пути:
 *   барабан уезжает НА неё, а не «куда-то в сторону» — прошлая версия пускала
 *   ленту наоборот, и готовый текст сначала пропадал из окна, а потом выезжал
 *   обратно. Читалось это как рывок, а не как вращение.
 */
@Composable
private fun OdometerDrum(
    char: Char,
    style: TextStyle,
    cellWidth: Dp,
    cellHeight: Dp,
    progress: Float,
    color: Color,
    measurer: TextMeasurer,
) {
    // Глифы измеряются заранее: на кадре измерения не должно быть вовсе.
    val real = remember(char, style, measurer) { measurer.measure(char.toString(), style) }

    // Соседний знак — тот, что «выезжает» на место настоящего. Он детерминирован
    // парой «знак + позиция»: столбец перерисовывается десятки раз за анимацию,
    // и случайный знак на каждый кадр дал бы мерцание вместо вращения.
    val passing = remember(char, style, measurer) {
        val pick = NoiseAlphabet[(char.code * 31 + char.code / 3).mod(NoiseAlphabet.length)]
        measurer.measure(pick.toString(), style)
    }

    // Путь барабана в долях шага. Едет от одного оборота с четвертью к нулю:
    // скорость приходит из кривой сглаживания, поэтому лента не тормозит у
    // самого края окна, а садится на букву в тот момент, когда уже почти
    // перестала двигаться.
    var path by remember(char) { mutableFloatStateOf(DrumTravel) }
    LaunchedEffect(progress, char) {
        val shape = easeOdometer(progress.coerceIn(0f, 1f))
        path = DrumTravel * (1f - shape)
        // Кадры приходят сверху: анимация внешняя, а здесь только её
        // отображение. Так эффект не заводит собственный таймер и не
        // «дорисовывает» хвост, пока идёт что-то другое.
    }

    Canvas(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .clipToBounds(),
    ) {
        val height = size.height
        // Округление сдвига до целых пикселей обязательно: при дробном сдвиге
        // сглаживание размазывает глифы, и прокрутка читается как мыло.
        val shift = (path * height).toInt().toFloat()
        val centerX = size.width / 2f

        drawGlyph(passing, centerX, height - shift + CenteringOffset, color.copy(alpha = NoiseAlpha))
        drawGlyph(real, centerX, -shift + CenteringOffset, color)
    }
}

/**
 * Кривая барабана.
 *
 * Квадратичное сглаживание вместо линейного: движение стартует сразу и
 * заметно тормозит к концу — так лента встаёт на букву, а не проезжает её.
 * Линейный вариант давал «конвейер»: буквы ехали ровно и останавливались
 * рывком в последнем кадре.
 */
private fun easeOdometer(t: Float): Float = 1f - (1f - t) * (1f - t)

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
 * Кириллические буквы и цифры, без `I`, `O`, `l`, `0`, `1` — неразличимые
 * в шрифте пары. Набор обязан быть **той же ширины**, что и настоящий текст:
 * широкий `#` или `@` раздвинул бы окно и вернул дрожание строки.
 */
private const val NoiseAlphabet = "АБВГДЕЖЗИКЛМНОПРСТУФХЦЧШЩЭЮЯ23456789"

/** Насколько приглушён выезжающий знак: настоящая буква обязана выделяться. */
private const val NoiseAlpha = 0.34f

/**
 * Полный путь барабана в долях шага ленты.
 *
 * 1.25 — это один оборот плюс четверть. Больше похоже на мельтешение,
 * меньше — на простой сдвиг шторки: барабан обязан «пройти через значения».
 * Четверть гарантирует, что выезжающий знак не окажется по случайности той
 * же буквой, что и настоящая.
 */
private const val DrumTravel = 1.25f

/** Сколько знаков во фразе считается нормой и не требует разбивки на строки. */
private const val OdometerBreakThreshold = 18

/** Доля ширины экрана, после которой фраза переносится на вторую строку. */
private const val OdometerBreakShare = 0.72f

/** Больше двух строк приветствие не занимает. */
private const val GreetingOdometerLines = 2

/**
 * Ширина, по которой раскладывается фраза приветствия.
 *
 * 320dp — нижняя граница поддерживаемых телефонов (360dp) минус боковые
 * отступы экрана. Взято константой в dp и переведено в пиксели через
 * плотность экрана: разбивка считается до первого замера, поэтому опереться
 * на фактическую ширину блока нельзя.
 */
private val GreetingOdometerWidthDp = 320f
