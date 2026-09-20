package com.my.amali.ui.assistant

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.my.amali.R
import com.my.amali.domain.entity.VoiceState
import com.my.amali.ui.components.GlassCard
import com.my.amali.ui.components.GlassIconButton
import com.my.amali.ui.components.GradientBackground
import com.my.amali.ui.components.MicButton
import com.my.amali.ui.components.SuggestionChips
import com.my.amali.ui.components.CircadianPreviewDialog
import com.my.amali.ui.theme.AmaliaTheme
import com.my.amali.ui.theme.AmaliaVisuals
import com.my.amali.ui.theme.CircadianPhase
import com.my.amali.ui.theme.LocalAmaliaVisuals
import com.my.amali.ui.theme.LocalLightProfile
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.glassSurface
import com.my.amali.ui.theme.iconAccent
import com.my.amali.ui.theme.paletteChip
import kotlinx.coroutines.launch

/**
 * AssistantScreen — главный экран Амалии.
 *
 * ═══════════════════════════════════════════════════════════
 *  КОМПОЗИЦИЯ (сверху вниз)
 * ═══════════════════════════════════════════════════════════
 *
 *  1. живой аурора-фон + декоративный мотив;
 *  2. компактная шапка: пульс состояния, имя, счётчик разговоров,
 *     индикатор текущего света, история и настройки;
 *  3. стеклянная карточка диалога — **единственная гибкая область**;
 *  4. герой-блок: слово состояния, голосовой орб, подпись-подсказка;
 *  5. запас под плавающую нижнюю навигацию.
 *
 * ═══════════════════════════════════════════════════════════
 *  ЧТО И ПОЧЕМУ ИЗМЕНИЛОСЬ В ЭТОЙ ВЕРСИИ
 * ═══════════════════════════════════════════════════════════
 *
 * ── 1. Появилась обработка разрешения микрофона ───────────────
 *
 * Раньше `AssistantUiState.micPermissionRequired` выставлялся
 * ViewModel-ем — и не читался НИКОМУ. Лончера разрешений на экране не было
 * вообще, поэтому на первом запуске тап по микрофону не делал ничего:
 * системный диалог не появлялся, ошибка не показывалась, экран молчал.
 * Теперь запрос запускается эффектом на флаг, а отказ обрабатывается
 * человеческим текстом с кнопкой «Открыть настройки».
 *
 * ── 2. Состояние переведено на строковые ресурсы ──────────────
 *
 * Экран печатал `VoiceState.label`, а это хардкод на русском: при девяти
 * локалях приложения слово состояния оставалось русским в любом языке.
 * Теперь подпись берётся из ресурсов (`assistant_listening` и т.д.).
 *
 * ── 3. Орб вместо кнопки ──────────────────────────────────────
 *
 * Микрофон перестал быть «кнопкой со свечением» и стал героем экрана:
 * ореол реагирует на уровень голоса, кольца расходятся по факту разговора,
 * орбита вращается всегда — видно, что система жива, ещё до первого слова.
 * Размер вырос до 104dp, тач-зона — 172dp: это главное действие, и оно
 * не должно требовать прицеливания.
 *
 * ── 4. Лента подсказок стала кликабельной лентой чипов ─────────
 *
 * В приветственной карточке подсказки выглядели как строки текста со
 * подчёркнутой ролью кнопки — по ним не хотелось тапать. Теперь это
 * стеклянные чипы ([SuggestionChips]) с нормальной тач-зоной.
 *
 * ── 5. Анимация фаз получила смысл ────────────────────────────
 *
 * Карточка не «подменяется» рывком: у каждой фазы своё направление
 * входа-выхода, поэтому слушание уходит вниз, а ответ приходит снизу —
 * движение читается как продолжение разговора, а не как перерисовка.
 *
 * ── 6. Экран разделён на stateful и stateless части ───────────
 *
 * [AssistantScreen] владеет ViewModel-ем, разрешением и буфером обмена;
 * [AssistantScreenContent] — чистая функция от состояния. Именно поэтому
 * превью ниже рисуют настоящий экран во всех фазах: раньше `@Preview`
 * вызывал `viewModel()` и падал с «No ViewModelStoreOwner was provided».
 *
 * @param onNavigateToHistory переход к истории разговоров.
 * @param onNavigateToSettings переход к настройкам.
 */
