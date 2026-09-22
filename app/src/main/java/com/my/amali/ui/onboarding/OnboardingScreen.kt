package com.my.amali.ui.onboarding

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.my.amali.R
import com.my.amali.domain.entity.WaveSettings
import com.my.amali.ui.components.AmaliaWaveform
import com.my.amali.ui.components.GhostButton
import com.my.amali.ui.components.GradientBackground
import com.my.amali.ui.components.PrimaryButton
import com.my.amali.ui.theme.AmaliaMotif
import com.my.amali.ui.theme.AmaliaTheme
import com.my.amali.ui.theme.AmaliaVisualTheme
import com.my.amali.ui.theme.BioGradientDay
import com.my.amali.ui.theme.DarkModePreference
import com.my.amali.ui.theme.GlassGradientPalette
import com.my.amali.ui.theme.GradientPalette
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.accentGlow
import com.my.amali.ui.theme.currentGradientPalette
import com.my.amali.ui.theme.glassSurface
import kotlin.math.absoluteValue

/** Индексы слайдов онбординга. */
private const val PAGE_WELCOME = 0
private const val PAGE_VOICE = 1
private const val PAGE_CONTROL = 2
private const val PAGE_THEMES = 3
private const val PAGE_FINISH = 4

/** Всего слайдов в пагере. Одно число для состояния и для превью. */
private const val ONBOARDING_PAGE_COUNT = 5

/** Иконка + подпись для слайда «Управление устройством». */
private data class ControlIcon(val icon: ImageVector, val labelResId: Int)

private val controlIcons = listOf(
    ControlIcon(Icons.Rounded.Wifi, R.string.device_wifi),
    ControlIcon(Icons.Rounded.Bluetooth, R.string.device_bluetooth),
    ControlIcon(Icons.Rounded.BrightnessMedium, R.string.device_brightness),
    ControlIcon(Icons.AutoMirrored.Rounded.VolumeUp, R.string.device_volume),
)

/**
 * Онбординг: пять слайдов в HorizontalPager.
 *
 * ══════════════════════════════════════════════════════════
 *  ДИЗАЙН-РЕШЕНИЕ (почему онбординг выглядит именно так)
 * ══════════════════════════════════════════════════════════
 *
 * Онбординг — это не «обучение», а первое дыхание продукта. Человек
 * открывает приложение, которого ещё не знает, и за пять экранов ему
 * нужно ответить ровно на один вопрос: «мне здесь будет спокойно?».
 * Поэтому каждый слайд держит ОДНОГО героя на воздухе, текст — три
 * строки, и ничего, что требует чтения.
 *
 *  1. Welcome — настоящий логотип приложения: то, что человек видел
 *     на рабочем столе, встречает его и здесь. Узнавание вместо вопроса.
 *  2. Voice — та же живая волна, что работает на главном экране, и она
 *     реально дышит: обещание совпадает с тем, что пользователь получит.
 *  3. Control — четыре стеклянные плитки устройств, входящие каскадом:
 *     «приложение живое» читается движением, а не словами.
 *  4. Themes — две темы рядом и лента циркадного света: продукт
 *     показывает свою главную идею (интерфейс едет за временем суток).
 *  5. Finish — снова орб с логотипом и честное «что будет дальше»:
 *     микрофон попросим при первом разговоре, дальше просто говори.
 *
 * Появление контента — стаггер: герой, затем заголовок, затем описание.
 * Каждый элемент вступает со своей задержкой, потому что глаз читает
 * их по очереди; одновременное появление трёх блоков выглядит как
 * «вспышка», а не как приглашение.
 *
 * Переход между слайдами — параллакс: герой двигается быстрее текста,
 * свайп ощущается объёмным.
 */
@Composable
fun OnboardingScreen(
    onNavigateToAssistant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: OnboardingViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { state.pageCount })

    // Включены ли системные анимации (см. тот же блок в AssistantScreen):
    // при выключенных стаггер не имеет куда играть — всё должно
    // появляться мгновенно.
    val context = LocalContext.current
    val animationsEnabled = remember {
        val scale: Float = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
            )
        }.getOrDefault(1f)
        scale > 0f
    }

    LaunchedEffect(state.currentPage) {
        if (pagerState.currentPage != state.currentPage) {
            pagerState.animateScrollToPage(state.currentPage)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page -> viewModel.onPageChanged(page) }
    }
    LaunchedEffect(state.isFinished) {
        if (state.isFinished) onNavigateToAssistant()
    }

    OnboardingScreenContent(
        state = state,
        pagerState = pagerState,
        animationsEnabled = animationsEnabled,
        onNext = viewModel::nextPage,
        onPrev = viewModel::prevPage,
        onFinish = viewModel::finish,
        modifier = modifier,
    )
}

