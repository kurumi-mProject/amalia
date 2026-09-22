package com.my.amali.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import com.my.amali.ui.components.GlassSlider
import com.my.amali.ui.components.ModelSelector
import com.my.amali.ui.components.SectionTitle
import androidx.compose.material3.TextField
import androidx.compose.ui.text.font.FontFamily
import com.my.amali.domain.entity.AiProfile
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
 * новой версии. Два провайдера конвейера (Groq — текст и слух, Fish Audio —
 * голос) требуют двух ключей, и оба вводятся здесь.
 *
 * Ключ Groq стоит один, а не два: распознавание речи теперь тоже идёт в
 * Groq (Whisper), поэтому «мозг» и «слух» — это одна строка на экране и
 * один бесплатный лимит. Раньше слух жил на отдельном аккаунте Deepgram,
 * и пользователю приходилось заводить второй ключ ради одной функции.
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

            // ── Профиль Амалии ────────────────────────────────────────────
            //
            // Стоит первым, до ключей: это самый важный выбор на экране, и
            // он определяет, каким промптом Амалия отвечает. Переключение
            // мгновенное — менять ключи и модели не нужно, они уже
            // разложены по профилям.
            AiProfileCard(
                profile = settings.aiProfile,
                customReady = api.customReady,
                onSelect = { vm.setAiProfile(it) },
            )

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

            // Своя точка подключения: адрес, модель и ключ вводит человек.
            //
            // Поля видны ВСЕГДА, а не только при активном профиле. Раньше
            // они показывались лишь у выбранного CUSTOM, а сам CUSTOM нельзя
            // было выбрать, пока поля пусты — получался замкнутый круг, и
            // настроить профиль было невозможно в принципе. Теперь наоборот:
            // человек вводит данные, и профиль становится доступным сам.
            CustomProviderGroup(
                endpoint = api.customEndpoint,
                model = api.customModel,
                apiKey = api.customKey,
                active = settings.aiProfile == AiProfile.CUSTOM,
                ready = api.customReady,
                onActivate = { vm.setAiProfile(AiProfile.CUSTOM) },
                onChange = { e, m, k -> vm.setCustomProvider(e, m, k) },
            )

            // ── Слух ──────────────────────────────────────────────────────
            //
            // Слух и мозг живут на одном ключе Groq, поэтому здесь нет поля
            // для ключа: это была бы та же самая строка, введённая дважды.
            // Блок отвечает только за модель распознавания и длину сегмента —
            // то, что действительно можно настроить.
            SectionTitle(stringResource(R.string.settings_api_stt))

            GroqSttGroup(
                modelValue = api.sttModel,
                silenceSeconds = api.sttSilenceSeconds,
                onModelChange = { vm.setProviderModel(ModelCatalog.Provider.GROQ_STT, it) },
                onSilenceChange = { vm.setSttSilenceSeconds(it) },
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

                    // Скорость и тон синтеза — свойства того же голоса.
                    // Раньше они жили на отдельном экране «Голос и речь»:
                    // два ползунка не заслуживали собственного каталога,
                    // а искать их приходилось по памяти.
                    Spacer(Modifier.height(Spacing.md))
                    GlassDivider()
                    Spacer(Modifier.height(Spacing.sm))
                    GlassSlider(
                        label = stringResource(R.string.voice_speech_rate),
                        valueText = "×%.1f".format(settings.speechRate),
                        value = settings.speechRate,
                        valueRange = 0.5f..2f,
                        onValueChange = { vm.setSpeechRate(it) },
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    GlassSlider(
                        label = stringResource(R.string.voice_speech_pitch),
                        valueText = "×%.1f".format(settings.speechPitch),
                        value = settings.speechPitch,
                        valueRange = 0.5f..2f,
                        onValueChange = { vm.setSpeechPitch(it) },
                    )
                }
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}

/**
 * Карточка выбора «мозга» Амалии.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ПОЧЕМУ ВАРИАНТА ВСЕГО ДВА
 * ════════════════════════════════════════════════════════════════════════
 *
 * Промежуточного варианта не существует по арифметике, а не по вкусу.
 * Бесплатный Groq принимает 7 000 входных токенов в минуту, а полный
 * промпт Амалии — почти 8 000: он не отправляется вообще, ни разу.
 * Поэтому выбор честный:
 *
 *  — Groq: урезанный промпт, работает у всех и сразу, но Амалия суше;
 *  — своя модель: полный промпт со всем характером на своём эндпоинте,
 *    где лимит задаёт сам пользователь.
 *
 * Второй вариант показан всегда, но без настроек выглядит как «сначала
 * настрой» — иначе человек выбрал бы его, не поняв, почему не работает.
 */