@Composable
fun AssistantScreen(
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm: AssistantViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    // LocalClipboardManager объявлен deprecated: он не поддерживает suspend и
    // не умеет отдавать в буфер ничего, кроме текста. LocalClipboard работает
    // через ClipboardEntry и поддерживает URI, HTML и картинки.
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Системный запрос доступа к микрофону. Лончер — один на экран
    // (внутри элемента списка он ломал бы реестр ActivityResult).
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> vm.onMicPermissionResult(granted) }

    LaunchedEffect(state.micPermissionRequired) {
        if (state.micPermissionRequired) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Диалог превью циркадного света (рассвет → закат)
    var showCircadianPreview by remember { mutableStateOf(false) }

    if (showCircadianPreview) {
        CircadianPreviewDialog(onDismissRequest = { showCircadianPreview = false })
    }

    AssistantScreenContent(
        state = state,
        onMicClick = vm::toggleConversation,
        onMicPress = vm::warmupStt,
        onPickSuggestion = vm::startConversation,
        onRepeat = vm::repeatLast,
        onDismissError = vm::dismissError,
        onNewSession = vm::startNewSession,
        onCopy = {
            clipboardScope.launch {
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText("amalia_reply", state.amaliaReply)),
                )
            }
        },
        onOpenAppSettings = {
            val intent = Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        },
        onNavigateToHistory = onNavigateToHistory,
        onNavigateToSettings = onNavigateToSettings,
        onShowCircadianPreview = { showCircadianPreview = true },
        modifier = modifier,
    )
}

/**
 * Чистый (stateless) главный экран.
 *
 * Вся логика приходит снаружи: состояние — параметр, действия — лямбды.
 * Благодаря этому экран можно отрисовать в превью в любой фазе, а тесты
 * не поднимают ViewModel и сеть.
 */
@Composable
fun AssistantScreenContent(
    state: AssistantUiState,
    onMicClick: () -> Unit,
    onMicPress: () -> Unit,
    onPickSuggestion: (String) -> Unit,
    onRepeat: () -> Unit,
    onDismissError: () -> Unit,
    onNewSession: () -> Unit,
    onCopy: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowCircadianPreview: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val visuals = LocalAmaliaVisuals.current
    val light = LocalLightProfile.current
    val lightLabel = light.lightLabel
    val cct = light.cct
    val suggestions = welcomeSuggestions()

    // На коротких экранах сжимается только «воздух» между блоками:
    // карточка диалога и орб обязаны остаться целыми.
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val compact = screenHeight < 700

    Box(modifier = modifier.fillMaxSize()) {
        GradientBackground(
            modifier = Modifier.fillMaxSize(),
            intensity = visuals.glassIntensity,
            motif = visuals.motif,
            motifDensity = (visuals.motifDensity * if (state.isBusy) 0.72f else 0.86f),
            luminance = light.displayLuminance,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AssistantTopBar(
                modifier = Modifier.widthIn(max = 560.dp),
                conversationCount = state.conversationCount,
                voiceState = state.voiceState,
                showLight = visuals.useBioTime,
                lightLabel = lightLabel,
                cct = cct,
                // «Новый разговор» показывается только когда есть что закрывать:
                // в покое кнопка была бы шумом без действия.
                showNewSession = state.amaliaReply.isNotEmpty(),
                onNewSession = onNewSession,
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToSettings = onNavigateToSettings,
                onShowCircadianPreview = onShowCircadianPreview,
            )

            Spacer(Modifier.weight(if (compact) 0.12f else 0.22f))

            // === ДИАЛОГ: единственная гибкая область экрана ===
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .weight(1f, fill = false),
                contentAlignment = Alignment.TopCenter,
            ) {
                AnimatedContent(
                    targetState = DialogPhase.of(state),
                    transitionSpec = {
                        (fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 6 })
                            .togetherWith(
                                fadeOut(tween(160)) + slideOutVertically(tween(220)) { -it / 6 },
                            )
                    },
                    label = "dialog",
                ) { phase ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Герой-приветствие: крупная типографика фазы суток
                        // занимает «воздух» над карточкой в покое. Именно он
                        // делает экран элегантным, а не пустым. На компактных
                        // экранах воздух жертвуется первым.
                        if (phase == DialogPhase.Welcome && !compact) {
                            GreetingHero()
                            Spacer(Modifier.height(Spacing.md))
                        }
                        when (phase) {
                            DialogPhase.Welcome -> WelcomeCard(
                                suggestions = suggestions,
                                onPickSuggestion = onPickSuggestion,
                            )
                            DialogPhase.Listening -> ListeningCard(transcript = state.userTranscript)
                            DialogPhase.Thinking -> ThinkingCard(
                                prompt = state.userTranscript,
                                activeTools = state.activeTools,
                            )
                            DialogPhase.Reply -> ReplyCard(
                                prompt = state.userTranscript,
                                reply = state.amaliaReply,
                                progress = state.replyProgress,
                                speaking = state.voiceState == VoiceState.Speaking,
                                toolReports = state.lastToolReports,
                                contextCompressed = state.contextCompressed,
                                contextMessageCount = state.contextMessageCount,
                                // Сбой синтеза: ответ получен, но произнести его
                                // не удалось. Показываем это рядом с ответом, а не
                                // вместо него — иначе пользователь думает, что
                                // сломался ассистент, хотя текст-то есть.
                                // Сравнение с флагом разрешения микрофона не даёт
                                // спутать это с ошибкой доступа: там свой экран.
                                voiceWarning = state.errorMessage
                                    ?.takeIf { !state.micPermissionDenied },
                                onRepeat = onRepeat,
                                onCopy = onCopy,
                            )
                            DialogPhase.Error -> ErrorCard(
                                message = state.errorMessage
                                    ?: stringResource(R.string.assistant_error),
                                onRetry = onMicClick,
                                onDismiss = onDismissError,
                                onOpenSettings = if (state.micPermissionDenied) {
                                    onOpenAppSettings
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(if (compact) Spacing.xs else Spacing.sm))

            // === ГЕРОЙ: состояние + главное действие + подпись ===
            StatusLabel(state = state.voiceState)

            Spacer(Modifier.height(if (compact) Spacing.xxs else Spacing.xs))

            MicButton(
                isActive = state.voiceState != VoiceState.Idle &&
                    state.voiceState != VoiceState.Error,
                stateLabel = voiceStateLabel(state.voiceState),
                level = state.audioLevel,
                onClick = onMicClick,
                onPress = onMicPress,
            )

            Spacer(Modifier.height(Spacing.xxs))

            // Подпись — под орбом: кнопка обязана стоять максимально близко
            // к нижнему краю, это главное действие экрана.
            Text(
                text = if (state.isBusy) {
                    stringResource(R.string.assistant_stop)
                } else {
                    stringResource(R.string.assistant_welcome_hint)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )

            // Запас ровно под плавающую нижнюю навигацию: 64dp панель +
            // 12dp нижний паддинг + воздух. Ни пикселем больше — иначе
            // главное действие уезжает вверх из зоны большого пальца.
            Spacer(Modifier.height(BottomBarReserve))
        }
    }
}

// ============================================================
//  ГЕРОЙ-ПРИВЕТСТВИЕ
// ============================================================

/**
 * Герой-приветствие главного экрана: крупная типографика фазы суток
 * и приглашение к разговору.
 *
 * Занимает «воздух» над карточкой диалога в покое — именно пустое
 * пространство прежде делало экран безликим. Приветствие следует
 * циркадному движку: тот же [LocalLightProfile], что красит фон,
 * выбирает и слова, поэтому текст и свет никогда не расходятся.
 *
 * Фирменная деталь — тонкая дышащая черта под текстом: экран живёт
 * даже в абсолютном покое, 3.2 с на цикл, без резких движений.
 */
@Composable
private fun GreetingHero(modifier: Modifier = Modifier) {
    val light = LocalLightProfile.current
    val greetingRes = when (light.phase) {
        CircadianPhase.DAWN, CircadianPhase.MORNING -> R.string.greeting_morning
        CircadianPhase.MIDDAY, CircadianPhase.AFTERNOON -> R.string.greeting_day
        CircadianPhase.DUSK, CircadianPhase.EVENING -> R.string.greeting_evening
        CircadianPhase.NIGHT, CircadianPhase.DEEP_NIGHT -> R.string.greeting_night
    }
    val transition = rememberInfiniteTransition(label = "greetingBreath")
    val breath by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(3_200, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "greetingBar",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(greetingRes),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.widthIn(max = 420.dp),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = stringResource(R.string.greeting_prompt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 2.5.dp)
                .clip(RoundedCornerShape(Radius.chip))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.95f * breath),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f * breath),
                        ),
                    ),
                ),
        )
    }
}

