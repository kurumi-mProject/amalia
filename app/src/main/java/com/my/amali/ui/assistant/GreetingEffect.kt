package com.my.amali.ui.assistant

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.random.Random

/**
 * Эффект появления новой фразы приветствия.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ЗАЧЕМ ДВА ЭФФЕКТА, А НЕ ОДИН
 * ════════════════════════════════════════════════════════════════════════
 *
 * Смена текста раз в 30 секунд — это событие, которое происходит само,
 * без участия человека. Такое событие обязано быть **замеченным**: если
 * фраза просто подменится, взгляд её не поймает, и вся затея с ротацией
 * превратится в «а, наверное, там что-то менялось».
 *
 * Но и один эффект быстро приедается: сорок показов подряд одного и того же
 * трюка — это уже не событие, а обои. Поэтому эффектов два, и они
 * чередуются случайно:
 *
 *  — [DECODER] — «шифратор»: на месте будущих букв на миг вспыхивают
 *    случайные знаки, и настоящий текст «выкристаллизовывается» из шума.
 *    Читается как «система что-то посчитала».
 *  — [ODOMETER] — «одометр»: каждый знак сидит на вертикальном барабане
 *    и подкручивается на нужный, как цифра в счётчике пробега. Читается
 *    как «значение установилось».
 *
 * Эффекты не смешиваются внутри одной смены: одна смена — один приём.
 * Смесь двух механик читалась бы как сбой отрисовки, а не как приём.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ПОЧЕМУ ВЫБОР ПСЕВДОСЛУЧАЙНЫЙ, А НЕ ПО КРУГУ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Чередование по кругу (шифратор, одометр, шифратор, одометр) предсказуемо
 * уже на третий раз: человек знает, что будет дальше, и эффект перестаёт
 * быть событием. Случайный выбор с запретом повтора дважды подряд даёт
 * непредсказуемость **и** избавляет от «залипания»: два одинаковых эффекта
 * кряду читались бы как отсутствие эффекта.
 */
internal enum class GreetingEffect {
    DECODER,
    ODOMETER;

    companion object {
        /**
         * Выбирает эффект для следующей смены.
         *
         * @param previous эффект предыдущей смены; null — смены ещё не было.
         * @param random источник случайности — вынесен параметром, чтобы
         *   превью и тест могли задать фиксированное зерно и увидеть
         *   конкретный эффект, а не случайный.
         */
        fun next(previous: GreetingEffect?, random: Random = Random.Default): GreetingEffect {
            // Первая смена на экране: одометр. Он спокойнее, и первое, что
            // видит человек, не должно выглядеть как помеха в эфире.
            if (previous == null) return ODOMETER
            val all = entries
            val other = all.filter { it != previous }
            return other[random.nextInt(other.size)]
        }
    }
}

/**
 * Прогресс одного проявления фразы, 0..1.
 *
 * Вынесено в отдельный тип, потому что эффектов два, а анимация запуска одна,
 * и запускаться она обязана одинаково: иначе один эффект будет чуть быстрее
 * другого, и глаз поймает рассинхрон на длинной фразе.
 *
 * @property progress 0 — старая фраза целиком, 1 — новая фраза целиком.
 * @property effect какой приём разыгрывается в этой смене.
 * @property from номер старой смены.
 * @property to номер новой смены.
 * @property seed зерно эффекта: одинаковое для всех знаков одной смены,
 *   разное между сменами.
 */
internal data class GreetingTransition(
    val progress: Float,
    val effect: GreetingEffect,
    val from: Int,
    val to: Int,
    val seed: Int,
)

/**
 * Ведёт смену фразы: помнит предыдущую, выбирает эффект и гонит прогресс.
 *
 * ## Почему анимация живёт здесь, а не в composable-эффекте на месте
 *
 * Смена происходит раз в полминуты, но **состояние перехода** обязано
 * переживать рекомпозиции между сменами: каждые несколько кадров волна
 * обновляет уровень, и экран перерисовывается десятки раз в секунду.
 * Если бы прогресс жил в обычном `remember`, он бы сбрасывался при любой
 * пересборке. [Animatable] хранит значение вне композиции и переживает всё.
 *
 * @param currentIndex номер смены — он же зерно эффекта.
 * @param frameKey ключ, меняющийся на каждой смене фразы.
 * @param effect эффект этой смены. Задаётся снаружи, а не выбирается здесь:
 *   длительность анимации зависит от эффекта, и выбирать его в двух местах
 *   означало бы гарантированный рассинхрон — снаружи посчитают одно время,
 *   внутри разыграют другой эффект.
 * @param durationMs длительность перехода; 0 — переход мгновенный
 *   (нужно превью и режиму «без анимации»).
 */
