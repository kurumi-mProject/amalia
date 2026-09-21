package com.my.amali.ui.assistant

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.my.amali.R
import com.my.amali.ui.theme.LocalLightProfile
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.greetingTextPages
import kotlinx.coroutines.delay

/**
 * Герой главного экрана: одна крупная фраза, которая тихо меняется сама.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ЗАЧЕМ ФРАЗА МЕНЯЕТСЯ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Неподвижный крупный текст на главном экране голосового ассистента читается
 * как «я не работаю». Живой фон и волна говорят о том же, но они
 * вспомогательные, а фраза — самое крупное на экране. Пока она стоит —
 * состояние читается как «жду команду»; когда тихо меняется — как «я здесь».
 *
 * Ротация идёт **внутри фазы суток**: ночью человек должен видеть ночные
 * фразы, а не «Доброе утро» в три часа. Замыкается это на тот же
 * [com.my.amali.ui.theme.CircadianPhase], по которому построен весь свет
 * экрана, — текст и освещение не могут разойтись.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ПОЧЕМУ ЗДЕСЬ НЕТ ОДОМЕТРА И ШИФРАТОРА
 * ════════════════════════════════════════════════════════════════════════
 *
 * У эффектов, стоявших здесь раньше, обе ноги — в посимвольной геометрии:
 * каждый знак живёт в своей ячейке фиксированной ширины. Чтобы ячейки не
 * резали широкие буквы (`Ш`, `Ж`, `W`), запас ширины делали с большим
 * коэффициентом — и строка растягивалась: между словами появлялись дыры,
 * а сами буквы стояли свободнее, чем в обычном тексте. Отсюда и ощущение
 * «сломанного шрифта», при том что шрифт не тронут вовсе.
 *
 * Вторая цена — честность рендера: в ячейках текст больше не был текстом.
 * Посимвольная раскладка ломала перенос строк, кернинг и лигатуры — всё то,
 * чем «до» и «после» отличаются от ручной вёрстки. Дизайн, который нельзя
 * набрать обычным `Text`, не может выглядеть набранным хорошо.
 *
 * Поэтому смена теперь — одно движение целого текста: старая фраза тихо
 * уходит вверх, новая мягко приходит снизу. Один ритм, без знаковых
 * механик. Текст набирается `Text`-ом — и выглядит как набранный.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ТАЙМЕР ИДЁТ ОТ ВХОДА И НЕ СБРАСЫВАЕТСЯ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Первая смена — через [rotationMs] после появления экрана, дальше — снова
 * и снова. Таймер не сбрасывается ни на тап, ни на смену состояния голоса,
 * ни на ответы Амалии: тот, кто пользуется приложением, тапает чаще, чем
 * раз в 30 секунд, и при сбросе «не увидит смены фразы никогда».
 *
 * `LaunchedEffect(slot, tone, rotationMs)` перезапускается только при смене
 * фазы суток, тона или периода — то есть когда фраза и так обязана смениться
 * (и счётчик честно начинается с приветствия). Волна обновляет состояние
 * десятки раз в секунду; зависни эффект на чём-то, что меняется вместе
 * с кадром, — `delay` не дожил бы до конца ни разу.
 *
 * @param breath фаза дыхания экрана 0..1, приходит снаружи. Один такт на
 *   весь экран: свой период у каждого элемента даёт несколько независимых
 *   ритмов, которые глаз читает как шум, а не как покой.
 * @param useAnimation false — смена мгновенной подменой: нужно превью и
 *   режимам, где системные анимации отключены.
 * @param rotationMs период смены фразы.
 */
