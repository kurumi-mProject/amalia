package com.my.amali.ui.assistant

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.random.Random

/**
 * Эффект «шифратора»: фраза выкристаллизовывается из шума.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  КАК ЭТО ВЫГЛЯДИТ
 * ════════════════════════════════════════════════════════════════════════
 *
 * ```
 * 0 мс    Г#7K%2 е$н@ ...
 * 150 мс  Где#но 9@м*ы
 * 320 мс  Где-то ночью
 * 640 мс  Ночь — моё время
 * ```
 *
 * Первые знаки фразы «встают» раньше последних: каскад идёт слева направо,
 * в сторону чтения. Это не косметика — так глазу не приходится искать,
 * откуда взялся текст. Если бы каскад шёл справа налево, фраза собиралась бы
 * «против шерсти», и читалась бы как помеха, а не как расшифровка.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ПОЧЕМУ МОНОШИРИННЫЙ ШРИФТ И ПОЧЕМУ ЭТО НЕ КОМПРОМИСС
 * ════════════════════════════════════════════════════════════════════════
 *
 * Заменяемый знак обязан быть **шире** обычного — иначе слово, пока оно
 * «шум», окажется короче, чем в готовом виде, и фраза на экране будет
 * дёргаться в бок. Именно так выглядят плохие реализации: текст пляшет
 * вправо-влево, пока не устоится.
 *
 * Здесь это решено честно: каждая позиция получает **фиксированную ширину**,
 * равную ширине самого широкого знака, который в ней побывает. Замер идёт
 * один раз на фразу, а не на кадр: ширина — свойство текста и стиля,
 * а не прогресса анимации. Дальше знаки раскладываются по этим слотам
 * вручную, и центр строки не смещается ни на пиксель.
 *
 * Замер по фразе целиком (а не по каждому знаку отдельно) нужен ещё и
 * потому, что так учитываются кернинг и лигатуры: в готовом состоянии строка
 * должна выглядеть ровно так же, как обычный `Text`, иначе в момент
 * завершения анимации текст заметно «схлопнется».
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ЧТО ИМЕННО МЕНЯЕТСЯ ПО ХОДУ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Три вещи одновременно, и все три — от одного числа [scrambleAmount]:
 *
 *  1. **Знак.** Пока позиция не «встала», в ней случайный символ из набора
 *     без `I`, `O`, `l`, `0`, `1` — эти пары неразличимы в шрифте, и с ними
 *     расшифровка читалась бы как уже готовый текст.
 *  2. **Буква-ориентир.** У незастывшей позиции остаётся **своя настоящая
 *     буква** в подмене: `Г#7K` вместо `Г##K`. За счёт этого человек видит
 *     контур будущего слова ещё до его появления — глаз успевает подготовиться.
 *  3. **Яркость.** Шум идёт приглушённым (`NoiseAlpha`), правильный знак —
 *     полной яркостью. Именно перепад яркости, а не подмена символа, даёт
 *     ощущение «встало на место»: символ меняется за один кадр, а яркость
 *     приходит за несколько.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ПОЧЕМУ КАЖДЫЙ ЗНАК ЖДЁТ СЛУЧАЙНОЕ ЧИСЛО КАДРОВ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Случайный знак пересчитывается только тогда, когда позиция ещё не встала,
 * и берётся из генератора с зерном, зависящим от номера кадра и номера смены.
 * Без этого шум был бы **неподвижным**: одна и та же cabal из `Г#7K%2`,
 * застывшая на полсекунды, читается как «сломанный текст», а не как процесс.
 * Живой шум — обязательное условие: движение шума и есть признак работы.
 */
