package com.my.amali.ui.onboarding

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.my.amali.R
import com.my.amali.ui.components.GhostButton
import com.my.amali.ui.components.GradientBackground
import com.my.amali.ui.components.PrimaryButton
import com.my.amali.ui.theme.AmaliaTheme
import com.my.amali.ui.theme.LocalLightProfile
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.stringRes
import kotlin.math.absoluteValue

/** Индексы слайдов онбординга. */
private const val PAGE_WELCOME = 0
private const val PAGE_VOICE = 1
private const val PAGE_LIGHT = 2
private const val PAGE_FINISH = 3

/** Всего слайдов в пагере. Одно число для состояния и для превью. */
private const val ONBOARDING_PAGE_COUNT = 4

/**
 * Онбординг: четыре слайда чистой типографики на живом фоне.
 *
 * ══════════════════════════════════════════════════════════
 *  ДИЗАЙН-РЕШЕНИЕ
 * ══════════════════════════════════════════════════════════
 *
 * Задача онбординга — не «показать красивые картинки», а ПРИГОТОВИТЬ
 * человека к продукту: объяснить, как им пользоваться, и дать глазам
 * привыкнуть к среде. Поэтому здесь нет ни орба, ни волны, ни декораций:
 *
 *  — **герой — сам фон.** Живой ауророй он уже дышит и по времени суток
 *    уже едет: первый вход вечером даёт тёплый янтарный свет, утром —
 *    прохладный. Глаз адаптируется к среде ровно так же, как потом
 *    на главном экране;
 *  — **контент — большие слова.** Один крупный заголовок, одна строка
 *    описания, тонкая акцентная черта, при необходимости — микроподпись.
 *    Максимум два уровня типографики; когда сомневаешься — убери элемент;
 *  — **каждый слайд готовит к одному факту продукта:**
 *      0. «Привет, я Амалия» — знакомство;
 *      1. «Голос — главный интерфейс» — жест: нажми и говори, и приватность
 *         («микрофон включается только по нажатию»);
 *      2. «Живой свет» — тема подстраивается по времени суток, слайд
 *         показывает СВОЙ свет: «Сейчас: Закат · 2500 K» — данные, а не
 *         обещание;
 *      3. «Всё готово» — что будет дальше: микрофон попросим при первом
 *         разговоре, дальше просто говори.
 *
 * Появление контента — стаггер (заголовок → описание → черта → подпись),
 * переход между слайдами — параллакс пейджера. Бесконечных аниматоров
 * в контенте ноль: движение даёт только фон и жесты пользователя.
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
    // при выключенных стаггер не имеет куда играть — всё появляется сразу.
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
        // Фон — живой и циркадный: палитра приходит из темы и едет по
        // времени суток (адаптация включена по умолчанию). Именно он
        // адаптирует глаза: первый вход в любое время суток даёт свет,
        // при котором глазу легко.
        GradientBackground(modifier = Modifier.fillMaxSize())

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
                        PAGE_LIGHT -> LightSlide(offset, active, animationsEnabled)
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
//  СЛАЙДЫ
// ============================================================

/** Слайд 0 — знакомство: имя и обещание, ничего больше. */
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
    )
}

/** Слайд 1 — жест продукта и честная строчка о приватности. */
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
        caption = {
            CaptionRow(
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        // Иконка подчиняется подписи: тот же цвет и кегль,
                        // чтобы строка читалась как одна сущность.
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp),
                    )
                },
                text = stringResource(R.string.onboarding_voice_privacy),
            )
        },
    )
}

/**
 * Слайд 2 — живой свет: главная идея темы, показанная фактом.
 *
 * Подпись берётся у [LocalLightProfile] — того же расчёта, что красит фон:
 * слайд и фон построены на одном источнике и потому не могут разойтись.
 * Вечером человек читает «Сейчас: Закат · 2500 K» и видит тот же закат
 * вокруг текста.
 */
@Composable
private fun LightSlide(
    offset: Float,
    active: Boolean,
    animationsEnabled: Boolean,
) {
    val light = LocalLightProfile.current
    SlideScaffold(
        offset = offset,
        active = active,
        animationsEnabled = animationsEnabled,
        title = stringResource(R.string.onboarding_light_title),
        description = stringResource(R.string.onboarding_theme_circadian),
        caption = {
            CaptionRow(
                text = stringResource(
                    R.string.appearance_light_now,
                    stringResource(light.lightLabel.stringRes),
                    light.cct,
                ),
            )
        },
    )
}

/** Слайд 3 — финал: что будет дальше, и большой CTA. */
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
    )
}

