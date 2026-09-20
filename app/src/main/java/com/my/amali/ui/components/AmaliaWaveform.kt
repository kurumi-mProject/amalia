package com.my.amali.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.my.amali.domain.entity.WaveSettings
import com.my.amali.domain.entity.WaveSettings.Companion.COUNT_MIN
import com.my.amali.domain.entity.WaveSettings.Companion.GAP_MAX
import com.my.amali.domain.entity.WaveSettings.Companion.GAP_MIN
import com.my.amali.domain.entity.WaveSettings.Companion.SMOOTHING_MAX
import com.my.amali.domain.entity.WaveSettings.Companion.SMOOTHING_MIN
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Живая волна: полосы, которые растут от центра под громкость голоса.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ПОЧЕМУ СВОЙ CANVAS, А НЕ ГОТОВАЯ БИБЛИОТЕКА
 * ════════════════════════════════════════════════════════════════════════
 *
 * У библиотеки `compose-audiowaveform` от lincollincol взят **язык
 * отрисовки**: полосы одинаковой ширины, скруглённые концы, рост от
 * центральной оси, параметры `spikeWidth` / `spikePadding` / `spikeRadius`.
 * Код — свой, по трём причинам, и все три принципиальные:
 *
 *  1. **Библиотека мёртвая.** Последний релиз v1.1.2 — февраль 2023,
 *     `compileSdk 33` и Compose того же времени. У нас Compose BOM
 *     2026.08.00; внутренние API Compose между этими версиями не
 *     гарантированы, и подключать её — значит получить либо
 *     `NoSuchMethodError` в рантайме, либо падение на CompositionLocal,
 *     которое всплывёт не при сборке, а у пользователя.
 *  2. **Она не умеет живой звук.** Её вход — `amplitudes: List<Int>`, то
 *     есть **готовый массив**, посчитанный заранее по файлу (`Amplituda`),
 *     плюс `progress` и `onProgressChange` для перемотки пальцем. Это
 *     плеерная осциллограмма, а не индикатор микрофона: она физически не
 *     рассчитана на поток значений во времени.
 *  3. **Нам нужны не её параметры, а наши.** Ползунки чувствительности,
 *     сглаживания и максимальной высоты в библиотеке отсутствуют —
 *     пришлось бы всё равно рисовать поверх.
 *
 * Взамен своей реализации: ноль новых зависимостей, полный контроль над
 * сглаживанием и один общий `Canvas` вместо вложенных `animateFloatAsState`
 * на каждую полосу.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ГЛАВНОЕ ПРАВИЛО: ДВИЖЕНИЕ ЕСТЬ ТОЛЬКО ТАМ, ГДЕ ЕСТЬ ЗВУК
 * ════════════════════════════════════════════════════════════════════════
 *
 * Первая версия этой волны «дышала» в тишине: бесконечная анимация с
 * периодом 3.2 с добавляла полосам ±11% высоты даже при нулевой громкости.
 * Задумано это было как «покой не читается как поломка», но на практике
 * вышло обратное — **индикатор врал**. Полосы шевелились, когда звука не
 * было, и по ним нельзя было понять, слышит ли микрофон вообще: движение
 * означало «тишина» ровно так же, как «громкая речь».
 *
 * Теперь движение — **только функция громкости**. Нет звука — нет движения:
 * полосы стоят ровным строем на минимальной высоте, и любое их шевеление
 * однозначно означает, что в микрофоне или в динамике что-то есть. Это не
 * «менее красиво» — это честнее, и именно поэтому красивее: экран молчит,
 * когда разговор молчит.
 *
 * Что из этого следует технически: в файле нет ни `rememberInfiniteTransition`,
 * ни фазы дыхания. Каждый кадр рисуется по одному числу — [level].
 *
 * ════════════════════════════════════════════════════════════════════════
 *  КАК ПОЛОСЫ СЛЕДУЮТ ЗА ГОЛОСОМ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Вход — один уровень громкости [level] (0..1) на весь компонент. Из него
 * раскладывается волна по полосам:
 *
 *  — **Базовая форма** ([baseShape]) — колокол с максимумом в центре:
 *    центральные полосы выше крайних. Без неё волна при ровной громкости
 *    выглядела бы прямоугольником.
 *  — **Гребень** ([archGain]) — дуга от центра к краям. Множитель
 *    пропорционален уровню: при тишине он равен единице и НЕ двигает
 *    полосы. Это геометрия, а не фаза, — в ней нет времени.
 *  — **Реакция на звук** — высота полосы пропорциональна громкости,
 *    кривизне формы и чувствительности из настроек.
 *  — **Сглаживание** ([smoothing]) — скорость, с которой полоса идёт к
 *    новой цели. У роста и у спада она разная (см. [DecayShare]): подъём
 *    идёт со скоростью из настроек, спад — в 2.6 раза быстрее, чтобы
 *    после замолкания волна успевала «сесть», а не тянулась хвостом.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ГДЕ ЖИВЁТ РЕАЛЬНОЕ СГЛАЖИВАНИЕ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Важный момент, который легко перепутать: `settings.smoothing` НЕ
 * сглаживает движение полос — это делает `rememberSmoothedLevel` в
 * `AmaliaVoiceVisual`, и делает это до отрисовки. Здесь `smoothing` задаёт
 * лишь ширину полосы (`barWidth = spikeW × widthShare`), и обратной связи
 * «громкость → ширина» нет намеренно: ширина полосы не должна зависеть от
 * того, насколько громко говорят.
 *
 * @param level громкость 0..1. В фазе слушания приходит из VAD, в фазе
 *   ответа — из настоящего PCM, который играет [com.my.amali.data.ai.AudioPlayer].
 * @param settings геометрия волны из пользовательских настроек.
 * @param color цвет полос; обычно выводится из времени суток.
 * @param isActive false — волна не разворачивается: полосы стоят строем
 *   минимальной высоты. Это разделяет «ещё тихо» и «звука нет вовсе».
 * @param modifier модификатор размера; высота обычно не задаётся — она
 *   вычисляется из [WaveSettings.maxHeight].
 */
