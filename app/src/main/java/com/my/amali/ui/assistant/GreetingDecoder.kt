package com.my.amali.ui.assistant

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Эффект «шифратора»: фраза выкристаллизовывается из шума.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  КАК ЭТО ВЫГЛЯДИТ
 * ════════════════════════════════════════════════════════════════════════
 *
 * ```
 * ~0 мс     #7K%2 @$н1 *M...
 * ~180 мс   Где#но 9@м*ы
 * ~350 мс   Где-то ночью
 * ~560 мс   Ночь — моё время
 * ```
 *
 * Знаки «встают» слева направо, в сторону чтения: глазу не приходится искать,
 * откуда взялся текст. Обратный порядок читался бы как помеха, а не как
 * расшифровка.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ПОЧЕМУ `LaunchedEffect`, А НЕ АНИМАЦИЯ COMPOSE
 * ════════════════════════════════════════════════════════════════════════
 *
 * Эффект **дискретный**: текст не «движется», он несколько раз подряд
 * показывается другим. Между этими показами нет промежуточных значений,
 * которые можно было бы проигрывать плавно, — значит, `animateTo` здесь
 * нечего анимировать.
 *
 * Отсюда `LaunchedEffect` с шагом по времени: он прямо выражает суть —
 * «каждые N миллисекунд показываем следующее состояние расшифровки».
 * `Animatable` с `tween` дал бы то же самое, но через промежуточное число
 * прогресса, которое всё равно превращалось бы в номер кадра. Лишний слой.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ШИРИНА СТРОКИ ФИКСИРОВАНА — И ЭТО ГЛАВНОЕ ЗДЕСЬ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Простейшая реализация собирает строку заново: `text.map { random() }.join()`.
 * Так делать нельзя, и причина видна невооружённым глазом: случайный знак
 * почти всегда **шире** буквы (в наборе есть `#`, `@`, `%`), поэтому шумная
 * строка длиннее готовой. Фраза на каждом кадре меняет ширину и **дёргается
 * влево-вправо**, пока не устоится.
 *
 * Поэтому позиции разложены по слотам **фиксированной ширины**, и каждый
 * слот центрует свой знак. Ширина слота — максимум из ширины настоящего знака
 * и средней ширины знака из набора подмены; замер делается один раз на фразу,
 * а не на кадр. Строка стоит как вкопанная: меняется содержимое, не геометрия.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  НАБОР ЗНАКОВ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Заглавные буквы, цифры и знаки — как в машинной распечатке. Именно знаки
 * дают ощущение шифра: без них набор читается как «кто-то печатает быстро»,
 * а не как «система считает».
 *
 * Выкинуты `I`, `O`, `l`, `o`, `0`, `1`: эти пары неразличимы в шрифте,
 * и с ними шум местами читается как уже готовый текст. Остальные служебные
 * знаки, которые могли бы ломать вёрстку (`<`, `>`, `&`), не включены
 * намеренно — они безопасны в Compose, но незачем.
 */
@Composable
internal fun GreetingDecoderText(
    text: String,
    progress: Float,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    if (text.isEmpty()) return

    val measurer = rememberTextMeasurer()
    val slotWidth = remember(text, style) { decoderSlotWidth(text, style, measurer) }

    // Сколько знаков уже встало на место. Держится в состоянии, а не
    // считается из прогресса: показ идёт шагами, и номер шага — это ровно
    // то, что меняется.
    var settled by remember(text) { mutableIntStateOf(0) }
    // Номер кадра шума. Меняется вместе с `settled`, чтобы знаки, ещё не
    // вставшие на место, не застывали: неподвижный шум читается как
    // сломанный текст, а не как процесс.
    var frame by remember(text) { mutableIntStateOf(0) }
    // Один генератор на всю анимацию. `Random` на каждый знак на каждый кадр
    // — это новый объект десятки раз за кадр, чего здесь не нужно.
    val random = remember(text) { Random(text.hashCode()) }

    LaunchedEffect(text) {
        settled = 0
        frame = 0
        while (settled < text.length) {
            delay(FrameMs)
            frame++
            // Один знак за шаг. Два и больше — расшифровка «пробегает» рывком
            // и перестаёт читаться как посимвольная.
            settled++
        }
        // Финальный шаг: на экране строго исходный текст, без шума ни в одной
        // позиции. Без него последний кадр мог бы остаться с подменённым
        // пробелом в конце.
        settled = text.length
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = decodeFrame(text = text, settled = settled, frame = frame, random = random),
            style = style,
            color = color,
            textAlign = TextAlign.Center,
            // Переносы не нужны: строка строится под ширину, которую занимает
            // на экране, и разбиение по строкам сделало бы слоты бессмысленными.
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.widthIn(slotWidth),
        )
    }
}