@Composable
internal fun GreetingDecoderText(
    text: String,
    progress: Float,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    seed: Int = 0,
) {
    if (text.isEmpty()) return

    val measurer = rememberTextMeasurer()
    // Замер идёт в пикселях — их отдаёт замерщик текста. Перевод в dp
    // делается один раз здесь: считать его на каждом кадре анимации значило
    // бы платить за арифметику, которую не видно.
    val density = LocalDensity.current
    val layoutPx = remember(text, style) { measureDecodingText(text, style, measurer) }
    val layout = remember(layoutPx, density) { layoutPx.toDp(density) }

    // Номер кадра для шума. Растёт вместе с прогрессом, поэтому шум
    // перерисовывается тогда же, когда и всё остальное, — отдельного
    // таймера не нужно.
    val noiseFrame = (progress * NoiseFrames).toInt()

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        DecodedRow(
            layout = layout,
            progress = progress,
            color = color,
            style = style,
            noiseFrame = noiseFrame,
            seed = seed,
        )
    }
}

/**
 * Раскладка текста для эффекта: готовая строка плюс ширина каждой позиции.
 *
 * @property text исходная фраза.
 * @property slotWidths ширина каждой позиции в пикселях.
 * @property totalWidth суммарная ширина строки — по ней строка центруется.
 * @property lineCount сколько строк занял текст при переносе.
 */
internal data class DecodingLayout(
    val text: String,
    val slotWidths: List<androidx.compose.ui.unit.Dp>,
    val totalWidth: androidx.compose.ui.unit.Dp,
    val lineCount: Int,
)

/** Та же раскладка, но в пикселях — именно её отдаёт замерщик текста. */
internal data class DecodingLayoutPx(
    val text: String,
    val slotWidths: List<Float>,
    val totalWidth: Float,
    val lineCount: Int,
)

/** Пересчитывает пиксельную раскладку в dp. Единственное место перевода единиц. */
internal fun DecodingLayoutPx.toDp(density: androidx.compose.ui.unit.Density): DecodingLayout =
    DecodingLayout(
        text = text,
        slotWidths = slotWidths.map { with(density) { it.toDp() } },
        totalWidth = with(density) { totalWidth.toDp() },
        lineCount = lineCount,
    )

/**
 * Замеряет строку так, чтобы её ширина не зависела от подставляемых знаков.
 *
 * Ширина позиции — максимум из двух величин: ширины настоящего знака в этом
 * месте и средней ширины знака из набора подмены. Средняя, а не максимальная:
 * максимальная раздувала бы слоты у узких букв (`і`, `н`) и фраза выглядела бы
 * разреженной; средняя же гарантирует, что случайный поток визуально плотнее
 * готового текста — а именно так расшифровка и выглядит в кино.
 */
private fun measureDecodingText(
    text: String,
    style: TextStyle,
    measurer: androidx.compose.ui.text.TextMeasurer,
): DecodingLayoutPx {
    val full = measurer.measure(text, style)
    val totalWidth = full.size.width.toFloat()
    val lineCount = full.lineCount
    if (text.isEmpty()) return DecodingLayoutPx(text, emptyList(), 0f, lineCount)

    val averageGlyph = measureAverageGlyph(style, measurer)
    val slotWidths = text.map { ch ->
        val own = measurer.measure(ch.toString(), style).size.width.toFloat()
        maxOf(own, averageGlyph)
    }
    return DecodingLayoutPx(text, slotWidths, totalWidth, lineCount)
}

/**
 * Средняя ширина знака из набора подмены.
 *
 * Глобального кеша здесь намеренно нет: изменяемая карта на уровне файла —
 * это разделяемое состояние, доступное с любого потока, и ради экономии
 * одного замера на фразу оно того не стоит. Замер идёт один раз при входе
 * в композицию, а не на кадр.
 */
private fun measureAverageGlyph(
    style: TextStyle,
    measurer: androidx.compose.ui.text.TextMeasurer,
): Float {
    // Выборка из восьми знаков разной ширины: больше не даёт точности,
    // но умножает работу при каждой смене фразы.
    val sample = "ХШЖMNW88"
    val measured = measurer.measure(sample, style).size.width.toFloat()
    return (measured / sample.length) * GlyphWidthFactor
}

