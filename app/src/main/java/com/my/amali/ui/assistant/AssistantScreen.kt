package com.my.amali.ui.assistant

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Главный экран Амалии. Минимальный фокус на голосовом диалоге:
 * орб в центре, живой транскрипт, ответ и одна большая кнопка микрофона.
 */
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(statusLabel = state.orbState.label)

        // Центральная зона: орб + транскрипт + ответ
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 28.dp),
            ) {
                OrbIndicator(state = state.orbState)

                Spacer(Modifier.height(28.dp))

                Text(
                    text = state.transcript.ifEmpty {
                        if (state.active) "…" else "Скажи что-нибудь"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    minLines = 2,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(16.dp))

                if (state.reply.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                    ) {
                        Text(
                            text = state.reply,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                }
            }
        }

        // Нижняя панель: большая кнопка микрофона
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            MicButton(active = state.active, onClick = viewModel::onMicTap)
        }
    }
}

@Composable
private fun TopBar(statusLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(10.dp),
        ) {}

        Spacer(Modifier.size(12.dp))

        Text(
            text = "Амалия",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Круглая градиентная кнопка с собственной Canvas-иконкой.
 * Неактивна — иконка микрофона; активна — квадрат «стоп».
 */
@Composable
private fun MicButton(
    active: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            active -> 0.9f
            pressed -> 0.94f
            else -> 1f
        },
        animationSpec = tween(160),
        label = "micScale",
    )

    Box(
        modifier = Modifier
            .size(72.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary,
                    ),
                ),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(34.dp)) {
            val stroke = 2.6.dp.toPx()
            if (active) {
                // Стоп: закруглённый квадрат
                val s = size.minDimension * 0.42f
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset((size.width - s) / 2f, (size.height - s) / 2f),
                    size = Size(s, s),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.18f),
                )
            } else {
                // Микрофон: капсула + дуга + ножка
                val cx = size.width / 2f
                val bodyW = size.width * 0.32f
                val bodyH = size.height * 0.46f
                val topY = size.height * 0.18f
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(cx - bodyW / 2f, topY),
                    size = Size(bodyW, bodyH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(bodyW / 2f),
                )
                val arcW = size.width * 0.4f
                drawArc(
                    color = Color.White,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx - arcW / 2f, topY + bodyH - arcW * 0.42f),
                    size = Size(arcW, arcW * 0.8f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                val stemTop = topY + bodyH + arcW * 0.26f
                drawLine(
                    color = Color.White,
                    start = Offset(cx, stemTop),
                    end = Offset(cx, stemTop + size.height * 0.1f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
