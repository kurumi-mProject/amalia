package com.my.amali.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.my.amali.R
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
 * Экран «История»: поиск, список разговоров, удаление.
 *
 * ## Что здесь пошло дальше «просто списка карточек»
 *
 * 1. **Группировка по дням.** «Сегодня / Вчера / На этой неделе / Раньше» —
 *    человек ищет разговор по моменту, а не по дате в правом углу.
 * 2. **Относительное время.** Свежие диалоги показывают «5 мин назад»,
 *    старые — дату. Один формат на весь список looked дешёво.
 * 3. **Сводка действий.** Если в диалоге Амалия что-то делала на телефоне,
 *    это видно прямо в карточке: «действий: 4». Иначе история выглядит как
 *    пустая переписка, хотя половина ценности — в исполненных командах.
 * 4. **Метка сжатого контекста.** Диалог, у которого есть резюме, помечен
 *    звёздочкой: пользователь понимает, где память перешла в пересказ.
 * 5. **Подсветка найденного** в заголовке и превью.
 */
@Composable
fun ConversationListScreen(
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: ConversationListViewModel = viewModel()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }

    AmaliaScreen(
        title = stringResource(R.string.history_title),
        subtitle = if (conversations.isNotEmpty()) {
            "${conversations.size} ${pluralizeConversations(conversations.size)}"
        } else {
            null
        },
        actions = {
            if (conversations.isNotEmpty()) {
                GlassIconButton(
                    icon = Icons.Rounded.DeleteSweep,
                    contentDescription = stringResource(R.string.privacy_clear),
                    onClick = { showClearDialog = true },
                )
            }
        },
        modifier = modifier,
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.screen),
        ) {
            GlassSearchField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    vm.setQuery(it)
                },
                onClear = {
                    searchQuery = ""
                    vm.setQuery("")
                },
            )

            Spacer(Modifier.height(Spacing.sm))

            when {
                conversations.isEmpty() && searchQuery.isNotBlank() -> EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = stringResource(R.string.history_no_results),
                    description = stringResource(R.string.history_no_results_desc),
                    actionLabel = stringResource(R.string.common_close),
                    onAction = {
                        searchQuery = ""
                        vm.setQuery("")
                    },
                    modifier = Modifier.weight(1f),
                )

                conversations.isEmpty() -> EmptyState(
                    icon = Icons.AutoMirrored.Rounded.Chat,
                    title = stringResource(R.string.history_empty),
                    description = stringResource(R.string.history_empty_desc),
                    modifier = Modifier.weight(1f),
                )

                else -> {
                    val rows = remember(conversations) { historyRows(conversations) }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        contentPadding = PaddingValues(top = Spacing.xxs, bottom = 104.dp),
                    ) {
                        items(rows, key = { it.key }) { row ->
                            when (row) {
                                is HistoryRow.Header -> DayHeader(row.timestamp)
                                is HistoryRow.Item -> HistoryCard(
                                    conversation = row.conversation,
                                    query = searchQuery,
                                    onClick = { onOpenConversation(row.conversation.id) },
                                    onDelete = { pendingDelete = row.conversation },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        GlassDialog(
            title = stringResource(R.string.privacy_clear_history_confirm),
            message = stringResource(R.string.privacy_clear_history_confirm_desc),
            confirmLabel = stringResource(R.string.privacy_clear),
            dismissLabel = stringResource(R.string.privacy_cancel),
            destructive = true,
            onConfirm = {
                showClearDialog = false
                vm.clearAll()
            },
            onDismiss = { showClearDialog = false },
        )
    }

    pendingDelete?.let { conversation ->
        GlassDialog(
            title = stringResource(R.string.history_delete_confirm),
            message = conversation.title,
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.privacy_cancel),
            destructive = true,
            onConfirm = {
                vm.delete(conversation.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

// ════════════════════════════════════════════════════════════
//  СЕГМЕНТЫ СПИСКА
// ════════════════════════════════════════════════════════════

/** Строка списка: заголовок дня или сам разговор. */
private sealed interface HistoryRow {
    val key: Any

    data class Header(
        override val key: String,
        val timestamp: Long,
    ) : HistoryRow

    data class Item(
        override val key: String,
        val conversation: Conversation,
    ) : HistoryRow
}

private fun historyRows(list: List<Conversation>): List<HistoryRow> {
    val rows = mutableListOf<HistoryRow>()
    var lastDay = Int.MIN_VALUE
    list.forEach { conversation ->
        val day = dayKey(conversation.updatedAt)
        if (day != lastDay) {
            lastDay = day
            rows += HistoryRow.Header(
                key = "header-$day",
                timestamp = conversation.updatedAt,
            )
        }
        rows += HistoryRow.Item(conversation.id, conversation)
    }
    return rows
}

@Composable
private fun DayHeader(timestamp: Long) {
    Text(
        text = dayLabel(timestamp).uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        modifier = Modifier.padding(
            start = Spacing.xxs,
            top = Spacing.sm,
            bottom = 2.dp,
        ),
    )
}

// ════════════════════════════════════════════════════════════
//  ПОИСК
// ════════════════════════════════════════════════════════════

/**
 * Стеклянное поле поиска: лупа, акцентный курсор, кнопка очистки
 * появляется только при непустом запросе. IME-действие — Search.
 */
@Composable
private fun GlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val placeholder = stringResource(R.string.history_search)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .glassSurface(shape = RoundedCornerShape(Radius.chip))
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(Spacing.xs))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = placeholder },
            )
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        AnimatedVisibility(
            visible = value.isNotEmpty(),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120)),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .clickable {
                        onClear()
                        focusManager.clearFocus()
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = placeholder
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  КАРТОЧКА РАЗГОВОРА
// ════════════════════════════════════════════════════════════

@Composable
private fun HistoryCard(
    conversation: Conversation,
    query: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val initial = remember(conversation.title) {
        conversation.title.trim().firstOrNull()?.uppercase() ?: "·"
    }
    val preview = conversation.lastMessage?.content.orEmpty()
    val actions = remember(conversation) {
        conversation.messages.sumOf { it.actions.size }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.982f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "historyPress",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .glassSurface(shape = RoundedCornerShape(Radius.md))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(Spacing.md)
            .semantics {
                role = Role.Button
                contentDescription = conversation.title
            },
        verticalAlignment = Alignment.Top,
    ) {
        // Инициал на подложке из текущей палитры: карточка принадлежит
        // тому же времени суток, что и фон за ней.
        Box(
            modifier = Modifier
                .size(42.dp)
                .paletteChip(CircleShape, strength = 1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                color = iconAccent(),
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = highlight(conversation.title, query),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = relativeTime(conversation.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (preview.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = highlight(preview, query),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetaChip(
                    icon = Icons.AutoMirrored.Rounded.Chat,
                    text = "${conversation.messages.size} ${stringResource(R.string.history_messages)}",
                )
                if (actions > 0) {
                    Spacer(Modifier.width(Spacing.xs))
                    MetaChip(
                        icon = Icons.Rounded.Tune,
                        text = stringResource(R.string.history_actions_count, actions),
                    )
                }
                if (conversation.hasCompressedContext) {
                    Spacer(Modifier.width(Spacing.xs))
                    // Маркер «помню пересказом»: без него длинная история
                    // выглядит так, будто Амалия её забыла.
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = stringResource(R.string.history_context_summary),
                        tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
                        modifier = Modifier.size(13.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDelete)
                        .semantics {
                            role = Role.Button
                            contentDescription = conversation.title
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Маленькая метрика в карточке: иконка + число. */
@Composable
private fun MetaChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier
            .heightIn(min = 22.dp)
            .clip(RoundedCornerShape(Radius.chip))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = Spacing.xs, vertical = 2.dp)
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Подсветка найденного подзапроса.
 *
 * Поиск по истории без подсветки заставляет открывать каждый разговор, чтобы
 * проверить, то ли это. Акцент — не только цветом: он ещё и полужирный, поэтому
 * различим и при монохромном отображении.
 */
@Composable
@ReadOnlyComposable
private fun highlight(text: String, query: String) = buildAnnotatedString {
    val trimmed = query.trim()
    append(text)
    if (trimmed.isEmpty()) return@buildAnnotatedString
    val matched = text.indexOf(trimmed, ignoreCase = true)
    if (matched >= 0) {
        addStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            ),
            matched,
            (matched + trimmed.length).coerceAtMost(text.length),
        )
    }
}

/** Русская форма слова «разговор». */
private fun pluralizeConversations(n: Int): String = when {
    n % 100 in 11..14 -> "разговоров"
    n % 10 == 1 -> "разговор"
    n % 10 in 2..4 -> "разговора"
    else -> "разговоров"
}
