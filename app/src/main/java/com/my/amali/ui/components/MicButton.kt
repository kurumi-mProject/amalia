package com.my.amali.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.my.amali.ui.theme.accentGlow

// ════════════════════════════════════════════════════════════
//  ГЕОМЕТРИЯ ОРБА
//  Одна шкала, из которой выводится всё остальное: кольца,
//  блики и орбита не могут «разъехаться» друг с другом.
// ════════════════════════════════════════════════════════════

/** Полный размер сенсорного поля — сюда попадает большой палец. */
private val OrbTouchSize = 148.dp

/** Диаметр светящегося ореола вокруг ядра. */
private val OrbHaloSize = 128.dp

/** Диаметр стеклянного кольца-оправы. */
private val OrbRingSize = 108.dp

/** Диаметр ядра — «капли» с микрофоном. */
private val OrbCoreSize = 84.dp

/**
 * MicButton — главный CTA приложения: живой голосовой орб Амалии.
 *
 * ═══════════════════════════════════════════════════════════
 *  КОНСТРУКЦИЯ (снизу вверх)
 * ═══════════════════════════════════════════════════════════
 *
 *  1. **дыхание покоя** — ореол мягко дышит, пока никто не говорит;
 *  2. **аудио-ореол** — радиальное свечение, которое растёт от уровня звука:
 *     кнопка физически «слышит» пользователя;
 *  3. **кольца-пульсы** — два расходящихся кольца в активном режиме;
 *  4. **орбита** — тонкая светящаяся дуга, вращается всегда: система жива;
 *  5. **стеклянная оправа** — тонкое кольцо, отделяющее орб от фона;
 *  6. **ядро** — градиентная капля с верхним бликом и нижней тенью;
 *  7. **иконка** — Mic ↔ Stop с пружинной подменой.
 *
 * ═══════════════════════════════════════════════════════════
 *  ПОЧЕМУ ТАК
 * ═══════════════════════════════════════════════════════════
 *
 * — **Ядро 104dp и сенсорное поле 172dp.** Это главное действие экрана,
 *   и оно обязано быть крупнее всех остальных элементов. Тач-зона почти
 *   вдвое больше минимума 48dp: попадать большим пальцем легко даже
 *   одной рукой, без перехвата.
 * — **Свечение вместо тени.** Material-тень под кнопкой всегда чёрная и
 *   на тёплом фоне читается как грязь; здесь глубина сделана светом
 *   акцента — так кнопка выглядит частью палитры времени суток.
 * — **Уровень звука управляет размером ореола, а не ядра.** Ядро не
 *   «прыгает» под палец, поэтому кнопку не нужно догонять; живой отклик
 *   при этом остаётся.
 *
 * @param isActive идёт запись/разговор.
 * @param stateLabel человекочитаемое состояние для TalkBack («Слушаю»).
 * @param onClick старт/остановка цикла.
 * @param enabled кнопка доступна (нет блокирующих разрешений).
 * @param level уровень звука 0..1 — громкость микрофона или речи.
 * @param onPress касание до отпускания: прогревает соединение STT.
 */
