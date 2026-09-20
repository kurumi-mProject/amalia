package com.my.amali.ui.assistant

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.my.amali.R
import com.my.amali.domain.entity.UserSettings
import com.my.amali.domain.entity.VoiceState
import com.my.amali.ui.components.GlassCard
import com.my.amali.ui.components.GradientBackground
import com.my.amali.ui.components.CircadianLamp
import com.my.amali.ui.icons.AmaliaAdd
import com.my.amali.ui.icons.AmaliaAlert
import com.my.amali.ui.icons.AmaliaHistory
import com.my.amali.ui.icons.AmaliaClose
import com.my.amali.ui.icons.AmaliaCopy
import com.my.amali.ui.icons.AmaliaRepeat
import com.my.amali.ui.icons.AmaliaSettings
import com.my.amali.ui.components.AmaliaVoiceVisual
import com.my.amali.ui.components.SuggestionChips
import com.my.amali.ui.theme.AmaliaTheme
import com.my.amali.ui.theme.AmaliaVisuals
import com.my.amali.ui.theme.CircadianPhase
import com.my.amali.ui.theme.LocalAmaliaVisuals
import com.my.amali.ui.theme.LocalLightProfile
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.glassSurface
import com.my.amali.ui.theme.paletteChip
import com.my.amali.ui.theme.stringRes
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
 *  4. голосовой блок: слово состояния, живая волна, подпись-подсказка;
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
 * волна дышит всегда — видно, что система жива, ещё до первого слова.
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
 * @param resumeConversationId разговор из истории, который нужно продолжить.
 * @param onResumeHandled сигнал «пересадка выполнена» — сбрасывает запрос.
 */
