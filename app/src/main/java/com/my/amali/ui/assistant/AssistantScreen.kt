package com.my.amali.ui.assistant

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.my.amali.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun AssistantScreen(
    modifier: Modifier = Modifier,
) {
    val vm: AssistantViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        delay(800)
        vm.onFirstLaunchHandled()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // === ТОПБАР ===
            AssistantTopBar(
                conversationCount = state.conversationCount,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.weight(1f))

            // === ЦЕНТР: визуализатор + статус ===
            VoiceVisualizer(
                state = state.voiceState,
                audioLevel = state.audioLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
            )

            Spacer(Modifier.height(8.dp))

            // Статус-текст
            StatusText(
                state = state.voiceState,
                modifier = Modifier.padding(horizontal = 32.dp),
            )

            Spacer(Modifier.height(24.dp))

            // === Транскрипт / Ответ (glass-карточка) ===
            AnimatedContent(
                targetState = state.voiceState,
                transitionSpec = {
                    fadeIn(tween(280)) + scaleIn(initialScale = 0.96f) togetherWith
                            fadeOut(tween(200)) + scaleOut(targetScale = 0.96f)
                },
                label = "content"
            ) { vs ->
                when {
                    vs == VoiceState.Listening && state.userTranscript.isEmpty() -> {
                        // Пусто — показываем тонкий hint
                        Spacer(Modifier.height(0.dp))
                    }
                    state.userTranscript.isNotEmpty() && vs == VoiceState.Listening -> {
                        TranscriptCard(
                            text = state.userTranscript.ifEmpty { "Слушаю…" },
                            isUser = true,
                        )
                    }
                    vs == VoiceState.Thinking -> {
                        ThinkingCard()
                    }
                    state.amaliaReply.isNotEmpty() && (vs == VoiceState.Speaking || vs == VoiceState.Idle) -> {
                        ReplyCard(
                            reply = state.amaliaReply,
                            progress = state.replyProgress,
                            onRepeat = { vm.startConversation(state.userTranscript) },
                        )
                    }
                    state.isFirstLaunch -> {
                        WelcomeCard()
                    }
                    else -> {
                        Spacer(Modifier.height(0.dp))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // === Подсказки ===
            if (state.suggestions.isNotEmpty() && state.voiceState == VoiceState.Idle) {
                SuggestionChips(
                    suggestions = state.suggestions,
                    onClick = { vm.startConversation(it) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(12.dp))
            }

            // === Кнопка микрофона ===
            MicButton(
                isActive = state.voiceState != VoiceState.Idle,
                state = state.voiceState,
                onClick = { vm.toggleConversation() },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ============================================================
// КОМПОНЕНТЫ
// ============================================================

@Composable
private fun AssistantTopBar(
    conversationCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Аватар — простая точка-индикатор
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(AccentDim)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "Амалия",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                text = if (conversationCount > 0)
                    "$conversationCount ${pluralize(conversationCount)}"
                else "на связи",
                style = MaterialTheme.typography.labelSmall,
                color = TextFaint,
            )
        }
    }
}

@Composable
private fun StatusText(
    state: VoiceState,
    modifier: Modifier = Modifier,
) {
    val text = state.label
    val color = when (state) {
        VoiceState.Idle -> TextSecondary
        VoiceState.Listening -> AccentSoft
        VoiceState.Thinking -> TextSecondary
        VoiceState.Speaking -> AccentSoft
        VoiceState.Error -> StateError
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun TranscriptCard(
    text: String,
    isUser: Boolean,
) {
    GlassCard(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun ThinkingCard() {
    GlassCard(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            val transition = rememberInfiniteTransition(label = "dots")
            repeat(3) { i ->
                val alpha by transition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500, delayMillis = i * 200),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dot_$i"
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(AccentSoft.copy(alpha = alpha))
                        .padding(start = if (i > 0) 4.dp else 0.dp)
                )
                if (i < 2) Spacer(Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun ReplyCard(
    reply: String,
    progress: Float,
    onRepeat: () -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Лейбл
            Text(
                text = "Амалия",
                style = MaterialTheme.typography.labelSmall,
                color = AccentSoft,
            )
            Spacer(Modifier.height(6.dp))
            // Текст ответа
            val visibleText = if (progress >= 1f) reply else {
                val wordCount = (reply.split(" ").size * progress).toInt()
                reply.split(" ").take(wordCount.coerceAtLeast(1)).joinToString(" ")
            }
            Text(
                text = visibleText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
            // Действия
            if (progress >= 1f) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "повторить",
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentSoft,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onRepeat)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .semantics { role = Role.Button }
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeCard() {
    GlassCard(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Нажми и говори",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Я Амалия — голосовой ассистент.\nДержи кнопку, чтобы начать разговор.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SuggestionChips(
    suggestions: List<String>,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(suggestions) { text ->
            SuggestionChip(text = text, onClick = { onClick(text) })
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = BgSurfaceHigh,
        contentColor = TextSecondary,
        modifier = Modifier
            .height(36.dp)
            .semantics { role = Role.Button }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .wrapContentHeight(Alignment.CenterVertically)
        )
    }
}

@Composable
private fun MicButton(
    isActive: Boolean,
    state: VoiceState,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            isActive -> 0.90f
            pressed -> 0.93f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "micScale"
    )

    val bgColor = when (state) {
        VoiceState.Listening -> AccentDim
        VoiceState.Speaking -> AccentSoft
        VoiceState.Thinking -> BgSurfaceHigh
        VoiceState.Error -> StateError
        VoiceState.Idle -> AccentDim
    }

    Box(
        modifier = Modifier
            .size(72.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(bgColor, bgColor.copy(alpha = 0.85f)),
                    radius = 120f,
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics {
                contentDescription = if (isActive) "Остановить" else "Начать разговор"
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isActive) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}

// ============================================================
// GLASS CARD — полупрозрачная карточка с blur-эффектом
// ============================================================

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Glass-эффект: полупрозрачный фон + лёгкий blur
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                color = BgGlass.copy(alpha = 0.6f)
            )
            .blur(0.dp) // нативный blur пока недоступен без graphicsLayer
    ) {
        // Тонкая граница через overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.04f),
                            Color.Transparent,
                        )
                    )
                )
        ) {
            content()
        }
    }
}

// ============================================================
// УТИЛИТЫ
// ============================================================

private fun pluralize(n: Int): String = when {
    n % 100 in 11..14 -> "разговоров"
    n % 10 == 1 -> "разговор"
    n % 10 in 2..4 -> "разговора"
    else -> "разговоров"
}

// ============================================================
// PREVIEW
// ============================================================

@Preview(showBackground = true, backgroundColor = 0xFF0E0E12)
@Composable
private fun AssistantScreenPreview() {
    AmaliaTheme {
        AssistantScreen()
    }
}