@Composable
fun MicButton(
    isActive: Boolean,
    stateLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    level: Float = 0f,
    onPress: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Прогрев STT вызывается в момент касания — до отпускания пальца.
    LaunchedEffect(pressed) {
        if (pressed) onPress()
    }

    val pulse = rememberInfiniteTransition(label = "micPulse")

    /** Расходящееся кольцо: 0 → 1 за 2.4 с. */
    val ring by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing)),
        label = "ring",
    )
    /** Вращение орбиты: 8 с на оборот — заметно, но не отвлекает. */
    val orbit by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8_000, easing = LinearEasing)),
        label = "orbit",
    )
    /** Дыхание покоя: 5.6 с на цикл, ±1.5 % — «живое», а не «анимированное». */
    val breath by pulse.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            tween(5_600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    // Уровень сглаживается пружиной: сырой RMS дрожит покадрово, и без
    // сглаживания ореол «дёргался» бы на каждом слоге.
    val smoothed by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "micLevel",
    )

    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.94f
            isActive -> 1.03f + smoothed * 0.04f
            else -> breath
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "micScale",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.40f + smoothed * 0.22f else 0.20f + smoothed * 0.10f,
        animationSpec = tween(420),
        label = "micGlow",
    )

    val primary = MaterialTheme.colorScheme.primary
    val accentSoft = MaterialTheme.colorScheme.secondary
    val cool = MaterialTheme.colorScheme.tertiary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val dimmed = !enabled

    Box(
        modifier = modifier
            .size(OrbTouchSize)
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
                if (dimmed) {
                    // Выключенная кнопка обязана быть «выключенной» и для TalkBack.
                    disabled()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // 1–2. Ореол: дыхание покоя плюс отклик на уровень звука.
        val haloScale = 0.86f + smoothed * 0.14f
        Box(
            modifier = Modifier
                .size(OrbHaloSize)
                .scale(if (isActive) haloScale else breath)
                .accentGlow(
                    color = if (isActive) accentSoft else primary,
                    alpha = glowAlpha,
                    spread = 2.0f,
                ),
        )

        // 3. Кольца-пульсы — только когда идёт разговор.
        if (isActive) {
            PulseRing(progress = ring, color = accentSoft, baseSize = OrbRingSize)
            PulseRing(progress = (ring + 0.5f) % 1f, color = cool, baseSize = OrbRingSize)
        }

        // 4. Орбита: тонкая дуга, которая всегда медленно вращается.
        Canvas(modifier = Modifier.size(OrbRingSize + 12.dp)) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color.Transparent,
                        primary.copy(alpha = if (isActive) 0.90f else 0.38f),
                        Color.Transparent,
                        Color.Transparent,
                    ),
                ),
                startAngle = orbit,
                sweepAngle = 104f,
                useCenter = false,
                style = Stroke(width = 3f, cap = StrokeCap.Round),
            )
        }

        // 5. Стеклянная оправа: отделяет орб от фона, держит форму.
        Box(
            modifier = Modifier
                .size(OrbRingSize)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                    ),
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.20f),
                            Color.White.copy(alpha = 0.04f),
                        ),
                    ),
                    shape = CircleShape,
                ),
        )

        // 6. Ядро: градиентная капля.
        Box(
            modifier = Modifier
                .size(OrbCoreSize)
                .scale(scale)
                .accentGlow(
                    color = if (isActive) accentSoft else primary,
                    alpha = if (dimmed) 0.10f else 0.30f,
                    spread = 1.35f,
                )
                .clip(CircleShape)
                .background(
                    brush = if (dimmed) {
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            ),
                        )
                    } else if (isActive) {
                        Brush.radialGradient(
                            colors = listOf(accentSoft, primary, primary.copy(alpha = 0.90f)),
                            center = Offset(150f, 110f),
                            radius = 420f,
                        )
                    } else {
                        Brush.radialGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.98f),
                                primary.copy(alpha = 0.84f),
                                primary.copy(alpha = 0.66f),
                            ),
                            center = Offset(150f, 110f),
                            radius = 420f,
                        )
                    },
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (dimmed) 0.10f else 0.44f),
                            Color.White.copy(alpha = 0.05f),
                        ),
                    ),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Верхний стеклянный блик: «капля поймала свет».
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.White.copy(alpha = 0.20f),
                                0.42f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.12f),
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )
            // Тонкая дуга-подсветка по нижней кромке — объём без «пластика».
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = Color.White.copy(alpha = 0.16f),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    style = Stroke(width = 1.5f, cap = StrokeCap.Round),
                    topLeft = Offset(size.width * 0.10f, size.height * 0.10f),
                    size = androidx.compose.ui.geometry.Size(
                        size.width * 0.80f,
                        size.height * 0.80f,
                    ),
                )
            }

            // 7. Иконка: Mic ↔ Stop.
            AnimatedContent(
                targetState = isActive,
                transitionSpec = {
                    (fadeIn(tween(160)) + scaleIn(initialScale = 0.7f)) togetherWith
                        (fadeOut(tween(120)) + scaleOut(targetScale = 0.7f))
                },
                label = "micIcon",
            ) { active ->
                Icon(
                    imageVector = if (active) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                    tint = if (dimmed) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    } else {
                        onAccent
                    },
                    modifier = Modifier.size(if (active) 28.dp else 34.dp),
                )
            }
        }
    }
}

/**
 * Одно расходящееся кольцо-пульс.
 *
 * Кольцо растёт от [baseSize] до +55 % и одновременно гаснет: получается
 * волна, а не мигание. Толщина уменьшается к концу — так кольцо «улетает»,
 * а не упирается в границу.
 */
@Composable
private fun PulseRing(progress: Float, color: Color, baseSize: Dp) {
    Box(
        modifier = Modifier
            .size(baseSize)
            .scale(1f + progress * 0.55f)
            .border(
                width = (1.6f - progress).coerceAtLeast(0.6f).dp,
                color = color.copy(alpha = (1f - progress) * 0.40f),
                shape = CircleShape,
            ),
    )
}