// ============================================================
//  КАРКАС СЛАЙДА И СТАГГЕР
// ============================================================

/**
 * Каркас слайда: заголовок + описание + черта + опциональная подпись.
 *
 * Параллакс: текстовый блок смещается медленнее страницы — свайп читается
 * объёмным. Появление — стаггер: у каждого элемента своя задержка, потому
 * что глаз читает их по очереди; одновременный вход читается вспышкой.
 */
@Composable
private fun SlideScaffold(
    offset: Float,
    active: Boolean,
    animationsEnabled: Boolean,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    caption: (@Composable () -> Unit)? = null,
) {
    val titleProgress = stagger(active, delayMs = 0, animationsEnabled = animationsEnabled)
    val descProgress = stagger(active, delayMs = 120, animationsEnabled = animationsEnabled)
    val markProgress = stagger(active, delayMs = 240, animationsEnabled = animationsEnabled)
    val captionProgress = stagger(active, delayMs = 340, animationsEnabled = animationsEnabled)

    Column(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { translationX = offset * size.width * 0.12f }
            .padding(horizontal = Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .reveal(titleProgress),
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .reveal(descProgress),
        )
        Spacer(Modifier.height(Spacing.lg))
        AccentMark(progress = markProgress)
        if (caption != null) {
            Spacer(Modifier.height(Spacing.md))
            Box(modifier = Modifier.reveal(captionProgress)) { caption() }
        }
    }
}

/**
 * Прогресс появления элемента 0..1 со своей задержкой.
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
 * рекомпозиции и не дёргает лейаут соседей. Подъём в пиксели переводит
 * плотность скоупа: на любом экране сдвиг зрительно одинаков.
 */
private fun Modifier.reveal(progress: Float): Modifier =
    graphicsLayer {
        alpha = progress
        val shown = 0.96f + 0.04f * progress
        scaleX = shown
        scaleY = shown
        translationY = (1f - progress) * RevealShift.toPx()
    }

/** Подъём при входе: половина строки — заметно, но спокойно. */
private val RevealShift: Dp = 14.dp

/**
 * Тонкая акцентная черта — подпись Амалии под словами.
 *
 * Тот же приём, что и черта под фразой приветствия главного экрана:
 * слабый, но различимый маркер удерживает взгляд на тексте, не споря
 * с ним. Градиент от прозрачного края — линия «растворяется» в воздухе,
 * а не обрубается.
 */
@Composable
private fun AccentMark(progress: Float, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Box(
        modifier = modifier
            .size(width = 56.dp, height = 2.dp)
            .graphicsLayer { alpha = 0.35f + 0.65f * progress }
            .clip(RoundedCornerShape(Radius.chip))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        accent.copy(alpha = 0.9f),
                        secondary.copy(alpha = 0.75f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

/**
 * Микроподпись слайда: маленькая строка с возможной иконкой.
 *
 * Это единственное место онбординга, где допустима иконка: она несёт
 * фактическую информацию («речь о микрофоне», «речь о свете»), а не
 * украшает. Цвет — вторичный текст, размер — минимальный.
 */
@Composable
private fun CaptionRow(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (icon != null) {
            Box(modifier = Modifier.size(14.dp)) { icon() }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )
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

/** Слайд 0 — приветствие: имя и обещание. */
@Preview(name = "Onboarding · Welcome", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun OnboardingWelcomePreview() = OnboardingPreview(PAGE_WELCOME)

/** Слайд 1 — жест и приватность. */
@Preview(name = "Onboarding · Voice", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun OnboardingVoicePreview() = OnboardingPreview(PAGE_VOICE)

/** Слайд 2 — живой свет: текущая ступень показывается фактом. */
@Preview(name = "Onboarding · Light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun OnboardingLightPreview() = OnboardingPreview(PAGE_LIGHT)

/** Слайд 3 — финал: CTA «Начать». */
@Preview(name = "Onboarding · Finish", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun OnboardingFinishPreview() = OnboardingPreview(PAGE_FINISH)

/** Компактный экран — проверка, что слайды не обрезаются на 640dp. */
@Preview(name = "Onboarding · Compact", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun OnboardingCompactPreview() = OnboardingPreview(PAGE_LIGHT)

/** Вечерний свет — проверка, что тёплые палитры читаются так же спокойно. */
@Preview(name = "Onboarding · Evening", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun OnboardingEveningPreview() {
    AmaliaTheme(useBioTime = true, userHourOverride = 21.5f) {
        OnboardingPreview(PAGE_LIGHT)
    }
}
