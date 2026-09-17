package com.my.amali.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.my.amali.R
import com.my.amali.domain.entity.VoiceState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * VoiceWave — «жидкая» волна Амалии: главный визуальный объект приложения.
 *
 * Вместо столбиков-эквалайзера рисуются три наложенные гладкие линии,
 * построенные суммой трёх бегущих гармоник. Линии имеют разную частоту,
 * фазу и скорость, поэтому картинка никогда не повторяется буквально и
 * выглядит как поверхность подсвеченной жидкости под стеклом.
 *
 * Слои сверху вниз:
 *  1. мягкое цветное свечение вокруг центральной линии;
 *  2. залитая область между зеркальными половинами (жидкость);
 *  3. три линии-«мениска» с градиентом акцент → холодный тон;
 *  4. тонкий блик-хайлайт на верхней линии.
 *
 * Поведение по состояниям:
 *  - Idle: почти плоская линия, дыхание 6 циклов/мин (10 с на период);
 *  - Listening: амплитуда пружиной следует за [audioLevel];
 *  - Thinking: медленная бегущая волна без всплесков;
 *  - Speaking: богатая гармоника с ритмом речи;
 *  - Error: волна оседает и окрашивается в цвет ошибки.
 *
 * @param state текущее состояние ассистента.
 * @param audioLevel нормализованный уровень входного/выходного звука 0..1.
 * @param waveHeight высота полотна волны. Экран передаёт уменьшенное значение
 *   на низких дисплеях (см. `compactHeight` в [AssistantScreen]): без этого
 *   на 640dp-высоте карточка диалога схлопывалась в ноль и кнопка микрофона
 *   уезжала под нижнюю навигацию.
 */
