package com.my.amali.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.my.amali.R
import com.my.amali.data.ai.ModelCatalog
import com.my.amali.domain.entity.UserApiSettings
import com.my.amali.ui.components.AmaliaScreen
import com.my.amali.ui.components.GlassCard
import com.my.amali.ui.components.GlassDivider
import com.my.amali.ui.components.GlassGroup
import com.my.amali.ui.components.ModelSelector
import com.my.amali.ui.components.SectionTitle
import com.my.amali.ui.components.SecretField
import com.my.amali.ui.components.modelOptionsFor
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing

/**
 * Экран «API и модели»: свои ключи и выбор моделей для трёх провайдеров.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ЗАЧЕМ ЭТОТ ЭКРАН
 * ════════════════════════════════════════════════════════════════════════
 *
 * Ключ, зашитый в сборку, — общий ресурс: он принадлежит сборке, а не
 * человеку. Когда его квота заканчивается, приложение замолкает у всех
 * сразу, и сделать с этим пользователь не может ничего — разве что ждать
 * новой версии. Три провайдера конвейера (Groq — текст, Deepgram — слух,
 * Fish Audio — голос) требуют трёх ключей, и все три теперь вводятся здесь.
 *
 * Пустое поле означает «взять ключ из сборки»: приложение остаётся
 * рабочим сразу после установки, а свой ключ — это улучшение, а не
 * обязательное условие для старта.
 *
 * Модель выбирается из списка с пояснениями, а последним пунктом всегда
 * идёт «Своя модель»: линейка у провайдеров меняется чаще, чем выходит
 * APK, и вписать свежий идентификатор руками должно быть так же просто,
 * как выбрать готовый.
 */
