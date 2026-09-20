package com.my.amali.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.my.amali.ui.icons.AmaliaLamp
import com.my.amali.ui.theme.LocalAmaliaPalette
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.amaliaShadow
import com.my.amali.ui.theme.glassSurface
import kotlinx.coroutines.delay

/**
 * ════════════════════════════════════════════════════════════════════════
 *  CircadianLamp — «какой сейчас свет» в двух состояниях
 * ════════════════════════════════════════════════════════════════════════
 *
 * ## Задача
 *
 * Пользователь должен видеть, что интерфейс живёт по времени суток, и не
 * должен читать об этом абзац на главном экране. Отсюда ровно два
 * состояния:
 *
 *  — **свёрнутое** — глиф лампы и число кельвинов: `🕯 2840 K`. Два
 *    элемента шириной, не мешают ничему;
 *  — **раскрытое** — `Закат · 2840 K`, где буквы проявляются слева
 *    направо, а стеклянная подложка растёт **вместе с последней видимой
 *    буквой**, а не отдельной анимацией.
 *
 * ## Почему текст и стекло не могут разъехаться
 *
 * Ключевое требование: «когда буквы появляются, барьер стекла тоже должен
 * увеличиваться, а не отставать». Поэтому здесь **одна** анимируемая
 * величина — [reveal], общая для букв, разделителя и кельвинов. Все три
 * части читают её одновременно, поэтому отставание невозможно физически,
 * а не «мы постарались».
 *
 * ## Почему буквы не «дрожат»
 *
 * Слово рисуется через [RevealingWord]: строка набрана **один раз** в
 * полную ширину, а окно едет по ней слева направо. Альтернатива —
 * подставлять урезанный префикс (`word.take(n)`) — заставляет Compose
 * пересчитывать раскладку на каждой букве, и правый край «плывёт» на доли
 * пикселя. Здесь буквы стоят намертво, едет только окно.
 *
 * ## Поведение
 *
 *  1. при появлении экрана лампа **сама** раскрывается один раз — иначе
 *     аффорданс не читается, и пользователь не знает, что внутри текст;
 *  2. тап раскрывает немедленно; повторный тап сворачивает сразу;
 *  3. раскрытая лампа через [AutoCollapseMs] сворачивается сама и **тем
 *     же путём назад** — обратное движение обязано быть тем же, иначе
 *     схлопывание читается как «мигнуло и пропало».
 *
 * @param label словесная характеристика света («Закат», «Тёплая лампа»).
 * @param cct цветовая температура в кельвинах.
 * @param autoExpandOnStart раскрыть один раз при появлении экрана.
 */
