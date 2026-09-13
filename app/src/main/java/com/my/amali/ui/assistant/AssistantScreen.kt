package com.my.amali.ui.assistant

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.my.amali.R
import com.my.amali.domain.entity.VoiceState
import com.my.amali.ui.components.GlassCard
import com.my.amali.ui.components.GlassIconButton
import com.my.amali.ui.components.GradientBackground
import com.my.amali.ui.components.MicButton
import com.my.amali.ui.components.VoiceWave
import com.my.amali.ui.theme.AmaliaTheme
import com.my.amali.ui.theme.LocalAmaliaVisuals
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.glassSurface
import kotlinx.coroutines.delay

/**
 * AssistantScreen — главный экран Амалии.
 *
 * Композиция сверху вниз:
 *  1. живой аурора-фон;
 *  2. лёгкая шапка: пульс-индикатор состояния, имя, история, настройки;
 *  3. центр — жидкая волна + одно слово состояния (главный фокус);
 *  4. стеклянная карточка диалога: реплика пользователя и ответ Амалии;
 *  5. лента подсказок;
 *  6. кнопка микрофона — единственное главное действие.
 *
 * Всё, кроме волны и кнопки, визуально тише: это делает главное
 * действие однозначным и даёт экрану «дорогое» спокойствие.
 */
@Composable
fun AssistantScreen(
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm: AssistantViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val visuals = LocalAmaliaVisuals.current
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        delay(900)
        vm.onFirstLaunchHandled()
    }

    Box(modifier = modifier.fillMaxSize()) {
        GradientBackground(
            visualTheme = visuals.visualTheme,
            darkModePref = visuals.darkModePref,
            useBioTime = visuals.useBioTime,
            intensity = visuals.glassIntensity,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AssistantTopBar(
                conversationCount = state.conversationCount,
                voiceState = state.voiceState,
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToSettings = onNavigateToSettings,
            )

            // Верхняя пустота меньше нижней: волна встаёт в «золотую»
            // верхнюю треть, а не в геометрический центр — так композиция
            // ощущается устойчивой, а не «съехавшей вниз».
            Spacer(Modifier.weight(0.85f))

            // === ГЛАВНЫЙ ФОКУС: волна + состояние ===
            VoiceWave(
                state = state.voiceState,
                audioLevel = state.audioLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl),
            )

            Spacer(Modifier.height(Spacing.xs))

            StatusLabel(state = state.voiceState)

            Spacer(Modifier.height(Spacing.lg))

            // === ДИАЛОГ ===
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 132.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                AnimatedContent(
                    targetState = DialogPhase.of(state),
                    transitionSpec = {
                        (fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 8 })
                            .togetherWith(
                                fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it / 8 },
                            )
                    },
                    label = "dialog",
                ) { phase ->
                    when (phase) {
                        DialogPhase.Welcome -> WelcomeCard()
                        DialogPhase.Listening -> ListeningCard(text = state.userTranscript)
                        DialogPhase.Thinking -> ThinkingCard(prompt = state.userTranscript)
                        DialogPhase.Reply -> ReplyCard(
                            prompt = state.userTranscript,
                            reply = state.amaliaReply,
                            progress = state.replyProgress,
                            speaking = state.voiceState == VoiceState.Speaking,
                            onRepeat = { vm.startConversation(state.userTranscript) },
                            onCopy = { clipboard.setText(AnnotatedString(state.amaliaReply)) },
                        )
                        DialogPhase.Error -> ErrorCard(
                            message = state.errorMessage
                                ?: stringResource(R.string.assistant_error),
                            onRetry = { vm.startConversation(state.userTranscript) },
                        )
                        DialogPhase.Empty -> Spacer(Modifier.height(0.dp))
                    }
                }
            }

            Spacer(Modifier.weight(1.15f))

            // === ПОДСКАЗКИ ===
            AnimatedVisibility(
                visible = state.suggestions.isNotEmpty() &&
                    state.voiceState == VoiceState.Idle,
                enter = fadeIn(tween(240)) + slideInVertically(tween(260)) { it / 4 },
                exit = fadeOut(tween(140)),
            ) {
                SuggestionRow(
                    suggestions = state.suggestions,
                    onClick = { vm.startConversation(it) },
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            MicButton(
                isActive = state.voiceState != VoiceState.Idle &&
                    state.voiceState != VoiceState.Error,
                stateLabel = state.voiceState.label,
                level = state.audioLevel,
                onClick = { vm.toggleConversation() },
                onPress = { vm.warmupStt() },
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text = if (state.voiceState == VoiceState.Idle) {
                    stringResource(R.string.assistant_welcome_hint)
                } else {
                    stringResource(R.string.assistant_stop)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            // Запас под плавающую нижнюю навигацию.
            Spacer(Modifier.height(96.dp))
        }
    }
}

// ============================================================
//  ФАЗЫ ДИАЛОГА
// ============================================================

/** Что именно показывать в центральной карточке. */
private enum class DialogPhase {
    Welcome, Listening, Thinking, Reply, Error, Empty;

    companion object {
        fun of(state: AssistantUiState): DialogPhase = when {
            state.voiceState == VoiceState.Error -> Error
            state.voiceState == VoiceState.Listening -> Listening
            state.voiceState == VoiceState.Thinking -> Thinking
            state.amaliaReply.isNotEmpty() -> Reply
            state.isFirstLaunch -> Welcome
            else -> Welcome
        }
    }
}

// ============================================================
//  ШАПКА
// ============================================================

@Composable
private fun AssistantTopBar(
    conversationCount: Int,
    voiceState: VoiceState,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.screen,
                end = Spacing.sm,
                top = Spacing.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatePulse(voiceState = voiceState)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (conversationCount > 0) {
                    "$conversationCount ${pluralizeConversations(conversationCount)}"
                } else {
                    stringResource(R.string.assistant_idle)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
        GlassIconButton(
            icon = Icons.AutoMirrored.Rounded.Chat,
            contentDescription = stringResource(R.string.nav_history),
            onClick = onNavigateToHistory,
            badge = conversationCount > 0,
        )
        GlassIconButton(
            icon = Icons.Rounded.Settings,
            contentDescription = stringResource(R.string.nav_settings),
            onClick = onNavigateToSettings,
        )
    }
}

/** Дышащая точка-индикатор состояния слева от имени. */
@Composable
private fun StatePulse(voiceState: VoiceState) {
    val transition = rememberInfiniteTransition(label = "statePulse")
    val breath by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (voiceState == VoiceState.Idle) 2600 else 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val color by animateColorAsState(
        targetValue = stateColor(voiceState),
        animationSpec = tween(360),
        label = "pulseColor",
    )

    Box(
        modifier = Modifier.size(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f * breath)),
        )
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.55f + 0.45f * breath)),
        )
    }
}

