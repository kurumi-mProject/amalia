package com.my.amali.ui.components

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.my.amali.data.ai.ModelCatalog
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing

/**
 * ════════════════════════════════════════════════════════════════════════
 *  Компоненты экрана «API и модели»
 * ════════════════════════════════════════════════════════════════════════
 *
 * Здесь живут три вещи, которых не было в проекте и без которых экран
 * настроек API невозможен:
 *
 *  1. [SecretField] — ввод ключа. Ключ по умолчанию скрыт точками (человек
 *     может вводить его при посторонних), но его можно показать одним тапом
 *     и скопировать: провайдеры часто требуют вставить ключ ещё куда-то.
 *  2. [ModelSelector] — раскрывающийся список моделей. Выбранная модель видна
 *     закрытым списком, а по тапу открывается перечень с пояснениями. Последним
 *     пунктом всегда идёт «Своя модель» — на случай, если провайдер выпустил
 *     модель позже, чем вышло приложение.
 *  3. [ApiProviderCard] — карточка провайдера целиком: статус, ключ, модель,
 *     ссылка на консоль.
 *
 * Все три подчинены одному правилу: **состояние читается без цвета**.
 * Заполненный ключ помечен галочкой и словами, выбранная модель — галочкой,
 * а не только акцентом.
 */

/**
 * Поле ввода секрета (API-ключа).
 *
 * @param value текущее значение ключа.
 * @param onValueChange изменение ключа (сохраняется вызывающей стороной).
 * @param label подпись поля.
 * @param placeholder подсказка, пока поле пустое.
 * @param maskedVisible открыт ли ключ в открытом виде.
 * @param onToggleVisibility переключение показа.
 * @param onClear очистка ключа (возврат к значению из сборки).
 */
@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    maskedVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(Radius.sm)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xxs))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .border(
                    width = 1.dp,
                    color = if (value.isNotBlank()) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    },
                    shape = shape,
                )
                .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        // Моноширинный шрифт: ключи читаются посимвольно, когда
                        // пользователь сверяет их с консолью провайдера.
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = if (maskedVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onValueChange(value.trim()) },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = label },
                )
            }

            // Показать/скрыть.
            IconTap(
                icon = if (maskedVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                description = if (maskedVisible) "Скрыть ключ" else "Показать ключ",
                onClick = onToggleVisibility,
            )
            // Копировать: ключ нужен не только здесь.
            if (value.isNotBlank()) {
                IconTap(
                    icon = Icons.Rounded.ContentCopy,
                    description = "Скопировать ключ",
                    onClick = { clipboard.setText(AnnotatedString(value)) },
                )
                IconTap(
                    icon = Icons.Rounded.Close,
                    description = "Очистить ключ",
                    onClick = onClear,
                )
            }
        }

        if (value.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xxs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Ключ сохранён · ${mask(value)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

/** Тап-иконка 44dp — минимальная комфортная зона нажатия. */
@Composable
private fun IconTap(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
    }
}

/** Короткая маска ключа: первые четыре символа и хвост. */
private fun mask(key: String): String {
    val trimmed = key.trim()
    if (trimmed.length <= 8) return "•".repeat(trimmed.length.coerceAtLeast(4))
    return trimmed.take(4) + "•".repeat(6) + trimmed.takeLast(4)
}

/**
 * Раскрывающийся список выбора модели.
 *
 * ## Почему не системный DropdownMenu
 *
 * `DropdownMenu` из Material 3 рисует собственное всплывающее окно поверх
 * иерархии: внутри прокручиваемого экрана настроек оно «отвязывается» от
 * карточки и перекрывает соседние поля, а на длинном списке моделей
 * превращается в отдельный слой, у которого своя логика закрытия. Здесь
 * список раскрывается **внутри** карточки: связь «поле → варианты» видна
 * физически, экран сам подстраивает высоту, а системная кнопка «назад»
 * закрывает список, а не уводит с экрана.
 *
 * @param models варианты (уже включая пункт «своя модель»).
 * @param selectedId выбранный идентификатор; может не совпадать ни с одним
 *   пунктом, если пользователь вписал модель вручную — тогда откроется
 *   режим ручного ввода.
 * @param customSentinel служебный идентификатор пункта «своя модель».
 * @param onSelect выбор варианта из списка.
 * @param onCustomChange ручной ввод идентификатора модели.
 */
@Composable
fun ModelSelector(
    title: String,
    subtitle: String,
    models: List<ModelCatalogOption>,
    selectedId: String,
    customSentinel: String,
    onSelect: (String) -> Unit,
    onCustomChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val isCustom = models.none { it.id == selectedId } || selectedId == customSentinel
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "modelChevron",
    )
    val shape = RoundedCornerShape(Radius.sm)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(shape)
                .clickable { expanded = !expanded }
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                .semantics {
                    role = Role.Button
                    contentDescription = "$title: ${if (isCustom) selectedId else titleFor(models, selectedId)}"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isCustom) selectedId else titleFor(models, selectedId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier
                    .size(22.dp)
                    .rotate(chevron),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180)) + expandVertically(tween(220)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(
                        start = Spacing.sm,
                        end = Spacing.sm,
                        bottom = Spacing.xxs,
                    ),
                )
                models.forEach { option ->
                    ModelOptionRow(
                        option = option,
                        selected = if (option.id == customSentinel) isCustom else option.id == selectedId,
                        onClick = {
                            if (option.id == customSentinel) {
                                onSelect(customSentinel)
                                // Не закрываем список: человек пришёл сюда
                                // именно чтобы вписать модель, и закрытие
                                // заставило бы его открывать список снова.
                            } else {
                                onSelect(option.id)
                                expanded = false
                            }
                        },
                    )
                }
                if (selectedId == customSentinel || models.none { it.id == selectedId }) {
                    CustomModelInput(
                        value = if (selectedId == customSentinel) "" else selectedId,
                        onValueChange = onCustomChange,
                    )
                }
            }
        }
    }
}