@Composable
fun CircadianLamp(
    label: String,
    cct: Int,
    modifier: Modifier = Modifier,
    autoExpandOnStart: Boolean = true,
) {
    val kelvinPart = "$cct K"
    // Слово первым, кельвины вторыми: буквы идут слева направо в том же
    // порядке, в котором человек читает фразу.
    val fullLength = label.length + KelvinGap + kelvinPart.length

    var expanded by remember(label) { mutableStateOf(false) }
    // Единственная анимируемая величина: сколько шагов строки раскрыто.
    val reveal = remember { Animatable(if (autoExpandOnStart) 0f else fullLength.toFloat()) }

    /**
     * Раскрывает или сворачивает лампу полным проходом по строке.
     *
     * 34 мс на шаг: на «Закат · 2840 K» (13 шагов) это ≈440 мс — буквы
     * успевают читаться по одной, но ждать не приходится. Линейно:
     * ускоряющийся текст читается как «прыжок», а не как речь.
     */
    suspend fun sweep(open: Boolean) {
        reveal.animateTo(
            targetValue = if (open) fullLength.toFloat() else 0f,
            animationSpec = tween(durationMillis = (fullLength * MsPerChar).coerceIn(180, 900)),
        )
    }

    // Первое появление экрана: показать себя один раз, затем убраться.
    LaunchedEffect(label, autoExpandOnStart) {
        if (autoExpandOnStart) {
            sweep(open = true)
            delay(AutoCollapseMs)
            sweep(open = false)
        }
    }

    // Тап: раскрыть и через паузу свернуть. Повторный тап сворачивает сразу.
    LaunchedEffect(expanded) {
        if (expanded) {
            sweep(open = true)
            delay(AutoCollapseMs)
            expanded = false
        } else {
            sweep(open = false)
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "lampPress",
    )

    // Свет лампы тонируется палитрой времени суток, а не константой: глиф
    // обязан быть частью той же световой системы, что и фон.
    val palette = LocalAmaliaPalette.current
    val lampColor = if (palette.isWarm) {
        palette.auroras.firstOrNull() ?: WarmDot
    } else {
        palette.auroras.lastOrNull() ?: CoolDot
    }

    val revealed = reveal.value
    val collapsed = revealed < 1f

    Row(
        modifier = modifier
            .heightIn(min = 34.dp)
            .scale(scale)
            .amaliaShadow(
                elevation = if (collapsed) 0.08f else 0.20f,
                shape = RoundedCornerShape(Radius.chip),
            )
            .glassSurface(
                shape = RoundedCornerShape(Radius.chip),
                fillAlpha = if (collapsed) 0.42f else 0.56f,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { expanded = !expanded },
            )
            .padding(horizontal = Spacing.xs, vertical = 6.dp)
            .semantics {
                role = Role.Button
                contentDescription = "$label, $kelvinPart"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        // Глиф лампы: в свёрнутом виде он — единственный визуальный якорь,
        // поэтому держит на себе всю аффорданс-нагрузку.
        Icon(
            imageVector = AmaliaLamp,
            contentDescription = null,
            tint = lampColor,
            modifier = Modifier.size(15.dp),
        )

        // Слово и кельвины раскрываются одной анимацией: слово первым,
        // цифры — сразу за ним. Тем самым подложка растёт непрерывно,
        // а не «прыжком» при смене части.
        RevealingRow(
            word = label,
            kelvin = kelvinPart,
            reveal = revealed,
        )
    }
}

/**
 * Рисует слово и кельвины, раскрывая их слева направо по мере роста [reveal].
 *
 * ## Как достигается синхронность стекла и букв
 *
 * Слово набрано **один раз** в полную ширину и обрезается окном: ширина
 * окна выводится из метрик самой строки (`TextLayoutResult`), а не из
 * предположений о шрифте. Кельвины появляются ровно на своём шаге
 * анимации — той же величиной, что двигает слово, поэтому между двумя
 * частями не может возникнуть рассинхрон.
 *
 * @param word словесная часть света.
 * @param kelvin вторая часть строки — кельвины.
 * @param reveal сколько шагов строки уже раскрыто.
 */
@Composable
private fun RevealingRow(
    word: String,
    kelvin: String,
    reveal: Float,
) {
    // Сколько шагов ушло на слово. Шаг — один символ.
    val wordShown = reveal.coerceIn(0f, word.length.toFloat())
    val kelvinRaw = reveal - word.length - KelvinGap
    val kelvinShown = kelvinRaw.coerceIn(0f, kelvin.length.toFloat() + 1f)

    Row(verticalAlignment = Alignment.CenterVertically) {
        RevealingWord(word = word, reveal = wordShown)
        if (kelvinShown > 0.5f) {
            Spacer(Modifier.width(Spacing.xxs))
            Text(
                text = kelvin,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.92f),
                maxLines = 1,
            )
        }
    }
}

/**
 * Показывает [word] целиком, открывая его по одной букве.
 *
 * Внутренний `Box` держит полную ширину строки и не сжимается окном —
 * внешний режет его по правому краю. Поэтому правый край подложки всегда
 * совпадает с последней проявленной буквой, а сами буквы не двигаются.
 *
 * @param reveal сколько букв уже открыто (дробная часть — «в пути»).
 */
@Composable
private fun RevealingWord(word: String, reveal: Float) {
    if (word.isEmpty()) return

    var stops by remember { mutableStateOf<List<Float>>(emptyList()) }

    // Позиция окна: точки входа букв из метрик шрифта, интерполяция между
    // соседними — так движение окна совпадает с движением букв.
    val openEnd = if (stops.size >= word.length + 1 && stops.isNotEmpty()) {
        val lower = reveal.toInt().coerceIn(0, word.length)
        val frac = (reveal - lower).coerceIn(0f, 1f)
        val from = stops[lower]
        val to = stops[(lower + 1).coerceAtMost(stops.size - 1)]
        from + (to - from) * frac
    } else {
        0f
    }

    val density = LocalDensity.current
    val widthDp = with(density) { openEnd.toDp() }

    Box(
        modifier = Modifier
            .width(widthDp)
            .clipToBounds(),
    ) {
        Box(modifier = Modifier.wrapContentWidth(unbounded = true)) {
            Text(
                text = word,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { result ->
                    val points = (0..word.length).map { index ->
                        if (index == word.length) {
                            result.size.width.toFloat()
                        } else {
                            result.getHorizontalPosition(index, usePrimaryDirection = true)
                        }
                    }
                    stops = points
                },
            )
        }
    }
}

/**
 * Сколько миллисекунд уходит на один шаг раскрытия.
 *
 * Значение подобрано так, чтобы короткая подпись («Ночь») успевала
 * прочитаться, а длинная («Нейтральный день») не затягивалась: на 13
 * шагах это ≈440 мс, дальше срабатывает верхняя граница.
 */
private const val MsPerChar = 34

/** Сколько шагов занимает разделитель «·» между словом и кельвинами. */
private const val KelvinGap = 3

/** Через сколько миллисекунд раскрытая лампа сворачивается сама. */
private const val AutoCollapseMs = 6_000L

/** Холодный свет — fallback, если палитра не задала ауроры. */
private val CoolDot = Color(0xFF9EC2F0)

/** Тёплый свет — fallback, если палитра не задала ауроры. */
private val WarmDot = Color(0xFFE8B054)