@Composable
internal fun GreetingHero(
    breath: Float,
    modifier: Modifier = Modifier,
    useAnimation: Boolean = true,
    rotationMs: Int = GREETING_ROTATION_MS,
) {
    val light = LocalLightProfile.current
    val slot = GreetingSlot.of(light.phase)
    val tone = GreetingTone.DEFAULT

    val catalogue = remember(slot, tone) { GreetingPhrases.forSlot(slot, tone) }
    val order = remember(catalogue) { rotationOrder(catalogue) }
    // Каталог пуст только при ошибочной правке [GreetingPhrases]; пустой герой
    // честнее краша на делении по нулю — экран обязан жить при любой правке.
    if (order.isEmpty()) return

    // Счётчик смен под ключом фазы: при смене суток набор другой, и позиция
    // в старом там бессмысленна. Инкремент меняет выбранную фразу; анимацию
    // запускает не сам счётчик, а отличие нового текста от старого —
    // повторяющаяся подряд фраза не даёт пустого движения.
    var step by remember(slot, tone) { mutableIntStateOf(0) }
    LaunchedEffect(slot, tone, rotationMs) {
        step = 0
        while (true) {
            delay(rotationMs.toLong())
            step++
        }
    }

    // Порядок набора упорядочен (см. [rotationOrder]): деление по модулю
    // честно проходит его по кругу, без мешка и без повторов соседей.
    val phraseRes = order[step % order.size]
    val text = stringResource(phraseRes)
    val style = MaterialTheme.typography.displayMedium.copy(textAlign = TextAlign.Center)
    val color = MaterialTheme.colorScheme.onBackground

    // Оптическая геометрия берётся у шрифта, а не назначается «на глаз»:
    // интерлиньяж — из displayMedium, запас — из той же функции, что
    // защищает низкие буквы от отсечения. Одна формула на все случаи, чтобы
    // блок не мог разъехаться при правке типографики.
    val density = LocalDensity.current
    val fontSize = with(density) { style.fontSize.toPx() }
    val lineHeight = with(density) { style.lineHeight.toPx() }
    val measurer = rememberTextMeasurer()

    // Ширина, в которую обязана уложиться строка: экран минус боковые отступы,
    // но не больше [GreetingStageWidth]. Считается из конфигурации, а не из
    // фактических размеров блока: ширина блока зависит от этой же величины,
    // и брать её из результата — замкнутый круг.
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val maxLineWidth = with(density) {
        minOf(screenWidth - Spacing.screen * 2, GreetingStageWidth).toPx().coerceAtLeast(1f)
    }

    // Одна строка или две — решается замером, а не догадкой. От этого зависит
    // только вертикальный запас: фраза в две строки обязана получить больше
    // места, иначе её низкие буквы срежет. Обычный `Text` переносит строки
    // сам — резерв высоты нужен лишь затем, чтобы карточки под героем не
    // прыгали при смене фразы.
    val pages = remember(text, style, maxLineWidth) {
        val measured = measureLineWidth(text, style, measurer, maxLineWidth)
        val effective = if (measured > 0f) measured else estimatedLineWidth(text, style)
        if (effective <= maxLineWidth) 1 else GreetingMaxLines
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Высота зарезервирована заранее и ровно настолько, насколько нужно:
        // вокруг односложных фраз («Вечер», «Я рядом») не остаётся мёртвой
        // полосы, а двухстрочные не толкают соседние блоки при смене.
        Box(
            modifier = Modifier
                .widthIn(max = GreetingStageWidth)
                .heightIn(min = greetingTextPages(fontSize, lineHeight, pages)),
            contentAlignment = Alignment.Center,
        ) {
            // Без анимаций (превью, отключённые системные анимации) смена —
            // честная мгновенная подмена: эффект не «ломается», а отсутствует.
            if (useAnimation) {
                AnimatedContent(
                    // Ключ — сам текст, а не номер смены: повторяющаяся подряд
                    // фраза (приветствие держится два такта) не даёт движения,
                    // потому что «смена» не меняет ничего на экране. Анимация
                    // срабатывает только там, где текст реально другой.
                    targetState = text,
                    // Направление одно и всегда одно: смена фразы — не навигация
                    // «вперёд/назад», а тихое течение времени. Асимметрия
                    // таймингов — вход чуть длиннее выхода: новая фраза
                    // перекрывает угасание старой, и на доле секунды экран
                    // не остаётся вовсе без текста.
                    transitionSpec = {
                        (fadeIn(tween(SwapInMs, easing = EaseOutCubic)) +
                            slideInVertically(tween(SwapInMs, easing = EaseOutCubic)) { it / 5 })
                            .togetherWith(
                                fadeOut(tween(SwapOutMs, easing = EaseOutCubic)) +
                                    slideOutVertically(tween(SwapOutMs, easing = EaseOutCubic)) { -it / 8 },
                            )
                    },
                    contentAlignment = Alignment.Center,
                    label = "greetingSwap",
                    modifier = Modifier.fillMaxWidth(),
                ) { visible ->
                    GreetingPhrase(text = visible, style = style, color = color)
                }
            } else {
                GreetingPhrase(text = text, style = style, color = color)
            }
        }

        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(R.string.greeting_prompt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = SubtitleAlpha),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(GreetingUnderlineGap))
        GreetingUnderline(breath = breath)
    }
}

/**
 * Одна фраза героя: обычный текст, набранный обычным рендером.
 *
 * Единственная точка, где рисуется главный текст экрана — и намеренно
 * ровно одна: анимации показывают этот же блок целиком, не пересобирая
 * его посимвольно. Любая будущая смена стиля правится здесь один раз.
 */
@Composable
private fun GreetingPhrase(text: String, style: TextStyle, color: Color) {
    Text(
        text = text,
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = GreetingMaxLines,
    )
}

