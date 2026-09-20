package com.my.amali.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.my.amali.domain.entity.WaveSettings
import com.my.amali.ui.icons.AmaliaMic
import kotlin.math.abs

/**
 * Главный элемент экрана: микрофон в покое, волна в разговоре.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ПОЧЕМУ ДВА СОСТОЯНИЯ, А НЕ ОДНО
 * ════════════════════════════════════════════════════════════════════════
 *
 * Микрофон и волна говорят разное, и именно поэтому они не могут быть одним
 * объектом:
 *
 *  — **Микрофон** — это обещание: «нажми, я услышу». Он не меняется, пока
 *    ничего не происходит, и это правильно: элемент покоя не должен
 *    двигаться, иначе взгляд не находит точку опоры.
 *  — **Волна** — это свидетельство: «я тебя слышу прямо сейчас». Она
 *    существует только пока идёт звук — твой или Амалии.
 *
 * Переход между ними — [AnimatedContent] с перекрёстным затуханием и
 * масштабом. Микрофон не «превращается» в волну морфингом формы: это
 * разные сущности, и честнее показать смену, чем имитировать метаморфозу.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ГДЕ ЖИВЁТ ГРОМКОСТЬ
 * ════════════════════════════════════════════════════════════════════════
 *
 * [level] приходит из одного поля состояния, и это не упрощение, а гарантия
 * синхронности: в фазе слушания ViewModel кладёт туда уровень из VAD
 * (`AiResponse.Level`), в фазе ответа — уровень настоящего PCM, который
 * играет `AudioPlayer`. Волна не знает и не должна знать, чей это голос:
 * она показывает звук.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ЖИВАЯ РЕАКЦИЯ БЕЗ «ТЕЛЕГРАФА»
 * ════════════════════════════════════════════════════════════════════════
 *
 * Уровень громкости приходит **пачками**: VAD отдаёт его по кадрам записи,
 * плеер — по аудио-чанкам. Между ними есть промежутки, и если рисовать
 * напрямую, волна дёргалась бы. Поэтому значение проходит через
 * [rememberSmoothedLevel]: оно плавно догоняет цель со скоростью из
 * настроек, и только потом попадает в отрисовку.
 */
@Composable
fun AmaliaVoiceVisual(
    enabled: Boolean,
    level: Float,
    settings: WaveSettings,
    color: Color,
    modifier: Modifier = Modifier,
    micSize: Dp = 30.dp,
    contentDescription: String? = null,
) {
    val smoothed = rememberSmoothedLevel(level = level, enabled = enabled, settings = settings)

    Box(
        modifier = modifier.semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        },
        contentAlignment = Alignment.Center,
    ) {
        // Дыхание в покое: микрофон живёт, но не отвлекает. Одна анимация на
        // весь компонент, а не по одной на элемент внутри.
        val idle = rememberInfiniteTransition(label = "voiceIdle")
        val idlePulse by idle.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2_800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "voiceIdlePulse",
        )

        AnimatedContent(
            targetState = enabled,
            transitionSpec = {
                // Включение: микрофон растворяется, волна приходит снизу —
                // читается как «начал говорить», а не как перерисовка.
                (fadeIn(tween(260)) + scaleIn(initialScale = 0.86f, animationSpec = tween(300)))
                    .togetherWith(
                        fadeOut(tween(180)) + scaleOut(targetScale = 0.92f, animationSpec = tween(200)),
                    )
            },
            label = "voiceVisual",
        ) { active ->
            if (active) {
                AmaliaWaveform(
                    level = smoothed,
                    settings = settings,
                    color = color,
                    isActive = true,
                )
            } else {
                Icon(
                    imageVector = AmaliaMic,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier
                        .size(micSize)
                        .scale(idlePulse),
                )
            }
        }
    }
}

/**
 * Плавно догоняет пришедший уровень громкости.
 *
 * Реализовано через [Animatable]: значение обновляется эффектом при
 * изменении [level], а не пересчитывается на каждом кадре рекомпозиции.
 * Длительность пропорциональна расстоянию до цели — резкий хлопок доходит
 * быстро, тихая речь плавно; это поведение аналогового индикатора, и оно
 * читается естественнее фиксированного времени анимации.
 */
@Composable
private fun rememberSmoothedLevel(
    level: Float,
    enabled: Boolean,
    settings: WaveSettings,
): Float {
    val target by rememberUpdatedState(level.coerceIn(0f, 1f))
    val animator = remember { Animatable(0f) }
    val smoothing by rememberUpdatedState(settings.smoothing)

    LaunchedEffect(enabled) {
        // При выключении волна обязана уйти в ноль, иначе последняя высота
        // «застынет» и при следующем включении полосы прыгнут.
        if (!enabled) animator.snapTo(0f)
    }

    LaunchedEffect(target, smoothing, enabled) {
        if (!enabled) return@LaunchedEffect
        val distance = abs(target - animator.value)
        val durationMs = (60 + (1f - smoothing) * 320f * distance.coerceAtMost(1f))
            .toInt()
            .coerceIn(40, 420)
        animator.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = durationMs, easing = LinearEasing),
        )
    }

    return animator.value
}

/**
 * Оператор для одновременного описания входа и выхода в [AnimatedContent].
 *
 * Локальное определение вместо импорта: в проекте он больше нигде не нужен,
 * а тащить расширение в общий файл ради одного места — лишняя сущность.
 */
private infix fun EnterTransition.togetherWith(exit: ExitTransition): ContentTransform =
    ContentTransform(targetContentEnter = this, initialContentExit = exit)