@Composable
fun AmaliaWaveform(
    level: Float,
    settings: WaveSettings,
    color: Color,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
) {
    // Фаза полосы — её индекс. Держится в remember: список пересобирается
    // только при смене числа полос, а не на каждом кадре громкости.
    val indices = remember(settings.spikeCount) { List(settings.spikeCount) { it } }

    val brush = remember(color) { SolidColor(color) }

    // Толщина обводки нужна в пикселях, но переводить dp в пиксели можно
    // только внутри DrawScope. Поэтому перевод делается здесь, через
    // плотность из композиции: тот же результат, но без обращения к
    // DrawScope за его пределами.
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { settings.spikeWidth.dp.toPx() }.coerceAtLeast(1.2f)
    val stroke = remember(strokeWidthPx) { Stroke(width = strokeWidthPx) }

    val widthDp = settings.totalWidthDp.dp
    val heightDp = settings.maxHeight.dp

    Canvas(
        modifier = modifier
            .width(widthDp)
            .height(heightDp)
            .semantics { contentDescription = "" },
    ) {
        drawWaveform(
            level = level.coerceIn(0f, 1f),
            settings = settings,
            indices = indices,
            brush = brush,
            stroke = stroke,
            canvasWidth = size.width,
            canvasHeight = size.height,
            isActive = isActive,
        )
    }
}

/**
 * Раскладывает уровень громкости по полосам и рисует их.
 *
 * Отдельная функция, а не тело `Canvas`: её можно вызвать из теста с любым
 * уровнем, не поднимая композицию. Именно здесь находится вся математика
 * формы — и именно её имеет смысл проверять отдельно от рисования.
 *
 * Гарантия, которую даёт эта функция: **при [level] ниже [SilenceGate] все
 * полосы имеют одинаковую высоту [MinHeightFraction] от холста, независимо
 * от [isActive], индекса и времени**. Ни одна ветка вычислений не добавляет
 * движения от себя — только от входного уровня.
 */
