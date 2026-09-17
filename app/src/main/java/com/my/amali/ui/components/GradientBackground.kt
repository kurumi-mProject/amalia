package com.my.amali.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.my.amali.ui.theme.AmaliaMotif
import com.my.amali.ui.theme.AmaliaShadow
import com.my.amali.ui.theme.CircadianEngine
import com.my.amali.ui.theme.GradientPalette
import com.my.amali.ui.theme.LocalAmaliaShadow
import com.my.amali.ui.theme.LocalLightProfile
import com.my.amali.ui.theme.currentPalette
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
 *     тонированных теней ([AmbientDepthCanvas]).
 *  3. **Блик был всегда белым.** На тёплом фоне белый блик = наклейка.
 *     Теперь он тонируется текущим светом.
 *
 * ─────────────────────────────────────────────────────────────
 *  СЛОИ (снизу вверх)
 * ─────────────────────────────────────────────────────────────
 *
 *  1. **база** — вертикальный градиент палитры;
 *  2. **аурора** — три крупных пятна, дрейфующих по эллипсам с периодами
 *     90/70/110 с. Движение медленное настолько, чтобы не отвлекать, но
 *     экран перестаёт быть «мёртвым»;
 *  3. **глубина** — две мягкие тени по нижним углам, дающие объём;
 *  4. **мотив** ([MotifLayer]) — лепестки/листья/снег/звёзды/светлячки:
 *     визуальная причина смены палитры;
 *  5. **виньетка** — тонированные (не чёрные) края, чтобы контент в центре
 *     читался, а не спорил с фоном;
 *  6. **верхний блик** — «стекло ловит свет сверху», поверх мотивов, чтобы
 *     частицы не выглядели наклеенными.
 *
 * Палитра берётся из [currentPalette] (то есть из темы). Это принципиально:
 * фон и акцентные цвета схемы — текст, иконки, волна — обязаны быть одним и
 * тем же временем суток, иначе интерфейс выглядит перекрашенным наполовину.
 *
 * @param intensity 0..1 — общая сила свечения (настройка «интенсивность стекла»).
 * @param motif декоративный слой; [AmaliaMotif.OFF] выключает его полностью.
 * @param motifDensity 0..1 — густота декораций.
 * @param luminance 0.42..1 — рекомендованная яркость светлой части контента;
 *   приходит из [CircadianEngine] и гасит фон ночью.
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

    Box(modifier = modifier) {
        AuroraCanvas(
            palette = palette,
            intensity = strength,
            modifier = Modifier.fillMaxSize(),
        )
        AmbientDepthCanvas(
            palette = palette,
            shadow = shadow,
            intensity = strength,
            modifier = Modifier.fillMaxSize(),
        )
        VignetteCanvas(
            palette = palette,
            shadow = shadow,
            modifier = Modifier.fillMaxSize(),
        )
        // ══════════════════════════════════════════════════════════════════
        //  ПОРЯДОК СЛОЁВ — вторая причина «сакуры не видно»
        // ══════════════════════════════════════════════════════════════════
        //
        // Раньше мотив рисовался ДО виньетки и блика:
        //
        //   1. Aurora      2. AmbientDepth      3. MotifLayer
        //   4. Vignette    5. Sheen
        //
        // Виньетка затемняет края, а Sheen добавляет светлый блик сверху —
        // оба слоя ложились **поверх** лепестков и гасили их. Особенно
        // страдал верх экрана, где блик самый сильный: лепесток там
        // превращался в еле заметное пятно.
        //
        // Теперь порядок обратный: сначала фон целиком (аурора + глубина +
        // виньетка + блик), затем мотив. Лепестки рисуются поверх готового
        // фона — как предметы в воздухе, а не как часть градиента. Это же
        // соответствует физике: объект перед нами, а не за ним.
        SheenCanvas(
            dark = palette.isDark,
            palette = palette,
            strength = light.displayLuminance,
            modifier = Modifier.fillMaxSize(),
        )
        MotifLayer(
            motif = motif,
            density = motifDensity,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Отрисовка ауроры: база + дрейфующие пятна. */
@Composable
private fun AuroraCanvas(
    palette: GradientPalette,
    intensity: Float,
    modifier: Modifier = Modifier,
) {
    val drift = rememberInfiniteTransition(label = "aurora")
    val p1 by drift.animateFloat(
        initialValue = 0f,
        targetValue = TAU,
        animationSpec = infiniteRepeatable(tween(90_000, easing = LinearEasing)),
        label = "p1",
    )
    val p2 by drift.animateFloat(
        initialValue = 0f,
        targetValue = TAU,
        animationSpec = infiniteRepeatable(tween(70_000, easing = LinearEasing)),
        label = "p2",
    )
    val p3 by drift.animateFloat(
        initialValue = 0f,
        targetValue = TAU,
        animationSpec = infiniteRepeatable(tween(110_000, easing = LinearEasing)),
        label = "p3",
    )

    val strength = intensity.coerceIn(0f, 1f)
    val auroras = palette.auroras.ifEmpty { palette.stops.map { it.color } }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colorStops = palette.stops
                    .map { it.position to it.color }
                    .toTypedArray(),
                startY = 0f,
                endY = h,
            ),
        )

        // Три дрейфующих пятна. Позиции — эллипсы вокруг «якорей».
        // Радиусы привязаны к диагонали, а не к ширине: на планшете и в
        // ландшафте пятна не должны схлопываться в узкие полосы.
        val spots = listOf(
            Triple(p1, Offset(0.22f, 0.18f), 0.95f),
            Triple(p2, Offset(0.84f, 0.40f), 0.80f),
            Triple(p3, Offset(0.46f, 0.92f), 1.05f),
        )
        val unit = maxOf(w, h)
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
    }
}