/**
 * Чистая (stateless) часть онбординга.
 *
 * Отделена от [OnboardingScreen] ровно по одной причине: `@Preview` не
 * умеет поднимать ViewModel — в режиме превью нет `ViewModelStoreOwner`,
 * и вызов `viewModel()` роняет рендер. Пока состояние и действия
 * приходят параметрами, экран рисуется в превью без единого мока.
 */
@Composable
fun OnboardingScreenContent(
    state: OnboardingState,
    pagerState: PagerState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = true,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Фон онбординга — живой и адаптивный: палитра приходит из темы,
        // а та рассчитывается по времени суток, когда включена циркадная
        // адаптация (она включена по умолчанию). Первый вход вечером даёт
        // тёплый вечерний свет, утром — прохладное утро: первое
        // впечатление совпадает с тем, что человек увидит дальше.
        GradientBackground(
            modifier = Modifier.fillMaxSize(),
            motif = AmaliaMotif.AUTO,
            motifDensity = 0.8f,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Верхняя строка: счётчик слайдов и «Пропустить».
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${state.pageCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                GhostButton(
                    text = stringResource(R.string.onboarding_skip),
                    onClick = onFinish,
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                val offset =
                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val active = pagerState.currentPage == page
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = (1f - offset.absoluteValue * 0.85f).coerceIn(0f, 1f)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    when (page) {
                        PAGE_WELCOME -> WelcomeSlide(offset, active, animationsEnabled)
                        PAGE_VOICE -> VoiceSlide(offset, active, animationsEnabled)
                        PAGE_CONTROL -> ControlSlide(offset, active, animationsEnabled)
                        PAGE_THEMES -> ThemesSlide(offset, active, animationsEnabled)
                        else -> FinishSlide(offset, active, animationsEnabled)
                    }
                }
            }

            PageIndicator(
                pageCount = state.pageCount,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = Spacing.md),
            )

            BottomControls(
                currentPage = pagerState.currentPage,
                pageCount = state.pageCount,
                animationsEnabled = animationsEnabled,
                onBack = onPrev,
                onNext = onNext,
                onFinish = onFinish,
            )
        }
    }
}

// ============================================================
//  СТАГГЕР-МЕХАНИКА
// ============================================================

/**
 * Прогресс появления элемента 0..1 со своей задержкой.
 *
 * Слагается из двух вещей:
 *  — [active] слайд «активен» — на нём стоит пользователь;
 *  — [delayMs] — насколько элемент отстаёт от начала сцены.
 *
 * `armed` нужен для самого первого слайда: без него `animateFloatAsState`
 * выставил бы цель 1f прямо в первой композиции, и вступительной анимации
 * не случилось бы вовсе — экран «уже был открыт», а не открылся.
 * Флаг взводится эффектом после первой композиции, и первый кадр честно
 * рисуется пустым, а дальше элементы входят каскадом.
 */
@Composable
private fun stagger(active: Boolean, delayMs: Int, animationsEnabled: Boolean): Float {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { armed = true }

    val duration = if (animationsEnabled) 460 else 0
    val hold = if (animationsEnabled) delayMs else 0
    val progress by animateFloatAsState(
        targetValue = if (armed && active) 1f else 0f,
        animationSpec = tween(duration, delayMillis = hold, easing = EaseOutCubic),
        label = "stagger$delayMs",
    )
    return progress
}

/**
 * Появление элемента: мягкий подъём + проявление.
 *
 * Работает в фазе отрисовки ([graphicsLayer]) — вход ничего не стоит
 * рекомпозиции и не дёргает лейаут соседей. Подъём задан в dp и
 * переводится в пиксели плотностью скоупа: на любом экране сдвиг
 * одинаковый зрительно.
 */
private fun Modifier.reveal(progress: Float, shiftDp: Float = 24f): Modifier =
    graphicsLayer {
        alpha = progress
        val shown = 0.94f + 0.06f * progress
        scaleX = shown
        scaleY = shown
        translationY = (1f - progress) * shiftDp.dp.toPx()
    }