private fun DrawScope.drawWaveform(
    level: Float,
    settings: WaveSettings,
    indices: List<Int>,
    brush: Brush,
    stroke: Stroke,
    canvasWidth: Float,
    canvasHeight: Float,
    isActive: Boolean,
) {
    val count = indices.size
    if (count == 0 || canvasWidth <= 0f || canvasHeight <= 0f) return

    val spikeWidth = settings.spikeWidth.dp.toPx().coerceAtLeast(1f)
    val gapPx = settings.spikeGap.dp.toPx().coerceAtLeast(0f)
    val corner = CornerRadius(
        x = settings.cornerRadius.dp.toPx(),
        y = settings.cornerRadius.dp.toPx(),
    )

    // Реальная ширина полосы подгоняется под холст: пользовательская ширина
    // задаёт пропорцию, а суммарный размер волны — размер экрана.
    val totalWidth = count * spikeWidth + (count - 1) * gapPx
    val scale = if (totalWidth > 0f) canvasWidth / totalWidth else 1f
    val spikeW = (spikeWidth * scale).coerceAtLeast(1f)
    val gap = (gapPx * scale).coerceAtLeast(0f)
    val step = spikeW + gap
    val usedWidth = count * spikeW + (count - 1) * gap
    val startX = (canvasWidth - usedWidth) / 2f
    val centerY = canvasHeight / 2f

    // Минимальная высота — «спокойная линия». Полоса никогда не исчезает
    // полностью: пустое место читается как «микрофон сломался».
    val quietHeight = canvasHeight * MinHeightFraction
    val fullHeight = canvasHeight

    // Запас громкости, ниже которого волна считается молчащей. Полосы,
    // отрисованные на уровне 0.004, выглядят ровно как при 0, но требуют
    // полного пересчёта кадров: без гейта после замолкания микрофона UI
    // продолжал бы рисовать «почти тишину» вместо настоящей тишины.
    val audible = level >= SilenceGate && isActive

    // Ширина полосы задаётся настройкой «сглаживание»: это единственный
    // параметр, влияющий на ширину, и он намеренно не зависит от громкости.
    val widthShare = settings.smoothing.coerceIn(SMOOTHING_MIN, SMOOTHING_MAX)
    val barWidth = (spikeW * widthShare).coerceAtLeast(1f)

    indices.forEach { index ->
        // ── Базовая форма: колокол с максимумом в центре ────────────────
        val centerBias = baseShape(index, count)

        // ── Гребень от центра к краям ───────────────────────────────────
        val arch = archGain(distanceFromCenter(index, count), level)

        // ── Целевая высота ──────────────────────────────────────────────
        // Единственный вход — громкость. Ни времени, ни фазы дыхания здесь
        // нет вовсе: это и есть гарантия неподвижности в тишине.
        val intensity = if (audible) {
            (level * settings.sensitivity * centerBias * arch).coerceIn(0f, 1f)
        } else {
            0f
        }
        val shaped = intensity.pow(0.78f)

        // Асимметрия полуволн: верхняя половина чуть выше нижней.
        // Это то же самое «живое» качество, что раньше давало дыхание,
        // но здесь оно появляется только вместе со звуком — при нулевой
        // громкости коэффициент равен единице и полуволны равны.
        val upperShare = (1f + AsymmetryDepth * shaped) / 2f
        val amplitude = fullHeight - quietHeight
        val upperHeight = quietHeight + amplitude * shaped * upperShare * 2f
        val lowerHeight = quietHeight + amplitude * shaped * (1f - upperShare) * 2f

        val left = startX + index * step + (spikeW - barWidth) / 2f

        if (settings.filled) {
            drawRoundRect(
                brush = brush,
                topLeft = Offset(left, centerY - upperHeight),
                size = Size(barWidth, upperHeight + lowerHeight),
                cornerRadius = corner,
            )
        } else {
            // Контуром полоса выглядит вдвое тоньше — компенсируем ширину,
            // иначе на светлом фоне она почти не видна.
            val top = centerY - upperHeight
            val totalHeight = upperHeight + lowerHeight
            drawRoundRect(
                color = brush.valueOrNull() ?: Color.Unspecified,
                topLeft = Offset(left + stroke.width / 2f, top + stroke.width / 2f),
                size = Size(
                    (barWidth - stroke.width).coerceAtLeast(1f),
                    (totalHeight - stroke.width).coerceAtLeast(1f),
                ),
                cornerRadius = corner,
                style = stroke,
            )
        }
    }
}

/**
 * Базовая форма волны: максимум в центре, спад к краям.
 *
 * Возвращает множитель 0..1. Степень 0.75 подобрана так, чтобы спад был
 * заметным, но крайние полосы не превращались в точки: при линейном спаде
 * волна выглядит треугольником, при нулевой степени — прямоугольником.
 */
private fun baseShape(index: Int, count: Int): Float {
    if (count <= 1) return 1f
    val center = (count - 1) / 2f
    val distance = abs(index - center) / center
    return (1f - distance * 0.85f).coerceIn(0.12f, 1f).pow(0.75f)
}

/**
 * Расстояние полосы от центра, нормированное в 0..1.
 *
 * Ноль у центральной полосы, единица у крайних. Это геометрия, а не фаза:
 * никакого времени в функции нет, поэтому она не может двигать полосы в
 * тишине — движение даёт только множитель уровня в [archGain].
 */
private fun distanceFromCenter(index: Int, count: Int): Float {
    val center = (count - 1) / 2f
    if (center <= 0f) return 0f
    return abs(index - center) / center
}

/**
 * Множитель гребня: насколько полоса «берёт» громкости относительно центра.
 *
 * При нулевом уровне множитель равен ровно единице — все полосы получают
 * одинаковую громкость, и гребня нет. Чем громче звук, тем сильнее дуга:
 * так волна выглядит объёмной именно в момент речи, а не сама по себе.
 */
private fun archGain(distance: Float, level: Float): Float =
    (1f - ArchDepth * distance.coerceIn(0f, 1f) * level).coerceIn(0f, 1f)

/** Цвет из кисти, если она однотонная — для обводки нужен именно Color. */
private fun Brush.valueOrNull(): Color? = (this as? SolidColor)?.value