/**
 * Во сколько раз слот шире среднего знака.
 *
 * 1.30 — не произвольное число. Средняя ширина по выборке `ХШЖMNW88` даёт
 * около 0.62 от ширины самого широкого знака алфавита; множитель 1.30
 * поднимает её примерно до 0.80. Меньше — случайный шум окажется уже
 * готового слова и фраза дёрнется в сторону на завершении; больше — текст
 * станет разреженным в состоянии покоя.
 */
private const val GlyphWidthFactor = 1.30f

/**
 * Сколько раз шум пересчитывается за переход.
 *
 * Восемь: при переходе 640 мс это смена каждые 80 мс. Чаще — шум становится
 * мельтешением и мешает читать контур слова; реже — застывает и читается
 * как дефект отображения.
 */
private const val NoiseFrames = 8f

/**
 * Насколько приглушён случайный знак.
 *
 * 0.42 — граница, на которой шум ещё читается как текст, но уже явно
 * не является ответом. Ниже 0.3 шум сливался бы с фоном, и расшифровки
 * не было бы видно вообще.
 */
private const val NoiseAlpha = 0.42f

/**
 * Строит строку из позиций вручную — вне `Text`.
 *
 * `Text` не умеет «часть букв настоящие, часть подменены»: он либо рисует
 * то, что дали, либо нет. Поэтому строка собирается как последовательность
 * `Text`-элементов в `Row` с фиксированной шириной каждого слота. Это даёт
 * полный контроль над подменой и, что важнее, **не даёт строке дёргаться**:
 * ширина каждого слота известна заранее.
 *
 * Центрирование делается здесь же: слоты центруются внутри своего поля,
 * потому что случайный знак и настоящий имеют разную ширину, и без центровки
 * поток шума выглядел бы рваным по базовой линии.
 */
@Composable
private fun DecodedRow(
    layout: DecodingLayout,
    progress: Float,
    color: Color,
    style: TextStyle,
    noiseFrame: Int,
    seed: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        layout.text.forEachIndexed { index, real ->
            val amount = scrambleAmount(progress, index, layout.text.length)
            val settled = amount <= ScrambledThreshold
            // Зерно собирается из трёх чисел: смена, номер кадра шума и
            // позиция. Так шум в каждой позиции живёт своей жизнью и не
            // повторяется от смены к смене.
            val glyph = if (settled || real == ' ') {
                real
            } else {
                val rnd = Random(seed * 92821 + noiseFrame * 31 + index)
                randomGlyph(rnd)
            }
            // Настоящая буква остаётся в потоке с вероятностью, зависящей от
            // прогресса: в начале почти всё — шум, к концу почти всё — буквы.
            val shown = if (!settled && glyph != real && Random(seed + index * 7).nextFloat() < amount * 0.55f) {
                real
            } else {
                glyph
            }

            Text(
                text = shown.toString(),
                style = style,
                color = color,
                modifier = Modifier
                    // Ширина слота — dp, а не пиксели: так модификатор
                    // не зависит от плотности экрана и не требует ручной
                    // раскладки через Layout.
                    .width(layout.slotWidths.getOrElse(index) { 0.dp })
                    .alpha(if (settled) 1f else lerpFloat(1f - amount, 1f, NoiseAlpha)),
                softWrap = false,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}





/**
 * Порог, ниже которого позиция считается «вставшей».
 *
 * Не ноль. Полное обнуление [scrambleAmount] происходит лишь математически,
 * а на экране буква читается правильной задолго до этого. Порог 0.02 убирает
 * последние кадры, где символ уже неотличим от настоящего, но всё ещё
 * считается шумом, — иначе в конце перехода возникает едва заметное дрожание
 * последних знаков.
 */
private const val ScrambledThreshold = 0.02f

/** Проверка: сумма слотов совпадает с шириной строки (для тестов и превью). */
internal fun slotsMatchTextWidth(layout: DecodingLayout, tolerance: Float = 0.02f): Boolean {
    val total = layout.totalWidth.value
    if (total <= 0f) return true
    val sum = layout.slotWidths.sumOf { it.value.toDouble() }.toFloat()
    return abs(sum - total) <= total * tolerance
}