// ============================================================
//  СЛАЙД 0: WELCOME ==========================================================
// ============================================================

@Composable
private fun WelcomeSlide(
    offset: Float,
    active: Boolean,
    animationsEnabled: Boolean,
) {
    SlideScaffold(
        offset = offset,
        active = active,
        animationsEnabled = animationsEnabled,
        title = stringResource(R.string.onboarding_welcome_title),
        description = stringResource(R.string.onboarding_welcome_desc),
        hero = { progress ->
            // Орб с настоящим логотипом: знакомый образ с рабочего стола.
            // Дышит по тому же принципу, что и голосовой орб главного
            // экрана, — онбординг сразу показывает главный жест продукта.
            val breathTransition = rememberInfiniteTransition(label = "onbBreath")
            val breath by breathTransition.animateFloat(
                initialValue = 0.965f,
                targetValue = 1.035f,
                animationSpec = infiniteRepeatable(
                    tween(4_600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "onbBreathValue",
            )
            Box(
                modifier = Modifier
                    .size(176.dp)
                    .reveal(progress, shiftDp = 10f)
                    .scale(breath)
                    .accentGlow(
                        color = MaterialTheme.colorScheme.primary,
                        alpha = 0.34f,
                        spread = 1.5f,
                    )
                    .clip(CircleShape)
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.secondary,
                                Color.Transparent,
                            ),
                        ),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.amalia_logo),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        },
    )
}

// ============================================================
//  СЛАЙД 1: VOICE ============================================================
// ============================================================

