package com.my.amali.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.my.amali.domain.entity.VoiceState
import com.my.amali.ui.theme.accentGlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * ════════════════════════════════════════════════════════════════════════
 *  VoiceOrb — главный элемент главного экрана
 * ════════════════════════════════════════════════════════════════════════
 *
 * ## Почему не микрофон
 *
 * Прежняя кнопка рисовала в центре ядра пиктограмму микрофона. Пиктограмма
 * инструмента сообщает «здесь записывают звук» — так выглядит диктофон, а
 * не собеседник. Амалия разговаривает, поэтому в центре экрана должна
 * стоять **её речь**, а не прибор для записи.
 *
 * Здесь пять вертикальных линий, длины которых живут от двух величин:
 * фазы дыхания и **уровня звука пользователя**. Это не эквалайзер:
 * линии построены суммой двух гармоник с гауссовым окном, поэтому
 * двигаются как поверхность жидкости, а не как столбики индикатора.
 *
 * ## Как именно громкость оживляет волну
 *
 * [level] приходит из `AssistantUiState.audioLevel` — нормализованный RMS
 * микрофона в состоянии «слушаю» и громкость синтеза в состоянии «говорю».
 * Он влияет на волну **тремя независимыми путями**, иначе реакция читалась
 * бы как однообразное «всё стало больше»:
 *
 *  1. **амплитуда** линий растёт — громче сказал, сильнее всплеск;
 *  2. **частота** гармоник растёт — громче сказал, волна «дробится» мельче;
 *  3. **радиус свечения** растёт — тихая речь светит локально, громкая
 *     заливает весь блок.
 *
 * Уровень проходит через пружину без отскока: сырой RMS дрожит покадрово,
 * и без сглаживания волна дёргалась бы на каждом слоге.
 *
 * ## Состояния
 *
 *  — **Покой** — почти плоские линии, медленное дыхание 6 циклов/мин
 *    (10 с на период: это темп спокойного дыхания человека, он не
 *    «анимирует», а успокаивает);
 *  — **Слушаю** — амплитуда следует за громкостью, реакция пружиной
 *    жёстко (stiffness 220): волна обязана успевать за голосом;
 *  — **Думаю** — ровная бегущая волна средней амплитуды, без всплесков:
 *    процесс идёт, но прерывать его нечем;
 *  — **Говорю** — амплитуда держится выше и «дышит» вместе с синтезом;
 *  — **Ошибка** — линии оседают почти в линию и окрашиваются в цвет
 *    ошибки: экран показывает не «всё сломалось», а «я замолчала».
 *
 * ## Стоп вместо микрофона
 *
 * Когда идёт цикл, поверх волны по центру проявляется **квадрат** — не
 * иконка, а геометрическая фигура той же толщины, что линии. Это читается
 * как «останови меня», но не приносит в экран вторую пиктограмму.
 *
 * @param isActive идёт запись или разговор.
 * @param stateLabel подпись состояния для TalkBack (из ресурсов).
 * @param level уровень звука 0..1 (микрофон при слушании, синтез при речи).
 * @param onClick старт/остановка цикла.
 * @param enabled доступность действия (например, нет разрешения микрофона).
 * @param onPress касание до отпускания: прогрев STT.
 * @param waveHeight высота полотна; экран уменьшает её на низких дисплеях.
 */