// ============================================================
//  ФАЗЫ ДИАЛОГА
// ============================================================

/** Что именно показывать в центральной карточке. */
private enum class DialogPhase {
    Welcome, Listening, Thinking, Reply, Error;

    companion object {
        fun of(state: AssistantUiState): DialogPhase = when {
            state.voiceState == VoiceState.Error -> Error
            state.voiceState == VoiceState.Listening -> Listening
            state.voiceState == VoiceState.Thinking -> Thinking
            state.amaliaReply.isNotEmpty() -> Reply
            else -> Welcome
        }
    }
}

/** Подпись состояния на языке интерфейса (а не хардкод из enum). */
@Composable
private fun voiceStateLabel(state: VoiceState): String = stringResource(
    when (state) {
        VoiceState.Idle -> R.string.assistant_ready
        VoiceState.Listening -> R.string.assistant_listening
        VoiceState.Thinking -> R.string.assistant_thinking
        VoiceState.Speaking -> R.string.assistant_speaking
        VoiceState.Error -> R.string.assistant_error
    },
)

// ============================================================
//  ШАПКА
// ============================================================

@Composable
private fun AssistantTopBar(
    conversationCount: Int,
    voiceState: VoiceState,
    showLight: Boolean,
    lightLabel: String,
    cct: Int,
    showNewSession: Boolean,
    onNewSession: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowCircadianPreview: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.md,
                end = Spacing.xs,
                top = Spacing.xxs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatePulse(voiceState = voiceState)
        Spacer(Modifier.width(Spacing.xs))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Вторая строка — контекст, а не статус: сколько разговоров
            // в памяти и какой сейчас свет. Чип света живёт здесь, а не
            // в верхнем ряду: в верхнем он вместе с тремя кнопками не
            // оставлял имени ни сантиметра на экране 360dp.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (conversationCount > 0) {
                        stringResource(R.string.assistant_conversations_count, conversationCount)
                    } else {
                        stringResource(R.string.assistant_idle)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Текст уступает место чипу: цифру можно и обрезать,
                    // а свет — нет, он объясняет смену палитры.
                    modifier = Modifier.weight(1f, fill = false),
                )
                AnimatedVisibility(visible = showLight) {
                    LightChip(
                        label = lightLabel,
                        cct = cct,
                        isWarm = cct <= 3200,
                        modifier = Modifier.padding(start = Spacing.xxs),
                    )
                }
                // Кнопка-превью рассвет→закат: только когда bio-time включён,
                // иначе превью не имеет контекста (фон не меняется).
                AnimatedVisibility(visible = showLight) {
                    GlassIconButton(
                        icon = Icons.Rounded.WbTwilight,
                        contentDescription = stringResource(R.string.appearance_preview),
                        onClick = onShowCircadianPreview,
                    )
                }
            }
        }

        // Новый разговор: доступен, когда на экране уже есть ответ —
        // то есть когда «начать заново» действительно что-то значит.
        AnimatedVisibility(visible = showNewSession) {
            GlassIconButton(
                icon = Icons.Rounded.AddComment,
                contentDescription = stringResource(R.string.assistant_new_session),
                onClick = onNewSession,
            )
        }
        GlassIconButton(
            icon = Icons.Rounded.History,
            contentDescription = stringResource(R.string.nav_history),
            onClick = onNavigateToHistory,
            badge = conversationCount > 0,
        )
        // Настройки — через overflow-меню: шапка не должна перегружаться.
        GlassIconButton(
            icon = Icons.Rounded.Settings,
            contentDescription = stringResource(R.string.nav_settings),
            onClick = onNavigateToSettings,
        )
    }
}

