package com.my.amali.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.DeleteOutline
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.my.amali.R
import com.my.amali.data.model.ChatMessage
import com.my.amali.data.model.Conversation
import com.my.amali.ui.components.AmaliaScreen
import com.my.amali.ui.components.EmptyState
import com.my.amali.ui.components.GlassDialog
import com.my.amali.ui.components.GlassIconButton
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.glassSurface
import com.my.amali.ui.theme.iconAccent
import com.my.amali.ui.theme.paletteChip

/**
 * Детальный экран разговора.
 *
 * Пузыри: реплика пользователя — справа, акцентный градиент; ответ Амалии —
 * слева, стекло с аватаром. Долгое нажатие копирует текст с тактильным
 * откликом, поэтому отдельные кнопки под каждым сообщением не нужны.
 *
 * ## Что добавлено поверх «просто пузырей»
 *
 * 1. **Действия внутри ответа.** Чипы «Wi-Fi включён», «яркость 30%» под
 *    репликой: восстановленный из истории диалог показывает, что Амалия
 *    реально сделала, а не только что сказала.
 * 2. **Разделители дней.** Ночной разговор и утренний выглядят по-разному,
 *    и без метки даты это нечитаемо.
 * 3. **Карточка сжатого контекста.** Там, где дословная память заканчивается,
 *    стоит раскрываемое резюме: пользователь видит границу памяти ассистента
 *    вместо ощущения «она половину забыла».
 */
@Composable
fun ConversationDetailScreen(
    conversationId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: ConversationDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ConversationDetailViewModel(
                    savedStateHandle = SavedStateHandle().apply {
                        set("conversationId", conversationId)
                    },
                )
            }
        },
    )
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val listState = rememberLazyListState()
    var showDelete by remember { mutableStateOf(false) }

    val messages = conversation?.messages.orEmpty()
    val rows = remember(messages, conversation) { detailRows(messages, conversation) }

    // При открытии показываем конец разговора — это его актуальная часть.
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) listState.scrollToItem(rows.lastIndex)
    }

    val fallbackTitle = stringResource(R.string.history_title)
    val screenTitle = conversation?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle
    val messagesWord = stringResource(R.string.history_messages)

    AmaliaScreen(
        title = screenTitle,
        subtitle = if (messages.isNotEmpty()) "${messages.size} $messagesWord" else null,
        onBack = onBack,
        backLabel = stringResource(R.string.common_back),
        actions = {
            if (conversation != null) {
                GlassIconButton(
                    icon = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.common_delete),
                    onClick = { showDelete = true },
                )
            }
        },
        modifier = modifier,
    ) { _ ->
        if (messages.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Rounded.Chat,
                title = stringResource(R.string.history_empty),
                description = stringResource(R.string.history_empty_desc),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    top = Spacing.xxs,
                    bottom = 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is DetailRow.Day -> DayDivider(row.timestamp)
                        is DetailRow.Summary -> SummaryMarker(conversation?.summaryPreview.orEmpty())
                        is DetailRow.Message -> MessageBubble(
                            message = row.message,
                            onCopy = {
                                clipboard.setText(AnnotatedString(row.message.content))
                                haptics.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    if (showDelete) {
        GlassDialog(
            title = stringResource(R.string.history_delete_confirm),
            message = conversation?.title.orEmpty(),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.privacy_cancel),
            destructive = true,
            onConfirm = {
                showDelete = false
                vm.delete(onDeleted = onBack)
            },
            onDismiss = { showDelete = false },
        )
    }
}

// ════════════════════════════════════════════════════════════
//  СТРОКИ ЭКРАНА
// ════════════════════════════════════════════════════════════

private sealed interface DetailRow {
    val key: Any

    data class Day(override val key: String, val timestamp: Long) : DetailRow
    data class Message(override val key: String, val message: ChatMessage) : DetailRow

    /** Граница дословной памяти: дальше — только пересказ. */
    data object Summary : DetailRow {
        override val key: Any get() = "summary-marker"
    }
}

private fun detailRows(messages: List<ChatMessage>, conversation: Conversation?): List<DetailRow> {
    if (messages.isEmpty()) return emptyList()
    val rows = mutableListOf<DetailRow>()
    var lastDay = Int.MIN_VALUE
    val cut = conversation?.summarizedCount ?: 0
    messages.forEachIndexed { index, message ->
        val day = dayKey(message.timestamp)
        if (day != lastDay) {
            lastDay = day
            rows += DetailRow.Day(key = "day-$day", timestamp = message.timestamp)
        }
        if (index == cut && conversation?.hasCompressedContext == true) {
            rows += DetailRow.Summary
        }
        rows += DetailRow.Message(key = message.id, message = message)
    }
    return rows
}

@Composable
private fun DayDivider(timestamp: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(0.7.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        )
        Text(
            text = dayLabel(timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(0.7.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        )
    }
}

/**
 * Маркер сжатого контекста с раскрываемым резюме.
 *
 * По умолчанию свёрнут: читать пересказ своего же разговора нужно не всегда,
 * но сама граница памяти должна быть видимой.
 */
@Composable
private fun SummaryMarker(summary: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val markerLabel = stringResource(R.string.history_context_summary)
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "summaryChevron",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(shape = RoundedCornerShape(Radius.md))
            .clip(RoundedCornerShape(Radius.md))
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = {},
            )
            .padding(Spacing.md)
            .semantics { contentDescription = markerLabel },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = stringResource(R.string.history_context_summary),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(16.dp)
                    .rotate(chevron),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180)) + expandVertically(tween(220)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
        ) {
            Column {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = summary.ifBlank { stringResource(R.string.history_compressed_marker) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (summary.isNotBlank()) {
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = stringResource(R.string.history_compressed_marker),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  ПУЗЫРИ
// ════════════════════════════════════════════════════════════

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onCopy: () -> Unit,
) {
    val isUser = message.isFromUser
    val copyLabel = stringResource(R.string.assistant_copy)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .paletteChip(CircleShape, strength = 1f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = iconAccent(),
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(Spacing.xs))
        }

        val shape = RoundedCornerShape(
            topStart = Radius.md,
            topEnd = Radius.md,
            bottomStart = if (isUser) Radius.md else Radius.bubbleTail,
            bottomEnd = if (isUser) Radius.bubbleTail else Radius.md,
        )

        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .then(
                    if (isUser) {
                        Modifier
                            .clip(shape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary,
                                    ),
                                ),
                            )
                            .border(width = 0.8.dp, color = Color.White.copy(alpha = 0.18f), shape = shape)
                    } else {
                        Modifier.glassSurface(shape = shape)
                    },
                )
                .combinedClickable(onClick = {}, onLongClick = onCopy)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                .semantics { contentDescription = "${message.content}. $copyLabel" },
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            // Что было сделано в этом ответе — короткие чипы под текстом.
            if (message.actions.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xs))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    message.actions.forEach { action ->
                        ActionChip(text = action)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = clockTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** Чип одного исполненного действия. */
@Composable
private fun ActionChip(text: String, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.secondary
    Row(
        modifier = modifier
            .heightIn(min = 22.dp)
            .clip(RoundedCornerShape(Radius.chip))
            .paletteChip(RoundedCornerShape(Radius.chip), strength = 0.8f)
            .padding(horizontal = Spacing.xs, vertical = 2.dp)
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(10.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