@Composable
fun VoiceOrb(
    isActive: Boolean,
    stateLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: VoiceState = if (isActive) VoiceState.Listening else VoiceState.Idle,
    level: Float = 0f,
    onPress: () -> Unit = {},
    waveHeight: Dp = 132.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Прогрев STT вызывается при касании — до отпускания пальца.
    LaunchedEffect(pressed) {
        if (pressed) onPress()
    }

    val pulse = rememberInfiniteTransition(label = "voiceOrb")

    /**
     * Дыхание: 10 с на цикл = 6 вдохов в минуту.
     *
     * Это не произвольное число: 0.1 Гц — нижняя граница диапазона, который
     * человек читает как «спокойное дыхание». Всё, что быстрее, начинает
     * читаться как «идёт процесс» и требует внимания, а покой внимания
     * требовать не имеет права.
     */
    val breath by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(10_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbBreath",
    )

    // Медленная фаза дрейфа жидкости: 4.2 с.
    val midPhase by pulse.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4_200, easing = LinearEasing)),
        label = "orbMid",
    )

    // Быстрая фаза речевого ритма: 1.3 с.
    val fastPhase by pulse.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1_300, easing = LinearEasing)),
        label = "orbFast",
    )

    // Сглаживание уровня: сырой RMS дрожит покадрово.
    val smoothed by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            // Пружина жёстче, пока человек говорит: волна обязана успевать
            // за голосом, иначе живой отклик превращается в отставание.
            stiffness = when (state) {
                VoiceState.Listening -> 260f
                VoiceState.Speaking -> 150f
                else -> 90f
            },
        ),
        label = "orbLevel",
    )

    // Энергия волны по состоянию: база + вклад громкости.
    val targetEnergy = when (state) {
        VoiceState.Idle -> 0.16f + smoothed * 0.06f
        VoiceState.Listening -> 0.30f + 0.70f * smoothed
        VoiceState.Thinking -> 0.42f
        VoiceState.Speaking -> 0.58f + 0.30f * abs(smoothed)
        VoiceState.Error -> 0.08f
    }
    val energy by animateFloatAsState(
        targetValue = targetEnergy,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (state == VoiceState.Listening) 220f else 110f,
        ),
        label = "orbEnergy",
    )

    // Дыхание вмешивается в амплитуду только в покое: в разговоре ритм
    // задаёт человек, а не таймер.
    val breathFactor by animateFloatAsState(
        targetValue = if (state == VoiceState.Idle) 1f else 0f,
        animationSpec = tween(520),
        label = "orbBreathMix",
    )

    val accent = MaterialTheme.colorScheme.primary
    val accentSoft = MaterialTheme.colorScheme.secondary
    val cool = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error

    val headColor by animateColorAsState(
        targetValue = if (state == VoiceState.Error) errorColor else accentSoft,
        animationSpec = tween(420),
        label = "orbHead",
    )
    val tailColor by animateColorAsState(
        targetValue = if (state == VoiceState.Error) errorColor.copy(alpha = 0.7f) else cool,
        animationSpec = tween(420),
        label = "orbTail",
    )
    val coreColor by animateColorAsState(
        targetValue = if (state == VoiceState.Error) errorColor else accent,
        animationSpec = tween(420),
        label = "orbCore",
    )

    // Нажатие: мягкая усадка. Единственная «тактильная» реакция экрана —
    // и indication = null, потому что волна сама показывает отклик.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "orbScale",
    )

    // Появление «стоп»-квадрата: анимируем и прозрачность, и размер, чтобы
    // фигура не «выскакивала» посреди волны.
    val stopProgress = remember { Animatable(0f) }
    LaunchedEffect(isActive) {
        stopProgress.animateTo(
            targetValue = if (isActive) 1f else 0f,
            animationSpec = tween(if (isActive) 260 else 180),
        )
    }

    val dimmed = !enabled
    val paths = remember { List(3) { Path() } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(waveHeight)
            // Тач-зона — всю ширину полотна и вся его высота: это главное
            // действие экрана, и промахнуться по нему невозможно.
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = stateLabel
                stateDescription = stateLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(waveHeight).scale(scale)) {
            val w = size.width
            val h = size.height
            val centerY = h / 2f
            val maxAmp = h * 0.34f

            // Живое дыхание: в покое линии едва заметно наливаются.
            val live = energy * (1f - breathFactor) + breathFactor * (0.72f + 0.28f * breath)

            // Свечение за волной. Радиус зависит от громкости: тихая речь
            // светит локально, громкая заливает весь блок.
            val glowRadius = w * (0.34f + 0.30f * smoothed)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = 0.22f * live + 0.06f),
                        Color.Transparent,
                    ),
                    center = Offset(w / 2f, centerY),
                    radius = glowRadius,
                ),
            )

            // Три слоя: дальний (дымка) → средний → передний (чёткий).
            // Частота растёт вместе с громкостью — громче сказал, волна
            // дробится мельче. Это второй, независимый канал отклика.
            val freqBoost = 1f + smoothed * 0.55f
            val layers = listOf(
                OrbWaveLayer(
                    amplitude = maxAmp * (0.40f + live * 0.52f),
                    frequency = 1.15f * freqBoost,
                    phase = midPhase * 0.6f + fastPhase * 0.22f,
                    harmonic = 0.32f,
                    harmonicPhase = fastPhase * 0.45f,
                    alpha = 0.24f,
                    strokeWidth = 2.1f,
                ),
                OrbWaveLayer(
                    amplitude = maxAmp * (0.58f + live * 0.74f),
                    frequency = 1.85f * freqBoost,
                    phase = -midPhase * 0.9f,
                    harmonic = 0.46f,
                    harmonicPhase = fastPhase * 0.8f,
                    alpha = 0.46f,
                    strokeWidth = 2.5f,
                ),
                OrbWaveLayer(
                    amplitude = maxAmp * (0.32f + live * 0.98f),
                    frequency = 2.60f * freqBoost,
                    phase = midPhase * 1.5f + fastPhase * 0.35f,
                    harmonic = 0.58f,
                    harmonicPhase = fastPhase * 1.6f,
                    alpha = 1f,
                    strokeWidth = 3.0f,
                ),
            )

            // Залитая «жидкость»: область между передней линией и её зеркалом.
            val front = layers.last()
            val liquid = paths[0].also { it.reset() }
            buildOrbWave(liquid, front, w, centerY, mirrored = false)
            buildOrbWave(liquid, front, w, centerY, mirrored = true, continuePath = true)
            liquid.close()
            drawPath(
                path = liquid,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        headColor.copy(alpha = 0.09f + live * 0.13f),
                        coreColor.copy(alpha = 0.04f + live * 0.09f),
                        tailColor.copy(alpha = 0.09f + live * 0.13f),
                    ),
                    startY = centerY - maxAmp,
                    endY = centerY + maxAmp,
                ),
            )

            // Линии-мениски.
            layers.forEachIndexed { index, layer ->
                val path = paths[1].also { it.reset() }
                buildOrbWave(path, layer, w, centerY, mirrored = false)
                drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            lerpColor(headColor, coreColor, 0.35f, layer.alpha),
                            lerpColor(coreColor, tailColor, 0.55f, layer.alpha),
                            Color.Transparent,
                        ),
                    ),
                    style = Stroke(width = layer.strokeWidth, cap = StrokeCap.Round),
                )
                // Зеркальная линия — только у переднего слоя, чтобы не шуметь.
                if (index == layers.lastIndex) {
                    val mirror = paths[2].also { it.reset() }
                    buildOrbWave(mirror, layer, w, centerY, mirrored = true)
                    drawPath(
                        path = mirror,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                tailColor.copy(alpha = 0.42f),
                                headColor.copy(alpha = 0.42f),
                                Color.Transparent,
                            ),
                        ),
                        style = Stroke(width = layer.strokeWidth * 0.72f, cap = StrokeCap.Round),
                    )
                }
            }

            // «Стоп»: квадрат той же толщины, что линии. Не пиктограмма —
            // геометрическая фигура, поэтому экран не набирает иконок.
            if (stopProgress.value > 0.01f) {
                val side = 18f * stopProgress.value
                val strokeW = 2.6f * stopProgress.value
                drawRect(
                    color = coreColor.copy(alpha = 0.85f * stopProgress.value),
                    topLeft = Offset(w / 2f - side / 2f, centerY - side / 2f),
                    size = androidx.compose.ui.geometry.Size(side, side),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                )
            }
        }

        // Свечение позади всего блока — подсказка «сюда нажимать».
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(96.dp)
                .accentGlow(
                    color = if (dimmed) MaterialTheme.colorScheme.onSurface else accentSoft,
                    alpha = if (dimmed) 0.06f else (0.16f + 0.18f * smoothed),
                    spread = 1.8f,
                ),
        )
    }
}