/**
 * Компактный чип «какой сейчас свет»: фаза суток + цветовая температура.
 *
 * Показывает ровно те две величины, которые рассчитал CircadianEngine,
 * поэтому индикатор невозможно «разъехать» с фактическим фоном: и то,
 * и другое читает один [LocalLightProfile].
 */
@Composable
private fun LightChip(
    label: String,
    cct: Int,
    isWarm: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = 22.dp)
            .clip(RoundedCornerShape(Radius.chip))
            .glassSurface(shape = RoundedCornerShape(Radius.chip))
            .padding(horizontal = Spacing.xs, vertical = 2.dp)
            .semantics { contentDescription = "$label, $cct K" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Точка-индикатор тонирована светом палитры: тёплый вечер — янтарь,
        // холодное утро — лёд. Цвет не константа, а производная палитры,
        // поэтому чип не спорит с фоном ни в одной фазе суток.
        val dotColor = if (isWarm) {
            Color(0xFFE8B054)
        } else {
            Color(0xFF9EC2F0)
        }
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = "$label · ${cct}K",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.88f),
            maxLines = 1,
        )
    }
}

/**
 * Дышащая точка слева от имени — «Амалия на связи».
 *
 * В покое дышит медленно (2.6 с), в разговоре часто (0.9 с): по одному
 * взгляду на точку видно, слушают тебя или нет.
 */
@Composable
private fun StatePulse(voiceState: VoiceState) {
    val transition = rememberInfiniteTransition(label = "statePulse")
    val breath by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (voiceState == VoiceState.Idle) 2_600 else 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val color by animateColorAsState(
        targetValue = stateColor(voiceState),
        animationSpec = tween(360),
        label = "pulseColor",
    )

    Box(
        modifier = Modifier.size(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f * breath)),
        )
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.55f + 0.45f * breath)),
        )
    }
}

// ============================================================
//  СОСТОЯНИЕ
// ============================================================

/**
 * Слово состояния — главный текстовый якорь экрана.
 *
 * Стоит непосредственно над орбом, поэтому «что происходит» и «что нажать»
 * читаются одним взглядом. Смена слова — вертикальный слайд: движение
 * совпадает с направлением разговора (вниз — слушание, вверх — ответ).
 */
