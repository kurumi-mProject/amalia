package com.my.amali.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.my.amali.ui.components.AmaliaButton
import com.my.amali.ui.components.GlassCard
import com.my.amali.ui.components.GradientBackground
import com.my.amali.ui.theme.AmaliaTheme
import com.my.amali.ui.theme.BioGradientMorning
import com.my.amali.ui.theme.GlassGradientPalette
import com.my.amali.ui.theme.GradientPalette
import kotlin.math.abs

/** Pager index of the "Welcome" slide. */
private const val PAGE_WELCOME = 0
/** Pager index of the "Voice" slide. */
private const val PAGE_VOICE = 1
/** Pager index of the "Control" slide. */
private const val PAGE_CONTROL = 2
/** Pager index of the "Themes" slide. */
private const val PAGE_THEMES = 3

/** Icon + label pair rendered as a badge on the "Device control" slide. */
private data class ControlIcon(val icon: ImageVector, val labelResId: Int)

private val controlIcons = listOf(
    ControlIcon(Icons.Outlined.Wifi, R.string.device_wifi),
    ControlIcon(Icons.Outlined.Bluetooth, R.string.device_bluetooth),
    ControlIcon(Icons.Outlined.Brightness6, R.string.device_brightness),
    ControlIcon(Icons.Outlined.VolumeUp, R.string.device_volume),
)

/**
 * Full-screen onboarding flow: 5 slides in a [HorizontalPager] with animated
 * transitions, page indicator dots and navigation controls. When the flow is
 * finished (or skipped) [onNavigateToAssistant] is invoked.
 */
@Composable
fun OnboardingScreen(
    onNavigateToAssistant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: OnboardingViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { state.pageCount })

    // VM -> Pager: animate the pager when navigation buttons change the page.
    LaunchedEffect(state.currentPage) {
        if (pagerState.currentPage != state.currentPage) {
            pagerState.animateScrollToPage(state.currentPage)
        }
    }
    // Pager -> VM: keep the state in sync with manual swipes.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page -> viewModel.onPageChanged(page) }
    }
    // Leave onboarding once it is finished (Get started or Skip).
    LaunchedEffect(state.isFinished) {
        if (state.isFinished) onNavigateToAssistant()
    }

    Box(modifier = modifier.fillMaxSize()) {
        GradientBackground(
            palette = GlassGradientPalette,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // === TOP BAR: Skip on the right ===
            Box(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = viewModel::finish,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 8.dp),
                ) {
                    Text(text = stringResource(R.string.onboarding_skip))
                }
            }

            // === PAGER ===
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                val pageOffset =
                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Fade + slide transition between slides.
                            alpha = (1f - abs(pageOffset)).coerceIn(0.2f, 1f)
                            translationX = pageOffset * size.width * 0.25f
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    when (page) {
                        PAGE_WELCOME -> WelcomeSlide()
                        PAGE_VOICE -> VoiceSlide()
                        PAGE_CONTROL -> ControlSlide()
                        PAGE_THEMES -> ThemesSlide()
                        else -> FinishSlide(onGetStarted = viewModel::finish)
                    }
                }
            }

            // === PAGE INDICATOR ===
            PageIndicator(
                pageCount = state.pageCount,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 20.dp),
            )

            // === BOTTOM CONTROLS ===
            BottomControls(
                currentPage = pagerState.currentPage,
                pageCount = state.pageCount,
                onBack = viewModel::prevPage,
                onNext = viewModel::nextPage,
                onFinish = viewModel::finish,
            )
        }
    }
}

// === SLIDE 0: WELCOME =======================================================

@Composable
private fun WelcomeSlide(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "logoPulse")
        val pulse by infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "logoPulseScale",
        )
        Box(
            modifier = Modifier
                .size(148.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                    ),
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        ),
                    ),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// === SLIDE 1: VOICE =========================================================

@Composable
private fun VoiceSlide(modifier: Modifier = Modifier) {
    SlideLayout(
        modifier = modifier,
        title = stringResource(R.string.onboarding_voice_title),
        description = stringResource(R.string.onboarding_voice_desc),
    ) {
        IconBadge(
            icon = Icons.Filled.Mic,
            contentDescription = stringResource(R.string.onboarding_voice_title),
            containerSize = 120.dp,
            iconSize = 48.dp,
        )
    }
}

// === SLIDE 2: CONTROL =======================================================

@Composable
private fun ControlSlide(modifier: Modifier = Modifier) {
    SlideLayout(
        modifier = modifier,
        title = stringResource(R.string.onboarding_control_title),
        description = stringResource(R.string.onboarding_control_desc),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            controlIcons.forEach { control ->
                IconBadge(
                    icon = control.icon,
                    contentDescription = stringResource(control.labelResId),
                    containerSize = 64.dp,
                    iconSize = 28.dp,
                )
            }
        }
    }
}

// === SLIDE 3: THEMES ========================================================

@Composable
private fun ThemesSlide(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ThemePreviewCard(
                palette = GlassGradientPalette,
                label = stringResource(R.string.appearance_theme_glass),
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            ThemePreviewCard(
                palette = BioGradientMorning,
                label = stringResource(R.string.appearance_theme_bio),
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.onboarding_theme_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_theme_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ThemePreviewCard(
    palette: GradientPalette,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = palette.stops
                                .map { it.position to it.color }
                                .toTypedArray(),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == 1) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = if (index == 1) 1f else 0.45f)),
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
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
    }
}

// === SLIDE 4: FINISH ========================================================

@Composable
private fun FinishSlide(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        AmaliaButton(
            text = stringResource(R.string.onboarding_start),
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        )
    }
}

// === SHARED SLIDE SCAFFOLD ==================================================

@Composable
private fun SlideLayout(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(32.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun IconBadge(
    icon: ImageVector,
    contentDescription: String,
    containerSize: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(containerSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                shape = CircleShape,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize),
        )
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            val width by animateDpAsState(
                targetValue = if (isActive) 22.dp else 7.dp,
                animationSpec = tween(durationMillis = 250),
                label = "dotWidth$index",
            )
            val color by animateColorAsState(
                targetValue = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
                animationSpec = tween(durationMillis = 250),
                label = "dotColor$index",
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(7.dp)
                    .clip(CircleShape)
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
    val isLastPage = currentPage == pageCount - 1
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = currentPage > 0,
            enter = fadeIn(tween(durationMillis = 200)) +
                    slideInHorizontally(initialOffsetX = { -it / 2 }),
            exit = fadeOut(tween(durationMillis = 200)) +
                    slideOutHorizontally(targetOffsetX = { -it / 2 }),
        ) {
            TextButton(onClick = onBack) {
                Text(text = stringResource(R.string.onboarding_back))
            }
        }
        Spacer(Modifier.weight(1f))
        AmaliaButton(
            text = stringResource(
                if (isLastPage) R.string.onboarding_start else R.string.onboarding_next
            ),
            onClick = if (isLastPage) onFinish else onNext,
            modifier = Modifier.height(48.dp),
        )
    }
}

// === PREVIEW ================================================================

@Preview(showBackground = true, backgroundColor = 0xFF0B0B0F)
@Composable
private fun OnboardingScreenPreview() {
    AmaliaTheme {
        OnboardingScreen(onNavigateToAssistant = {})
    }
}
