package com.my.amali.ui.assistant

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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlin.random.Random

/**
 * Герой главного экрана: фраза приветствия, которая меняется сама.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ЗАЧЕМ ФРАЗА МЕНЯЕТСЯ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Неподвижный крупный текст на главном экране голосового ассистента читается
 * как «я не работаю». Живой фон, лампа и волна говорят о том же, но они
 * вспомогательные, а фраза — самое крупное на экране. Пока она стоит —
 * состояние читается как «жду команду»; когда меняется — как «я здесь».
 *
 * Ротация идёт **внутри фазы суток**: ночью человек должен видеть ночные
 * фразы, а не «Доброе утро» в три часа. Замыкается это на тот же
 * [com.my.amali.ui.theme.CircadianPhase], по которому построен весь свет
 * экрана, — текст и освещение не могут разойтись.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ТАЙМЕР ИДЁТ ОТ ВХОДА И НЕ СБРАСЫВАЕТСЯ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Первая смена — через [rotationMs] после появления экрана, дальше — снова
 * и снова. Таймер не сбрасывается ни на тап, ни на смену состояния голоса,
 * ни на ответы Амалии.
 *
 * Здесь была развилка, и решение не очевидное. Вариант «сбрасывать при
 * любой активности» выглядит заботливым — «не отвлекать, пока занят», —
 * но даёт обратное: тот, кто пользуется приложением, тапает чаще, чем раз
 * в 30 секунд, и **не увидит смены фразы никогда**. Ротация существовала бы
 * только для того, кто открыл экран и ушёл. Непрерывный таймер — единственный
 * вариант, при котором механизм живой для всех.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ДВА ЭФФЕКТА СМЕНЫ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Смена идёт сама, без участия человека, — значит, обязана быть замеченной.
 * Но и один приём приедается: сорок показов одного и того же трюка
 * превращают его в обои. Поэтому эффектов два, и они чередуются случайно,
 * без повтора подряд (см. [GreetingEffect.next]):
 *
 *  — [GreetingEffect.DECODER] — фраза выкристаллизовывается из шума:
 *    на месте будущих букв вспыхивают случайные знаки;
 *  — [GreetingEffect.ODOMETER] — каждая буква сидит на своём барабане
 *    и подкручивается на нужную, как цифра в счётчике.
 *
 * Эффекты разной длительности, и это не мелочь: барабану нужно время
 * на оборот, шифратору — нет. Длительность берётся по тому эффекту,
 * который будет разыгран, — поэтому переход всегда успевает закончиться
 * до следующей смены.
 *
 * @param useAnimation false — смена мгновенным затуханием: нужно превью
 *   и режимам, где системные анимации отключены. Тогда эффекты не играют,
 *   но и не ломаются.
 * @param breath фаза дыхания экрана 0..1, приходит снаружи. Один такт на весь
 *   экран: свой период у каждого элемента даёт четыре независимых ритма,
 *   которые глаз читает как шум, а не как покой.
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

    val rotation = rememberRotatingGreeting(slot = slot, rotationMs = rotationMs)

    // Анимация не нужна — показываем текст как есть. Так превью и режим
    // с выключенными системными анимациями выглядят честно: ни одного
    // скрытого эффекта не остаётся «на всякий случай».
    val effect = if (useAnimation) rotation.effect else null
    val text = stringResource(rotation.phraseRes)
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
    // места, иначе её низкие буквы срежет отсечение барабана.
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
        // прошлая версия резервировала две строки всегда, и вокруг односложных
        // фраз («Вечер», «Я рядом») оставалась мёртвая полоса — тот самый
        // воздух, из-за которого главный текст выглядел «далеко» от всего
        // остального. Здесь страница одна, а вторая появляется только тогда,
        // когда фраза действительно переносится.
        Box(
            modifier = Modifier
                .widthIn(max = GreetingStageWidth)
                .heightIn(min = greetingTextPages(fontSize, lineHeight, pages)),
            contentAlignment = Alignment.Center,
        ) {
            // Ключ — номер смены: он меняется на каждой ротации, даже если
            // мешок случайно выдал ту же фразу. По тексту эффект бы не
            // перезапустился, и смена прошла бы без анимации.
            key(rotation.key) {
                when (effect) {
                    null -> Text(
                        text = text,
                        style = style,
                        color = color,
                        textAlign = TextAlign.Center,
                        maxLines = GreetingMaxLines,
                    )
                    GreetingEffect.DECODER -> GreetingDecoderText(
                        text = text,
                        progress = 1f,
                        color = color,
                        style = style,
                    )
                    GreetingEffect.ODOMETER -> GreetingOdometerText(
                        text = text,
                        progress = 1f,
                        color = color,
                        style = style,
                    )
                }
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
 * Совместимость: множитель заглавных букв в пропорциональном шрифте.
 *
 * Заглавная занимает примерно на треть больше ширины, чем строчная того же
 * знака. Число нужно одному месту — оценке переноса фразы на вторую строку.
 */