@Composable
private fun StatusLabel(state: VoiceState, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        targetValue = stateColor(state),
        animationSpec = tween(360),
        label = "statusColor",
    )
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 })
                .togetherWith(fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it / 3 })
        },
        label = "statusLabel",
        modifier = modifier,
    ) { target ->
        Text(
            text = voiceStateLabel(target),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun stateColor(state: VoiceState): Color = when (state) {
    VoiceState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
    VoiceState.Listening -> MaterialTheme.colorScheme.secondary
    // «Думаю» — третичный акцент, а не серый: фаза активная, и она должна
    // читаться живой, а не «погасшей».
    VoiceState.Thinking -> MaterialTheme.colorScheme.tertiary
    VoiceState.Speaking -> MaterialTheme.colorScheme.tertiary
    VoiceState.Error -> MaterialTheme.colorScheme.error
}

// ============================================================
//  КАРТОЧКИ ДИАЛОГА
// ============================================================

/**
 * Приветственная карточка: кто говорит, что делать и что можно сказать.
 *
 * Подсказки — чипы с нормальной тач-зоной и горизонтальным скроллом.
 * Раньше они были строками текста с ролью кнопки: выглядели как абзац,
 * тапать по ним не хотелось, а на длинном переводе строка обрезалась.
 */
@Composable
private fun WelcomeCard(
    suggestions: List<String>,
    onPickSuggestion: (String) -> Unit,
) {
    GlassCard(
        modifier = Modifier.padding(horizontal = Spacing.screen),
        cornerRadius = Radius.lg,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AmaliaAvatar(size = 34.dp)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.assistant_welcome_hint),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.assistant_welcome_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.assistant_welcome_chips),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(Spacing.xs))
            SuggestionChips(
                suggestions = suggestions,
                onClick = onPickSuggestion,
            )
        }
    }
}

/**
 * Карточка слушания.
 *
 * Пока человек говорит, текст не показывается — и это осознанно. Запись
 * отправляется в распознавание один раз, когда фраза закончена, поэтому
 * промежуточного текста не существует физически. Показывать вместо него
 * что-то выдуманное («слушаю…», «распознаю…») значило бы обещать то, чего
 * приложение не делает.
 *
 * Когда фраза распознана, сюда попадает уже готовый текст: он приходит
 * одновременно с ответом, и человек видит, что именно было услышано.
 */
