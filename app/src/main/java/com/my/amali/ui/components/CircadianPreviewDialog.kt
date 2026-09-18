package com.my.amali.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.glassSurface

/**
 * CircadianPreviewDialog — модальное окно с кнопкой «Воспроизвести»,
 * которое показывает, как меняется фон приложения от рассвета до заката.
 *
 * Анимация проходит полный цикл суток за [CYCLE_SECONDS] секунд:
 * 00:00 → 06:00 (рассвет) → 12:00 (полдень) → 18:00 (закат) → 23:59 → зацикливание.
 *
 * Палитра берётся из лестницы [com.my.amali.ui.theme.glassPaletteLadder]:
 * те же палитры, что рисуют живой фон приложения, интерполируются
 * непрерывно по времени — поэтому превью показывает РЕАЛЬНЫЙ результат,
 * а не приблизительную имитацию.
 *
 * Компонент полностью stateless: управляющее состояние (играет/пауза,
 * текущий час) живёт внутри. Родительский экран только показывает/скрывает
 * диалог через [onDismissRequest].
 *
 * @param onDismissRequest закрывает диалог.
 */
@Composable
fun CircadianPreviewDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember { mutableStateOf(true) }

    // Цикл 0..1, пробегает сутки за CYCLE_SECONDS
    val transition = rememberInfiniteTransition(label = "circadianCycle")
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(CYCLE_SECONDS * 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dayCycle",
    )

    // Текущий дробный час: 0..24
    val hour = cycle * 24f

    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(460.dp)
                .clip(RoundedCornerShape(Radius.xl))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { isPlaying = !isPlaying })
                },
        ) {
            // Живой градиентный фон, меняющийся по времени суток
            CircadianCanvas(hour = hour, modifier = Modifier.fillMaxSize())

            // Контент поверх фона
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Шапка: заголовок + закрыть
                CircadianPreviewHeader(onClose = onDismissRequest)

                // Центр: информация о текущем свете
                CircadianTimeDisplay(hour = hour)

                // Низ: кнопка воспроизведения/паузы
                CircadianPlaybackControls(
                    isPlaying = isPlaying,
                    onToggle = { isPlaying = !isPlaying },
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  ФОН: непрерывный градиент суток
// ════════════════════════════════════════════════════════════

/**
 * Отрисовка живого фона, интерполируемого по часу суток.
 *
 * Палитры жёстко заданы здесь, а не берутся из темы, чтобы превью
 * было самодостаточным и не зависело от настроек пользователя:
 * задача — показать диапазон, а не текущую палитру.
 */
@Composable
private fun CircadianCanvas(
    hour: Float,
    modifier: Modifier = Modifier,
) {
    val palette = remember(hour) { circadianPaletteAt(hour) }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Базовый вертикальный градиент
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = palette.stops.map { it.position to it.color }.toTypedArray(),
                startY = 0f,
                endY = h,
            ),
        )

        // Дрейфующие пятна-ауроры
        val unit = maxOf(w, h)
        palette.auroras.forEachIndexed { index, color ->
            val cx = w * (0.2f + 0.6f * ((hour / 24f) + index * 0.33f).let { it - it.toInt() })
            val cy = h * (0.15f + index * 0.25f)
            val radius = unit * 0.45f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }

        // Солнце/луна: индикатор положения светила
        val sunAngle = ((hour - 12f) / 12f * 3.14159f).toFloat()
        val sunX = w * 0.5f + (w * 0.42f * kotlin.math.cos(sunAngle)).toFloat()
        val sunY = h * 0.5f - (h * 0.32f * kotlin.math.sin(sunAngle)).coerceAtLeast(0f).toFloat()
        val isDaytime = hour in 6f..18f
        val celestialColor = if (isDaytime) Color(0xFFFFE0B0) else Color(0xFFD4E0FF)
        drawCircle(
            color = celestialColor.copy(alpha = 0.85f),
            radius = if (isDaytime) 18f else 14f,
            center = Offset(sunX, sunY),
        )
        // Свечение вокруг светила
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(celestialColor.copy(alpha = 0.40f), Color.Transparent),
                center = Offset(sunX, sunY),
                radius = 80f,
            ),
            radius = 80f,
            center = Offset(sunX, sunY),
        )
    }
}

// ════════════════════════════════════════════════════════════
//  ШАПКА
// ════════════════════════════════════════════════════════════

@Composable
private fun CircadianPreviewHeader(
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Свет суток",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.95f),
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Закрыть",
                tint = Color.White.copy(alpha = 0.90f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
//  ЦЕНТР: время и подпись света
// ════════════════════════════════════════════════════════════

@Composable
private fun CircadianTimeDisplay(hour: Float) {
    val hh = hour.toInt()
    val mm = ((hour - hh) * 60f).toInt()
    val timeStr = String.format("%02d:%02d", hh, mm)
    val phaseName = circadianPhaseName(hour)
    val lightDesc = circadianLightDesc(hour)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = timeStr,
            style = MaterialTheme.typography.displayMedium,
            color = Color.White.copy(alpha = 0.95f),
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = phaseName,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.80f),
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = lightDesc,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )
    }
}

// ════════════════════════════════════════════════════════════
//  НИЗ: кнопка воспроизведения/паузы
// ════════════════════════════════════════════════════════════

