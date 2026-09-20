package com.my.amali.ui.assistant

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.my.amali.R
import com.my.amali.ui.theme.LocalLightProfile
import com.my.amali.ui.theme.Spacing
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Ключ — номер смены: он меняется на каждой ротации, даже если мешок
        // случайно выдал ту же фразу. По тексту эффект бы не перезапустился,
        // и смена прошла бы без анимации.
        Box(modifier = Modifier.widthIn(max = MaxGreetingWidth)) {
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
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.md))
        GreetingUnderline(breath = breath)
    }
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
 *  МЕШОК, А НЕ ИНДЕКС ПО КРУГУ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Фразы выдаются из **перемешанного мешка**: берём первую, выбрасываем её,
 * берём следующую. Мешок опустел — перемешиваем заново.
 *
 * Так решаются три вещи сразу:
 *
 *  1. **Одна фраза не выпадет дважды подряд** — она уже вынута. Простой
 *     выбор случайной давал бы повтор в трети случаев, и это выглядело бы
 *     как «сломалось, не сменилось».
 *  2. **Весь набор прокрутится прежде, чем что-то повторится.** Случайный
 *     выбор может три раза подряд достать одну и ту же — для пользователя
 *     это не случайность, а баг.
 *  3. **Порядок не повторяется от круга к кругу** — перемешивание каждый раз
 *     новое. Ротация по индексу выдала бы тот же порядок, и цикл был бы виден
 *     уже на втором круге.
 */
@Composable
private fun rememberRotatingGreeting(slot: GreetingSlot, rotationMs: Int): RotatingGreeting {
    val tone = GreetingTone.DEFAULT
    val pool = remember(slot, tone) { GreetingPhrases.forSlot(slot, tone) }

    // Мешок, текущая фраза и счётчик смен живут под одним ключом: при смене
    // суток набор другой, и остатки старого мешка там бессмысленны.
    var bag by remember(slot, tone) { mutableStateOf(emptyList<Int>()) }
    var current by remember(slot, tone) { mutableStateOf(pool.first()) }
    var effect by remember(slot, tone) { mutableStateOf(GreetingEffect.ODOMETER) }
    var rotations by remember(slot, tone) { mutableStateOf(0) }
    val random = remember(slot, tone) { Random(System.nanoTime()) }

    LaunchedEffect(slot, tone) {
        val fresh = pool.shuffled(random)
        // Первая фраза тоже случайная, а не первая из списка: иначе каждый
        // запуск приложения показывал бы одну и ту же строку, и живым это
        // перестало бы казаться на второй день.
        current = fresh.first()
        bag = fresh.drop(1)

        while (true) {
            delay(rotationMs.toLong())
            if (bag.isEmpty()) bag = pool.shuffled(random)
            current = bag.first()
            bag = bag.drop(1)
            // Эффект выбирается под данные условия, а не под глобальный
            // счётчик: GreetingEffect.next запрещает повтор текущего.
            effect = GreetingEffect.next(previous = effect, random = random)
            rotations++
        }
    }

    return RotatingGreeting(
        phraseRes = current,
        index = rotations,
        key = rotations,
        effect = effect,
    )
}

/** Максимальная ширина фразы: на широких экранах строка не растягивается. */
private val MaxGreetingWidth = 440.dp

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