@Composable
private fun ListeningCard(transcript: String) {
    GlassCard(
        modifier = Modifier.padding(horizontal = Spacing.screen),
        cornerRadius = Radius.lg,
    ) {
        CardLabel(
            text = stringResource(R.string.assistant_listening),
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(Spacing.xs))
        if (transcript.isBlank()) {
            // Живая волна уже дышит под карточкой, поэтому здесь достаточно
            // пульсирующих точек — второго индикатора не нужно.
            TypingDots()
        } else {
            Text(
                text = transcript,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ThinkingCard(
    prompt: String,
    activeTools: List<ToolActivity>,
) {
    GlassCard(
        modifier = Modifier.padding(horizontal = Spacing.screen),
        cornerRadius = Radius.lg,
    ) {
        if (prompt.isNotBlank()) {
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        CardLabel(
            text = stringResource(R.string.assistant_thinking),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        if (activeTools.isEmpty()) {
            TypingDots()
        } else {
            ToolActivityStrip(tools = activeTools)
        }
    }
}

/**
 * Индикаторы работающих инструментов.
 *
 * Показываются максимум [VISIBLE_TOOL_ROWS] строк: «выключи всё» с десятью
 * командами не должно превращать карточку в пропасть, которая выталкивает
 * орб за пределы экрана. Остальное — счётчиком.
 */
@Composable
private fun ToolActivityStrip(tools: List<ToolActivity>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        tools.take(VISIBLE_TOOL_ROWS).forEach { tool ->
            ToolChip(tool = tool)
        }
        if (tools.size > VISIBLE_TOOL_ROWS) {
            Text(
                text = stringResource(
                    R.string.assistant_tools_more,
                    tools.size - VISIBLE_TOOL_ROWS,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.padding(start = Spacing.xxs, top = 2.dp),
            )
        }
    }
}

@Composable
private fun ToolChip(tool: ToolActivity) {
    val transition = rememberInfiniteTransition(label = "tool-${tool.name}")
    val breath by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(820, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tool-breath",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 30.dp)
            .glassSurface(shape = RoundedCornerShape(Radius.chip))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
            .semantics { contentDescription = tool.humanLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // Пульсирующая точка — признак «работает».
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = breath)),
        )
        Text(
            text = tool.humanLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Карточка ответа.
 *
 * Четыре зоны: шапка «кто говорит», предупреждение о несостоявшейся озвучке,
 * прокручиваемый текст, сводка действий и кнопки. Прокручивается только
 * текст — кнопки и сводка остаются доступными, сколько бы Амалия ни наболтала.
 *
 * @param voiceWarning не-null, если ответ получен, но синтез речи не сработал.
 *   Раньше этот случай был полностью безмолвным: пользователь видел текст и
 *   решал, что ассистент сломался. Теперь он видит, что именно произошло.
 */
@Composable
private fun ReplyCard(
    prompt: String,
    reply: String,
    progress: Float,
    speaking: Boolean,
    toolReports: List<ToolReport>,
    contextCompressed: Boolean,
    contextMessageCount: Int,
    voiceWarning: String?,
    onRepeat: () -> Unit,
    onCopy: () -> Unit,
) {
    val scrollState = rememberScrollState()

    // Пока текст растёт, держим взгляд на его конце — ровно там, где Амалия
    // сейчас «говорит». Пользовательский скролл не ломаем: тянем вниз только
    // пока ответ ещё генерируется.
    LaunchedEffect(reply, speaking) {
        if (speaking || progress < 1f) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    GlassCard(
        modifier = Modifier.padding(horizontal = Spacing.screen),
        cornerRadius = Radius.lg,
        elevated = true,
    ) {
        if (prompt.isNotBlank()) {
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xs))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            AmaliaAvatar(size = 22.dp)
            Spacer(Modifier.width(Spacing.xs))
            CardLabel(
                text = stringResource(R.string.app_name),
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.weight(1f))
            if (contextCompressed) {
                ContextChip(literalCount = contextMessageCount)
            }
        }
        Spacer(Modifier.height(Spacing.xs))

        // Ответ проявляется по словам синхронно с «речью».
        val words = remember(reply) { reply.split(' ') }
        val visible = if (progress >= 1f) {
            reply
        } else {
            val count = (words.size * progress).toInt().coerceAtLeast(1)
            words.take(count).joinToString(" ")
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = ReplyMaxHeight)
                .verticalScroll(scrollState),
        ) {
            Text(
                text = visible,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Сводка «что сделала Амалия» — одна строка, раскрывается по тапу.
        ToolSummary(
            reports = toolReports,
            modifier = Modifier.padding(top = Spacing.sm),
        )

        // Предупреждение о несостоявшейся озвучке: компактная строка с
        // иконкой и текстом причины. Живёт под ответом, а не вместо него —
        // текст важнее, и терять его из-за сбоя голоса нельзя.
        AnimatedVisibility(
            visible = !voiceWarning.isNullOrBlank(),
            enter = fadeIn(tween(220)) + expandVertically(tween(220)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(180)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Rounded.VolumeOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = voiceWarning.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        AnimatedVisibility(
            visible = !speaking && progress >= 1f,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(120)),
        ) {
            Row(
                modifier = Modifier.padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassTextAction(
                    icon = Icons.Rounded.Refresh,
                    text = stringResource(R.string.assistant_repeat),
                    onClick = onRepeat,
                )
                GlassTextAction(
                    icon = Icons.Rounded.ContentCopy,
                    text = stringResource(R.string.assistant_copy),
                    onClick = onCopy,
                )
            }
        }
    }
}

/**
 * Сводка выполненных действий: всегда одна строка, список — по нажатию.
 *
 * Именно эта экономия лечит «команды перекрывают UI»: десять исполненных
 * инструментов больше не занимают десять строк в карточке ответа.
 */
@Composable
private fun ToolSummary(reports: List<ToolReport>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    // stringResource нельзя звать внутри semantics-лямбды — она не композабл.
    val actionsTitle = stringResource(R.string.assistant_actions_title)
    // «Выполнено: 2» рядом с одной красной строкой ошибки читалось как ошибка
    // в подсчёте: цвет ошибки брался от любой неудачи, а цифра — от общего
    // числа действий. Теперь считаем только успешные, и цифры сходятся со
    // списком, который раскрывается под сводкой.
    val doneCount = reports.count { it.ok }
    val fails = reports.size - doneCount
    val accent = if (fails == 0 && reports.isNotEmpty()) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.error
    }

    AnimatedVisibility(
        visible = reports.isNotEmpty(),
        enter = fadeIn(tween(220)) + expandVertically(tween(220)),
        exit = fadeOut(tween(160)) + shrinkVertically(tween(180)),
        modifier = modifier,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = actionsTitle
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = if (fails == 0) {
                        stringResource(R.string.assistant_actions_ok, doneCount)
                    } else {
                        stringResource(R.string.assistant_actions_partial, doneCount, fails)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Rounded.KeyboardArrowUp
                    } else {
                        Icons.Rounded.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 116.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    reports.forEach { report -> ToolReportRow(report = report) }
                }
            }
        }
    }
}

/** Одна строка сводки «что сделано» — короткий значок + текст. */
@Composable
private fun ToolReportRow(report: ToolReport) {
    val color = if (report.ok) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = report.summary,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Пилюля «контекст сжат» — объясняет, что память перешла в режим пересказа. */
@Composable
private fun ContextChip(literalCount: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.assistant_context_compressed)
    // Сколько реплик ещё помнится дословно — цифра вместо догадок.
    val readable = "$label · $literalCount"
    Row(
        modifier = modifier
            .heightIn(min = 22.dp)
            .clip(RoundedCornerShape(Radius.chip))
            .paletteChip(RoundedCornerShape(Radius.chip), strength = 0.9f)
            .padding(horizontal = Spacing.xs, vertical = 2.dp)
            .semantics { contentDescription = readable },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/** Аватар Амалии: стеклянный кружок с цветовым «зрачком» текущей палитры. */
@Composable
private fun AmaliaAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    val accent = iconAccent()
    val label = stringResource(R.string.app_name)
    Box(
        modifier = modifier
            .size(size)
            .paletteChip(shape = CircleShape, strength = 1f)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(size / 2.4f),
        )
    }
}

/**
 * Карточка ошибки.
 *
 * @param onDismiss закрывает ошибку без повтора: пользователь вправе просто
 *   вернуться к разговору, а не «исправлять» то, что ему не мешает.
 * @param onOpenSettings не-null → показывается второе действие «Открыть
 *   настройки». Это ровно тот случай, когда разрешение отклонено навсегда:
 *   системный диалог больше не появится, и «Повторить» будет упираться
 *   в закрытую дверь. Кнопка ведёт прямо в настройки приложения.
 */
@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: (() -> Unit)?,
) {
    GlassCard(
        modifier = Modifier.padding(horizontal = Spacing.screen),
        cornerRadius = Radius.lg,
        tint = MaterialTheme.colorScheme.error,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Spacing.xs))
            CardLabel(
                text = stringResource(R.string.common_error),
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.weight(1f))
            // Крестик в углу: закрыть, не повторяя. Отдельной строкой он
            // отнимал бы высоту у главного действия карточки.
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .padding(5.dp),
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassTextAction(
                icon = Icons.Rounded.Refresh,
                text = stringResource(R.string.common_retry),
                onClick = onRetry,
            )
            if (onOpenSettings != null) {
                GlassTextAction(
                    icon = Icons.Rounded.Settings,
                    text = stringResource(R.string.permission_open_settings),
                    onClick = onOpenSettings,
                )
            }
        }
    }
}

/** Мелкий лейбл-«кто говорит» внутри карточки. */
@Composable
private fun CardLabel(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

/** Три пульсирующие точки — единый индикатор «идёт процесс». */
@Composable
private fun TypingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = modifier.height(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.22f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(560, delayMillis = index * 160, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = alpha)),
            )
        }
    }
}

