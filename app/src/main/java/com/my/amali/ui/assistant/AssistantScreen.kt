package com.my.amali.ui.assistant

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expanded
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.my.amali.ui.theme.iconAccent
import com.my.amali.ui.theme.paletteChip
import kotlinx.coroutines.delay

/**
 * AssistantScreen — главный экран Амалии.
 *
 * Композиция сверху вниз:
 *  1. живой аурора-фон + декоративный мотив (лепестки/звёзды/…);
 *  2. лёгкая шапка: пульс-индикатор состояния, имя, история, настройки;
 *  3. центр — жидкая волна + одно слово состояния (главный фокус);
 *  4. **единственная гибкая область** — стеклянная карточка диалога;
 *  5. лента подсказок, кнопка микрофона и подсказка-подпись.
 *
 * ## Почему карточка диалога живёт в `weight`, а не «растёт как хочет»
 *
 * Экран собирается в [Column], и раньше центральная карточка не была
 * ограничена по высоте: длинный ответ с кучей выполненных команд просто
 * распухал и лез поверх волны, подсказок и кнопки — то есть в то самое
 * «место, где команды перекрывают UI». Теперь жёсткое правило:
 *
 *  — фиксированными остаются шапка, волна, подсказки и кнопка;
 *  — всё свободное пространство отдаётся карточке (`weight(1f, fill = false)`);
 *  — если контента больше, чем места, карточка скроллит **свою** область,
 *    а соседей не трогает;
 *  — список инструментов ограничен сверху (три строки + «+N ещё»), а сводка
 *    «что сделано» свёрнута в одну строку и раскрывается по нажатию.
 *
 * Всё, кроме волны и кнопки, визуально тише: это делает главное действие
 * однозначным и даёт экрану «дорогое» спокойствие.
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
            modifier = Modifier.fillMaxSize(),
            intensity = visuals.glassIntensity,
            motif = visuals.motif,
            motifDensity = visuals.motifDensity,
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
                contextCompressed = state.contextCompressed,
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToSettings = onNavigateToSettings,
            )

            // Верхняя пустота меньше нижней: волна встаёт в «золотую»
            // верхнюю треть, а не в геометрический центр — так композиция
            // ощущается устойчивой, а не «съехавшей вниз».
            Spacer(Modifier.weight(0.5f))

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

            // === ДИАЛОГ: единственная гибкая область экрана ===
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
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
                        DialogPhase.Welcome -> WelcomeCard(
                            onPickSuggestion = { vm.startConversation(it) },
                        )
                        DialogPhase.Listening -> ListeningCard(text = state.userTranscript)
                        DialogPhase.Thinking -> ThinkingCard(
                            prompt = state.userTranscript,
                            activeTools = state.activeTools,
                        )
                        DialogPhase.Reply -> ReplyCard(
                            prompt = state.userTranscript,
                            reply = state.amaliaReply,
                            progress = state.replyProgress,
                            speaking = state.voiceState == VoiceState.Speaking,
                            toolReports = state.lastToolReports,
                            contextCompressed = state.contextCompressed,
                            contextMessageCount = state.contextMessageCount,
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

            Spacer(Modifier.height(Spacing.md))

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
            Spacer(Modifier.height(92.dp))
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
    contextCompressed: Boolean,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (conversationCount > 0) {
                        "$conversationCount ${pluralizeConversations(conversationCount)}"
                    } else {
                        stringResource(R.string.assistant_idle)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
                // Маркер сжатого контекста: видно, что Амалия помнит разговор
                // пересказом, а не дословно — без этого «память» выглядит багом.
                AnimatedVisibility(visible = contextCompressed) {
                    Row(
                        modifier = Modifier.padding(start = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                            modifier = Modifier.size(11.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = stringResource(R.string.assistant_context_compressed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
                        )
                    }
                }
            }
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
private fun WelcomeCard(onPickSuggestion: (String) -> Unit) {
    val suggestions = welcomeSuggestions()
    GlassCard(
        modifier = Modifier.padding(horizontal = Spacing.screen),
        cornerRadius = Radius.lg,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AmaliaAvatar()
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
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
        Spacer(Modifier.height(Spacing.sm))
        // Сразу видно, что можно сказать: это и есть главное действие экрана.
        suggestions.take(3).forEach { suggestion ->
            Text(
                text = "«$suggestion»",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.chip))
                    .clickable { onPickSuggestion(suggestion) }
                    .padding(vertical = 4.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = suggestion
                    },
            )
        }
    }
}

@Composable
private fun ListeningCard(text: String) {
    // STT подключается ~1 секунду после нажатия — показываем подсказку
    val connecting = remember { mutableStateOf(true) }
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
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ThinkingCard(
    prompt: String,
    activeTools: List<ToolActivity>,
) {
    GlassCard(
        modifier = Modifier.padding(horizontal = Spacing.screen),
        cornerRadius = Radius.lg,
    ) {
        if (prompt.isNotBlank()) {
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        CardLabel(
            text = stringResource(R.string.assistant_thinking),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        if (activeTools.isEmpty()) {
            TypingDots()
        } else {
            ToolActivityStrip(tools = activeTools)
        }
    }
}

/**
 * Индикаторы работающих инструментов.
 *
 * Показываются максимум [VISIBLE_TOOL_ROWS] строк: «выключи всё» с десятью
 * командами не должно превращать карточку в Пропастырь, который выталкивает
 * кнопку микрофона за пределы экрана. Остальное — счётчиком.
 */