private const val CapsWidthFactor = 1.32f

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
 * Что показывать прямо сейчас: фраза, номер смены и эффект этой смены.
 *
 * @property phraseRes адрес строки текущей фразы.
 * @property index порядковый номер смены — ключ для анимации.
 * @property key то же число, что [index]; отдельным полем, потому что
 *   читается из разных мест и называется по-разному по смыслу.
 * @property effect эффект, разыгрываемый при переходе к этой фразе.
 */
internal data class RotatingGreeting(
    val phraseRes: Int,
    val index: Int,
    val key: Int,
    val effect: GreetingEffect,
)

/**
 * Помнит, какую фразу показывать, когда менять и чем показывать смену.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ПОЧЕМУ ТАЙМЕР ЗАПУСКАЕТСЯ ТОЛЬКО ОТ ФАЗЫ
 * ════════════════════════════════════════════════════════════════════════
 *
 * `LaunchedEffect(slot, tone)` перезапускается ровно тогда, когда сменилась
 * фаза суток или тон, — то есть когда фраза и так обязана смениться. Внутри
 * идёт цикл с `delay`.
 *
 * Ключ — только фаза и тон, и это принципиально: волна обновляет состояние
 * десятки раз в секунду, экран перерисовывается постоянно. Зависел бы эффект
 * от чего-то, что меняется вместе с перерисовкой, — `delay` не дожил бы
 * до конца ни разу, и смены фразы не произошло бы вообще.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ЧТО ПОКАЗЫВАЕТСЯ ПРЯМО СЕЙЧАС — И ПОЧЕМУ ЭТО НЕ СЛУЧАЙНЫЙ МЕШОК
 * ════════════════════════════════════════════════════════════════════════
 *
 * Раньше первой показывалась случайная фраза из набора, а дальше шёл
 * перемешанный мешок. Для утра это означало, что примерно в двух случаях
 * из трёх на экране главного ассистента вместо «Доброе утро» оказывалось
 * «Кофе, потом всё остальное» — и человек, открывший приложение утром,
 * не получал приветствия вообще. Это не разнообразие, а потеря функции:
 * приветствие и есть та функция, ради которой главный текст существует.
 *
 * Поэтому набор упорядочен, а не перемешан. Первая фраза — **само
 * приветствие** («Доброе утро», «Добрый день», «Добрый вечер», «Доброй
 * ночи») и она держится на экране две ротации подряд. Дальше идут фразы
 * характера, и только потом — короткие («Утро», «Вечер»), которые читаются
 * как ритмическая точка в конце круга, а не как «экран сломался».
 *
 * Порядок задаётся здесь, а не в каталоге [GreetingPhrases]: каталог — это
 * набор текстов, а порядок показа — решение экрана.
 */
@Composable
private fun rememberRotatingGreeting(slot: GreetingSlot, rotationMs: Int): RotatingGreeting {
    val tone = GreetingTone.DEFAULT
    val catalogue = remember(slot, tone) { GreetingPhrases.forSlot(slot, tone) }
    val order = remember(catalogue) { rotationOrder(catalogue) }

    // Индекс в упорядоченном наборе и счётчик смен живут под одним ключом:
    // при смене суток набор другой, и позиция в старом там бессмысленна.
    var step by remember(slot, tone) { mutableStateOf(0) }
    var effect by remember(slot, tone) { mutableStateOf(GreetingEffect.ODOMETER) }
    var rotations by remember(slot, tone) { mutableStateOf(0) }
    val random = remember(slot, tone) { Random(System.nanoTime()) }

    LaunchedEffect(slot, tone) {
        step = 0
        rotations = 0
        while (true) {
            delay(rotationMs.toLong())
            step++
            // Эффект выбирается под данные условия, а не под глобальный
            // счётчик: GreetingEffect.next запрещает повтор текущего.
            effect = GreetingEffect.next(previous = effect, random = random)
            rotations++
        }
    }

    val phrase = order[step % order.size]
    return RotatingGreeting(
        phraseRes = phrase,
        index = rotations,
        key = rotations,
        effect = effect,
    )
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

    // «Длинная» фраза — самая информативная часть набора: идёт после
    // приветствия, потому что несёт характер, а не функцию.
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
 * Период смены фразы.
 *
 * 30 секунд: реже — человек успевает забыть, что фраза вообще меняется;
 * чаще — экран начинает шевелиться сам по себе и отвлекает от того, ради
 * чего приложение открыли.
 */
internal const val GREETING_ROTATION_MS = 30_000