/** Компактное стеклянное действие «иконка + слово». */
@Composable
private fun GlassTextAction(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "actionPress",
    )
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .scale(scale)
            .glassSurface(shape = RoundedCornerShape(Radius.chip))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            .semantics {
                role = Role.Button
                contentDescription = text
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

// ============================================================
//  УТИЛИТЫ
// ============================================================

/** Подсказки для пустого экрана — берутся из локализованных строк. */
@Composable
private fun welcomeSuggestions(): List<String> = listOf(
    stringResource(R.string.suggestion_hello),
    stringResource(R.string.suggestion_about),
    stringResource(R.string.suggestion_time),
    stringResource(R.string.suggestion_weather),
)

/** Сколько строк работающих инструментов показывать до счётчика «+N». */
private const val VISIBLE_TOOL_ROWS = 3

/** Потолок высоты текста ответа: дальше — внутренний скролл, а не рост карточки. */
private val ReplyMaxHeight = 180.dp

/**
 * Отступ под плавающую нижнюю навигацию.
 *
 * Считается, а не берётся «на глаз»: 64dp высота панели + 12dp её собственный
 * вертикальный паддинг ×2 + 8dp воздуха = 96dp. Ровно столько, чтобы панель
 * не наехала на подпись под орбом. Прежние 84dp были меньше фактической
 * высоты панели (88dp), и подпись уходила под стекло на 4dp.
 */
private val BottomBarReserve = 88.dp

// ============================================================
//  PREVIEW
//  Превью рисуют [AssistantScreenContent] — чистую функцию состояния.
//  Поэтому они не падают и показывают каждую фазу разговора целиком.
// ============================================================

private val PreviewModifier = Modifier.fillMaxSize()

/** Демо-состояние: покой, ответа ещё нет. */
private val IdlePreviewState = AssistantUiState(
    voiceState = VoiceState.Idle,
    conversationCount = 0,
)

/** Демо-состояние: слушание с живым транскриптом. */
private val ListeningPreviewState = AssistantUiState(
    voiceState = VoiceState.Listening,
    userTranscript = "включи фонарик и поставь таймер на пять минут",
    audioLevel = 0.62f,
    conversationCount = 4,
)

/** Демо-состояние: модель думает и запускает инструменты. */
private val ThinkingPreviewState = AssistantUiState(
    voiceState = VoiceState.Thinking,
    userTranscript = "выключи вайфай и убавь яркость до тридцати",
    conversationCount = 4,
    activeTools = listOf(
        ToolActivity("set_wifi", "выключаю Wi-Fi"),
        ToolActivity("set_brightness", "ставлю яркость 30%"),
        ToolActivity("set_bluetooth", "выключаю Bluetooth"),
        ToolActivity("set_volume", "ставлю громкость 40%"),
    ),
)

/** Демо-состояние: ответ получен, действия выполнены. */
private val ReplyPreviewState = AssistantUiState(
    voiceState = VoiceState.Idle,
    userTranscript = "выключи вайфай и убавь яркость до тридцати",
    amaliaReply = "готово, и то и другое. если станет темно — просто скажи.",
    replyProgress = 1f,
    conversationCount = 5,
    contextCompressed = true,
    contextMessageCount = 6,
    lastToolReports = listOf(
        ToolReport("set_wifi", true, "Wi-Fi выключен"),
        ToolReport("set_brightness", true, "яркость установлена"),
    ),
)

/** Демо-состояние: ошибка с предложением открыть настройки. */
private val ErrorPreviewState = AssistantUiState(
    voiceState = VoiceState.Error,
    errorMessage = "Без доступа к микрофону я не слышу. Разреши доступ в настройках приложения.",
    micPermissionDenied = true,
    conversationCount = 2,
)

/**
 * Демо-состояние: ответ получен, но синтез речи не сработал.
 *
 * Ровно тот случай, из-за которого пользователь считал приложение
 * сломанным: текст на экране, тишина в динамике и ни одного объяснения.
 */
private val VoiceFailedPreviewState = AssistantUiState(
    voiceState = VoiceState.Idle,
    userTranscript = "расскажи, какая сегодня погода",
    amaliaReply = "облачно, но без дождя. если пойдёшь гулять — куртку возьми.",
    replyProgress = 1f,
    conversationCount = 7,
    errorMessage = "Ответ получен, но озвучить его не удалось.",
)

/**
 * Демо-состояние: в одной фразе два действия.
 *
 * Проверка того самого сценария, ради которого в промпте появился раздел
 * «несколько желаний в одной фразе»: два инструмента должны прийти двумя
 * отдельными записями, а не одной на выбор. Если сводка показывает «2
 * действия» — контракт промпта работает.
 */
private val MultiToolPreviewState = AssistantUiState(
    voiceState = VoiceState.Idle,
    userTranscript = "увеличь яркость и звук",
    amaliaReply = "сейчас. яркость на девяносто, звук на восемьдесят",
    replyProgress = 1f,
    isSpeaking = true,
    conversationCount = 9,
    lastToolReports = listOf(
        ToolReport(name = "set_brightness", ok = true, summary = "яркость 90%"),
        ToolReport(name = "set_volume", ok = true, summary = "звук 80%"),
    ),
)

/** Экран в покое: приветствие, подсказки, орб. */
@Preview(name = "Assistant · Idle", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun AssistantIdlePreview() {
    AmaliaTheme {
        PreviewShell(IdlePreviewState)
    }
}

/** Слушание: живые субтитры и активный орб. */
@Preview(name = "Assistant · Listening", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun AssistantListeningPreview() {
    AmaliaTheme {
        PreviewShell(ListeningPreviewState)
    }
}

/** Размышление: список работающих инструментов. */
@Preview(name = "Assistant · Thinking", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun AssistantThinkingPreview() {
    AmaliaTheme {
        PreviewShell(ThinkingPreviewState)
    }
}

/** Ответ: текст, сводка действий и кнопки. */
@Preview(name = "Assistant · Reply", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun AssistantReplyPreview() {
    AmaliaTheme {
        PreviewShell(ReplyPreviewState)
    }
}

/** Ошибка: объяснение и два действия. */
@Preview(name = "Assistant · Error", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun AssistantErrorPreview() {
    AmaliaTheme {
        PreviewShell(ErrorPreviewState)
    }
}

/** Ответ без звука: предупреждение о сбое синтеза под текстом ответа. */
@Preview(name = "Assistant · Silent reply", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun AssistantVoiceFailedPreview() {
    AmaliaTheme {
        PreviewShell(VoiceFailedPreviewState)
    }
}

/** Два действия в одной фразе: обе настройки попали в инструменты. */
@Preview(name = "Assistant · Two actions", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun AssistantMultiToolPreview() {
    AmaliaTheme {
        PreviewShell(MultiToolPreviewState)
    }
}

/** Тёплый вечерний свет — проверка, что стекло и акценты следуют за CCT. */
@Preview(name = "Assistant · Night", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun AssistantNightPreview() {
    AmaliaTheme(useBioTime = true, userHourOverride = 22.5f) {
        // Включаем биовремя и в визуальном контексте: тогда на экране виден
        // чип «какой сейчас свет» — тот самый, что объясняет смену палитры.
        CompositionLocalProvider(
            LocalAmaliaVisuals provides AmaliaVisuals(useBioTime = true),
        ) {
            PreviewShell(ReplyPreviewState)
        }
    }
}

/** Компактный экран (640dp высоты) — проверка, что орб не выдавливает карточку. */
@Preview(name = "Assistant · Compact", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun AssistantCompactPreview() {
    AmaliaTheme {
        PreviewShell(ThinkingPreviewState)
    }
}

/** Общая обёртка превью: экран без ViewModel и без сети. */
@Composable
private fun PreviewShell(state: AssistantUiState) {
    AssistantScreenContent(
        state = state,
        onMicClick = {},
        onMicPress = {},
        onPickSuggestion = {},
        onRepeat = {},
        onDismissError = {},
        onNewSession = {},
        onCopy = {},
        onOpenAppSettings = {},
        onNavigateToHistory = {},
        onNavigateToSettings = {},
        onShowCircadianPreview = {},
        modifier = PreviewModifier,
    )
}
