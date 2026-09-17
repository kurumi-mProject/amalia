package com.my.amali.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import com.my.amali.ui.theme.AmaliaMotif
import com.my.amali.ui.theme.MotifBehavior
import com.my.amali.ui.theme.behavior
import com.my.amali.ui.theme.currentPalette
import com.my.amali.ui.theme.previewColors
import com.my.amali.ui.theme.resolveMotif
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * MotifLayer — декоративный слой поверх ауроры и под контентом.
 *
 * ## Как это нарисовано и почему не ест кадры
 *
 * Позиция каждой частицы считается **аналитически** от одной общей фазы `t`:
 * никакого состояния на частицу и никаких списков, пересобираемых каждый кадр.
 * На кадр меняется одно float-значение, и обновляется оно не чаще
 * [FRAME_BUDGET_MS] мс — 30 Гц для фоновых лепестков более чем достаточно и
 * вдвое дешевле 60 Гц.
 *
 * Форма — один unit-путь (1×1 вокруг нуля), масштабируемый трансформацией.
 * Path не строится заново на каждую частицу в каждом кадре: это главный
 * источник alloc-лагов в подобных слоях.
 *
 * [density] = 0 или мотив OFF → слой не появляется в дереве вообще.
 *
 * @param motif что рисуем; [AmaliaMotif.AUTO] разрешается через палитру.
 * @param density 0..1 — множитель числа частиц и их плотности.
 * @param modifier размеры слоя; по умолчанию — на весь экран.
 */
@Composable
fun MotifLayer(
    modifier: Modifier = Modifier,
    motif: AmaliaMotif = AmaliaMotif.AUTO,
    density: Float = 1f,
) {
    val palette = currentPalette
    val resolved = resolveMotif(motif, palette)
    val spec = resolved.behavior()
    if (spec == null || density <= 0.01f) return

    val shape = remember(resolved) { MotifShape.of(resolved) }
    val pixelsPerDp = LocalDensity.current.density
    val particles = remember(resolved, density) {
        buildParticles(resolved, spec, density)
    }

    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(resolved) {
        var elapsed = 0f
        while (true) {
            kotlinx.coroutines.delay(FRAME_BUDGET_MS)
            elapsed += FRAME_BUDGET_MS / 1000f
            phase = elapsed
        }
    }

    val mainColor = palette.motifAccent.takeIf { it != Color.Unspecified }
        ?: palette.auroras.firstOrNull()
        ?: Color.White
    val altColor = palette.motifAccentAlt.takeIf { it != Color.Unspecified } ?: mainColor

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Wrap по высоте с запасом, чтобы частица «выходила» за нижний край
        // и возвращалась сверху без видимого телепорта.
        val span = h + 2f * MARGIN_PX
        val t = phase

        particles.forEach { p ->
            val color = mix(mainColor, altColor, p.tint)
            val radius = p.size * pixelsPerDp * p.depth
            val alpha = (if (palette.isDark) 0.62f else 0.78f) * (0.4f + 0.6f * p.depth)

            if (spec.falling) {
                val y = ((p.y * span + t * p.fallSpeed * span * p.depth) % span) - MARGIN_PX
                val sway = sin(t * p.swayFreq * TAU + p.phase) * w * spec.sway * p.depth
                // fold имитирует поворот лепестка вокруг вертикальной оси:
                // он то раскрывается плашмя, то складывается в профиль.
                val fold = 0.35f + 0.65f * cos(t * p.spin * TAU + p.phase)
                translate(left = p.x * w + sway, top = y) {
                    rotate(cos(t * p.spin * TAU + p.phase) * MAX_TILT * p.spinSign) {
                        scale(scaleX = radius, scaleY = radius * fold) {
                            shape.draw(this, color.copy(alpha = alpha), radius)
                        }
                    }
                }
            } else {
                val twinkle = 0.45f + 0.55f * sin(t * p.pulseRate * TAU + p.phase)
                val drift = spec.drift * w * sin(t * 0.25f + p.phase)
                val center = Offset(
                    x = p.x * w + drift,
                    y = p.y * h + cos(t * 0.18f + p.phase) * h * if (spec.drift > 0f) 0.02f else 0f,
                )
                drawGlow(color, center, radius, twinkle)
                translate(left = center.x, top = center.y) {
                    rotate(twinkle * TILT_STARS) {
                        scale(scaleX = radius, scaleY = radius) {
                            shape.draw(this, color.copy(alpha = alpha * twinkle), radius)
                        }
                    }
                }
            }
        }
    }
}

/**
 * MotifSwatch — статичная миниатюра мотива для настроек.
 *
 * Рисует три формы в тех же координатах, что и живой слой, но без анимации:
 * шесть пульсирующих канвасов в списке настроек стоили бы кадров, а пользователю
 * нужен лишь ответ на вопрос «что это за мотив».
 */
