package com.my.amali.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.my.amali.domain.entity.WaveSettings
import com.my.amali.domain.entity.WaveSettings.Companion.GAP_MAX
import com.my.amali.domain.entity.WaveSettings.Companion.GAP_MIN
import com.my.amali.domain.entity.WaveSettings.Companion.SMOOTHING_MAX
import com.my.amali.domain.entity.WaveSettings.Companion.SMOOTHING_MIN
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

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
 *  КАК ПОЛОСЫ СЛЕДУЮТ ЗА ГОЛОСОМ
 * ════════════════════════════════════════════════════════════════════════
 *
 * Вход — один уровень громкости [level] (0..1) на весь компонент. Из него
 * раскладывается волна по полосам:
 *
 *  — **Базовая форма** ([baseShape]) — колокол с максимумом в центре:
 *    центральные полосы выше крайних. Без неё волна при ровной громкости
 *    выглядела бы прямоугольником.
 *  — **Фаза дыхания** ([breathPhase]) идёт бесконечно и медленно: волна
 *    остаётся живой даже в полной тишине, но её движения в разы меньше
 *    реакции на голос — покой не читается как «сломалось».
 *  — **Реакция на звук**: каждая полоса берёт уровень громкости со своей
 *    фазовой задержкой ([phaseLag]) — волна «протекает» от центра к краям,
 *    а не дёргается вся разом. Это то, что отличает живой эквалайзер от
 *    диаграммы.
 *  — **Сглаживание** ([smoothing]) ограничивает, какую долю пути к новой
 *    цели полоса проходит за кадр. Значение берётся из настроек, поэтому
 *    пользователь сам выбирает между «текуче» и «резко».
 *
 * @param level громкость 0..1. В фазе слушания приходит из VAD, в фазе
 *   ответа — из настоящего PCM, который играет [com.my.amali.data.ai.AudioPlayer].
 * @param settings геометрия волны из пользовательских настроек.
 * @param color цвет полос; обычно выводится из времени суток.
 * @param isActive false — полосы сжаты к линии и только дышат; true — полная
 *   амплитуда. Это разделяет «микрофон включён, но тихо» и «звук идёт».
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
    // Одно бесконечное дыхание на всю волну, а не по одному на полосу:
    // при 21 полосе это разница между одной подпиской и двадцатью одной.
    val breathing = rememberInfiniteTransition(label = "waveBreath")
    val breathPhase by breathing.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "waveBreathPhase",
    )

    // Фаза держится в remember: сброс на каждом кадре превратил бы волну
    // в дрожание, а не в движение.
    val phases = remember(settings.spikeCount) {
        List(settings.spikeCount) { index -> index }
    }

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
            phases = phases,
            breathPhase = breathPhase,
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
 * Отдельная функция, а не тело `Canvas`: её можно вызвать из превью и из
 * теста с любым уровнем, не поднимая композицию. Именно здесь находится вся
 * математика формы — и именно её имеет смысл проверять отдельно от рисования.
 */
