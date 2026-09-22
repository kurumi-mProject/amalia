package com.my.amali.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import com.my.amali.ui.theme.AmaliaMotif
import com.my.amali.ui.theme.MotifBehavior
import com.my.amali.ui.theme.behavior
import com.my.amali.ui.theme.currentPalette
import com.my.amali.ui.theme.particleColors
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
    // Полный набор частиц строится ОДИН раз на мотив, с фиксированным семенем.
    //
    // Раньше набор пересобирался от density, а seed зависел от их числа:
    // смена густоты (например, «занят → свободен» на главном экране)
    // пересыпала ВСЕ частицы на новые позиции — мотив «телепортировался»
    // одним кадром. Теперь позиция каждой частицы неизменна, а густота
    // управляет только тем, СКОЛЬКО из них видно, — и это число анимируется.
    val particles = remember(resolved) { buildParticles(resolved, spec) }

    // Видимое число частиц: доля density от базового счёта мотива, со
    // стертыми хвостами округления. Плавное значение нужно для хвоста:
    // последняя (дробная) частица входит через собственную альфу, поэтому
    // смена густоты читается как постепенное проявление, а не подмена кадра.
    val targetCount = (spec.count * density.coerceIn(0f, 1f))
        .roundToInt()
        .coerceIn(if (spec.count > 0) 1 else 0, MAX_PARTICLES)
    val visibleCount by animateFloatAsState(
        targetValue = targetCount.toFloat(),
        animationSpec = tween(durationMillis = COUNT_FADE_MS, easing = LinearEasing),
        label = "motifVisibleCount",
    )

    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(resolved) {
        var elapsed = 0f
        while (true) {
            kotlinx.coroutines.delay(FRAME_BUDGET_MS)
            elapsed += FRAME_BUDGET_MS / 1000f
            phase = elapsed
        }
    }

    // ── Цвета частиц ──────────────────────────────────────────────────────
    //
    // Цвет берётся из КАНОНИЧЕСКОЙ пары мотива (particleColors), а не из
    // акцентов текущей палитры: ручная «Сакура» обязана быть розовой и ночью,
    // «Клены» — медными вечером. Для AUTO канонический мотив и палитра
    // согласованы по построению, так что картинка не меняется.
    val (mainColor, altColor) = resolved.particleColors()

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Wrap по высоте с запасом, чтобы частица «выходила» за нижний край
        // и возвращалась сверху без видимого телепорта.
        val span = h + 2f * MARGIN_PX
        val t = phase

        // Видимое число: целая часть рисуется полностью, дробный хвост —
        // через альфу. Сглаживание счёта — то, что делает смену густоты
        // «дыханием», а не пересыпанием частиц.
        val full = visibleCount.toInt()
        val fraction = visibleCount - full

        particles.forEachIndexed { index, p ->
            if (index > full) return@forEachIndexed
            val color = mix(mainColor, altColor, p.tint)
            // Хвостовая частица входит с прозрачностью, а не с нуля размера:
            // появление «из ниоткуда» на ровном месте читается как глитч.
            val presence = if (index == full) fraction else 1f
            if (presence <= 0f) return@forEachIndexed

            // ══════════════════════════════════════════════════════════════
            //  РАЗМЕР — здесь была причина «сакуры не видно»
            // ══════════════════════════════════════════════════════════════
            //
            // `p.size` задан в **dp** (spec.minSize = 7f, maxSize = 15f).
            // `scale(scaleX = radius, ...)` масштабирует путь, нарисованный в
            // единичных координатах −0.5..+0.5, то есть его полуширина равна
            // 0.5. Значит видимый радиус в пикселях = radius * 0.5.
            //
            // Прежний код: `val radius = p.size * pixelsPerDp * p.depth`,
            // и тогда видимый размер = p.size * density * depth * 0.5
            //                        = 10dp * 3.0 * 0.7 * 0.5 = 10.5 px = 3.5 dp
            // То есть лепесток «7–15 dp» рисовался как 2.5–5 dp — он был
            // физически меньше, чем точка на экране, и на фоне ауроры просто
            // не читался. Отсюда «нихуя не видно».
            //
            // Теперь размер приводится к пикселям целиком и сразу делится на
            // полуширину пути: `scale` получает ровно то число, которое даёт
            // нужный видимый радиус.
            val radius = p.size * pixelsPerDp * p.depth * PATH_HALF_EXTENT

            // presence — вход/выход хвостовой частицы при смене густоты.
            val alpha = (if (palette.isDark) 0.78f else 0.88f) *
                (0.62f + 0.38f * p.depth) * presence

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
                // Мерцание висячих мотивов. Нижняя граница поднята с 0.45
                // до 0.62: на светлом фоне тусклая частица исчезала совсем.
                val twinkle = 0.62f + 0.38f * sin(t * p.pulseRate * TAU + p.phase)
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
 * Всегда [MAX_PARTICLES] штук с фиксированным семенем: «дождь» остаётся тем
 * же при любой пересборке композиции И при любой густоте — смена плотности
 * управляет только видимым числом частиц (см. [MotifLayer]), поэтому частицы
 * никогда не пересыпают на новые позиции.
 */
private fun buildParticles(
    motif: AmaliaMotif,
    spec: MotifBehavior,
): List<MotifParticle> {
    val random = Random(motif.ordinal * 7919)
    return List(MAX_PARTICLES) { index ->
        MotifParticle(
            x = random.nextFloat(),
            y = random.nextFloat(),
            size = spec.minSize + random.nextFloat() * (spec.maxSize - spec.minSize),
            // Скорость берётся из поведения мотива, а не из общей константы:
            // иначе снег и лепестки падают одинаково и разница форм теряется.
            fallSpeed = BASE_FALL_SPEED *
                spec.fallSpeedScale *
                (0.72f + random.nextFloat() * 0.56f),
            swayFreq = 0.25f + random.nextFloat() * 0.5f,
            spin = spec.spin * (0.6f + random.nextFloat() * 0.8f),
            spinSign = if (index % 2 == 0) 1f else -1f,
            phase = random.nextFloat() * TAU,
            tint = random.nextFloat(),
            depth = 0.45f + random.nextFloat() * 0.55f,
            // Мерцание масштабируется поведением: у звёзд и светлячков
            // spec.pulse = 1, у падающих мотивов его нет вовсе.
            pulseRate = (0.35f + random.nextFloat() * 0.65f) * (0.4f + spec.pulse),
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

/**
 * Лепесток сакуры: капля с выемкой на широком конце.
 *
 * ## Что было сломано
 *
 * Подсветка рисовалась **тем же путём целиком**: `drawPath(path, white α0.16)`
 * заливал весь лепесток белым поверх основного цвета. Визуально это давало
 * не «блик», а просто более бледный лепесток — вся форма теряла объём и
 * превращалась в плоское пятно, а внутренняя выемка (характерная деталь
 * сакуры) вообще переставала читаться.
 *
 * Теперь свет падает **только на левую половину**: заливка ограничена
 * прямоугольником через `clipRect`, поэтому появляется граница света и тени.
 * Это то, что отличает «лепесток» от «розового овала».
 */
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
            // Свет только на левой половине — объём вместо плоской заливки.
            clipRect(left = -0.5f, top = -0.5f, right = 0f, bottom = 0.5f) {
                drawPath(path, Color.White.copy(alpha = 0.20f * color.alpha))
            }
            // Тонкая прожилка от основания к кончику — узнаваемая деталь.
            drawLine(
                color = color.copy(alpha = color.alpha * 0.35f),
                start = Offset(0f, -0.36f),
                end = Offset(0f, 0.30f),
                strokeWidth = 0.045f,
            )
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
            // Свет на левой половине — та же логика, что у лепестка:
            // заливка целиком делает лист плоским пятном.
            clipRect(left = -0.5f, top = -0.5f, right = 0f, bottom = 0.5f) {
                drawPath(path, Color.White.copy(alpha = 0.16f * color.alpha))
            }
            // Центральная жилка делает лист читаемым даже в мелком размере.
            drawLine(
                color = color.copy(alpha = color.alpha * 0.5f),
                start = Offset(0f, -0.42f),
                end = Offset(0f, 0.5f),
                strokeWidth = 0.06f,
            )
            // Боковые жилки: без них лист на 12 dp выглядит как запятая.
            listOf(-0.22f to 0.26f, 0.02f to 0.34f, 0.22f to 0.30f).forEach { (y, len) ->
                drawLine(
                    color = color.copy(alpha = color.alpha * 0.3f),
                    start = Offset(0f, y),
                    end = Offset(len * 0.55f, y + 0.12f),
                    strokeWidth = 0.035f,
                )
                drawLine(
                    color = color.copy(alpha = color.alpha * 0.3f),
                    start = Offset(0f, y),
                    end = Offset(-len * 0.55f, y + 0.12f),
                    strokeWidth = 0.035f,
                )
            }
        }
    }
}

/**
 * Снежинка: шесть лучей с боковыми веточками.
 *
 * Раньше было три линии крест-накрест — это читалось как «звёздочка», а не
 * как снежинка, и на мелком размере превращалось в плюсик. Шесть лучей по
 * 60° с короткими веточками дают узнаваемый силуэт даже в 3 dp, потому что
 * веточки создают характерную «пушистость» края.
 *
 * Толщина в unit-координатах домножается на радиус при отрисовке, поэтому
 * снежинка выглядит одинаково на любом размере экрана.
 */
private object Snowflake : MotifShape {
    override fun draw(scope: DrawScope, color: Color, radius: Float) {
        with(scope) {
            val arm = 0.5f
            repeat(6) { index ->
                val rad = index * 60f * PI.toFloat() / 180f
                val dx = cos(rad)
                val dy = sin(rad)
                drawLine(
                    color = color,
                    start = Offset(-dx * arm, -dy * arm),
                    end = Offset(dx * arm, dy * arm),
                    strokeWidth = 0.09f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
                // Две пары боковых веточек на каждой спице.
                listOf(0.22f, 0.36f).forEach { at ->
                    val bx = dx * at
                    val by = dy * at
                    val spread = 0.15f
                    // Перпендикуляр к спице, повёрнутый на ±35°.
                    val perpX = -dy * spread
                    val perpY = dx * spread
                    val fwdX = dx * spread
                    val fwdY = dy * spread
                    drawLine(
                        color = color.copy(alpha = color.alpha * 0.85f),
                        start = Offset(bx, by),
                        end = Offset(bx + perpX + fwdX, by + perpY + fwdY),
                        strokeWidth = 0.055f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                    drawLine(
                        color = color.copy(alpha = color.alpha * 0.85f),
                        start = Offset(bx, by),
                        end = Offset(bx - perpX + fwdX, by - perpY + fwdY),
                        strokeWidth = 0.055f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            }
            // Шестиугольное ядро — центр, который держит композицию.
            drawCircle(color.copy(alpha = color.alpha * 0.9f), radius = 0.075f)
        }
    }
}

/**
 * Звезда: основной четырёхлучевой блик плюс второй, повёрнутый на 45°.
 *
 * Одиночный четырёхлучевой путь на мелком размере выглядел как случайная
 * точка: глазу не за что зацепиться. Второй, более короткий и тусклый блик
 * под 45° даёт характерное «мерцание» — так рисуют звёзды в оптике, и это
 * то, что читается как звезда даже в 1.6 dp.
 */
private object Star : MotifShape {
    private fun sparklePath() = Path().apply {
        moveTo(0f, -0.5f)
        quadraticTo(0.05f, -0.05f, 0.5f, 0f)
        quadraticTo(0.05f, 0.05f, 0f, 0.5f)
        quadraticTo(-0.05f, 0.05f, -0.5f, 0f)
        quadraticTo(-0.05f, -0.05f, 0f, -0.5f)
        close()
    }

    private val main = sparklePath()

    override fun draw(scope: DrawScope, color: Color, radius: Float) {
        with(scope) {
            drawPath(main, color)
            // Вторичный блик под 45°, короче и тусклее — даёт «лучистость».
            rotate(45f) {
                scale(scaleX = 0.5f, scaleY = 0.5f) {
                    drawPath(main, color.copy(alpha = color.alpha * 0.45f))
                }
            }
            // Яркое ядро: без него звезда выглядит вырезанной, а не светящейся.
            drawCircle(Color.White.copy(alpha = color.alpha * 0.55f), radius = 0.06f)
        }
    }
}

/**
 * Светлячок: светящееся брюшко плюс тёмная головка и крылья.
 *
 * Раньше это был один круг: со свечением получалась «светящаяся точка», то
 * есть то же самое, что звезда, только крупнее. Разница между звездой и
 * светлячком в силуэте: у светлячка есть тёмная головка и сомкнутые крылья,
 * и именно они делают его узнаваемым, когда он пролетает мимо.
 */
private object Firefly : MotifShape {
    override fun draw(scope: DrawScope, color: Color, radius: Float) {
        with(scope) {
            // Крылья: вытянутый овал под углом — тело в полёте.
            rotate(-28f) {
                drawOval(
                    color = color.copy(alpha = color.alpha * 0.32f),
                    topLeft = Offset(-0.11f, -0.30f),
                    size = androidx.compose.ui.geometry.Size(0.22f, 0.44f),
                )
            }
            // Светящееся брюшко — основной источник света.
            drawCircle(color, radius = 0.20f)
            // Яркое ядро внутри брюшка.
            drawCircle(
                Color.White.copy(alpha = color.alpha * 0.6f),
                radius = 0.09f,
            )
            // Тёмная головка: даёт силуэту направление.
            drawCircle(
                Color.Black.copy(alpha = 0.42f * color.alpha),
                radius = 0.075f,
                center = Offset(0f, -0.24f),
            )
        }
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

/**
 * Длительность плавной смены видимого числа частиц.
 *
 * 700 мс — достаточно долго, чтобы хвостовая частица вошла мягко, и
 * достаточно коротко, чтобы смена густоты не выглядела «догоняющей».
 */
private const val COUNT_FADE_MS = 700

/**
 * Базовая скорость падения — доля высоты экрана в секунду.
 *
 * Вынесена в константу, потому что от неё теперь умножается индивидуальный
 * [MotifBehavior.fallSpeedScale]: так «лист падает быстрее лепестка»
 * выражается одним числом в описании мотива, а не подбором случайных
 * диапазонов в двух местах.
 */
private const val BASE_FALL_SPEED = 0.055f

/**
 * Полуширина единичного пути мотива.
 *
 * Все фигуры ([Petal], [Leaf], [Snowflake], [Star] и прочие) рисуются в
 * координатах −0.5..+0.5, то есть от центра до края ровно 0.5 единицы.
 * `scale(scaleX = r)` умножает эти единицы на r, поэтому чтобы получить
 * видимый радиус `r` пикселей, в `scale` нужно передать `r / 0.5 = r * 2`.
 *
 * Эта константа — то место, где ошибка в размерностях была допущена, и
 * единственное место, где она теперь исправляется.
 */
private const val PATH_HALF_EXTENT = 2f
private const val GLOW_RATIO = 3.4f
private const val MAX_TILT = 34f
private const val TILT_STARS = 12f
private val MARGIN_PX = 64f