@Composable
fun MotifSwatch(
    motif: AmaliaMotif,
    modifier: Modifier = Modifier,
    dark: Boolean = true,
) {
    val (main, alt) = motif.previewColors()
    val shape = remember(motif) { MotifShape.of(motif) }
    Canvas(modifier) {
        if (motif == AmaliaMotif.OFF) {
            drawCircle(
                color = main.copy(alpha = 0.35f),
                radius = size.minDimension * 0.28f,
                center = Offset(size.width / 2f, size.height / 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.05f),
            )
            return@Canvas
        }
        val anchor = if (dark) 1f else 0.85f
        val falling = motif.behavior()?.falling ?: true
        val spots = listOf(
            Triple(0.28f, 0.62f, 0.30f),
            Triple(0.55f, 0.34f, 0.42f),
            Triple(0.74f, 0.68f, 0.24f),
        )
        spots.forEachIndexed { index, (fx, fy, scale) ->
            val color = if (index % 2 == 0) main else alt
            val radius = size.minDimension * scale
            if (!falling) {
                drawGlow(
                    color = color.copy(alpha = 0.5f * anchor),
                    center = Offset(fx * size.width, fy * size.height),
                    radius = radius,
                    strength = 1f,
                )
            }
            translate(left = fx * size.width, top = fy * size.height) {
                rotate((index - 1) * 22f) {
                    scale(scaleX = radius, scaleY = radius * 0.85f) {
                        shape.draw(this, color.copy(alpha = 0.9f * anchor), radius)
                    }
                }
            }
        }
    }
}

/**
 * Набор частиц мотива.
 *
 * Случайность — с фиксированным семенем: при любой пересборке композиции
 * «дождь» остаётся тем же, а не перескакивает на новые позиции.
 */
private fun buildParticles(
    motif: AmaliaMotif,
    spec: MotifBehavior,
    density: Float,
): List<MotifParticle> {
    val count = (spec.count * density.coerceIn(0f, 1f)).roundToInt().coerceIn(3, MAX_PARTICLES)
    val random = Random(motif.ordinal * 7919 + count)
    return List(count) { index ->
        MotifParticle(
            x = random.nextFloat(),
            y = random.nextFloat(),
            size = spec.minSize + random.nextFloat() * (spec.maxSize - spec.minSize),
            fallSpeed = 0.035f + random.nextFloat() * 0.055f,
            swayFreq = 0.25f + random.nextFloat() * 0.5f,
            spin = spec.spin * (0.6f + random.nextFloat() * 0.8f),
            spinSign = if (index % 2 == 0) 1f else -1f,
            phase = random.nextFloat() * TAU,
            tint = random.nextFloat(),
            depth = 0.45f + random.nextFloat() * 0.55f,
            pulseRate = 0.35f + random.nextFloat() * 0.65f,
        )
    }
}

/** Мягкое свечение под «висячими» частицами: радиальный градиент вместо размытия. */
private fun DrawScope.drawGlow(color: Color, center: Offset, radius: Float, strength: Float) {
    val glow = radius * GLOW_RATIO
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.30f * strength), Color.Transparent),
            center = center,
            radius = glow,
        ),
        radius = glow,
        center = center,
    )
}

private fun mix(from: Color, to: Color, k: Float): Color = Color(
    red = from.red + (to.red - from.red) * k,
    green = from.green + (to.green - from.green) * k,
    blue = from.blue + (to.blue - from.blue) * k,
    alpha = from.alpha,
)

/**
 * Геометрия мотива в unit-координатах.
 *
 * Каждый вариант рисует себя сам, потому что «просто путь» плохо описывает
 * снежинку (это штрихи) и светлячка (это ядро без формы, зато со свечением).
 * [scope] передаётся явно: формы вызываются внутри transform, и явный
 * получатель проще читать, чем цепочку неявных ресиверов.
 */
private sealed interface MotifShape {
    fun draw(scope: DrawScope, color: Color, radius: Float)

    companion object {
        fun of(motif: AmaliaMotif): MotifShape = when (motif) {
            AmaliaMotif.SAKURA -> Petal
            AmaliaMotif.MAPLE -> Leaf
            AmaliaMotif.SNOW -> Snowflake
            AmaliaMotif.STARS -> Star
            AmaliaMotif.FIREFLY -> Firefly
            AmaliaMotif.OFF, AmaliaMotif.AUTO -> Dot
        }
    }
}

/** Лепесток сакуры: капля с выемкой на широком конце. */
private object Petal : MotifShape {
    private val path = Path().apply {
        moveTo(0f, -0.5f)
        cubicTo(0.40f, -0.34f, 0.36f, 0.26f, 0.10f, 0.46f)
        cubicTo(0.04f, 0.51f, 0.02f, 0.40f, 0f, 0.34f)
        cubicTo(-0.02f, 0.40f, -0.04f, 0.51f, -0.10f, 0.46f)
        cubicTo(-0.36f, 0.26f, -0.40f, -0.34f, 0f, -0.5f)
        close()
    }

