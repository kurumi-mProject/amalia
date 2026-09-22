package com.my.amali.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.my.amali.ui.theme.AmaliaMotif
import com.my.amali.ui.theme.AmaliaShadow
import com.my.amali.ui.theme.GradientPalette
import com.my.amali.ui.theme.LocalAmaliaShadow
import com.my.amali.ui.theme.LocalLightProfile
import com.my.amali.ui.theme.currentPalette
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * GradientBackground — живой фон под всем интерфейсом.
 *
 * ─────────────────────────────────────────────────────────────
 *  ЧТО БЫЛО СЛОМАНО И ПОЧЕМУ
 * ─────────────────────────────────────────────────────────────
 *
 * Прошлая версия фона рисовала красивую статичную картинку и **не участвовала
 * в адаптации по времени**. Отсюда три конкретных дефекта, которые пришлось
 * лечить архитектурно, а не косметикой:
 *
 *  1. **Виньетка была чёрной константой.** `Color.Black` по краям поверх
 *     тёплого янтарного фона — это не «глубина», а грязь: чёрный на тёплом
 *     выглядит как выгоревшая область. Теперь виньетка берётся из палитры
 *     ([GradientPalette.shadow]) и тонируется ведущим тоном.
 *  2. **Не было слоя глубины.** Плоская заливка + пятна не создают
 *     пространства, из-за чего все стеклянные карточки «висели» на одном
 *     уровне и интерфейс выглядел наклеенным. Добавлен слой мягких
 *     тонированных теней ([BackdropCanvas]).
 *  3. **Блик был всегда белым.** На тёплом фоне белый блик = наклейка.
 *     Теперь он тонируется текущим светом.
 *
 * ─────────────────────────────────────────────────────────────
 *  СЛОИ (снизу вверх) И ПОЧЕМУ ИХ ДВА, А НЕ ПЯТЬ
 * ─────────────────────────────────────────────────────────────
 *
 *  1. **[BackdropCanvas]** — один проход, в котором рисуются по порядку:
 *       база (вертикальный градиент палитры) → аурора (три крупных пятна,
 *       дрейфующих по эллипсам с периодами 90/70/110 с) → глубина (две
 *       тонированные тени по нижним углам + «высота неба» сверху) →
 *       виньетка (тонированные, не чёрные края) → верхний блик.
 *
 *     Раньше это были ЧЕТЫРЕ отдельных Canvas на весь экран: каждый кадр
 *     GPU заполнял четыре полных экрана градиентами. На старых телефонах
 *     именно fill-rate фона съедал кадры. Слияние в один Canvas — та же
 *     картинка, вчетверо меньше полноэкранных проходов.
 *
 *  2. **[MotifLayer]** — лепестки/листья/снег/звёзды/светлячки поверх
 *     готового фона, как предметы в воздухе, а не часть градиента.
 *
 * Палитра берётся из [currentPalette] (то есть из темы). Это принципиально:
 * фон и акцентные цвета схемы — текст, иконки, волна — обязаны быть одним и
 * тем же временем суток, иначе интерфейс выглядит перекрашенным наполовину.
 *
 * ─────────────────────────────────────────────────────────────
 *  ТЕМП ОБНОВЛЕНИЯ — 10 ГЦ, И ЭТО НЕ ЭКОНОМИЯ РАДИ ЭКОНОМИИ
 * ─────────────────────────────────────────────────────────────
 *
 * Дрейф ауроры — периоды 70–110 секунд. За 100 мс пятно проходит 0.1%
 * своего пути: глаз физически не способен отличить 10 Гц от 60 Гц на
 * движении, которое за минуту смещается на диаметр пятна. Раньше фаза
 * дрейфа шла через `InfiniteTransition` — три аниматора перерисовывали
 * весь фон 60 раз в секунду. Теперь один медленный тик кормит отрисовку
 * (чтение состояния — в фазе draw, без рекомпозиции), и фон стоит на
 * слабом железе ровно столько же, сколько стоит аурора.
 *
 * @param intensity 0..1 — общая сила свечения (настройка «интенсивность стекла»).
 * @param motif декоративный слой; [AmaliaMotif.OFF] выключает его полностью.
 * @param motifDensity 0..1 — густота декораций.
 * @param luminance 0.42..1 — рекомендованная яркость светлой части контента;
 *   приходит из [com.my.amali.ui.theme.CircadianEngine] и гасит фон ночью.
 */