/**
 * Ширина строки фразы в пикселях для её стиля.
 *
 * Считается через [TextMeasurer], а не «на глазок»: оценка длины строки нужна
 * каждый раз при смене фразы, и ошибиться в ней — значит либо зря зарезервировать
 * вторую строку, либо пустить текст под отсечение по вертикали.
 */
private fun measureLineWidth(
    text: String,
    style: TextStyle,
    measurer: TextMeasurer,
    maxWidthPx: Float,
): Float {
    // Измеритель обязан получить те же условия, что и настоящий текст:
    // иначе замер вернёт ширину, которой на экране не будет, и решение
    // «одна строка или две» окажется неверным.
    val constraints = Constraints(maxWidth = maxWidthPx.toInt().coerceAtLeast(1))
    return measurer.measure(
        text = text,
        style = style,
        maxLines = 1,
        softWrap = false,
        constraints = constraints,
    ).size.width.toFloat()
}

/**
 * Оценка ширины строки, если замера под рукой нет.
 *
 * Используется только как запасной путь (в превью, где измеритель текста
 * недоступен). 0.58 от кегля на знак — консервативная середина для
 * пропорционального шрифта: строчная в среднем 0.5, заглавная 0.66.
 */
private fun estimatedLineWidth(text: String, style: TextStyle): Float {
    val glyphs = text.length.toFloat()
    val size = style.fontSize.value
    return glyphs * size * 0.58f
}

/**
 * Порядок показа фраз фазы.
 *
 * Первая — всегда приветствие: оно обязано быть первым, что человек видит,
 * открыв приложение. Дальше идут фразы характера, последней — самая короткая.
 * Такая последовательность читается как «поздоровалась → сказала что-то
 * своё → коротко выдохнула», а не как случайный набор строк.
 *
 * Если в наборе одна фраза (так бывает при правке каталога), порядок
 * вырождается в неё саму — повтор здесь честнее, чем подстановка чужой фазы.
 */
private fun rotationOrder(paragraphs: List<Int>): List<Int> {
    if (paragraphs.size < 3) return paragraphs

    val greeting = paragraphs.first()
    val short = paragraphs.last()
    val middle = paragraphs.subList(1, paragraphs.lastIndex)

    return buildList {
        // Приветствие держится два такта подряд: 30 секунд — это ровно тот
        // интервал, за который человек успевает отвлечься и снова взглянуть
        // на экран, и увидеть во второй раз чужое «Пусть всё затихнет» вместо
        // «Доброй ночи» было бы потерей, а не сменой.
        add(greeting)
        add(greeting)
        addAll(middle)
        add(short)
    }
}

/**
 * Ширина, на которой стоит герой главного экрана.
 *
 * 400dp: предел, при котором фраза приветствия читается как одна строка на
 * любом телефоне из поддерживаемых, и при этом текст не растягивается на
 * планшете в полосу. Значение экспортировано и используется ещё волной — она
 * обязана быть ровно того же размера, иначе две центральные колонны экрана
 * разъезжаются на пару пикселей, и это видно.
 */
internal val GreetingStageWidth = 400.dp

/**
 * Приглушение подписи под фразой.
 *
 * 0.62 — вторичный текст, который обязан читаться, но не спорить с главной
 * фразой. Прошлые 0.82 делали подпись почти такой же яркой, как заголовок
 * вторичной строки, и это давало ту самую «серую серость»: читая экран,
 * глаз не находил, что здесь главное.
 */
private const val SubtitleAlpha = 0.62f

/**
 * Зазор между подписью и чертой под фразой.
 *
 * Меньше, чем [Spacing.md], потому что подпись и черта — один смысловой
 * элемент («приглашение и его подчёркивание»), а не два независимых блока.
 */
private val GreetingUnderlineGap = Spacing.sm

/** Сколько строк допускается у фразы. Больше двух не влезает ни одна. */
private const val GreetingMaxLines = 2

/**
 * Длительность входа новой фразы, миллисекунды.
 *
 * 560 мс — дольше «обычных» UI-переходов, и это осознанно: смена раз в
 * 30 секунд не отклик, а течение. Медленный вход читается как дыхание,
 * быстрый — как подмену слайда.
 */
private const val SwapInMs = 560

/** Длительность ухода старой фразы: короче входа, чтобы не было двойного движения. */
private const val SwapOutMs = 380

/**
 * Период смены фразы.
 *
 * 30 секунд: реже — человек успевает забыть, что фраза вообще меняется;
 * чаще — экран начинает шевелиться сам по себе и отвлекает от того, ради
 * чего приложение открыли.
 */
internal const val GREETING_ROTATION_MS = 30_000