/**
 * Параметры одного слоя волны.
 *
 * @param amplitude максимальное отклонение линии от центра.
 * @param frequency число периодов на всю ширину полотна.
 * @param phase бегущая фаза: именно она заставляет линию двигаться.
 * @param harmonic доля второй гармоники: без неё линия была бы синусоидой
 *   «из учебника» и выглядела бы механически.
 * @param harmonicPhase фаза второй гармоники — своя, поэтому слои не
 *   складываются в одну предсказуемую фигуру.
 * @param alpha прозрачность слоя: дальние слои приглушены, передний полный.
 * @param strokeWidth толщина линии.
 */
private data class OrbWaveLayer(
    val amplitude: Float,
    val frequency: Float,
    val phase: Float,
    val harmonic: Float,
    val harmonicPhase: Float,
    val alpha: Float,
    val strokeWidth: Float,
)

/**
 * Строит гладкую линию: сумма двух гармоник, умноженная на гауссово окно,
 * чтобы линия мягко затухала к краям и не «обрубалась» о границу полотна.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.buildOrbWave(
    path: Path,
    layer: OrbWaveLayer,
    width: Float,
    centerY: Float,
    mirrored: Boolean,
    continuePath: Boolean = false,
    samples: Int = 64,
) {
    val sign = if (mirrored) -1f else 1f
    for (i in 0..samples) {
        val t = i / samples.toFloat()
        val x = if (mirrored && continuePath) width * (1f - t) else width * t
        val tx = if (mirrored && continuePath) 1f - t else t

        // Окно: 1 в центре, ~0 по краям (сигма ≈ 0.30).
        val centered = tx - 0.5f
        val window = exp(-(centered * centered) / (2f * 0.085f))

        val base = sin(tx * layer.frequency * 2f * PI.toFloat() + layer.phase)
        val second = sin(tx * layer.frequency * 3.7f * PI.toFloat() + layer.harmonicPhase)
        val value = base * (1f - layer.harmonic) + second * layer.harmonic
        val y = centerY - sign * value * layer.amplitude * window

        if (i == 0 && !continuePath) path.moveTo(x, y) else path.lineTo(x, y)
    }
}

/** Перевод цвета в градиент с заданной прозрачностью. */
private fun lerpColor(from: Color, to: Color, fraction: Float, alpha: Float): Color =
    androidx.compose.ui.graphics.lerp(from, to, fraction).copy(alpha = alpha)
