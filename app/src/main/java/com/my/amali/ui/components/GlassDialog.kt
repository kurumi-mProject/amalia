package com.my.amali.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.my.amali.ui.theme.amaliaShadow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.glassSurface

/**
 * GlassDialog — диалог подтверждения в фирменном стекле.
 *
 * Заменяет системный AlertDialog: тот рисуется плотной серой
 * плашкой и полностью выбивается из стеклянного интерфейса.
 * Здесь — стеклянная панель со световым контуром, крупный заголовок,
 * пояснение и два действия, где деструктивное окрашено в цвет ошибки.
 *
 * @param destructive true — подтверждающее действие необратимо.
 */
@Composable
fun GlassDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    GlassDialogShell(
        title = title,
        onDismiss = onDismiss,
        modifier = modifier,
        destructive = destructive,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryButton(
                text = dismissLabel,
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = confirmLabel,
                onClick = onConfirm,
                destructive = destructive,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Стеклянная оболочка диалога с произвольным содержимым.
 *
 * Нужна там, где диалог не сводится к «заголовок + абзац + две кнопки»:
 * например, задание голосового названия приложения требует поля ввода,
 * списка уже заданных названий и собственной логики кнопок. Дублировать
 * стекло, скругления, тень и обработку клика мимо окна в каждом таком
 * случае — верный способ получить три разных диалога в одном приложении.
 *
 * @param title заголовок; также служит подписью для TalkBack.
 * @param onDismiss закрытие по тапу мимо окна или системной кнопкой «назад».
 * @param destructive окрашивает свечение стекла в цвет ошибки.
 * @param content содержимое внутри уже готовой стеклянной поверхности.
 */
@Composable
fun GlassDialogShell(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = modifier
                .padding(horizontal = Spacing.xl)
                .fillMaxWidth()
                // Длинный список названий иначе выдавил бы кнопки за экран
                // и на низких устройствах диалог стал бы не закрыть.
                .heightIn(max = 520.dp)
                .amaliaShadow(elevation = 0.8f, shape = RoundedCornerShape(Radius.lg))
                .glassSurface(
                    shape = RoundedCornerShape(Radius.lg),
                    elevated = true,
                    fillAlpha = 0.97f,
                    tint = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                .verticalScroll(scrollState)
                .padding(Spacing.xl),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.xs))
            content()
        }
    }
}