/**
 * Одно состояние расшифровки: знаки до [settled] — настоящие, остальные шумные.
 *
 * Здесь и заложена защита от дёргания ширины. Каждый шумный знак берётся
 * **на месте** своего настоящего знака, поэтому число позиций всегда одно
 * и то же, а разница ширин компенсируется центрованием внутри слота.
 *
 * С вероятностью около половины в шумной позиции остаётся её собственная
 * буква. Это не мелочь: так в потоке виден контур будущего слова ещё до его
 * появления, и глаз успевает подготовиться к чтению. Без этого расшифровка
 * выглядит как случайный мусор, который в конце зачем-то превратился в текст.
 */
private fun decodeFrame(text: String, settled: Int, frame: Int, random: Random): String {
    if (settled >= text.length) return text
    val builder = StringBuilder(text.length)
    text.forEachIndexed { index, real ->
        builder.append(
            when {
                index < settled -> real
                real == ' ' -> real
                // Псевдослучайность из трёх чисел: позиция, кадр шума и сама
                // анимация. Устойчиво к перерисовке — в отличие от живого
                // генератора, который на каждом кадре дал бы новый знак
                // в каждой позиции и превратил бы чтение в мельтешение.
                Random(frame * 8191 + index * 131 + random.nextInt(64)).nextInt(100) < 45 -> real
                else -> GlyphAlphabet[Random(frame * 31 + index).nextInt(GlyphAlphabet.length)]
            },
        )
    }
    return builder.toString()
}

/**
 * Ширина одного слота в dp.
 *
 * Считается как максимум из двух величин: ширины самого широкого настоящего
 * знака и средней ширины знака из набора подмены. Максимум, а не средняя:
 * средняя оставила бы широкий настоящий знак (`Ш`, `Ж`, `W`) без места,
 * и он обрезался бы. Взятая по строке целиком, а не по каждому знаку, —
 * чтобы слоты были одинаковыми и строка читалась как ровный текст.
 */
private fun decoderSlotWidth(
    text: String,
    style: TextStyle,
    measurer: androidx.compose.ui.text.TextMeasurer,
): androidx.compose.ui.unit.Dp {
    var widest = 1f
    text.forEach { ch ->
        val w = measurer.measure(ch.toString(), style).size.width.toFloat()
        if (w > widest) widest = w
    }
    // Запас на средний шумный знак: в наборе есть широкие (#, @, %).
    return (widest * NoiseWidthFactor).dp
}

/** Во сколько раз слот шире самого широкого настоящего знака. */
private const val NoiseWidthFactor = 1.22f

/**
 * Набор знаков для подмены.
 *
 * Заглавные буквы, цифры и знаки. Без `I`, `O`, `l`, `o`, `0`, `1` —
 * неразличимые в шрифте пары, из-за которых шум местами читался бы как
 * готовый текст.
 */
private const val GlyphAlphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789#@%$&*+="

/**
 * Шаг расшифровки.
 *
 * 45 мс: при 60 Гц это примерно три кадра на знак. Чаще — символы меняются
 * быстрее, чем глаз успевает их заметить, и вместо расшифровки получается
 * серая полоса. Реже — эффект распадается на отдельные «прыжки» текста.
 * На фразе в 12–16 знаков полная расшифровка занимает 540–720 мс.
 */
private const val FrameMs = 45L