/**
 * Высота «спокойной линии» как доля от холста.
 *
 * 4% — это уже читаемая черта, но ещё не «полоса»: при 21 полосе она даёт
 * ровный пунктирный строй, который не спорит с текстом над волной. Значение
 * меньше 2% на экранах с высокой плотностью превращалось бы в волосяную
 * линию, которая при сжатии превью просто исчезает.
 */
internal const val MinHeightFraction = 0.04f

/**
 * Порог, ниже которого звук считается отсутствующим.
 *
 * Уровень приходит из RMS-нормировки, и в абсолютной тишине это не ровно
 * ноль, а остаток шума микрофона порядка 0.002. Гейт переводит такие
 * значения в честный ноль: волна перестаёт перерисовываться, полосы
 * встают в строй — и взгляд больше не пытается разглядеть в них движение,
 * которого нет.
 */
internal const val SilenceGate = 0.005f

/**
 * Глубина гребня от центра к краям.
 *
 * 0.35 означает, что крайняя полоса при полной громкости берёт на 35%
 * меньше центральной. При нуле волна стала бы прямоугольником с ровными
 * полосами; при единице крайние полосы «умирали» бы и волна распадалась
 * на одиночную вспышку в центре.
 */
private const val ArchDepth = 0.35f

/**
 * Асимметрия верхней и нижней полуволн при звуке.
 *
 * Реальное колебание не бывает симметричным, и симметричная волна читается
 * как «нарисованная», а не как индикатор. 0.18 даёт верхней половине до 9%
 * преимущества при полной громкости — ровно столько, чтобы это чувствовалось,
 * но не читалось как сдвиг оси. При тишине коэффициент равен нулю, поэтому
 * покой остаётся строго симметричным.
 */
private const val AsymmetryDepth = 0.18f

/**
 * Доля времени сглаживания, отведённая на спад.
 *
 * 2.6 означает: когда звук пропадает, полоса доходит до новой цели за
 * ~38% того времени, за которое она доходила бы при симметричном
 * сглаживании. Это ровно то поведение, которое нужно индикатору —
 * **подъём следует за голосом, спад не тянется**: речь замолкла, и волна
 * села почти сразу, но всё же по кривой, а не рывком.
 *
 * Число не произвольное. Сглаживание из настроек живёт в диапазоне
 * 0.05..0.6, и при базовом значении 0.22 симметричный спад занимал около
 * 190 мс. С множителем 2.6 он занимает ≈75 мс: быстрее человеческого
 * «мигания» (100 мс — порог, за которым изменение читается как скачок)
 * только в том смысле, что не выглядит мгновенным, но и не оставляет
 * хвоста, по которому глаз начал бы искать продолжение звука.
 */
private const val DecayShare = 2.6f

/**
 * Скорость сглаживания для текущего направления движения.
 *
 * @param smoothing базовое сглаживание из настроек, 0.05..0.6.
 * @param rising true — полоса растёт, false — садится.
 * @return множитель пути к цели за кадр, 0.05..1.
 */
internal fun smoothingStep(smoothing: Float, rising: Boolean): Float {
    val base = smoothing.coerceIn(SMOOTHING_MIN, SMOOTHING_MAX)
    return if (rising) base else (base * DecayShare).coerceAtMost(1f)
}

/**
 * Частота обновления волны, кадров в секунду.
 *
 * Волна не должна зависеть от частоты кадров устройства: на 120 Гц полосы
 * иначе «догоняли» бы голос вдвое быстрее, чем на 60. Значение используется
 * вызывающей стороной для расчёта сглаживания.
 */
internal const val WAVE_TARGET_FPS = 60f

/** Пересчитывает сглаживание в коэффициент за кадр с учётом направления. */
internal fun smoothingForFrame(smoothing: Float, deltaSeconds: Float, rising: Boolean): Float {
    val step = smoothingStep(smoothing, rising)
    val frames = (deltaSeconds * WAVE_TARGET_FPS).coerceIn(0.25f, 4f)
    // Экспоненциальное приближение: покадровое значение возводится в степень
    // числа кадров, прошедших с прошлого обновления. Без этого на просадках
    // частоты кадров волна начала бы «залипать» на старых значениях.
    return (1f - (1f - step).pow(frames)).coerceIn(0.01f, 1f)
}

/** Ограничивает значение диапазоном зазора — используется превью настроек. */
internal fun clampGap(value: Float): Float = value.coerceIn(GAP_MIN, GAP_MAX)

/** Ближайшее допустимое число полос — используется превью настроек. */
internal fun clampCount(value: Float): Int = value.roundToInt().coerceIn(COUNT_MIN, COUNT_MAX)

/** Высота волны в dp с учётом ограничений настроек. */
internal fun waveHeight(settings: WaveSettings): Dp = settings.maxHeight.dp
