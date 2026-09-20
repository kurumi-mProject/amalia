package com.my.amali.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Эффект «одометра»: буквы подкручиваются на нужные, как цифры в счётчике.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ЧЕМ ЭТО ОТЛИЧАЕТСЯ ОТ ПРОСТОЙ ПРОКРУТКИ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Простейший «одометр» рисуют так: сверху старая строка, снизу новая,
 * вся полоса сдвигается вверх — и всё. Это не одометр, а шторка: буквы
 * едут с одной скоростью, и вместо механизма получается прокрутка.
 *
 * Настоящий счётчик тем и характерен, что **каждый барабан крутится
 * по-своему**: на одометре машины единицы мельтешат, пока десятки проходят
 * один шаг. Поэтому здесь каждая позиция — отдельный барабан со своим числом
 * оборотов:
 *
 * ```
 *  позиция 0  (Д): 1 оборот  — почти не двигалась
 *  позиция 5  (й): 3 оборота — подтянулась издалека
 *  позиция 11 (я): 2 оборота — успела вернуться
 * ```
 *
 * Число оборотов берётся из хеша пары «позиция + номер смены», поэтому
 * у каждой смены свой узор. С постоянными оборотами третья смена выглядела бы
 * в точности как первая, и приём за три показа стал бы обоиной.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  КАК УСТРОЕН ОДИН БАРАБАН
 * ════════════════════════════════════════════════════════════════════════
 *
 * ```text
 *        ┌─────────┐  ← отсечение по высоте строки
 *    ▓   │    З    │  уходящий знак уезжает вверх
 *    ▓   │    Х    │  ← мелькающие знаки между ними
 *    ▓   │    Ю    │
 *        │    Я    │  приходящий выезжает снизу
 *        └─────────┘
 * ```
 *
 * В окне с отсечением лежит вертикальный столбец из нескольких знаков,
 * сдвинутый на текущее положение барабана. Знаки вне окна не видны — это
 * и превращает сдвинутый столбец в механизм: без отсечения буквы просто
 * ехали бы поверх соседей.
 *
 * Пробел барабана не получает. Иначе на месте пробела крутился бы мусор,
 * и между словами мелькали бы случайные символы — слово перестало бы
 * читаться как слово.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ПОЧЕМУ БАРАБАНЫ ОСТАНАВЛИВАЮТСЯ ПО ОЧЕРЕДИ, А НЕ ВСЕ РАЗОМ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Хочется поставить общее замедление в конце — так «дороже». Но замедление
 * одинаково для всех: все барабаны остановятся в один кадр, и ряд снова
 * превратится в шторку. Механический счётчик останавливается **по очереди**,
 * и это единственное, что делает движение живым.
 *
 * Ровно это даёт [charProgress] с каскадом: у каждой следующей позиции
 * прокрутка начинается позже. Скорость при этом линейная — как у настоящего
 * механизма, который не тормозит, а просто доходит до упора.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ЦЕНА
 * ════════════════════════════════════════════════════════════════════════
 *
 * Эффект дороже шифратора: каждая позиция — своё окно с отсечением и
 * несколькими потомками вместо одного `Text`. На фразе приветствия (до
 * 18 знаков, раз в 30 секунд) это незаметно. Тащить его в список ответов
 * нельзя: там сотни знаков, и цена станет видимой.
 */