    override fun draw(scope: DrawScope, color: Color, radius: Float) {
        with(scope) {
            drawPath(path, color)
            // Внутренняя подсветка: половина лепестка светлее — он перестаёт
            // быть плоским пятном и выглядит как тонкая ткань.
            drawPath(path, Color.White.copy(alpha = 0.16f * color.alpha))
        }
    }
}

/** Лист: асимметричная капля с острым кончиком и черешком. */
private object Leaf : MotifShape {
    private val path = Path().apply {
        moveTo(0f, -0.52f)
        cubicTo(0.44f, -0.20f, 0.36f, 0.30f, 0.04f, 0.44f)
        lineTo(0f, 0.54f)
        lineTo(-0.04f, 0.44f)
        cubicTo(-0.36f, 0.30f, -0.44f, -0.20f, 0f, -0.52f)
        close()
    }

    override fun draw(scope: DrawScope, color: Color, radius: Float) {
        with(scope) {
            drawPath(path, color)
            // Центральная жилка делает лист читаемым даже в мелком размере.
            drawLine(
                color = color.copy(alpha = color.alpha * 0.45f),
                start = Offset(0f, -0.42f),
                end = Offset(0f, 0.5f),
                strokeWidth = 0.07f,
            )
        }
    }
}

/** Снежинка: три пересекающихся луча — форма, а не пятно. */
private object Snowflake : MotifShape {
    override fun draw(scope: DrawScope, color: Color, radius: Float) {
        with(scope) {
            val arm = 0.5f
            repeat(3) { index ->
                val rad = index * 60f * PI.toFloat() / 180f
                val dx = cos(rad) * arm
                val dy = sin(rad) * arm
                drawLine(
                    color = color,
                    start = Offset(-dx, -dy),
                    end = Offset(dx, dy),
                    strokeWidth = 0.12f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
            drawCircle(color.copy(alpha = color.alpha * 0.8f), radius = 0.1f)
        }
    }
}

/** Звезда: четыре луча, собранные вогнутыми дугами. */
private object Star : MotifShape {
    private val path = Path().apply {
        moveTo(0f, -0.5f)
        quadraticTo(0.05f, -0.05f, 0.5f, 0f)
        quadraticTo(0.05f, 0.05f, 0f, 0.5f)
        quadraticTo(-0.05f, 0.05f, -0.5f, 0f)
        quadraticTo(-0.05f, -0.05f, 0f, -0.5f)
        close()
    }

    override fun draw(scope: DrawScope, color: Color, radius: Float) {
        with(scope) { drawPath(path, color) }
    }
}

/** Светлячок: яркое ядро; ореол рисует [drawGlow]. */
private object Firefly : MotifShape {
    override fun draw(scope: DrawScope, color: Color, radius: Float) {
        with(scope) { drawCircle(color, radius = 0.22f) }
    }
}

/** Запасная «пылинка» — используется, если мотив не задан явно. */
private object Dot : MotifShape {
    override fun draw(scope: DrawScope, color: Color, radius: Float) {
        with(scope) { drawCircle(color, radius = 0.35f) }
    }
}

/**
 * Одна частица мотива.
 *
 * Всё — константы, заданные один раз: движение даёт только общая фаза.
 *
 * @property x нормированная стартовая позиция по горизонтали.
 * @property y нормированная стартовая позиция по вертикали.
 * @property size базовый размер в dp.
 * @property fallSpeed скорость падения, доли высоты экрана в секунду.
 * @property swayFreq частота бокового покачивания.
 * @property spin амплитуда собственного вращения.
 * @property spinSign направление вращения (вправо/влево).
 * @property phase случайный сдвиг фазы — разносит частицы по времени.
 * @property tint доля второго акцентного цвета.
 * @property depth «глубина»: дальние частицы мельче, медленнее и бледнее.
 * @property pulseRate частота мерцания для висячих мотивов.
 */
private data class MotifParticle(
    val x: Float,
    val y: Float,
    val size: Float,
    val fallSpeed: Float,
    val swayFreq: Float,
    val spin: Float,
    val spinSign: Float,
    val phase: Float,
    val tint: Float,
    val depth: Float,
    val pulseRate: Float,
)

private val TAU = (2 * PI).toFloat()
private const val FRAME_BUDGET_MS = 33L
private const val MAX_PARTICLES = 40
private const val GLOW_RATIO = 3.4f
private const val MAX_TILT = 34f
private const val TILT_STARS = 12f
private val MARGIN_PX = 64f