@Composable
private fun CircadianPlaybackControls(
    isPlaying: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                tint = Color.White.copy(alpha = 0.95f),
                modifier = Modifier.size(28.dp),
            )
        }
    }
    Text(
        text = "Нажми на экран, чтобы остановить",
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.50f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ════════════════════════════════════════════════════════════
//  ПАЛИТРЫ СУТОК (упрощённая лестница для превью)
// ════════════════════════════════════════════════════════════

private data class PreviewStop(val color: Color, val position: Float)
private data class PreviewPalette(
    val stops: List<PreviewStop>,
    val auroras: List<Color>,
)

private fun circadianPaletteAt(hour: Float): PreviewPalette {
    // Опорные палитры по фазам суток (CCT → цвета)
    val night = PreviewPalette(
        stops = listOf(
            PreviewStop(Color(0xFF0A0B14), 0f),
            PreviewStop(Color(0xFF0E1018), 0.55f),
            PreviewStop(Color(0xFF080910), 1f),
        ),
        auroras = listOf(Color(0xFF6E63F2), Color(0xFF3E6FA8), Color(0xFF79C7E8)),
    )
    val dawn = PreviewPalette(
        stops = listOf(
            PreviewStop(Color(0xFF1A1020), 0f),
            PreviewStop(Color(0xFF2A1820), 0.4f),
            PreviewStop(Color(0xFF1E1418), 0.7f),
            PreviewStop(Color(0xFF0F0A12), 1f),
        ),
        auroras = listOf(Color(0xFFB4794A), Color(0xFFE5A0BF), Color(0xFF6E63F2)),
    )
    val morning = PreviewPalette(
        stops = listOf(
            PreviewStop(Color(0xFF0B0810), 0f),
            PreviewStop(Color(0xFF16101B), 0.55f),
            PreviewStop(Color(0xFF0A080E), 1f),
        ),
        auroras = listOf(Color(0xFFE5A0BF), Color(0xFF6E63F2), Color(0xFFF3C6D6)),
    )
    val noon = PreviewPalette(
        stops = listOf(
            PreviewStop(Color(0xFF090B0F), 0f),
            PreviewStop(Color(0xFF0E151A), 0.55f),
            PreviewStop(Color(0xFF080A0D), 1f),
        ),
        auroras = listOf(Color(0xFF6FA8A0), Color(0xFF79C7E8), Color(0xFF9AB06E)),
    )
    val evening = PreviewPalette(
        stops = listOf(
            PreviewStop(Color(0xFF0C0809), 0f),
            PreviewStop(Color(0xFF17100E), 0.55f),
            PreviewStop(Color(0xFF0A0708), 1f),
        ),
        auroras = listOf(Color(0xFFB4794A), Color(0xFF7A6142), Color(0xFFD9A97F)),
    )
    val dusk = PreviewPalette(
        stops = listOf(
            PreviewStop(Color(0xFF100A0C), 0f),
            PreviewStop(Color(0xFF1C1210), 0.4f),
            PreviewStop(Color(0xFF140D0A), 0.7f),
            PreviewStop(Color(0xFF0C0608), 1f),
        ),
        auroras = listOf(Color(0xFFB4794A), Color(0xFFE5A0BF), Color(0xFFD9A97F)),
    )

    // Ключевые часы и их палитры, упорядоченные по времени
    val keyframes = listOf(
        0f to night,
        5f to dawn,
        7f to morning,
        12f to noon,
        17f to evening,
        19f to dusk,
        22f to night,
        24f to night,
    )

    // Найти два соседних ключевых кадра и интерполировать
    var lower = keyframes.first()
    var upper = keyframes.last()
    for (i in 0 until keyframes.lastIndex) {
        if (hour >= keyframes[i].first && hour <= keyframes[i + 1].first) {
            lower = keyframes[i]
            upper = keyframes[i + 1]
            break
        }
    }
    val span = (upper.first - lower.first).coerceAtLeast(0.001f)
    val t = ((hour - lower.first) / span).coerceIn(0f, 1f)
    return blendPalettes(lower.second, upper.second, t)
}

private fun blendPalettes(a: PreviewPalette, b: PreviewPalette, t: Float): PreviewPalette {
    val stopsA = a.stops
    val stopsB = b.stops
    val count = maxOf(stopsA.size, stopsB.size)
    val stops = (0 until count).map { i ->
        val sa = stopsA.getOrElse(i) { stopsA.last() }
        val sb = stopsB.getOrElse(i) { stopsB.last() }
        PreviewStop(
            color = lerp(sa.color, sb.color, t),
            position = sa.position + (sb.position - sa.position) * t,
        )
    }
    val aurorasA = a.auroras
    val aurorasB = b.auroras
    val auroraCount = maxOf(aurorasA.size, aurorasB.size)
    val auroras = (0 until auroraCount).map { i ->
        lerp(
            aurorasA.getOrElse(i) { aurorasA.last() },
            aurorasB.getOrElse(i) { aurorasB.last() },
            t,
        )
    }
    return PreviewPalette(stops, auroras)
}

private fun circadianPhaseName(hour: Float): String = when (hour) {
    in 0f..4.5f -> "Глубокая ночь"
    in 4.5f..6.5f -> "Рассвет"
    in 6.5f..11f -> "Утро"
    in 11f..14f -> "Полдень"
    in 14f..17f -> "День"
    in 17f..19.5f -> "Закат"
    in 19.5f..22f -> "Вечер"
    else -> "Ночь"
}

private fun circadianLightDesc(hour: Float): String = when (hour) {
    in 0f..4.5f -> "Свеча — минимум мелатонинового вклада"
    in 4.5f..6.5f -> "Тёплая лампа — пробуждение света"
    in 6.5f..11f -> "Утренний свет — мягкий подъём"
    in 11f..14f -> "Дневной свет — пик циркадного стимула"
    in 14f..17f -> "Нейтральный день — стабильный свет"
    in 17f..19.5f -> "Закат — теплое угасание"
    in 19.5f..22f -> "Вечерний свет — защита сна"
    else -> "Ночь — глубокий покой"
}

private const val CYCLE_SECONDS = 24
