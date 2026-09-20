package com.my.amali.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.my.amali.ui.components.GhostButton
import com.my.amali.ui.components.GradientBackground
import com.my.amali.ui.components.PrimaryButton
import com.my.amali.ui.components.AmaliaWaveform
import com.my.amali.ui.theme.AmaliaTheme
import com.my.amali.ui.theme.AmaliaVisualTheme
import com.my.amali.ui.theme.AmaliaMotif
import com.my.amali.ui.theme.BioGradientDay
import com.my.amali.ui.theme.GlassGradientPalette
import com.my.amali.ui.theme.GradientPalette
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.accentGlow
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
 * Каждый слайд имеет один визуальный герой-объект и один смысл.
 * Переход между слайдами — параллакс: герой двигается быстрее текста,
 * из-за чего свайп ощущается объёмным, а не как смена картинок.
 * Внизу — индикатор-пилюля и главная кнопка, «Пропустить» доступна
 * всегда, но визуально тише главного действия.
 */
@Composable
fun OnboardingScreen(
    onNavigateToAssistant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: OnboardingViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { state.pageCount })

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
 * и вызов `viewModel()` роняет рендер с «No ViewModelStoreOwner was
 * provided via LocalViewModelStoreOwner». Пока состояние и действия
 * приходят параметрами, экран рисуется в превью без единого мока.
 *
 * @param state текущее состояние онбординга (страница, счётчик).
 * @param pagerState состояние пагера — принадлежит вызывающей стороне,
 *   потому что жесты пользователя и кнопки обязаны двигать один и тот же
 *   объект, а не две его копии.
 */
@Composable
fun OnboardingScreenContent(
    state: OnboardingState,
    pagerState: PagerState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Онбординг всегда в «стекле»: у пользователя ещё нет настроек,
        // а первое впечатление должно быть одним и тем же на любом устройстве.
        GradientBackground(
            modifier = Modifier.fillMaxSize(),
            palette = GlassGradientPalette,
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = (1f - offset.absoluteValue * 0.85f).coerceIn(0f, 1f)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    when (page) {
                        PAGE_WELCOME -> WelcomeSlide(offset)
                        PAGE_VOICE -> VoiceSlide(offset)
                        PAGE_CONTROL -> ControlSlide(offset)
                        PAGE_THEMES -> ThemesSlide(offset)
                        else -> FinishSlide(offset)
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
                onBack = onPrev,
                onNext = onNext,
                onFinish = onFinish,
            )
        }
    }
}

// === SLIDE 0: WELCOME =======================================================

@Composable
private fun WelcomeSlide(offset: Float) {
    SlideScaffold(
        offset = offset,
        title = stringResource(R.string.onboarding_welcome_title),
        description = stringResource(R.string.onboarding_welcome_desc),
    ) {
        val transition = rememberInfiniteTransition(label = "logoBreath")
        val breath by transition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                tween(3_600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breath",
        )
        Box(
            modifier = Modifier
                .size(168.dp)
                .scale(breath)
                .accentGlow(
                    color = MaterialTheme.colorScheme.primary,
                    alpha = 0.34f,
                    spread = 1.5f,
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                            Color.Transparent,
                        ),
                    ),
                )
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
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(58.dp),
            )
        }
    }
}

// === SLIDE 1: VOICE =========================================================

@Composable
private fun VoiceSlide(offset: Float) {
    SlideScaffold(
        offset = offset,
        title = stringResource(R.string.onboarding_voice_title),
        description = stringResource(R.string.onboarding_voice_desc),
    ) {
        // Живая волна прямо в онбординге: обещание = реальный UI.
        // Та же волна, что на главном экране, — не декоративная картинка.
        // Значит, и ведёт она себя так же: полосы идут по уровню и стоят
        // ровным строем, когда звука нет. Пользователь видит не «обещание
        // красоты», а ровно то, что получит в разговоре.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val onboardingWave = remember { WaveSettings() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                AmaliaWaveform(
                    level = 0.55f,
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
    }
}

// === SLIDE 2: CONTROL =======================================================

@Composable
private fun ControlSlide(offset: Float) {
    SlideScaffold(
        offset = offset,
        title = stringResource(R.string.onboarding_control_title),
        description = stringResource(R.string.onboarding_control_desc),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            controlIcons.forEach { control ->
                val label = stringResource(control.labelResId)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.semantics { contentDescription = label },
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
    }
}

// === SLIDE 3: THEMES ========================================================

@Composable
private fun ThemesSlide(offset: Float) {
    SlideScaffold(
        offset = offset,
        title = stringResource(R.string.onboarding_theme_title),
        description = stringResource(R.string.onboarding_theme_desc),
    ) {
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
                .height(128.dp)
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
                        2 -> 28.dp
                        1, 3 -> 19.dp
                        else -> 11.dp
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

// === SLIDE 4: FINISH ========================================================

@Composable
private fun FinishSlide(offset: Float) {
    SlideScaffold(
        offset = offset,
        title = stringResource(R.string.onboarding_welcome_title),
        description = stringResource(R.string.assistant_welcome_desc),
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .accentGlow(MaterialTheme.colorScheme.primary, alpha = 0.32f, spread = 1.5f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(58.dp),
            )
        }
    }
}

// === SHARED SLIDE LAYOUT ====================================================

/**
 * Каркас слайда: герой-объект + заголовок + описание.
 * Параллакс: герой смещается на 35% ширины, текст — на 12%.
 */
@Composable
private fun SlideScaffold(
    offset: Float,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    hero: @Composable () -> Unit,
) {
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
            hero()
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
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// === PAGE INDICATOR =========================================================

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

// === BOTTOM CONTROLS ========================================================

@Composable
private fun BottomControls(
    currentPage: Int,
    pageCount: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLast = currentPage == pageCount - 1
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        AnimatedVisibility(
            visible = currentPage > 0,
            enter = fadeIn(tween(200)) + slideInHorizontally { -it / 2 },
            exit = fadeOut(tween(160)) + slideOutHorizontally { -it / 2 },
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

// === PREVIEW ================================================================
//
//  Превью собирают [OnboardingScreenContent] — чистое представление слайдов.
//  Раньше превью звало OnboardingScreen(), который поднимает ViewModel, и
//  падало в Android Studio («No ViewModelStoreOwner…»): посмотреть макет
//  было нельзя вообще. Теперь каждый слайд можно открыть отдельным превью
//  и увидеть его ровно таким, каким он будет на устройстве.

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

/** Слайд 2 — управление устройством: сетка иконок. */
@Preview(name = "Onboarding · Control", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun OnboardingControlPreview() = OnboardingPreview(PAGE_CONTROL)

/** Слайд 3 — темы: сравнение палитр. */
@Preview(name = "Onboarding · Themes", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun OnboardingThemesPreview() = OnboardingPreview(PAGE_THEMES)

/** Слайд 4 — финал: жирный CTA «Начать». */
@Preview(name = "Onboarding · Finish", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun OnboardingFinishPreview() = OnboardingPreview(PAGE_FINISH)

/** Компактный экран — проверка, что слайды не обрезаются на 640dp. */
@Preview(name = "Onboarding · Compact", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun OnboardingCompactPreview() = OnboardingPreview(PAGE_CONTROL)
