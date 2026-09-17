package com.my.amali.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.my.amali.R
import com.my.amali.data.apps.InstalledApp
import com.my.amali.ui.components.AmaliaScreen
import com.my.amali.ui.components.GlassCard
import com.my.amali.ui.components.GlassGroup
import com.my.amali.ui.components.SectionTitle
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.glassSurface
import com.my.amali.ui.theme.iconAccent
import com.my.amali.ui.theme.paletteChip

/**
 * ════════════════════════════════════════════════════════════════════════
 *  «ПРИЛОЖЕНИЯ АМАЛИИ» — обучение голосовому открытию приложений
 * ════════════════════════════════════════════════════════════════════════
 *
 * ## Задача, которую решает экран
 *
 * Раньше открытие приложений держалось на захардкоженном списке из 18
 * пакетов Google. На живом телефоне это означает: половина приложений не
 * находится, а те, что находятся, угадываются по одному-единственному
 * варианту пакета, которого на не-Google прошивке может не быть.
 *
 * Здесь пользователь **сам** выбирает из своего реального списка то, что
 * ему важно, и — главное — задаёт **название, которым он это называет**.
 *
 * ## Два уровня настройки, и почему оба нужны
 *
 * ── 1. Избранное («Амалия знает эти приложения») ────────────────────────
 *
 *    Отмеченные приложения уходят в системный промпт LLM. Это решает две
 *    задачи сразу:
 *
 *     — **модель не угадывает.** Она видит готовый список «название → пакет»
 *       и вместо `com.miui.gallery` пишет «Галерея», как её видят на экране;
 *     — **контекст не раздувается.** Отдать все 180 пакетов нельзя — это
 *       сотни токенов мусора на каждый запрос. Пользователь отбирает 5–15
 *       того, что реально просит голосом.
 *
 * ── 2. Синоним («как именно я его называю») ─────────────────────────────
 *
 *    Даже идеальный поиск по названию не спасает от того, как люди говорят:
 *    «музон», «телега», «видосы», «читалка». Синоним — единственный способ
 *    это покрыть, и он же снимает **опасный случай омонимов**: если на
 *    телефоне три приложения со словом «Банк», угадывание может открыть не
 *    то. Явно заданный синоним имеет приоритет выше любого автопоиска.
 *
 * ## Почему список системных приложений не скрыт
 *
 * Камера, Часы, Галерея, Калькулятор — именно они чаще всего системные.
 * Скрывать их «чтобы не засорять список» — значит ломать главный сценарий.
 * Вместо скрытия используется сортировка: избранные сверху, затем обычные,
 * системные в конце, а сверху всегда есть поиск.
 *
 * ## Состояния экрана
 *
 *  — **загрузка**: скан `PackageManager` занимает 100–400 мс, поэтому
 *    показываются скелетоны, а не пустой экран;
 *  — **пусто после поиска**: честная подсказка + предложение добавить
 *    приложение вручную по пакету (для лончеров и скрытых приложений);
 *  — **приложение удалено**: запись в избранном помечается «не найдено» и
 *    убирается одним тапом — иначе модель будет обещать открыть то, чего нет.
 */