@Composable
fun AssistantScreen(
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    resumeConversationId: String? = null,
    onResumeHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm: AssistantViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settings by vm.userSettings.collectAsStateWithLifecycle()

    // Продолжение разговора, выбранного в истории. Срабатывает ровно один
    // раз на каждый новый id: как только ViewModel пересадила сессию,
    // навигация сбрасывает сигнал, и повторной композиции уже нечего делать.
    LaunchedEffect(resumeConversationId) {
        val id = resumeConversationId ?: return@LaunchedEffect
        vm.resumeConversation(id, onResumed = onResumeHandled)
    }

    // LocalClipboardManager объявлен deprecated: он не поддерживает suspend и
    // не умеет отдавать в буфер ничего, кроме текста. LocalClipboard работает
    // через ClipboardEntry и поддерживает URI, HTML и картинки.
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Включены ли анимации системы. Читается один раз: значение меняется
    // только в настройках разработчика, и перечитывать его на каждом кадре
    // значило бы читать глобальные настройки десятки раз в секунду.
    //
    // Зачем вообще: при выключенных анимациях показывать переход смены фразы
    // нельзя — система просила покой, и анимация расшифровки здесь читалась
    // бы как неповиновение. Проверка делается там, где есть доступ к
    // настройкам (здесь), а чистый компонент получает готовый флаг.
    val animationsEnabled = remember {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
        }.getOrDefault(1f)
        greetingAnimationEnabled(scale)
    }

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

    AssistantScreenContent(
        state = state,
        settings = settings,
        onMicClick = vm::toggleConversation,
        onMicPress = vm::warmupStt,
        onPickSuggestion = vm::startConversation,
        onRepeat = vm::repeatLast,
        onDismissError = vm::dismissError,
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
        // «Новый разговор» — единственная кнопка верхней зоны. Она
        // появляется, только когда есть что закрывать: в покое кнопка
        // была бы шумом без действия.
        showsNewSession = state.amaliaReply.isNotEmpty() || state.userTranscript.isNotEmpty(),
        onNewSession = vm::startNewSession,
        modifier = modifier,
        animationsEnabled = animationsEnabled,
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
    /**
     * Пользовательские настройки: сейчас отсюда берётся геометрия волны.
     *
     * Передаются целиком, а не одной волной: экран уже зависит от темы и
     * палитры, а настройки — такая же среда, и дробить их на отдельные
     * параметры значит плодить сигнатуру, которую никто не читает.
     */
    settings: UserSettings,
    onMicClick: () -> Unit,
    onMicPress: () -> Unit,
    onPickSuggestion: (String) -> Unit,
    onRepeat: () -> Unit,
    onDismissError: () -> Unit,
    onNewSession: () -> Unit,
    showsNewSession: Boolean,
    onCopy: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Включены ли анимации системы.
     *
     * Значение по умолчанию `true`, а не `false`: забыть передать параметр
     * дешевле, чем получить экран без анимации на одном вызове и с ней
     * на другом. Определяется вызывающей стороной из `Settings.Global`
     * (см. `AssistantScreen`) — компоненту незачем читать системные
     * настройки самому.
     */
    animationsEnabled: Boolean = true,
) {
    // Источник взаимодействия для микрофона: indication = null, потому что
    // рябь на этом элементе конфликтует с волной — она и есть отклик.
    val micInteraction = remember { MutableInteractionSource() }

    val visuals = LocalAmaliaVisuals.current
    val light = LocalLightProfile.current
    val cct = light.cct
    val suggestions = welcomeSuggestions()

    // На коротких экранах сжимается только «воздух» между блоками:
    // карточка диалога и волна обязаны остаться целыми.
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

        // Одно дыхание на весь экран. Раньше приветствие, волна и точка в
        // шапке дышали каждый со своим периодом (3.2 / 5.6 / 2.6 с) — четыре
        // независимых ритма, которые глаз читает как шум, а не как покой.
        // Здесь один такт, из него выводится всё живое на экране.
        val breathTransition = rememberInfiniteTransition(label = "screenBreath")
        val breath by breathTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(10_000, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "screenBreathValue",
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // === ГЕРОЙ: главный текст — основа экрана ===
            // После снятия шапки (имя, счётчик разговоров, кнопки истории
            // и настроек) верх освободился, и крупная фраза получила его
            // целиком. Это и есть «благородство» экрана — один большой
            // текст на воздухе вместо пяти мелких элементов в ряд.
            val welcome = DialogPhase.of(state) == DialogPhase.Welcome

            // === СВЕТ: единственный элемент в шапке ===
            // Свёрнутая лампа стоит всегда и на одном месте — под главным
            // текстом. Она сообщает, что интерфейс живой и по какому свету
            // сейчас нарисован; слова внутри неё раскрываются по тапу или
            // сами при входе. Будь она в углу, она спорила бы с текстом;
            // по центру она читается как «солнце» композиции.
            Spacer(Modifier.height(Spacing.sm))
            CircadianLamp(
                label = stringResource(light.lightLabel.stringRes),
                cct = cct,
                autoExpandOnStart = true,
            )

            Spacer(Modifier.height(Spacing.md))
            // useAnimation = false в превью и при отключённых системных
            // анимациях: там смена фразы мгновенная, и это правильно —
            // показывать переход некуда.
            GreetingHero(
                breath = breath,
                useAnimation = LocalInspectionMode.current.not() && animationsEnabled,
            )

            if (welcome) {
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.height(Spacing.md))
            }

            // === ДОПОЛНИТЕЛЬНОЕ: карточки диалога под героем ===
            // В покое здесь стоят только чипы «что сказать». С началом
            // разговора герой остаётся сверху как заголовок экрана, а
            // карточка разговора занимает центр.

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
                        when (phase) {
                            // В покое карточки нет вовсе: чипы «что сказать»
                            // живут прямо на экране, без стеклянной коробки
                            // вокруг. Коробка ради одной строки текста —
                            // лишний объект в кадре, а элегантность здесь
                            // держится на том, что считать нечего.
                            DialogPhase.Welcome -> SuggestionChips(
                                suggestions = suggestions,
                                onClick = onPickSuggestion,
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

            // === ДЕЙСТВИЯ ДИАЛОГА ===
            // «Новый разговор» живёт здесь, рядом с самим разговором, а не
            // в шапке экрана: оно относится к текущей беседе, а не к
            // приложению целиком.
            AnimatedVisibility(
                visible = showsNewSession,
                enter = fadeIn(tween(220)) + expandVertically(tween(240)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    GlassTextAction(
                        icon = AmaliaAdd,
                        text = stringResource(R.string.assistant_new_session),
                        onClick = onNewSession,
                    )
                }
            }

            // === ГОЛОСОВОЙ БЛОК: слово + волна + подпись ===
            // Ритм этого блока задан намеренно: слово и волна стоят плотно
            // (2dp) — они один объект, «что происходит» и «куда нажать».
            // А от карточки диалога блок отделён крупно (20dp), потому что
            // это уже другая смысловая зона. Раньше везде стояло 4dp, и
            // именно поэтому всё «слипалось»: глаз не находил границ.
            Spacer(Modifier.height(if (compact) Spacing.sm else Spacing.lg))

            StatusLabel(state = state.voiceState)

            Spacer(Modifier.height(2.dp))

            // Волна включается только там, где есть звук: слушание (микрофон)
            // и речь (динамик). В покое и в ошибке звука нет вовсе, а в фазе
            // «думаю» идёт ожидание ответа модели — микрофон уже закрыт,
            // Амалия ещё молчит. Раньше волна разворачивалась и в «думаю»,
            // то есть показывала звук, которого не было: полосы стояли
            // неподвижно, но сам факт их развёрнутого строя читался как
            // «слушаю». Состояние обязано совпадать с тем, что нарисовано.
            val voiceAudible = state.voiceState == VoiceState.Listening ||
                state.voiceState == VoiceState.Speaking
            AmaliaVoiceVisual(
                enabled = voiceAudible,
                level = state.audioLevel,
                settings = settings.wave,
                color = stateColor(state.voiceState),
                micSize = if (compact) 28.dp else 32.dp,
                modifier = Modifier
                    .clickable(
                        interactionSource = micInteraction,
                        indication = null,
                        onClick = onMicClick,
                    )
                    .size(if (compact) 132.dp else 156.dp),
            )

            Spacer(Modifier.height(Spacing.sm))

            // Подпись — под волной: главное действие обязано стоять
            // максимально близко к нижнему краю, в зоне большого пальца.
            // Текст меняется по состоянию, потому что «Нажми и говори»
            // в момент, когда ассистент уже слушает, — просто неправда.
            Text(
                text = stringResource(voiceHintRes(state.voiceState, state.isBusy)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )

            // Запас под плавающую нижнюю навигацию: 64dp панель + 12dp её
            // паддинг + воздух. Меньше 88dp нельзя — панель наедет на
            // подпись; больше — главное действие уедет из зоны большого
            // пальца. Волна сама по себе даёт воздух, поэтому здесь
            // хватает нижней границы запаса.
            Spacer(Modifier.height(BottomBarReserve))
        }
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

/**
 * Подсказка под волной — что именно делать прямо сейчас.
 *
 * Раньше здесь стояли всего две строки: «Нажми и говори» либо
 * «остановить». Из-за этого в фазе «думаю» экран предлагал «остановить»
 * (то есть обещал действие, которого не ждут), а в фазе «слушаю» —
 * «Нажми и говори», хотя человек уже говорит. Подсказка обязана
 * описывать текущий шаг, а не исходное состояние.
 */
@Composable
private fun voiceHintRes(state: VoiceState, busy: Boolean): Int = when (state) {
    VoiceState.Idle -> R.string.assistant_welcome_hint
    VoiceState.Listening -> R.string.assistant_hint_listening
    VoiceState.Thinking -> R.string.assistant_hint_thinking
    VoiceState.Speaking -> R.string.assistant_hint_speaking
    VoiceState.Error -> R.string.assistant_hint_error
}

// ============================================================
//  РЕЖИМ ЦИКЛА (для превью CircadianLamp)
// ============================================================

/**
 * Запускается ли раскрытие лампы автоматически при появлении экрана.
 *
 * В реальном приложении — всегда true: пользователь обязан один раз
 * увидеть, что внутри скрыт текст, иначе аффорданс не читается. В превью
 * это всё равно не работает (анимации в превью не запускаются), поэтому
 * параметр существует ровно для того, чтобы превью не притворялись,
 * будто они что-то анимируют.
 */
private const val LAMP_AUTO_EXPAND = true

// ============================================================
//  СОСТОЯНИЕ
// ============================================================

/**
 * Слово состояния — главный текстовый якорь голосового блока.
 *
 * Стоит вплотную над волной, поэтому «что происходит» и «куда нажать»
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

/**
 * Цвет слова состояния.
 *
 * В покое — приглушённый «onBackground», а не «onSurfaceVariant». Разница
 * не косметическая: `onSurfaceVariant` — служебный цвет для второстепенного
 * текста, и главный якорь экрана в нём выглядел выключенным. Здесь покой
 * читается спокойным, но живым: это состояние ожидания, а не отказа.
 */
@Composable
private fun stateColor(state: VoiceState): Color = when (state) {
    VoiceState.Idle -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.88f)
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
        // Лейбл «слушаю» здесь больше не нужен: слово состояния уже стоит
        // под волной, и дублировать его внутри карточки — значит писать
        // одно и то же дважды в одном кадре.
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
        // Слово «думаю» живёт под волной — здесь оно было бы повтором.
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
 * волну за пределы экрана. Остальное — счётчиком.
 */
@Composable
private fun ToolActivityStrip(tools: List<ToolActivity>) {
    // Одна бесконечная анимация на всю полосу, а не по одной на инструмент.
    val strip = rememberInfiniteTransition(label = "toolStrip")
    val breath by strip.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1_640, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "toolStripBreath",
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        tools.take(VISIBLE_TOOL_ROWS).forEachIndexed { index, tool ->
            // Сдвиг по индексу: чипы дышат «волной», а не хором, но
            // источник движения по-прежнему один.
            val phase = ((breath + index * 0.18f) % 1f + 1f) % 1f
            ToolChip(tool = tool, breathBase = phase)
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

/**
 * Один инструмент в работе.
 *
 * @param breathBase общая фаза пульсации полосы 0..1. Раньше каждый чип
 *   заводил собственный `InfiniteTransition`: четыре активных инструмента
 *   означали четыре независимые бесконечные анимации, каждая со своими
 *   подписками и своим кадровым расчётом. Теперь фаза одна на полосу, а
 *   чипы лишь читают её со сдвигом: визуально то же самое, по кадрам —
 *   вчетверо дешевле.
 */
@Composable
private fun ToolChip(
    tool: ToolActivity,
    breathBase: Float,
) {
    val breath = 0.4f + 0.6f * breathBase
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
            // Имя говорящего убрано: на этом экране говорит только один
            // голос, и подписывать его не нужно. Вместо имени — тонкая
            // акцентная черта, которая просто отмечает начало ответа.
            Box(
                modifier = Modifier
                    .size(width = 22.dp, height = 2.dp)
                    .clip(RoundedCornerShape(Radius.chip))
                    .background(MaterialTheme.colorScheme.secondary),
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
                    icon = AmaliaRepeat,
                    text = stringResource(R.string.assistant_repeat),
                    onClick = onRepeat,
                )
                GlassTextAction(
                    icon = AmaliaCopy,
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
            imageVector = AmaliaHistory,
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
                imageVector = AmaliaAlert,
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
                imageVector = AmaliaClose,
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
                icon = AmaliaRepeat,
                text = stringResource(R.string.common_retry),
                onClick = onRetry,
            )
            if (onOpenSettings != null) {
                GlassTextAction(
                    icon = AmaliaSettings,
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
 * не наехала на подпись под волной. Прежние 84dp были меньше фактической
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

/** Экран в покое: приветствие, свет, подсказки, волна. */
@Preview(name = "Assistant · Idle", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun AssistantIdlePreview() {
    AmaliaTheme {
        PreviewShell(IdlePreviewState)
    }
}

/** Слушание: живые субтитры и живая волна. */
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

/** Компактный экран (640dp высоты) — проверка, что волна не выдавливает карточку. */
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
        settings = UserSettings.DEFAULT,
        onMicClick = {},
        onMicPress = {},
        onPickSuggestion = {},
        onRepeat = {},
        onDismissError = {},
        onNewSession = {},
        showsNewSession = state.amaliaReply.isNotEmpty(),
        onCopy = {},
        onOpenAppSettings = {},
        modifier = PreviewModifier,
    )
}