/**
 * Представление модели для UI.
 *
 * Компонент намеренно не зависит от [ModelCatalog] напрямую: список моделей
 * может прийти и из настроек (пользовательские варианты), и из каталога,
 * а рисование строки от источника не зависит. Конвертация — в
 * [ModelCatalog.modelsWithCustom].
 */
data class ModelCatalogOption(
    val id: String,
    val title: String,
    val note: String,
    val recommended: Boolean = false,
)

/**
 * Модели провайдера в виде, который рисует [ModelSelector].
 *
 * Служебный пункт «своя модель» уже добавлен последним — так он гарантированно
 * оказывается внизу списка, а не спорит за внимание с рекомендованной моделью.
 */
fun modelOptionsFor(provider: ModelCatalog.Provider): List<ModelCatalogOption> =
    ModelCatalog.modelsWithCustom(provider).map { option ->
        ModelCatalogOption(
            id = option.id,
            title = option.title,
            note = option.note,
            recommended = option.recommended,
        )
    }

@Composable
private fun ModelOptionRow(
    option: ModelCatalogOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "modelRowPress",
    )
    val accent = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = 2.dp)
            .scale(scale)
            .clip(RoundedCornerShape(Radius.sm))
            .background(
                if (selected) accent.copy(alpha = 0.12f) else Color.Transparent,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.width(Spacing.xs))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (option.recommended) {
                    Spacer(Modifier.width(Spacing.xxs))
                    Text(
                        text = "рекомендую",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            if (option.note.isNotBlank()) {
                Text(
                    text = option.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Поле ручного ввода идентификатора модели. */
@Composable
private fun CustomModelInput(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val shape = RoundedCornerShape(Radius.sm)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    shape = shape,
                )
                .padding(horizontal = Spacing.sm),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = "например, qwen/qwen3.8-27b",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Идентификатор уходит в запрос как есть — проверь его в консоли провайдера.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

private fun titleFor(models: List<ModelCatalogOption>, id: String): String =
    models.firstOrNull { it.id == id }?.title ?: id