@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 0.75f,
    motif: AmaliaMotif = AmaliaMotif.AUTO,
    motifDensity: Float = 1f,
    palette: GradientPalette = currentPalette,
    luminance: Float = 1f,
) {
    val shadow = LocalAmaliaShadow.current
    val light = LocalLightProfile.current
    val strength = intensity.coerceIn(0f, 1f) * luminance.coerceIn(0.42f, 1f)

    val drift = rememberDriftPhase()

    Box(modifier = modifier) {
        BackdropCanvas(
            palette = palette,
            shadow = shadow,
            intensity = strength,
            drift = drift,
            modifier = Modifier.fillMaxSize(),
        )
        // ══════════════════════════════════════════════════════════════════
        //  Мотив рисуется ПОСЛЕ всего фона — порядок «предмет за предметом».
        //  Раньше мотив стоял ДО виньетки и блика, и они гасили лепестки;
        //  теперь фон готов целиком, и мотив лежит поверх, как в воздухе.
        // ══════════════════════════════════════════════════════════════════
        MotifLayer(
            motif = motif,
            density = motifDensity,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Медленная фаза дрейфа ауроры, в секундах.
 *
 * `State` возвращается намеренно, а не «сырое» значение: чтение происходит
 * внутри draw-фазы [BackdropCanvas], поэтому каждый тик перерисовывает
 * канву, НЕ рекомпозируя дерево. Частота — [DRIFT_TICK_MS]: для периодов
 * 70–110 секунд это неотличимо от плавного дрейфа.
 */
@Composable
private fun rememberDriftPhase(): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val step = DRIFT_TICK_MS / 1000f
        while (true) {
            delay(DRIFT_TICK_MS)
            phase.floatValue += step
        }
    }
    return phase
}

/**
 * Весь статичный по слоям фон — один Canvas, один полноэкранный проход.
 *
 * @param drift медленная фаза дрейфа ауроры в секундах (см. [rememberDriftPhase]).
 */
@Composable
private fun BackdropCanvas(
    palette: GradientPalette,
    shadow: AmaliaShadow,
    intensity: Float,
    drift: State<Float>,
    modifier: Modifier = Modifier,
) {
    val light = LocalLightProfile.current
    val strength = intensity.coerceIn(0f, 1f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val unit = maxOf(w, h)
        // Чтение фазы — здесь, в draw-фазе: тик меняет только перерисовку.
        val t = drift.value

        // ── 1. База ──────────────────────────────────────────────────────
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = palette.stops
                    .map { it.position to it.color }
                    .toTypedArray(),
                startY = 0f,
                endY = h,
            ),
        )

        // ── 2. Аурора: три дрейфующих пятна ─────────────────────────────
        // Периоды 90/70/110 с. Позиции — эллипсы вокруг «якорей»; радиусы
        // привязаны к диагонали, а не к ширине: на планшете и в ландшафте
        // пятна не должны схлопываться в узкие полосы.
        val p1 = TAU * ((t % 90f) / 90f)
        val p2 = TAU * ((t % 70f) / 70f)
        val p3 = TAU * ((t % 110f) / 110f)
        val auroras = palette.auroras.ifEmpty { palette.stops.map { it.color } }
        val spots = listOf(
            Triple(p1, Offset(0.22f, 0.18f), 0.95f),
            Triple(p2, Offset(0.84f, 0.40f), 0.80f),
            Triple(p3, Offset(0.46f, 0.92f), 1.05f),
        )
        spots.forEachIndexed { index, (phase, anchor, scale) ->
            val color = auroras[index % auroras.size]
            val cx = w * (anchor.x + 0.09f * cos(phase + index))
            val cy = h * (anchor.y + 0.06f * sin(phase * 1.3f + index))
            val radius = unit * 0.52f * scale
            val alpha = (if (palette.isDark) 0.26f else 0.34f) *
                strength * (0.85f + 0.15f * sin(phase))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }

        // ── 3. Глубина: две тени по нижним углам + «высота неба» ────────
        // Тени тонированные: чёрная тень на тёплом фоне убивает адаптацию
        // света мгновенно. В тёмной теме слой слабее (глубину даёт контраст
        // стекла), в светлой сильнее — иначе светлый фон совсем плоский.
        val depthAlpha = (if (palette.isDark) 0.34f else 0.16f) * (0.5f + strength * 0.5f)
        val lowTone = if (palette.isWarm) {
            palette.auroras.lastOrNull() ?: shadow.color
        } else {
            palette.auroras.getOrNull(1) ?: shadow.color
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    lerp(shadow.color, lowTone, 0.35f).copy(alpha = depthAlpha),
                    Color.Transparent,
                ),
                center = Offset(w * 0.10f, h * 1.02f),
                radius = unit * 0.78f,
            ),
            radius = unit * 0.78f,
            center = Offset(w * 0.10f, h * 1.02f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    shadow.color.copy(alpha = depthAlpha * 0.85f),
                    Color.Transparent,
                ),
                center = Offset(w * 0.94f, h * 0.96f),
                radius = unit * 0.66f,
            ),
            radius = unit * 0.66f,
            center = Offset(w * 0.94f, h * 0.96f),
        )
        val highTone = palette.auroras.firstOrNull() ?: palette.stops.first().color
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    highTone.copy(alpha = depthAlpha * 0.30f),
                    Color.Transparent,
                ),
                center = Offset(w * 0.5f, -h * 0.08f),
                radius = unit * 0.60f,
            ),
            radius = unit * 0.60f,
            center = Offset(w * 0.5f, -h * 0.08f),
        )

        // ── 4. Виньетка: тонированные края ───────────────────────────────
        val edge = if (palette.isDark) {
            shadow.color
        } else {
            lerp(shadow.color, Color(0xFF8E8A7E), 0.35f)
        }
        val edgeAlpha = if (palette.isDark) 0.50f else 0.16f
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    edge.copy(alpha = edgeAlpha),
                ),
                center = Offset(w / 2f, h * 0.42f),
                radius = unit * 0.78f,
            ),
        )

        // ── 5. Верхний блик: «стекло ловит свет сверху» ──────────────────
        // Ночью экран не «светит» сверху; днём блик читается как
        // преломление на стекле. Лежит поверх виньетки, но ПОД мотивом —
        // частицы не должны выглядеть наклеенными.
        val tone = if (palette.isWarm) {
            palette.auroras.lastOrNull() ?: Color.White
        } else {
            Color.White
        }
        val peak = (if (palette.isDark) 0.035f else 0.10f) *
            palette.sheen * 8f *
            light.displayLuminance.coerceIn(0.42f, 1f)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    tone.copy(alpha = peak),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = h * 0.32f,
            ),
        )
    }
}

private val TAU = (2 * PI).toFloat()

/** Тик дрейфа ауроры: 10 Гц — предел, после которого движение неотличимо. */
private const val DRIFT_TICK_MS = 100L