@Composable
fun AppPickerSettings(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: AppPickerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    // При смене поискового запроса возвращаемся к началу списка: иначе
    // результаты появляются «где-то в середине» и выглядят как пустой экран.
    LaunchedEffect(state.query) {
        listState.scrollToItem(0)
    }

    var aliasTarget by remember { mutableStateOf<InstalledApp?>(null) }

    AmaliaScreen(
        title = stringResource(R.string.settings_apps),
        subtitle = stringResource(R.string.settings_apps_desc),
        onBack = onBack,
        backLabel = stringResource(R.string.common_back),
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.listGap),
        ) {
            // ── Пояснение + счётчик ─────────────────────────────────────
            item(key = "intro") {
                AppsIntroCard(
                    selectedCount = state.pinned.size,
                    totalCount = state.apps.size,
                    loading = state.isLoading,
                )
            }

            // ── Поиск ───────────────────────────────────────────────────
            item(key = "search") {
                AppSearchField(
                    query = state.query,
                    onQueryChange = vm::setQuery,
                )
            }

            // ── Избранное ───────────────────────────────────────────────
            if (state.pinned.isNotEmpty()) {
                item(key = "pinned-title") {
                    SectionTitle(stringResource(R.string.apps_pinned_title))
                }
                items(state.pinned, key = { "pin-${it.packageName}" }) { app ->
                    PinnedAppRow(
                        app = app,
                        status = state.statusOf(app.packageName),
                        onRemove = { vm.unpin(app.packageName) },
                        onAddAlias = { aliasTarget = it },
                    )
                }
                item(key = "pinned-gap") { Spacer(Modifier.height(Spacing.xs)) }
            }

            // ── Загрузка ────────────────────────────────────────────────
            if (state.isLoading) {
                items(6, key = { "skeleton-$it" }) {
                    AppRowSkeleton()
                }
                return@LazyColumn
            }

            // ── Пустой результат поиска ─────────────────────────────────
            if (state.visibleApps.isEmpty()) {
                item(key = "empty") {
                    EmptyAppsState(
                        query = state.query,
                        onClearQuery = { vm.setQuery("") },
                    )
                }
                return@LazyColumn
            }

            item(key = "all-title") {
                SectionTitle(
                    if (state.query.isBlank()) {
                        stringResource(R.string.apps_all_title)
                    } else {
                        stringResource(R.string.apps_found_title, state.visibleApps.size)
                    },
                )
            }

            items(state.visibleApps, key = { it.packageName }) { app ->
                AppPickerRow(
                    app = app,
                    selected = state.isPinned(app.packageName),
                    onToggle = { vm.togglePin(app) },
                    onAddAlias = { aliasTarget = app },
                )
            }
        }

        // ── Диалог синонима ────────────────────────────────────────────
        aliasTarget?.let { target ->
            AliasDialog(
                app = target,
                existing = vm.aliasesFor(target.packageName),
                onDismiss = { aliasTarget = null },
                onSave = { alias ->
                    vm.addAlias(alias, target.packageName)
                    aliasTarget = null
                },
                onRemove = { alias ->
                    vm.removeAlias(alias)
                },
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  ВВОДНАЯ КАРТОЧКА
// ════════════════════════════════════════════════════════════════════════

/**
 * Объясняет, зачем нужен экран, и показывает прогресс.
 *
 * Счётчик «выбрано N из M» здесь принципиален: без него непонятно, что
 * выбор вообще на что-то влияет. Число выбранных видно, и рядом сразу
 * сказано, куда оно уходит.
 */
@Composable
private fun AppsIntroCard(
    selectedCount: Int,
    totalCount: Int,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    GlassCard(cornerRadius = Radius.md, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .paletteChip(shape = CircleShape, strength = 1f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = iconAccent(),
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (loading) {
                        stringResource(R.string.apps_scanning)
                    } else {
                        stringResource(R.string.apps_selected_count, selectedCount, totalCount)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.apps_intro_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  ПОИСК
// ════════════════════════════════════════════════════════════════════════

/**
 * Поле поиска по установленным приложениям.
 *
 * `ImeAction.Done` вместо `Search`: поиск идёт **на каждое нажатие клавиши**,
 * а не по кнопке, поэтому единственное осмысленное действие на клавиатуре —
 * закрыть её и посмотреть результат.
 */
@Composable
private fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = stringResource(R.string.apps_search_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = {
            AnimatedVisibility(visible = query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onQueryChange("") }
                        .semantics {
                            role = Role.Button
                            contentDescription = "Очистить"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(Radius.sm),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {}),
    )
}

// ════════════════════════════════════════════════════════════════════════
//  СТРОКИ СПИСКА
// ════════════════════════════════════════════════════════════════════════

/**
 * Строка приложения: название, пакет, галочка выбора и кнопка синонима.
 *
 * Нажатие на строку — переключение выбора. Это главное действие, поэтому
 * оно занимает всю ширину; добавление синонима — вторичное и живёт
 * отдельной маленькой кнопкой справа, чтобы случайно не попасть по ней.
 */
@Composable
private fun AppPickerRow(
    app: InstalledApp,
    selected: Boolean,
    onToggle: () -> Unit,
    onAddAlias: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "appRowPress",
    )
    val shape = RoundedCornerShape(Radius.sm)
    val title = app.label
    val hint = app.displayHint

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .heightIn(min = 64.dp)
            .glassSurface(shape = shape)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 1.2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onToggle,
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics {
                role = Role.Checkbox
                this.selected = selected
                contentDescription = "$title, ${if (selected) "выбрано" else "не выбрано"}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppAvatar(label = app.label, selected = selected)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = if (app.isSystem) {
                    stringResource(R.string.apps_system_hint, hint)
                } else {
                    hint
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Spacing.xs))

        // Вторичное действие — задать название голосом.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onAddAlias)
                .semantics {
                    role = Role.Button
                    contentDescription = "Добавить название для $title"
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp),
            )
        }

        SelectionMark(selected = selected)
    }
}

/**
 * Строка уже отмеченного приложения.
 *
 * Отличия от [AppPickerRow]: показывает статус (установлено / нет точки
 * входа / удалено) и позволяет убрать запись. Список избранного — это то,
 * что уходит в промпт модели, поэтому «мёртвые» записи здесь опаснее всего:
 * по ним LLM пообещает открыть то, чего на телефоне нет.
 */
@Composable
private fun PinnedAppRow(
    app: com.my.amali.data.repository.PinnedApp,
    status: com.my.amali.data.repository.PinnedAppStatus,
    onRemove: () -> Unit,
    onAddAlias: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val available = status == com.my.amali.data.repository.PinnedAppStatus.AVAILABLE
    val shape = RoundedCornerShape(Radius.sm)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .glassSurface(shape = shape)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppAvatar(label = app.label, selected = true)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = when (status) {
                    com.my.amali.data.repository.PinnedAppStatus.AVAILABLE ->
                        app.packageName
                    com.my.amali.data.repository.PinnedAppStatus.NO_LAUNCHER ->
                        stringResource(R.string.apps_status_no_launcher)
                    com.my.amali.data.repository.PinnedAppStatus.MISSING ->
                        stringResource(R.string.apps_status_missing)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (available) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (available) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable {
                        onAddAlias(
                            InstalledApp(
                                packageName = app.packageName,
                                label = app.label,
                                isSystem = app.isSystem,
                            ),
                        )
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = "Добавить название"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove)
                .semantics {
                    role = Role.Button
                    contentDescription = "Убрать ${app.label}"
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = if (available) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Аватар приложения: первые буквы названия на тонированной подложке.
 *
 * Иконки приложений (`loadIcon`) сознательно **не** грузятся: на списке из
 * сотни строк это сотни Bitmap-ов в памяти и заметные подтормаживания при
 * скролле, а для выбора «какое из двух одинаковых названий» буквы работают
 * не хуже. Иконка появится потом, на экране уже отмеченных приложений.
 */
@Composable
private fun AppAvatar(label: String, selected: Boolean) {
    val initials = remember(label) {
        label.trim().split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifBlank { "?" }
    }
    val size by animateDpAsState(
        targetValue = 40.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "avatarSize",
    )
    Box(
        modifier = Modifier
            .size(size)
            .paletteChip(shape = RoundedCornerShape(Radius.xs), strength = if (selected) 1.4f else 0.8f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/** Галочка выбора с «пружинным» появлением — понятная обратная связь. */
@Composable
private fun SelectionMark(selected: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.001f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "selectionMark",
    )
    Box(
        modifier = Modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  СОСТОЯНИЯ
// ════════════════════════════════════════════════════════════════════════

/** Скелетон строки — сканирование занимает время, пустой экран выглядит как баг. */
@Composable
private fun AppRowSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .glassSurface(shape = RoundedCornerShape(Radius.sm))
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        )
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(Radius.chip))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.32f)
                    .height(9.dp)
                    .clip(RoundedCornerShape(Radius.chip))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
            )
        }
    }
}

/**
 * Пустой результат поиска.
 *
 * Текст зависит от того, **искал** пользователь или список реально пуст:
 * это разные ситуации и лечатся по-разному. В первом случае помогает
 * очистить запрос, во втором — проблема с видимостью пакетов.
 */
@Composable
private fun EmptyAppsState(query: String, onClearQuery: () -> Unit) {
    GlassCard(cornerRadius = Radius.md) {
        Text(
            text = if (query.isBlank()) {
                stringResource(R.string.apps_empty_none)
            } else {
                stringResource(R.string.apps_empty_query, query)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = stringResource(R.string.apps_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (query.isNotBlank()) {
            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(Radius.chip))
                    .glassSurface(shape = RoundedCornerShape(Radius.chip))
                    .clickable(onClick = onClearQuery)
                    .padding(horizontal = Spacing.md)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Очистить поиск"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = stringResource(R.string.apps_clear_search),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

/**
 * Диалог задания голосового синонима.
 *
 * Показывает уже существующие названия с возможностью удалить и поле для
 * нового. Подсказки под полем берут пример именно из этого приложения —
 * абстрактное «введите синоним» пользователю ничего не объясняет.
 */
@Composable
private fun AliasDialog(
    app: InstalledApp,
    existing: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val canSave = draft.isNotBlank()

    com.my.amali.ui.components.GlassDialogShell(
        title = stringResource(R.string.apps_alias_title, app.label),
        onDismiss = onDismiss,
    ) {
        Text(
            text = stringResource(R.string.apps_alias_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text(
                    text = aliasPlaceholderFor(app.label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            },
            shape = RoundedCornerShape(Radius.sm),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(draft) }),
        )

        if (existing.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.apps_alias_existing),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xxs))
            GlassGroup {
                existing.forEach { alias ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .padding(horizontal = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "«$alias»",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { onRemove(alias) }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Удалить «$alias»"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
        ) {
            DialogTextButton(
                text = stringResource(R.string.common_back),
                onClick = onDismiss,
                muted = true,
            )
            DialogTextButton(
                text = stringResource(R.string.common_save),
                onClick = { if (canSave) onSave(draft) },
                muted = !canSave,
            )
        }
    }
}

/** Кнопка диалога: текстовая, чтобы не спорить с главным CTA экрана. */
@Composable
private fun DialogTextButton(text: String, onClick: () -> Unit, muted: Boolean) {
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(Radius.chip))
            .clickable(enabled = !muted, onClick = onClick)
            .padding(horizontal = Spacing.md)
            .semantics {
                role = Role.Button
                contentDescription = text
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (muted) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.secondary
            },
        )
    }
}

/**
 * Пример-подсказка для поля синонима, подобранный под конкретное приложение.
 *
 * Общий текст вроде «введите альтернативное название» не помогает: человек
 * не понимает, что от него хотят. Готовый пример из лексикона снимает
 * вопрос и заодно показывает формулировку, которую ждёт система.
 */
private fun aliasPlaceholderFor(label: String): String {
    val lower = label.lowercase()
    return when {
        lower.contains("youtube") || lower.contains("ютуб") -> "например: ютуб, видосы"
        lower.contains("telegram") || lower.contains("телеграм") -> "например: телега, тг"
        lower.contains("spotify") || lower.contains("музык") -> "например: музон, музыка"
        lower.contains("camera") || lower.contains("камер") -> "например: камера, фотоаппарат"
        lower.contains("gallery") || lower.contains("галере") -> "например: галерея, фотки"
        lower.contains("chrome") || lower.contains("браузер") -> "например: браузер, интернет"
        lower.contains("maps") || lower.contains("карт") -> "например: карты, навигатор"
        lower.contains("clock") || lower.contains("час") -> "например: часы, будильник"
        lower.contains("bank") || lower.contains("банк") -> "например: банк, оплата"
        else -> "например: ${label.lowercase().take(14)}"
    }
}