@Composable
internal fun rememberGreetingTransition(
    currentIndex: Int,
    frameKey: Int,
    effect: GreetingEffect?,
    durationMs: Int,
): GreetingTransition {
    // Начальное значение — 1: в первый кадр фраза уже стоит на месте.
    // Старт с нуля означал бы, что при самом первом показе экрана фраза
    // «расшифровывается» на пустом месте, хотя меняться было не на что.
    val progress = remember { Animatable(1f) }
    var displayed by remember { mutableStateOf(currentIndex) }
    val activeEffect = effect ?: GreetingEffect.ODOMETER

    LaunchedEffect(frameKey) {
        // Переход запускается по ключу, а не по сравнению индексов. Мешок
        // может случайно выдать ту же фразу, что и в прошлый раз, — по
        // индексам переход бы не сработал, и текст сменился бы молча,
        // без эффекта. Ключ растёт на каждой ротации и такой случай видит.
        displayed = currentIndex
        if (durationMs <= 0) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMs, easing = LinearEasing),
        )
    }

    return GreetingTransition(
        progress = progress.value,
        effect = activeEffect,
        from = displayed,
        to = currentIndex,
        // Зерно эффекта — номер смены: у каждой смены свой узор шума
        // и своя раскладка оборотов барабанов.
        seed = currentIndex,
    )
}

/**
 * Сколько знаков новой строки уже встало на место.
 *
 * Каскад, а не одновременная замена: если вся строка переключается за один
 * кадр, эффект не читается — глаз видит не «расшифровку», а подмену.
 * Небольшой сдвиг начала на каждом следующем знаке даёт волну, идущую
 * слева направо: она же совпадает с направлением чтения, поэтому не
 * приходится искать, откуда текст появился.
 *
 * @param progress общий прогресс 0..1.
 * @param length длина строки в знаках.
 * @param spread доля прогресса, отданная каскаду. 0 — все знаки меняются
 *   одновременно; 1 — последний знак меняется в самом конце.
 */
internal fun settledCount(progress: Float, length: Int, spread: Float = 0.55f): Int {
    if (length <= 0) return 0
    val settled = progress * (1f + spread) - spread
    if (settled <= 0f) return 0
    if (settled >= 1f) return length
    return (settled * length).toInt().coerceIn(0, length)
}

/**
 * Прогресс одного знака внутри каскада, 0..1.
 *
 * Каскад идёт слева направо, в сторону чтения: глазу не приходится искать,
 * откуда взялся текст. Обратное направление читалось бы как помеха.
 */
internal fun charProgress(progress: Float, index: Int, length: Int, spread: Float = 0.55f): Float {
    if (length <= 0) return 1f
    val start = (index.toFloat() / length) * spread
    val local = ((progress - start) / (1f - spread)).coerceIn(0f, 1f)
    return local
}

/** Смешивает два числа по прогрессу: 0 → [a], 1 → [b]. */
internal fun lerpFloat(progress: Float, a: Float, b: Float): Float =
    a + (b - a) * progress.coerceIn(0f, 1f)

/**
 * Насколько знак ещё «не свой», 0..1.
 *
 * Единица означает «знак полностью подменён случайным», ноль — «знак стоит
 * правильно». Используется обоими эффектами: шифратор берёт по этому числу
 * подмену символа и яркость вспышки, одометр — высоту, на которую барабан
 * ещё не довернулся.
 */
internal fun scrambleAmount(progress: Float, index: Int, length: Int): Float {
    val p = charProgress(progress, index, length)
    // Небольшой «хвост» в конце: знак не встаёт идеально ровно в тот же
    // кадр, что и сосед. Чистая синхронность читается как таблица, а не
    // как текст, который проявился.
    val jitter = ((index * 37) % 11) / 110f
    return (1f - p + jitter * (1f - p)).coerceIn(0f, 1f)
}

/**
 * Случайный знак из набора, похожего на текст.
 *
 * Набор — обычная [String], а не константа времени компиляции: строковые
 * константы в Kotlin длиннее 65 тысяч символов не бывают, но главное —
 * у строки есть готовый метод [String.length] и индексация, которых нет
 * у `const val`. Пул короткий, и держать его константой незачем.
 */
internal fun randomGlyph(random: Random): Char {
    val pool = GlyphAlphabet
    return pool[random.nextInt(pool.length)]
}

/**
 * Набор знаков для подмены.
 *
 * Буквы и цифры, но **без** `I`, `O`, `l`, `0` и `1`: эти пары неразличимы
 * в шрифте, и «расшифровка» начала бы читаться как уже готовый текст,
 * в котором мелькают настоящие слова.
 */
private const val GlyphAlphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

/** Длительность перехода фразы: сорок кадров при 60 Гц — как раз на грани. */
internal const val GREETING_TRANSITION_MS = 640

/**
 * Есть ли смысл в анимации прямо сейчас.
 *
 * Проверка нужна, потому что переход запускается таймером, а не действием:
 * если анимации системы отключены (в настройках разработчика — «Animator
 * duration scale: off»), показывать расшифровку всё равно не стоит — но и
 * ломаться не должно. Тогда фраза просто меняется.
 */
internal fun greetingAnimationEnabled(scale: Float): Boolean = abs(scale - 0f) > 0.01f