/**
 * Слой пространственной глубины.
 *
 * Две мягкие тени по нижним углам — «земля под стеклом». Без них фон выглядит
 * плоской заливкой, и все стеклянные поверхности висят на одном уровне, что
 * читается как дешёвая вёрстка. Тени **тонированные**: чёрная тень на тёплом
 * фоне убивает адаптацию света мгновенно.
 *
 * В тёмной теме слой слабее (там глубину даёт сам контраст стекла), в светлой
 * сильнее — иначе светлый фон становится совсем плоским.
 */
@Composable
private fun AmbientDepthCanvas(
    palette: GradientPalette,
    shadow: AmaliaShadow,
    intensity: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val unit = maxOf(w, h)
        val base = if (palette.isDark) 0.34f else 0.16f
        val alpha = base * (0.5f + intensity * 0.5f)

        // Нижний левый угол — тёплое пятно, если свет тёплый, иначе холодное.
        val lowTone = if (palette.isWarm) {
            palette.auroras.lastOrNull() ?: shadow.color
        } else {
            palette.auroras.getOrNull(1) ?: shadow.color
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    lerp(shadow.color, lowTone, 0.35f).copy(alpha = alpha),
                    Color.Transparent,
                ),
                center = Offset(w * 0.10f, h * 1.02f),
                radius = unit * 0.78f,
            ),
            radius = unit * 0.78f,
            center = Offset(w * 0.10f, h * 1.02f),
        )

        // Нижний правый — основной объём.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    shadow.color.copy(alpha = alpha * 0.85f),
                    Color.Transparent,
                ),
                center = Offset(w * 0.94f, h * 0.96f),
                radius = unit * 0.66f,
            ),
            radius = unit * 0.66f,
            center = Offset(w * 0.94f, h * 0.96f),
        )

        // Верхняя «высота неба» — лёгкое свечение у верхней кромки.
        val highTone = palette.auroras.firstOrNull() ?: palette.stops.first().color
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    highTone.copy(alpha = alpha * 0.30f),
                    Color.Transparent,
                ),
                center = Offset(w * 0.5f, -h * 0.08f),
                radius = unit * 0.60f,
            ),
            radius = unit * 0.60f,
            center = Offset(w * 0.5f, -h * 0.08f),
        )
    }
}

/**
 * Виньетка — тонированные края, а не чёрные.
 *
 * Раньше здесь был `Color.Black` с альфой 0.55: на холодном фоне это
 * работало, на тёплом превращалось в грязные бурые края. Теперь цвет
 * виньетки — производное от палитры и её тени, поэтому янтарный вечер
 * получает тёплое затемнение, а ледяное утро — холодное.
 */
@Composable
private fun VignetteCanvas(
    palette: GradientPalette,
    shadow: AmaliaShadow,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val unit = maxOf(w, h)

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
    }
}

/**
 * Верхний блик — отдельный слой, чтобы лежать поверх декораций.
 *
 * Тонируется текущим светом и гасится вместе с рекомендованной яркостью:
 * ночью экран не должен «светить» сверху, днём блик читается как настоящее
 * преломление на стекле.
 */
@Composable
private fun SheenCanvas(
    dark: Boolean,
    palette: GradientPalette,
    strength: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val tone = if (palette.isWarm) {
            palette.auroras.lastOrNull() ?: Color.White
        } else {
            Color.White
        }
        val peak = (if (dark) 0.035f else 0.10f) *
            palette.sheen * 8f *
            strength.coerceIn(0.42f, 1f)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    tone.copy(alpha = peak),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = size.height * 0.32f,
            ),
        )
    }
}

private val TAU = (2 * PI).toFloat()
