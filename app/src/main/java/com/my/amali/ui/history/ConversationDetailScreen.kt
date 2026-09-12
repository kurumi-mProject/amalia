package com.my.amali.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.my.amali.ui.components.AmaliaScreen
import com.my.amali.ui.components.EmptyState
import com.my.amali.ui.components.GlassDialog
import com.my.amali.ui.components.GlassIconButton
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.glassSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Детальный экран разговора.
 *
 * Пузыри: реплики пользователя — справа, акцентный градиент;
 * ответы Амалии — слева, стекло с иконкой-аватаром. Долгое нажатие
 * на любой пузырь копирует текст и даёт тактильный отклик, что
 * убирает необходимость в отдельных кнопках под каждым сообщением.
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
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    var showDelete by remember { mutableStateOf(false) }

    val messages = conversation?.messages.orEmpty()

    // При открытии показываем конец разговора — это его актуальная часть.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
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
    ) { padding ->
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    top = Spacing.xxs,
                    bottom = 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onCopy = {
                            clipboard.setText(AnnotatedString(message.content))
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                    )
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

/** Пузырь одного сообщения. */
@Composable
private fun MessageBubble(
    message: ChatMessage,
    onCopy: () -> Unit,
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
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
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
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
                                        MaterialTheme.colorScheme.secondary,
                                    ),
                                ),
                            )
                            .border(
                                width = 0.8.dp,
                                color = Color.White.copy(alpha = 0.18f),
                                shape = shape,
                            )
                    } else {
                        Modifier.glassSurface(shape = shape)
                    },
                )
                .combinedClickable(
                    onClick = {},
                    onLongClick = onCopy,
                )
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
            Spacer(Modifier.height(2.dp))
            Text(
                text = timeFormat.format(Date(message.timestamp)),
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
