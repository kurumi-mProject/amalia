package com.my.amali.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.my.amali.R
import com.my.amali.domain.entity.AppLanguage
import com.my.amali.ui.components.AmaliaScreen
import com.my.amali.ui.components.SettingsValueRow
import com.my.amali.ui.theme.Spacing

/**
 * Экран «Язык»: системный язык + девять локалей.
 *
 * Список на LazyColumn — он длинный, и ленивая отрисовка здесь
 * оправдана. Каждая строка показывает нативное название крупно
 * и английское — мелко, чтобы язык можно было найти, даже если
 * интерфейс сейчас на незнакомом языке.
 */
@Composable
fun LanguageSettings(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()

    AmaliaScreen(
        title = stringResource(R.string.settings_language),
        subtitle = stringResource(R.string.settings_language_desc),
        onBack = onBack,
        backLabel = stringResource(R.string.common_back),
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            items(AppLanguage.entries.toList(), key = { it.name }) { language ->
                SettingsValueRow(
                    title = if (language.isSystem) {
                        stringResource(R.string.language_follow_system)
                    } else {
                        language.nativeName
                    },
                    subtitle = if (language.isSystem) null else language.displayName,
                    selected = settings.selectedLanguage == language,
                    onClick = { vm.setLanguage(language) },
                )
            }
            item {
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}