// ============================================================
//  СОСТОЯНИЕ
// ============================================================

@Composable
private fun StatusLabel(state: VoiceState, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        targetValue = stateColor(state),
        animationSpec = tween(360),
        label = "statusColor",
    )
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically(tween(240)) { it / 3 })
                .togetherWith(fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it / 3 })
        },
        label = "statusLabel",
        modifier = modifier,
    ) { target ->
        Text(
            text = target.label,
            style = MaterialTheme.typography.titleLarge,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun stateColor(state: VoiceState): Color = when (state) {
    VoiceState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
    VoiceState.Listening -> MaterialTheme.colorScheme.secondary
    VoiceState.Thinking -> MaterialTheme.colorScheme.onSurfaceVariant
    VoiceState.Speaking -> MaterialTheme.colorScheme.tertiary
    VoiceState.Error -> MaterialTheme.colorScheme.error
}

// ============================================================
//  КАРТОЧКИ ДИАЛОГА
// ============================================================

@Composable
private fun WelcomeCard() {
    GlassCard(
        modifier = Modifier.padding(horizontal = Spacing.screen),
        cornerRadius = Radius.lg,
    ) {
        Text(
            text = stringResource(R.string.assistant_welcome_hint),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = stringResource(R.string.assistant_welcome_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ListeningCard(text: String) {
    // STT подключается ~1 секунду после нажатия — показываем подсказку
    val connecting = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1100)
        connecting.value = false
    }

    GlassCard(
        modifier = Modifier.padding(horizontal = Spacing.screen),
        cornerRadius = Radius.lg,
    ) {
        CardLabel(
            text = stringResource(R.string.assistant_listening),
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(Spacing.xs))
        when {
            connecting.value -> Text(
                text = stringResource(R.string.assistant_connecting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            text.isBlank() -> TypingDots()
            else -> Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ThinkingCard(prompt: String) {
    GlassCard(
        modifier = Modifier.padding(horizontal = Spacing.screen),
        cornerRadius = Radius.lg,
    ) {
        if (prompt.isNotBlank()) {
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        CardLabel(
            text = stringResource(R.string.assistant_thinking),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        TypingDots()
    }
}

@Composable
private fun ReplyCard(
    prompt: String,
    reply: String,
    progress: Float,
    speaking: Boolean,
    onRepeat: () -> Unit,
    onCopy: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.padding(horizontal = Spacing.screen),
        cornerRadius = Radius.lg,
        elevated = true,
    ) {
        if (prompt.isNotBlank()) {
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(Spacing.xs))
        }
        CardLabel(
            text = stringResource(R.string.app_name),
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(Spacing.xs))

        // Ответ проявляется по словам синхронно с «речью».
        val words = remember(reply) { reply.split(' ') }
        val visible = if (progress >= 1f) {
            reply
        } else {
            val count = (words.size * progress).toInt().coerceAtLeast(1)
            words.take(count).joinToString(" ")
        }
        Column(
            modifier = Modifier
                .heightIn(max = 190.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = visible,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        AnimatedVisibility(
            visible = !speaking && progress >= 1f,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(120)),
        ) {
            Row(
                modifier = Modifier.padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassTextAction(
                    icon = Icons.Rounded.Refresh,
                    text = stringResource(R.string.assistant_repeat),
                    onClick = onRepeat,
                )
                GlassTextAction(
                    icon = Icons.Rounded.ContentCopy,
                    text = stringResource(R.string.assistant_copy),
                    onClick = onCopy,
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    GlassCard(
        modifier = Modifier.padding(horizontal = Spacing.screen),
        cornerRadius = Radius.lg,
        tint = MaterialTheme.colorScheme.error,
    ) {
        CardLabel(
            text = stringResource(R.string.common_error),
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.sm))
        GlassTextAction(
            icon = Icons.Rounded.Refresh,
            text = stringResource(R.string.common_retry),
            onClick = onRetry,
        )
    }
}

/** Мелкий лейбл-«кто говорит» внутри карточки. */
@Composable
private fun CardLabel(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

/** Три пульсирующие точки — единый индикатор «идёт процесс». */
@Composable
private fun TypingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = modifier.height(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.22f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(560, delayMillis = index * 160, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = alpha)),
            )
        }
    }
}

/** Компактное стеклянное действие «иконка + слово». */
@Composable
private fun GlassTextAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .glassSurface(shape = RoundedCornerShape(Radius.chip))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            .semantics {
                role = Role.Button
                contentDescription = text
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

// ============================================================
//  ПОДСКАЗКИ
// ============================================================

@Composable
private fun SuggestionRow(
    suggestions: List<String>,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        contentPadding = PaddingValues(horizontal = Spacing.screen),
    ) {
        items(suggestions, key = { it }) { suggestion ->
            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .glassSurface(shape = RoundedCornerShape(Radius.chip))
                    .clickable { onClick(suggestion) }
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    .semantics {
                        role = Role.Button
                        contentDescription = suggestion
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                )
            }
        }
    }
}

// ============================================================
//  УТИЛИТЫ
// ============================================================

/** Русская форма слова «разговор» для счётчика в шапке. */
private fun pluralizeConversations(n: Int): String = when {
    n % 100 in 11..14 -> "разговоров"
    n % 10 == 1 -> "разговор"
    n % 10 in 2..4 -> "разговора"
    else -> "разговоров"
}

// ============================================================
//  PREVIEW
// ============================================================

@Preview(showBackground = true, backgroundColor = 0xFF07070B)
@Composable
private fun AssistantScreenPreview() {
    AmaliaTheme {
        AssistantScreen()
    }
}