@Composable
private fun ToolActivityStrip(tools: List<ToolActivity>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        tools.take(VISIBLE_TOOL_ROWS).forEach { tool ->
            ToolChip(tool = tool)
        }
        if (tools.size > VISIBLE_TOOL_ROWS) {
            Text(
                text = stringResource(
                    R.string.assistant_tools_more,
                    tools.size - VISIBLE_TOOL_ROWS,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = Spacing.xxs, top = 2.dp),
            )
        }
    }
}

@Composable
private fun ToolChip(tool: ToolActivity) {
    val transition = rememberInfiniteTransition(label = "tool-${tool.name}")
    val breath by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(820, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tool-breath",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 30.dp)
            .glassSurface(shape = RoundedCornerShape(Radius.chip))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
            .semantics { contentDescription = tool.humanLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // Пульсирующая точка — признак «работает».
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = breath)),
        )
        Text(
            text = tool.humanLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Карточка ответа.
 *
 * Три зоны: шапка «кто говорит», прокручиваемый текст, сводка действий и
 * кнопки. Прокручивается только текст — кнопки и сводка остаются доступными,
 * сколько бы Амалия ни наболтала.
 */
@Composable
private fun ReplyCard(
    prompt: String,
    reply: String,
    progress: Float,
    speaking: Boolean,
    toolReports: List<ToolReport>,
    contextCompressed: Boolean,
    contextMessageCount: Int,
    onRepeat: () -> Unit,
    onCopy: () -> Unit,
) {
    val scrollState = rememberScrollState()

    // Пока текст растёт, держим взгляд на его конце — ровно там, где Амалия
    // сейчас «говорит». Пользовательский скролл не ломаем: тянем вниз только
    // пока ответ ещё генерируется.
    LaunchedEffect(reply, speaking) {
        if (speaking || progress < 1f) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xs))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            AmaliaAvatar(size = 22.dp)
            Spacer(Modifier.width(Spacing.xs))
            CardLabel(
                text = stringResource(R.string.app_name),
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.weight(1f))
            if (contextCompressed) {
                ContextChip(literalCount = contextMessageCount)
            }
        }
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
                .fillMaxWidth()
                .heightIn(max = ReplyMaxHeight)
                .verticalScroll(scrollState),
        ) {
            Text(
                text = visible,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Сводка «что сделала Амалия» — одна строка, раскрывается по тапу.
        ToolSummary(
            reports = toolReports,
            modifier = Modifier.padding(top = Spacing.sm),
        )

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

/**
 * Сводка выполненных действий: всегда одна строка, список — по нажатию.
 *
 * Именно эта экономия лечит «команды перекрывают UI»: десять исполненных
 * инструментов больше не занимают десять строк в карточке ответа.
 */
@Composable
private fun ToolSummary(reports: List<ToolReport>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    // stringResource нельзя звать внутри semantics-лямбды — она не композабл.
    val actionsTitle = stringResource(R.string.assistant_actions_title)
    val okCount = reports.count { it.ok }
    val fails = reports.size - okCount
    val accent = if (fails == 0 && reports.isNotEmpty()) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.error
    }

    AnimatedVisibility(
        visible = reports.isNotEmpty(),
        enter = fadeIn(tween(220)) + expandVertically(tween(220)),
        exit = fadeOut(tween(160)) + shrinkVertically(tween(180)),
        modifier = modifier,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = actionsTitle
                        this.expanded = expanded
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = if (fails == 0) {
                        stringResource(R.string.assistant_actions_ok, reports.size)
                    } else {
                        stringResource(R.string.assistant_actions_partial, reports.size, fails)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Rounded.KeyboardArrowUp
                    } else {
                        Icons.Rounded.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 116.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    reports.forEach { report -> ToolReportRow(report = report) }
                }
            }
        }
    }
}

