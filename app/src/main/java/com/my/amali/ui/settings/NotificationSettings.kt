package com.my.amali.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.my.amali.R
import com.my.amali.core.di.ServiceLocator
import com.my.amali.ui.components.AmaliaScreen
import com.my.amali.ui.components.GlassCard
import com.my.amali.ui.components.SecondaryButton
import com.my.amali.ui.components.SectionTitle
import com.my.amali.ui.components.StatusBanner
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing

/**
 * Экран «Уведомления».
 *
 * Показывает реальный статус разрешения POST_NOTIFICATIONS баннером
 * (иконка + цвет + текст — состояние читается без опоры на цвет) и
 * даёт одну понятную кнопку перехода в системные настройки.
 * Статус перечитывается при возврате на экран, поэтому пользователь
 * сразу видит результат своих действий в системных настройках.
 */
@Composable
fun NotificationSettings(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hub = remember { ServiceLocator.systemControllers }
    var enabled by remember { mutableStateOf(hub.areNotificationsEnabled()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Перечитываем статус при каждом возврате на экран и корректно
    // снимаем наблюдателя — иначе утечка при уходе с экрана.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = hub.areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AmaliaScreen(
        title = stringResource(R.string.settings_notifications),
        subtitle = stringResource(R.string.settings_notifications_desc),
        onBack = onBack,
        backLabel = stringResource(R.string.common_back),
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            StatusBanner(
                icon = if (enabled) {
                    Icons.Rounded.NotificationsActive
                } else {
                    Icons.Rounded.NotificationsOff
                },
                title = stringResource(R.string.device_notifications),
                message = stringResource(
                    if (enabled) R.string.device_status_on else R.string.device_status_off,
                ),
                positive = enabled,
            )

            SectionTitle(stringResource(R.string.permission_rationale_title))

            GlassCard(cornerRadius = Radius.md) {
                Text(
                    text = stringResource(R.string.permission_notifications_rationale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                SecondaryButton(
                    text = stringResource(R.string.permission_open_settings),
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = { hub.openNotificationSettings() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}