@Composable
fun ApiKeysSettings(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val api = settings.api

    AmaliaScreen(
        title = stringResource(R.string.settings_api_title),
        subtitle = stringResource(R.string.settings_api_desc),
        onBack = onBack,
        backLabel = stringResource(R.string.common_back),
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
        ) {
            ApiStatusCard(api = api, configured = settings.api.configuredProviders)

            SectionTitle(stringResource(R.string.settings_api_llm))
            ProviderGroup(
                provider = ModelCatalog.Provider.GROQ,
                icon = Icons.Rounded.Memory,
                keyValue = api.groqKey,
                modelValue = api.llmModel,
                modelTitle = stringResource(R.string.settings_api_model_llm),
                modelSubtitle = stringResource(R.string.settings_api_model_llm_desc),
                keyLabel = stringResource(R.string.settings_api_key_label, "Groq"),
                onKeyChange = { vm.setProviderKey(ModelCatalog.Provider.GROQ, it) },
                onKeyClear = { vm.clearProviderKey(ModelCatalog.Provider.GROQ) },
                onModelChange = { vm.setProviderModel(ModelCatalog.Provider.GROQ, it) },
            )

            SectionTitle(stringResource(R.string.settings_api_stt))
            ProviderGroup(
                provider = ModelCatalog.Provider.DEEPGRAM,
                icon = Icons.Rounded.GraphicEq,
                keyValue = api.deepgramKey,
                modelValue = api.sttModel,
                modelTitle = stringResource(R.string.settings_api_model_stt),
                modelSubtitle = stringResource(R.string.settings_api_model_stt_desc),
                keyLabel = stringResource(R.string.settings_api_key_label, "Deepgram"),
                onKeyChange = { vm.setProviderKey(ModelCatalog.Provider.DEEPGRAM, it) },
                onKeyClear = { vm.clearProviderKey(ModelCatalog.Provider.DEEPGRAM) },
                onModelChange = { vm.setProviderModel(ModelCatalog.Provider.DEEPGRAM, it) },
            )

            SectionTitle(stringResource(R.string.settings_api_tts))
            ProviderGroup(
                provider = ModelCatalog.Provider.FISH_AUDIO,
                icon = Icons.Rounded.RecordVoiceOver,
                keyValue = api.fishAudioKey,
                modelValue = api.ttsModel,
                modelTitle = stringResource(R.string.settings_api_model_tts),
                modelSubtitle = stringResource(R.string.settings_api_model_tts_desc),
                keyLabel = stringResource(R.string.settings_api_key_label, "Fish Audio"),
                onKeyChange = { vm.setProviderKey(ModelCatalog.Provider.FISH_AUDIO, it) },
                onKeyClear = { vm.clearProviderKey(ModelCatalog.Provider.FISH_AUDIO) },
                onModelChange = { vm.setProviderModel(ModelCatalog.Provider.FISH_AUDIO, it) },
            )

            // Голос — часть синтеза, поэтому живёт в этом же блоке: отдельный
            // экран для одной строки заставлял бы прыгать между разделами.
            GlassGroup(modifier = Modifier.padding(top = Spacing.xs)) {
                Column(Modifier.padding(Spacing.md)) {
                    VoiceIdField(
                        value = api.fishVoiceId,
                        onValueChange = { vm.setFishVoiceId(it) },
                    )
                }
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}

/**
 * Сводка по ключам сверху: сколько провайдеров уже настроено.
 *
 * Пользователь приходит сюда с конкретным вопросом «почему молчит», и
 * ответ должен быть виден до прокрутки: галочка и число настроенных
 * провайдеров.
 */
@Composable
private fun ApiStatusCard(api: UserApiSettings, configured: Int) {
    val title = stringResource(R.string.settings_api_status_title)
    val ready = configured == 3

    GlassCard(cornerRadius = Radius.lg, elevated = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (ready) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (ready) Icons.Rounded.Check else Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    tint = if (ready) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.settings_api_status_value, configured),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(R.string.settings_api_status_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

/**
 * Блок одного провайдера: ключ и модель в одной стеклянной группе.
 *
 * Модель идёт сразу под ключом, потому что они всегда настраиваются вместе:
 * ключ без модели или модель без ключа бессмысленны, а разносить их по
 * разным экранам — заставлять пользователя держать контекст в голове.
 */
@Composable
private fun ProviderGroup(
    provider: ModelCatalog.Provider,
    icon: ImageVector,
    keyValue: String,
    modelValue: String,
    modelTitle: String,
    modelSubtitle: String,
    keyLabel: String,
    onKeyChange: (String) -> Unit,
    onKeyClear: () -> Unit,
    onModelChange: (String) -> Unit,
) {
    // Показ ключа — состояние экрана, а не настройка: ключ открывается на
    // время, чтобы сверить его с консолью, и прячется при уходе с экрана.
    var keyVisible by remember { mutableStateOf(false) }
    val options = remember(provider) { modelOptionsFor(provider) }

    GlassGroup {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.xs))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = provider.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_api_console_hint,
                            provider.consoleUrl,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (keyValue.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.settings_api_own_key),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            SecretField(
                value = keyValue,
                onValueChange = onKeyChange,
                label = keyLabel,
                placeholder = stringResource(R.string.settings_api_key_placeholder),
                maskedVisible = keyVisible,
                onToggleVisibility = { keyVisible = !keyVisible },
                onClear = onKeyClear,
            )

            Spacer(Modifier.height(Spacing.sm))
            GlassDivider()
            Spacer(Modifier.height(Spacing.xxs))

            ModelSelector(
                title = modelTitle,
                subtitle = modelSubtitle,
                models = options,
                selectedId = ModelCatalog.resolveForRequest(provider, modelValue),
                customSentinel = ModelCatalog.CUSTOM_SENTINEL,
                onSelect = { picked ->
                    // «Своя модель» — это не значение модели, а режим ввода:
                    // сохраняем пусто, поле ввода покажется сразу под списком.
                    onModelChange(
                        if (picked == ModelCatalog.CUSTOM_SENTINEL) "" else picked,
                    )
                },
                onCustomChange = onModelChange,
            )
        }
    }
}

/**
 * Голос Амалии в Fish Audio.
 *
 * Отделён от ключа и модели, потому что это третья независимая вещь:
 * ключ — доступ, модель — движок, голос — тембр. У Fish Audio голос
 * задаётся строковым `reference_id`, и его можно получить клонированием
 * своего голоса в личном кабинете.
 */
@Composable
private fun VoiceIdField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.settings_api_voice_title),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = stringResource(R.string.settings_api_voice_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.xs))

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(160)) + expandVertically(tween(180)),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
    ) {
        SecretField(
            value = value,
            onValueChange = onValueChange,
            label = stringResource(R.string.settings_api_voice_label),
            placeholder = stringResource(R.string.settings_api_voice_placeholder),
            maskedVisible = visible,
            onToggleVisibility = { visible = !visible },
            onClear = { onValueChange("") },
        )
    }
}