/** Одна строка сводки «что сделано» — короткий значок + текст. */
@Composable
private fun ToolReportRow(report: ToolReport) {
    val color = if (report.ok) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = report.summary,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Пилюля «контекст сжат» — объясняет, что память перешла в режим пересказа. */
@Composable
private fun ContextChip(literalCount: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.assistant_context_compressed)
    // Сколько реплик ещё помнится дословно — цифра вместо догадок.
    val readable = "$label · $literalCount"
    Row(
        modifier = modifier
            .heightIn(min = 22.dp)
            .clip(RoundedCornerShape(Radius.chip))
            .paletteChip(RoundedCornerShape(Radius.chip), strength = 0.9f)
            .padding(horizontal = Spacing.xs, vertical = 2.dp)
            .semantics { contentDescription = readable },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = stringResource(R.string.assistant_context_compressed),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/** Аватар Амалии: стеклянный кружок с цветовым «зрачком» текущей палитры. */
@Composable
private fun AmaliaAvatar(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 34.dp,
) {
    val accent = iconAccent()
    val label = stringResource(R.string.app_name)
    Box(
        modifier = modifier
            .size(size)
            .paletteChip(shape = CircleShape, strength = 1f)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(size / 2.4f),
        )
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
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
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
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "actionPress",
    )
    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .scale(scale)
            .glassSurface(shape = RoundedCornerShape(Radius.chip))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
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
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        contentPadding = PaddingValues(horizontal = Spacing.screen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(suggestions, key = { it }) { suggestion ->
            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp, max = 44.dp)
                    .widthIn(max = 220.dp)
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ============================================================
//  УТИЛИТЫ
// ============================================================

/** Подсказки для пустого экрана — берутся из локализованных строк. */
@Composable
private fun welcomeSuggestions(): List<String> = listOf(
    stringResource(R.string.suggestion_hello),
    stringResource(R.string.suggestion_about),
    stringResource(R.string.suggestion_time),
)

/** Русская форма слова «разговор» для счётчика в шапке. */
private fun pluralizeConversations(n: Int): String = when {
    n % 100 in 11..14 -> "разговоров"
    n % 10 == 1 -> "разговор"
    n % 10 in 2..4 -> "разговора"
    else -> "разговоров"
}

/** Сколько строк работающих инструментов показывать до счётчика «+N». */
private const val VISIBLE_TOOL_ROWS = 3

/** Потолок высоты текста ответа: дальше — внутренний скролл, а не рост карточки. */
private val ReplyMaxHeight = 200.dp

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