@Composable
private fun VoiceSlide(
    offset: Float,
    active: Boolean,
    animationsEnabled: Boolean,
) {
    SlideScaffold(
        offset = offset,
        active = active,
        animationsEnabled = animationsEnabled,
        title = stringResource(R.string.onboarding_voice_title),
        description = stringResource(R.string.onboarding_voice_desc),
        hero = { progress ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .reveal(progress),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Живая волна прямо в онбординге: обещание = реальный UI.
                // Уровень дышит сам в диапазоне 0.32..0.62 — полосы стоят
                // ровно так, как волне положено стоять при тихой речи.
                val waveBreath = rememberInfiniteTransition(label = "onbWave")
                val level by waveBreath.animateFloat(
                    initialValue = 0.32f,
                    targetValue = 0.62f,
                    animationSpec = infiniteRepeatable(
                        tween(2_600, easing = LinearEasing),
                        RepeatMode.Reverse,
                    ),
                    label = "onbWaveLevel",
                )
                val onboardingWave = remember { WaveSettings() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    AmaliaWaveform(
                        level = level,
                        settings = onboardingWave,
                        color = MaterialTheme.colorScheme.primary,
                        isActive = true,
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .accentGlow(MaterialTheme.colorScheme.primary, alpha = 0.30f, spread = 1.7f)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.primary,
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        },
    )
}

// ============================================================
//  СЛАЙД 2: CONTROL ==========================================================
// ============================================================

@Composable
private fun ControlSlide(
    offset: Float,
    active: Boolean,
    animationsEnabled: Boolean,
) {
    SlideScaffold(
        offset = offset,
        active = active,
        animationsEnabled = animationsEnabled,
        title = stringResource(R.string.onboarding_control_title),
        description = stringResource(R.string.onboarding_control_desc),
        hero = { progress ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                // Плитки входят каскадом: каждая отстаёт от предыдущей на
                // 70 мс — «приложение живое» читается движением.
                controlIcons.forEachIndexed { index, control ->
                    val label = stringResource(control.labelResId)
                    val tileProgress = stagger(
                        active = active,
                        delayMs = 140 + index * 70,
                        animationsEnabled = animationsEnabled,
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .semantics { contentDescription = label }
                            .reveal(tileProgress, shiftDp = 14f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .glassSurface(shape = RoundedCornerShape(Radius.sm)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = control.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
    )
}

// ============================================================
//  СЛАЙД 3: THEMES ===========================================================
// ============================================================

@Composable
private fun ThemesSlide(
    offset: Float,
    active: Boolean,
    animationsEnabled: Boolean,
) {
    SlideScaffold(
        offset = offset,
        active = active,
        animationsEnabled = animationsEnabled,
        title = stringResource(R.string.onboarding_theme_title),
        description = stringResource(R.string.onboarding_theme_desc),
        hero = { progress ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    ThemePreview(
                        palette = GlassGradientPalette,
                        label = stringResource(R.string.appearance_theme_glass),
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    ThemePreview(
                        palette = BioGradientDay,
                        label = stringResource(R.string.appearance_theme_bio),
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                // Лента циркадного света — главная идея темы, показанная
                // цветом: палитра и правда едет за часом суток. Текст под
                // ней объясняет одним предложением, что это было.
                CircadianRibbon(
                    progress = progress,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.onboarding_theme_circadian),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .reveal(progress, shiftDp = 8f)
                        .padding(horizontal = Spacing.md),
                )
            }
        },
    )
}

/**
 * Лента света суток: пять срезов биофильной лестницы палитр — рассвет,
 * полдень, закат, вечер, ночь. Статичная полоса: смена палитры в ней
 * — это то, что пользователь увидит сам через сутки, а не то, что он
 * обязан высмотреть за секунду.
 */
@Composable
private fun CircadianRibbon(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    // Часы выбраны как «световые события», а не равные интервалы:
    // рассветный лёд, полдень, тёплый закат, вечер и глубокая ночь.
    val hours = remember { listOf(6.5f, 13f, 18.5f, 22.5f, 2.5f) }
    val colors = remember(hours) {
        hours.map { hour ->
            val palette = currentGradientPalette(
                visualTheme = AmaliaVisualTheme.BIOPHILIC,
                darkModePref = DarkModePreference.SYSTEM,
                useBioTime = true,
                hour = hour,
            )
            palette.stops.first().color
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .reveal(progress, shiftDp = 8f)
            .clip(RoundedCornerShape(Radius.chip)),
    ) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                color,
                                color.copy(alpha = color.alpha * 0.55f),
                            ),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun ThemePreview(
    palette: GradientPalette,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.md)
    Column(
        modifier = modifier
            .clip(shape)
            .glassSurface(shape = shape)
            .semantics { contentDescription = label },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops = palette.stops
                            .map { it.position to it.color }
                            .toTypedArray(),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(5) { index ->
                    val h = when (index) {
                        2 -> 26.dp
                        1, 3 -> 18.dp
                        else -> 10.dp
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = h)
                            .clip(RoundedCornerShape(Radius.chip))
                            .background(accent.copy(alpha = if (index == 2) 1f else 0.5f)),
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        )
    }
}

// ============================================================
//  СЛАЙД 4: FINISH ===========================================================
// ============================================================

@Composable
private fun FinishSlide(
    offset: Float,
    active: Boolean,
    animationsEnabled: Boolean,
) {
    SlideScaffold(
        offset = offset,
        active = active,
        animationsEnabled = animationsEnabled,
        title = stringResource(R.string.onboarding_finish_title),
        description = stringResource(R.string.onboarding_finish_desc),
        hero = { progress ->
            Box(contentAlignment = Alignment.Center) {
                // Орб с логотипом — тот же образ, что и на первом слайде:
                // кольцо замкнулось, знакомство закончилось узнаванием.
                Box(
                    modifier = Modifier
                        .size(148.dp)
                        .reveal(progress, shiftDp = 10f)
                        .accentGlow(MaterialTheme.colorScheme.primary, alpha = 0.32f, spread = 1.5f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.amalia_logo),
                        contentDescription = null,
                        modifier = Modifier.size(112.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                // Галочка-бейдж на кромке орба: состояние «готово» читается
                // и без текста, и не цветом — формой значка.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp)
                        .reveal(progress, shiftDp = 18f)
                        .glassSurface(shape = CircleShape, elevated = true),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    )
}

// ============================================================
//  ОБЩИЙ КАРКАС СЛАЙДА =======================================================
// ============================================================

/**
 * Каркас слайда: герой-объект + заголовок + описание.
 *
 * Параллакс: герой смещается на 35% ширины, текст — на 12%.
 * Внутри слайда элементы появляются стаггером: герой (с его собственной
 * задержкой), затем заголовок, затем описание.
 */
@Composable
private fun SlideScaffold(
    offset: Float,
    active: Boolean,
    animationsEnabled: Boolean,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    hero: @Composable (progress: Float) -> Unit,
) {
    val heroProgress = stagger(active, delayMs = 0, animationsEnabled = animationsEnabled)
    val titleProgress = stagger(active, delayMs = 120, animationsEnabled = animationsEnabled)
    val descProgress = stagger(active, delayMs = 220, animationsEnabled = animationsEnabled)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                translationX = offset * size.width * 0.35f
            },
            contentAlignment = Alignment.Center,
        ) {
            hero(heroProgress)
        }
        Spacer(Modifier.height(Spacing.xxl))
        Column(
            modifier = Modifier.graphicsLayer {
                translationX = offset * size.width * 0.12f
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.reveal(titleProgress, shiftDp = 14f),
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.reveal(descProgress, shiftDp = 14f),
            )
        }
    }
}

// ============================================================
//  PAGE INDICATOR ============================================================
// ============================================================

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val active = index == currentPage
            val width: Dp by animateDpAsState(
                targetValue = if (active) 26.dp else 7.dp,
                animationSpec = tween(260),
                label = "dotWidth$index",
            )
            val color by animateColorAsState(
                targetValue = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
                },
                animationSpec = tween(260),
                label = "dotColor$index",
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(7.dp)
                    .clip(RoundedCornerShape(Radius.chip))
                    .background(color),
            )
        }
    }
}

// ============================================================
//  BOTTOM CONTROLS ===========================================================
// ============================================================

@Composable
private fun BottomControls(
    currentPage: Int,
    pageCount: Int,
    animationsEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLast = currentPage == pageCount - 1
    // При выключенных системных анимациях появления честно мгновенные.
    val enterMs = if (animationsEnabled) 200 else 0
    val exitMs = if (animationsEnabled) 160 else 0
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        AnimatedVisibility(
            visible = currentPage > 0,
            enter = fadeIn(tween(enterMs)) + slideInHorizontally(tween(enterMs)) { -it / 2 },
            exit = fadeOut(tween(exitMs)) + slideOutHorizontally(tween(exitMs)) { -it / 2 },
        ) {
            GhostButton(
                text = stringResource(R.string.onboarding_back),
                onClick = onBack,
            )
        }
        PrimaryButton(
            text = stringResource(
                if (isLast) R.string.onboarding_start else R.string.onboarding_next,
            ),
            onClick = if (isLast) onFinish else onNext,
            modifier = Modifier.weight(1f),
        )
    }
}

// ============================================================
//  PREVIEW ===================================================================
// ============================================================
//
//  Превью собирают [OnboardingScreenContent] — чистое представление слайдов.
//  Каждый слайд можно открыть отдельным превью и увидеть его ровно таким,
//  каким он будет на устройстве.

/** Общая обёртка превью: онбординг на конкретном слайде, без ViewModel. */
@Composable
private fun OnboardingPreview(page: Int) {
    AmaliaTheme {
        OnboardingScreenContent(
            state = OnboardingState(currentPage = page, pageCount = ONBOARDING_PAGE_COUNT),
            pagerState = rememberPagerState(
                initialPage = page,
                pageCount = { ONBOARDING_PAGE_COUNT },
            ),
            onNext = {},
            onPrev = {},
            onFinish = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Слайд 0 — приветствие: логотип-орб и обещание. */
@Preview(name = "Onboarding · Welcome", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun OnboardingWelcomePreview() = OnboardingPreview(PAGE_WELCOME)

/** Слайд 1 — голос: живая волна и микрофон. */
@Preview(name = "Onboarding · Voice", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun OnboardingVoicePreview() = OnboardingPreview(PAGE_VOICE)

/** Слайд 2 — управление устройством: каскад плиток. */
@Preview(name = "Onboarding · Control", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun OnboardingControlPreview() = OnboardingPreview(PAGE_CONTROL)

/** Слайд 3 — темы: сравнение палитр и лента циркадного света. */
@Preview(name = "Onboarding · Themes", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun OnboardingThemesPreview() = OnboardingPreview(PAGE_THEMES)

/** Слайд 4 — финал: орб с логотипом и CTA «Начать». */
@Preview(name = "Onboarding · Finish", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun OnboardingFinishPreview() = OnboardingPreview(PAGE_FINISH)

/** Компактный экран — проверка, что слайды не обрезаются на 640dp. */
@Preview(name = "Onboarding · Compact", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun OnboardingCompactPreview() = OnboardingPreview(PAGE_CONTROL)