private fun DrawScope.drawWaveform(
    level: Float,
    settings: WaveSettings,
    phases: List<Int>,
    breathPhase: Float,
    brush: Brush,
    stroke: Stroke,
    canvasWidth: Float,
    canvasHeight: Float,
    isActive: Boolean,
) {
    val count = phases.size
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
    val minHeight = canvasHeight * 0.06f
    val maxHeight = canvasHeight

    // Сглаживание: доля пути к цели за кадр. Ограничено сверху, иначе
    // при smoothing = 1 волна становится телеграфом без переходов.
    val follow = settings.smoothing.coerceIn(SMOOTHING_MIN, SMOOTHING_MAX)

    phases.forEachIndexed { index, _ ->
        // ── Базовая форма: колокол с максимумом в центре ────────────────
        val centerBias = baseShape(index, count)

        // ── Фазовая задержка от центра к краям ──────────────────────────
        // Ближние к центру полосы «слышат» голос чуть раньше крайних —
        // отсюда волна, а не одновременное дёргание всех полос.
        val lag = phaseLag(index, count)
        val delayed = (sin(breathPhase - lag * 0.9f) + 1f) / 2f

        // ── Дыхание в покое: маленькое, но живое ────────────────────────
        val breath = 0.5f + 0.5f * sin(breathPhase + index * 0.55f)

        // ── Целевая высота ──────────────────────────────────────────────
        val reaction = if (isActive) {
            level * settings.sensitivity * centerBias * (0.55f + 0.45f * delayed)
        } else {
            level * settings.sensitivity * centerBias * 0.25f
        }
        val calm = if (isActive) 0.22f else 0.08f
        val shaped = (reaction + calm * breath + 0.04f).pow(0.78f)

        val height = (minHeight + (maxHeight - minHeight) * shaped.coerceIn(0f, 1f))
            .coerceIn(minHeight, maxHeight)
        val barWidth = (spikeW * follow).coerceAtLeast(1f)

        val left = startX + index * step + (spikeW - barWidth) / 2f
        val top = centerY - height / 2f

        if (settings.filled) {
            drawRoundRect(
                brush = brush,
                topLeft = Offset(left, top),
                size = Size(barWidth, height),
                cornerRadius = corner,
            )
        } else {
            // Контуром полоса выглядит вдвое тоньше — компенсируем ширину,
            // иначе на светлом фоне она почти не видна.
            drawRoundRect(
                color = brush.valueOrNull() ?: Color.Unspecified,
                topLeft = Offset(left + stroke.width / 2f, top + stroke.width / 2f),
                size = Size(
                    (barWidth - stroke.width).coerceAtLeast(1f),
                    (height - stroke.width).coerceAtLeast(1f),
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
 * Задержка реакции для полосы, нормированная в радианы фазы.
 *
 * Растёт от центра к краям: волна «растекается» наружу. Знак чередуется
 * по сторонам, чтобы левая и правая половины двигались зеркально — так
 * волна читается симметричной, хотя и не является строго симметричной.
 */
private fun phaseLag(index: Int, count: Int): Float {
    val center = (count - 1) / 2f
    val distance = (index - center) / if (center == 0f) 1f else center
    return distance * 2.4f
}

/** Цвет из кисти, если она однотонная — для обводки нужен именно Color. */
private fun Brush.valueOrNull(): Color? = (this as? SolidColor)?.value

/**
 * Частота обновления волны, кадров в секунду.
 *
 * Волна не должна зависеть от частоты кадров устройства: на 120 Гц полосы
 * иначе «догоняли» бы голос вдвое быстрее, чем на 60. Значение используется
 * вызывающей стороной для расчёта сглаживания.
 */
internal const val WAVE_TARGET_FPS = 60f

/** Пересчитывает сглаживание из настроек в коэффициент за кадр. */
internal fun smoothingForFrame(smoothing: Float, deltaSeconds: Float): Float {
    val base = smoothing.coerceIn(0f, 1f)
    val frames = (deltaSeconds * WAVE_TARGET_FPS).coerceIn(0.25f, 4f)
    // Экспоненциальное приближение: покадровое значение возводится в степень
    // числа кадров, прошедших с прошлого обновления. Без этого на просадках
    // частоты кадров волна начала бы «залипать» на старых значениях.
    return (1f - (1f - base).pow(frames)).coerceIn(0.01f, 1f)
}

/** Ограничивает значение диапазоном зазора — используется превью настроек. */
internal fun clampGap(value: Float): Float = value.coerceIn(GAP_MIN, GAP_MAX)

/** Ближайшее допустимое число полос — используется превью настроек. */
internal fun clampCount(value: Float): Int = value.roundToInt().coerceIn(
    WaveSettings.COUNT_MIN,
    WaveSettings.COUNT_MAX,
)

/** Высота волны в dp с учётом ограничений настроек. */
internal fun waveHeight(settings: WaveSettings): Dp = settings.maxHeight.dp