@Composable
internal fun GreetingOdometerText(
    text: String,
    progress: Float,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    seed: Int = 0,
) {
    if (text.isEmpty()) return

    val measurer = rememberTextMeasurer()
    // Метрики считаются в ПИКСЕЛЯХ: замерщик текста отдаёт пиксели, и
    // пересчитывать их в dp на каждом кадре анимации — трата на ровном месте.
    // Перевод делается один раз здесь, при входе в композицию.
    val density = LocalDensity.current
    val metricsPx = remember(text, style, density) { odometerMetrics(text, style, measurer) }
    val metrics = remember(metricsPx, density) {
        OdometerMetrics(
            slotWidth = with(density) { metricsPx.slotWidth.toDp() },
            lineHeight = with(density) { metricsPx.lineHeight.toDp() },
        )
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                text.forEachIndexed { index, ch ->
                    if (ch == ' ') {
                        // Пробел — просто пустое место той же ширины, что
                        // слот барабана. Так строка не разъезжается.
                        Box(Modifier.width(metrics.slotWidth))
                    } else {
                        OdometerDrum(
                            char = ch,
                            index = index,
                            length = text.length,
                            metrics = metrics,
                            progress = progress,
                            color = color,
                            style = style,
                            seed = seed,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Метрики строки для одометра.
 *
 * @property slotWidth ширина одной позиции — общая для всей строки.
 * @property lineHeight высота окна барабана.
 */
internal data class OdometerMetrics(
    val slotWidth: androidx.compose.ui.unit.Dp,
    val lineHeight: androidx.compose.ui.unit.Dp,
)

/**
 * Метрики в пикселях — то, что отдаёт замерщик.
 *
 * Отдельный тип, чтобы не путать единицы: замер приходит в пикселях, а
 * модификаторы Compose требуют dp. Ошибка здесь дала бы слот высотой
 * в 3 пикселя на экране с плотностью 3 — то есть буквы в три ряда.
 */
internal data class OdometerMetricsPx(
    val slotWidth: Float,
    val lineHeight: Float,
)

/**
 * Считает геометрию слота.
 *
 * Ширина — максимум по знакам фразы плюс запас. Узкий слот обрезал бы широкие
 * буквы (`Ж`, `Ш`, `W`, `M`), и вместо буквы была бы её половина. Общая ширина
 * на все позиции, а не своя у каждой, — сознательно: барабаны в механизме
 * одинаковые, и это читается как счётчик, а не как рваный ряд.
 *
 * Высота берётся по самой высокой паре знаков в фразе: у некоторых глифов
 * (например, с диакритикой) высота больше базовой, и окно по средней строке
 * срезало бы им верх.
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
        val w = m.size.width.toFloat()
        val h = m.size.height.toFloat()
        if (w > widest) widest = w
        if (h > tallest) tallest = h
    }
    return OdometerMetricsPx(
        slotWidth = widest * SlotWidthFactor,
        lineHeight = tallest,
    )
}

/** Запас к ширине слота: 6% хватает на боковые выносы курсивных глифов. */
private const val SlotWidthFactor = 1.06f

/**
 * Один барабан: знак, прокрученный по вертикали.
 *
 * Столбец знаков сдвигается целиком, а окно отсекает всё, что вышло за его
 * границы. Сдвиг считается в пикселях: знак должен встать ровно на базовую
 * линию, а перевод в dp на каждом кадре дал бы субпиксельное дрожание.
 */
@Composable
private fun OdometerDrum(
    char: Char,
    index: Int,
    length: Int,
    metrics: OdometerMetrics,
    progress: Float,
    color: Color,
    style: TextStyle,
    seed: Int,
) {
    val p = charProgress(progress, index, length, spread = DrumSpread)
    val turns = drumTurns(index, seed)
    val settled = p >= 1f
    val lineHeightPx = with(LocalDensity.current) { metrics.lineHeight.toPx() }

    // Узор барабана: приходящий знак и мелькающие между ними. Считается
    // из хеша, а не из живого генератора: столбец перерисовывается десятки
    // раз за анимацию, и случайные знаки на каждый кадр дали бы мерцание
    // вместо вращения.
    val column = remember(index, seed, turns, char) {
        buildDrumColumn(char = char, index = index, seed = seed, turns = turns)
    }

    // Полный путь: (turns + 1) слотов, потому что столбец начинается
    // с приходящего знака и заканчивается им же — барабан делает целое
    // число оборотов и возвращается к той же букве.
    val travel = p * (turns + 1) * lineHeightPx
    val direction = drumDirection(index, seed)

    Box(
        modifier = Modifier
            .width(metrics.slotWidth)
            .height(metrics.lineHeight)
            .clip(DrumClipShape),
        contentAlignment = Alignment.Center,
    ) {
        if (settled) {
            // Барабан встал: рисуем только настоящий знак. Иначе в последнем
            // кадре поверх него мелькал бы сосед из столбца.
            Text(
                text = char.toString(),
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(metrics.slotWidth),
            )
        } else {
            // Само смещение столбца. `Modifier.offset` с лямбдой считается
            // на фазе размещения, а не композиции: при сдвиге на каждом кадре
            // это единственный способ не пересобирать поддерево целиком.
            // Округление до целых пикселей обязательно — при дробном сдвиге
            // сглаживание размазывает глифы, и мелькание читается как мыло,
            // а не как вращение.
            Column(
                modifier = Modifier.offset { IntOffset(x = 0, y = -(direction * travel).roundToInt()) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                column.forEach { drumChar ->
                    Text(
                        text = drumChar.toString(),
                        style = style,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(metrics.slotWidth)
                            .height(metrics.lineHeight)
                            // Мелькающие знаки идут вполсилы: настоящая буква
                            // в конце пути должна выделяться на их фоне,
                            // иначе не читается, что барабан «встал».
                            .alpha(if (drumChar == char) 1f else PassingAlpha),
                    )
                }
            }
        }
    }
}

/**
 * Столбец барабана: приходящий знак и мелькающие между оборотами.
 *
 * Заканчивается тем же знаком, что и начинается, — барабан делает целое
 * число оборотов. Иначе последний кадр прыгал бы на первую букву, и вместо
 * остановки получался бы скачок.
 */
private fun buildDrumColumn(char: Char, index: Int, seed: Int, turns: Int): List<Char> {
    val rnd = Random(index * 104729 + seed * 8191 + turns)
    return buildList {
        add(char)
        repeat(turns) { add(randomGlyph(rnd)) }
        add(char)
    }
}

/**
 * Сколько оборотов делает барабан.
 *
 * 1..4. Один — минимум движения, больше четырёх перестаёт читаться как
 * механизм и превращается в мельтешение, в котором не разглядеть, куда
 * крутится.
 */
private fun drumTurns(index: Int, seed: Int): Int {
    val h = (index * 73856093) xor (seed * 19349663)
    return (h and 0x7fffffff) % 4 + 1
}

/**
 * Направление вращения барабана.
 *
 * Часть колёс едет вверх, часть вниз. В настоящем механизме так и есть —
 * колёса вращаются независимо; при общем направлении ряд читается как
 * одна прокрутка, а не как счётчик.
 */
private fun drumDirection(index: Int, seed: Int): Int =
    if (((index * 83492791) xor seed) and 1 == 0) 1 else -1

/** Скругление окна барабана: заметно мягкое, но не круглое. */
private val DrumClipShape = RoundedCornerShape(
    topStartPercent = 12,
    topEndPercent = 12,
    bottomStartPercent = 12,
    bottomEndPercent = 12,
)

/**
 * Насколько приглушены мелькающие знаки.
 *
 * 0.45. При единице вращение читалось бы как мешанина равноправных букв;
 * при 0.2 мелькания не видно вовсе, и барабан выглядел бы просто сдвигом.
 */
private const val PassingAlpha = 0.45f

/**
 * Каскад одометра растянут сильнее, чем у шифратора.
 *
 * 0.72 против 0.55: барабаны обязаны останавливаться ощутимо по очереди.
 * С каскадом шифратора последние позиции вставали бы одновременно с первыми,
 * и ряд превращался бы обратно в шторку.
 */
private const val DrumSpread = 0.72f

/** Встал ли барабан этой позиции — для тестов и превью. */
internal fun odometerSettled(progress: Float, index: Int, length: Int): Boolean =
    charProgress(progress, index, length, spread = DrumSpread) >= 1f

/** Длительность анимации одометра, миллисекунды. */
internal const val ODOMETER_TRANSITION_MS = 820