@Composable
private fun AiProfileCard(
    profile: AiProfile,
    customReady: Boolean,
    onSelect: (AiProfile) -> Unit,
) {
    GlassCard(cornerRadius = Radius.lg, elevated = true) {
        Text(
            text = stringResource(R.string.settings_profile_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.settings_profile_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Воздух между шапкой и первым вариантом: 16dp, а не 12dp —
        // заголовок и описание «относятся к обоим вариантам», и без
        // этого зазора первый вариант прилипал к описанию.
        Spacer(Modifier.height(Spacing.md))

        ProfileOption(
            icon = Icons.Rounded.Bolt,
            title = stringResource(R.string.settings_profile_groq),
            subtitle = stringResource(R.string.settings_profile_groq_desc),
            selected = profile == AiProfile.GROQ,
            enabled = true,
            onClick = { onSelect(AiProfile.GROQ) },
        )
        // 12dp между вариантами: у каждого своя рамка, и на 8dp рамки
        // читались как один слитый блок — «варианты слиплись».
        Spacer(Modifier.height(Spacing.sm))
        ProfileOption(
            icon = Icons.Rounded.Dns,
            title = stringResource(
                if (customReady) {
                    R.string.settings_profile_custom
                } else {
                    R.string.settings_profile_custom_empty
                },
            ),
            subtitle = stringResource(
                if (customReady) {
                    R.string.settings_profile_custom_desc
                } else {
                    R.string.settings_profile_custom_hint
                },
            ),
            selected = profile == AiProfile.CUSTOM,
            // Вариант нажимаем ВСЕГДА, даже без настроек: нажатие — это
            // намерение настроить, и оно лишь подсвечивает поля ниже.
            // Раньше он был заблокирован до заполнения полей, а поля были
            // скрыты до выбора — профиль нельзя было включить никогда.
            enabled = true,
            highlighted = !customReady,
            onClick = { onSelect(AiProfile.CUSTOM) },
        )
    }
}

/**
 * Один вариант профиля: иконка, название, описание и видимая отметка выбора.
 *
 * Выбор показан рамкой И галочкой, а не только цветом: цвет как единственный
 * носитель состояния не читается ни на солнце, ни при дальтонизме.
 *
 * Недоступный вариант (свой эндпоинт без адреса и модели) остаётся на экране,
 * но приглушён и не нажимается: человек должен видеть, что такая возможность
 * есть и чего ей не хватает, а не догадываться о ней после настройки.
 */
@Composable
private fun ProfileOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    /**
     * Вариант доступен, но ещё не готов к работе: подсвечиваем рамкой
     * и подсказкой, куда смотреть. Так человек сразу понимает, что делать,
     * вместо того чтобы гадать про серый пункт.
     */
    highlighted: Boolean = false,
) {
    val alpha = if (enabled) 1f else 0.45f
    val border = when {
        selected -> MaterialTheme.colorScheme.primary
        highlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    }
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(Radius.md))
            .clickable(enabled = enabled, onClick = onClick)
            // 16dp вместо 12dp: строка с рамкой требует внутреннего воздуха,
            // иначе заголовок упирается в контур и вариант читается сжатым.
            // Минимальная высота 56dp — тач-зона выше системного минимума.
            .heightIn(min = 56.dp)
            .padding(Spacing.md)
            .semantics { role = Role.RadioButton },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }.copy(alpha = alpha),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        if (selected) {
            Spacer(Modifier.width(Spacing.xs))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Блок своего эндпоинта: адрес, модель, ключ.
 *
 * Поля видны всегда, а не только при активном профиле: иначе получается
 * замкнутый круг — профиль нельзя выбрать, пока поля пусты, а поля не
 * показать, пока профиль не выбран. Человек должен сначала ввести данные,
 * и только потом включить профиль (кнопка появляется сама).
 *
 * Все три поля пишутся разом при каждом изменении — так исключается
 * состояние «адрес есть, модели нет», в котором профиль включён, но
 * запрос падает.
 */
@Composable
private fun CustomProviderGroup(
    endpoint: String,
    model: String,
    apiKey: String,
    active: Boolean,
    ready: Boolean,
    onActivate: () -> Unit,
    onChange: (String, String, String) -> Unit,
) {
    // Показ ключа — состояние экрана, а не настройка: ключ открывают на
    // время, чтобы сверить, и он снова прячется при уходе с экрана.
    var keyVisible by remember { mutableStateOf(false) }

    GlassGroup(modifier = Modifier.padding(top = Spacing.xs)) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = stringResource(R.string.settings_custom_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = stringResource(R.string.settings_custom_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))

            PlainField(
                value = endpoint,
                onValueChange = { onChange(it, model, apiKey) },
                label = stringResource(R.string.settings_custom_url),
                placeholder = "https://host/v1/chat/completions",
            )
            Spacer(Modifier.height(Spacing.xs))
            PlainField(
                value = model,
                onValueChange = { onChange(endpoint, it, apiKey) },
                label = stringResource(R.string.settings_custom_model),
                placeholder = "gpt-4o-mini",
            )
            Spacer(Modifier.height(Spacing.xs))
            SecretField(
                value = apiKey,
                onValueChange = { onChange(endpoint, model, it) },
                label = stringResource(R.string.settings_custom_key),
                placeholder = stringResource(R.string.settings_custom_key_hint),
                maskedVisible = keyVisible,
                onToggleVisibility = { keyVisible = !keyVisible },
                onClear = { onChange(endpoint, model, "") },
            )
            if (endpoint.isNotBlank() && model.isBlank()) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = stringResource(R.string.settings_custom_need_model),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Кнопка включения появляется ровно тогда, когда профиль уже
            // настроен, но ещё не выбран. Это самый вероятный следующий шаг:
            // человек только что ввёл адрес и модель — осталось сказать
            // «работай через это». Пока профиль активен, кнопка не нужна.
            if (ready && !active) {
                Spacer(Modifier.height(Spacing.sm))
                Button(
                    onClick = onActivate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(text = stringResource(R.string.settings_custom_activate))
                }
            }

            // Активный профиль отмечаем прямо в блоке: иначе, прокрутив
            // экран, человек не поймёт, чей это адрес — его или чужой.
            if (active) {
                Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(Spacing.xxs))
                    Text(
                        text = stringResource(R.string.settings_custom_active),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

/**
 * Блок распознавания речи: модель Whisper и пауза конца фразы.
 *
 * ## Почему здесь нет поля для ключа
 *
 * Слух и мозг работают на одном ключе Groq: распознавание идёт в Whisper
 * внутри того же аккаунта. Второе поле с тем же значением было бы не
 * настройкой, а ловушкой — человек вписал бы ключ в одно из них, не понял,
 * почему не работает, и решил, что приложение сломано.
 *
 * ## Что означает «пауза конца фразы»
 *
 * Ассистент пишет всё, что слышит, и перестаёт слушать, когда наступает
 * тишина. Слайдер задаёт её длину: сколько миллисекунд молчания означают,
 * что человек договорил. Меньше — реагирует проворнее, но рискует оборвать
 * фразу на вдохе между словами; больше — надёжнее, но заставляет ждать.
 *
 * Границы и значение по умолчанию заданы в [UserApiSettings].
 */
@Composable
private fun GroqSttGroup(
    modelValue: String,
    silenceSeconds: Float,
    onModelChange: (String) -> Unit,
    onSilenceChange: (Float) -> Unit,
) {
    val options = remember { modelOptionsFor(ModelCatalog.Provider.GROQ_STT) }
    val provider = ModelCatalog.Provider.GROQ_STT

    GlassGroup(modifier = Modifier.padding(top = Spacing.xs)) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
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
                        text = stringResource(R.string.settings_stt_key_shared, "Groq"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            ModelSelector(
                title = stringResource(R.string.settings_api_model_stt),
                subtitle = stringResource(R.string.settings_api_model_stt_desc),
                models = options,
                selectedId = ModelCatalog.resolveForRequest(provider, modelValue),
                customSentinel = ModelCatalog.CUSTOM_SENTINEL,
                onSelect = { picked ->
                    onModelChange(if (picked == ModelCatalog.CUSTOM_SENTINEL) "" else picked)
                },
                onCustomChange = onModelChange,
            )

            Spacer(Modifier.height(Spacing.xxs))
            GlassDivider()
            Spacer(Modifier.height(Spacing.sm))

            // Пауза конца фразы: слайдер с шагом 0.1 с. Секунды одинаковы
            // во всех языках, поэтому формат короткий и без перевода.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_stt_silence_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_stt_silence_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = "%.1f с".format(silenceSeconds),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Slider(
                value = silenceSeconds,
                onValueChange = onSilenceChange,
                valueRange = UserApiSettings.STT_SILENCE_MIN_SECONDS..
                    UserApiSettings.STT_SILENCE_MAX_SECONDS,
                steps = STT_SILENCE_STEPS,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.settings_stt_silence_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

/**
 * Число промежуточных делений слайдера паузы.
 *
 * Диапазон 0.4…1.5 с шагом 0.1 — это 12 возможных значений, то есть 10
 * промежуточных точек между крайними. Считается здесь, а не вводится
 * числом: иначе при смене границ слайдер начал бы «дробить» значения
 * неравномерно.
 */
private const val STT_SILENCE_STEPS: Int = 10

/**
 * Обычное текстовое поле без маскировки — для адреса и имени модели.
 *
 * Отдельно от [SecretField], потому что маскировать адрес бессмысленно:
 * его всё равно видно в логах и в документации, а скрытый URL невозможно
 * проверить взглядом на опечатку.
 */
@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xxs))
        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}


@Composable
private fun ApiStatusCard(api: UserApiSettings, configured: Int) {
    val title = stringResource(R.string.settings_api_status_title)
    // Провайдеров теперь три: ключ Groq закрывает сразу текст и слух,
    // поэтому «полностью настроено» — это три заполненных пункта, а не
    // четыре, как было при отдельном аккаунте распознавания.
    val ready = configured >= UserApiSettings.PROVIDER_COUNT

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