@Composable
fun VoiceWave(
    state: VoiceState,
    audioLevel: Float,
    modifier: Modifier = Modifier,
    waveHeight: Dp = 148.dp,
) {
    // Медленная фаза «дыхания»: 10 с = 6 циклов в минуту.
    val slow = rememberInfiniteTransition(label = "waveSlow")
    val slowPhase by slow.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(10_000, easing = LinearEasing)),
        label = "slowPhase",
    )

    // Средняя фаза: дрейф жидкости, 4.2 с.
    val mid = rememberInfiniteTransition(label = "waveMid")
    val midPhase by mid.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4_200, easing = LinearEasing)),
        label = "midPhase",
    )

    // Быстрая фаза: речевой ритм, 1.3 с.
    val fast = rememberInfiniteTransition(label = "waveFast")
    val fastPhase by fast.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1_300, easing = LinearEasing)),
        label = "fastPhase",
    )

    // Целевая энергия волны по состоянию — переход всегда плавный.
    val targetEnergy = when (state) {
        VoiceState.Idle -> 0.16f
        VoiceState.Listening -> 0.30f + 0.70f * audioLevel.coerceIn(0f, 1f)
        VoiceState.Thinking -> 0.42f
        VoiceState.Speaking -> 0.58f + 0.30f * audioLevel.coerceIn(0f, 1f)
        VoiceState.Error -> 0.08f
    }
    val energy by animateFloatAsState(
        targetValue = targetEnergy,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (state == VoiceState.Listening) 220f else 90f,
        ),
        label = "waveEnergy",
    )

    val accent = MaterialTheme.colorScheme.primary
    val accentSoft = MaterialTheme.colorScheme.secondary
    val cool = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error

    val headColor by animateColorAsState(
        targetValue = if (state == VoiceState.Error) errorColor else accentSoft,
        animationSpec = tween(420),
        label = "waveHead",
    )
    val tailColor by animateColorAsState(
        targetValue = if (state == VoiceState.Error) errorColor.copy(alpha = 0.7f) else cool,
        animationSpec = tween(420),
        label = "waveTail",
    )
    val coreColor by animateColorAsState(
        targetValue = if (state == VoiceState.Error) errorColor else accent,
        animationSpec = tween(420),
        label = "waveCore",
    )

    // Подпись для TalkBack берётся из ресурсов, а не из VoiceState.label:
    // в enum-е она захардкожена по-русски, и в девяти локалях приложения
    // незрячий пользователь слышал русское слово вместо своего языка.
    val description = stringResource(
        when (state) {
            VoiceState.Idle -> R.string.assistant_ready
            VoiceState.Listening -> R.string.assistant_listening
            VoiceState.Thinking -> R.string.assistant_thinking
            VoiceState.Speaking -> R.string.assistant_speaking
            VoiceState.Error -> R.string.assistant_error
        },
    )
    // Кэшируем Path-объекты: аллокации на каждый кадр не нужны.
    val paths = remember { List(4) { Path() } }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(waveHeight)
            .semantics { contentDescription = description },
    ) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val maxAmp = h * 0.36f

        // Свечение за волной — «жидкость светится изнутри».
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    coreColor.copy(alpha = 0.20f * (0.35f + energy)),
                    Color.Transparent,
                ),
                center = Offset(w / 2f, centerY),
                radius = w * 0.62f,
            ),
        )

        // Три слоя: дальний (дымка) → средний → передний (чёткий).
        val layers = listOf(
            WaveLayer(
                amplitude = maxAmp * (0.42f + energy * 0.55f),
                frequency = 1.15f,
                phase = slowPhase * 0.6f + midPhase * 0.25f,
                harmonic = 0.32f,
                harmonicPhase = fastPhase * 0.45f,
                alpha = 0.22f,
                strokeWidth = 2.2f,
            ),
            WaveLayer(
                amplitude = maxAmp * (0.62f + energy * 0.78f),
                frequency = 1.85f,
                phase = -midPhase * 0.9f,
                harmonic = 0.46f,
                harmonicPhase = fastPhase * 0.8f,
                alpha = 0.45f,
                strokeWidth = 2.6f,
            ),
            WaveLayer(
                amplitude = maxAmp * (0.34f + energy * 1.05f),
                frequency = 2.6f,
                phase = midPhase * 1.5f + fastPhase * 0.35f,
                harmonic = 0.58f,
                harmonicPhase = fastPhase * 1.6f,
                alpha = 1f,
                strokeWidth = 3.2f,
            ),
        )

        // Залитая «жидкость»: область между передней линией и её зеркалом.
        val front = layers.last()
        val liquid = paths[0].also { it.reset() }
        buildWavePath(liquid, front, w, centerY, mirrored = false)
        buildWavePath(liquid, front, w, centerY, mirrored = true, continuePath = true)
        liquid.close()
        drawPath(
            path = liquid,
            brush = Brush.verticalGradient(
                colors = listOf(
                    headColor.copy(alpha = 0.10f + energy * 0.14f),
                    coreColor.copy(alpha = 0.05f + energy * 0.10f),
                    tailColor.copy(alpha = 0.10f + energy * 0.14f),
                ),
                startY = centerY - maxAmp,
                endY = centerY + maxAmp,
            ),
        )

        // Линии-мениски.
        layers.forEachIndexed { index, layer ->
            val path = paths[index + 1].also { it.reset() }
            buildWavePath(path, layer, w, centerY, mirrored = false)
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        lerp(headColor, coreColor, 0.35f).copy(alpha = layer.alpha),
                        lerp(coreColor, tailColor, 0.55f).copy(alpha = layer.alpha),
                        Color.Transparent,
                    ),
                ),
                style = Stroke(width = layer.strokeWidth, cap = StrokeCap.Round),
            )
            // Зеркальная линия — только у переднего слоя, чтобы не шуметь.
            if (index == layers.lastIndex) {
                val mirror = paths[0].also { it.reset() }
                buildWavePath(mirror, layer, w, centerY, mirrored = true)
                drawPath(
                    path = mirror,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            tailColor.copy(alpha = 0.45f),
                            headColor.copy(alpha = 0.45f),
                            Color.Transparent,
                        ),
                    ),
                    style = Stroke(width = layer.strokeWidth * 0.72f, cap = StrokeCap.Round),
                )
            }
        }

        // Стеклянный блик по центру — тонкая светлая полоса.
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.10f + energy * 0.10f),
                    Color.Transparent,
                ),
            ),
            start = Offset(w * 0.06f, centerY),
            end = Offset(w * 0.94f, centerY),
            strokeWidth = 1f,
            blendMode = BlendMode.Plus,
        )
    }
}

/** Параметры одного слоя волны. */
private data class WaveLayer(
    val amplitude: Float,
    val frequency: Float,
    val phase: Float,
    val harmonic: Float,
    val harmonicPhase: Float,
    val alpha: Float,
    val strokeWidth: Float,
)

/**
 * Строит гладкую волну: сумма двух гармоник, умноженная на оконную
 * функцию Гаусса, чтобы линия мягко затухала к краям и не «обрубалась».
 */
private fun DrawScope.buildWavePath(
    path: Path,
    layer: WaveLayer,
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

/** Нормализованная амплитуда для внешних индикаторов (например, кнопки). */
fun waveEnergyFor(state: VoiceState, audioLevel: Float): Float = when (state) {
    VoiceState.Idle -> 0.16f
    VoiceState.Listening -> 0.30f + 0.70f * audioLevel.coerceIn(0f, 1f)
    VoiceState.Thinking -> 0.42f
    VoiceState.Speaking -> 0.58f + 0.30f * abs(audioLevel).coerceIn(0f, 1f)
    VoiceState.Error -> 0.08f
}
