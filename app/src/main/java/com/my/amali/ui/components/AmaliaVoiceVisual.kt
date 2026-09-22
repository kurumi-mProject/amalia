package com.my.amali.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.runtime.withFrameNanos

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
 * [rememberSmoothedLevel] — покадровый экспоненциальный сглаживатель,
 * скорость которого задаёт ползунок «сглаживание» из настроек.
 *
 * Задержка всей цепочки — 2–3 кадра (35–50 мс): полосы двигаются **вместе
 * с голосом**, а не через полсекунды после него. Спад при этом идёт быстрее
 * подъёма (см. [smoothingStep]): после замолкания волна садится без хвоста.
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ЖДЁМ МЕДИАНУ, А НЕ ЕДИНИЧНЫЙ ВСПЛЕСК
 * ════════════════════════════════════════════════════════════════════════
 *
 * Одиночный чанк может прийти с громкостью 0.9 просто потому, что рядом
 * хлопнули дверью. Гнать эту цифру прямо в волну — значит показать то,
 * чего в речи не было. Поэтому цель вычисляется как **медиана последних
 * [LevelWindow] значений**: она устойчива к одиночным выбросам и при этом
 * почти не добавляет задержки — окно в три значения при кадре записи
 * 20–60 мс означает 1–1.5 кадра ожидания, то есть 20–90 мс, что глаз не
 * отличает от «сразу».
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
        AnimatedContent(
            targetState = enabled,
            transitionSpec = {
                // Оба направления — длинное мягкое перекрытие: микрофон не
                // «исчезает под волну», а тает, волна не «выпрыгивает»,
                // а проявляется. Масштаб близок к единице (в покое микрофон
                // дышит в пределах ±4% — прежний 0.94 давал рывок размера
                // на ровном месте), easing — стандартный материаловский:
                // он замедляется в конце, и вход читается как дыхание.
                (fadeIn(tween(340, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = 0.96f, animationSpec = tween(380, easing = FastOutSlowInEasing)))
                    .togetherWith(
                        fadeOut(tween(260, easing = FastOutSlowInEasing)) +
                            scaleOut(targetScale = 0.96f, animationSpec = tween(300, easing = FastOutSlowInEasing)),
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
                // Дыхание живёт только в покое: пока идёт звук, пульс
                // микрофона никто не видит, а бесконечная анимация,
                // которую никто не смотрит, — это кадры, снятые с волны.
                // Композируется только здесь — и умирает вместе с веткой.
                val idle = rememberInfiniteTransition(label = "voiceIdle")
                val idlePulse by idle.animateFloat(
                    initialValue = 0.955f,
                    targetValue = 1.035f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = IdleBreathMs, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "voiceIdlePulse",
                )
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

/** Период дыхания микрофона — тот же такт, что у фона и черты под фразой. */
private const val IdleBreathMs = 10_000

/**
 * Плавно догоняет пришедший уровень громкости и держит строй в тишине.
 *
 * ## Архитектура: кадровый цикл + [Animatable.snapTo]
 *
 * Один [androidx.compose.runtime.withFrameNanos]-цикл — единственный источник
 * движения. На каждом кадре он читает свежую медиану окна (окно пополняется
 * отдельным эффектом при приходе уровня) и делает **один шаг**
 * экспоненциального приближения к цели через [smoothingForFrame].
 *
 * Почему не `tween`-пересчёты на каждое обновление уровня, как раньше:
 * каждая смена уровня отменяла недоигранную анимацию и заводила новую,
 * с длительностью, пересчитанной от расстояния. Сумма этих «незавершений»
 * и медианного окна давала на экране ту самую задержку в полсекунды, за
 * которую волну справедливо браковали. Шаг за кадр не накапливает ничего:
 * каждый кадр независимо подтягивает значение к цели — и волна отвечает
 * за 2–3 кадра.
 *
 * ## Почему спад короче подъёма
 *
 * [smoothingForFrame] умножает шаг сглаживания на [DecayShare] при спаде:
 * звук кончился — волна садится почти сразу, но по кривой, а не рывком.
 * Это то же правило, что проверяют инварианты волны.
 */
@Composable
private fun rememberSmoothedLevel(
    level: Float,
    enabled: Boolean,
    settings: WaveSettings,
): Float {
    val target by rememberUpdatedState(level.coerceIn(0f, 1f))
    val smoothing by rememberUpdatedState(settings.smoothing)
    val animator = remember { Animatable(0f) }

    // Медианное окно. Хранится в обычном состоянии: три числа, пересборка
    // массива дешевле, чем ловить мутации в общем буфере. Пополняется
    // эффектом ниже — на каждый ПРИХОДЯЩИЙ уровень, а не на каждый кадр.
    var window by remember { mutableStateOf(FloatArray(LevelWindow)) }

    // Приход уровня: окно пополняется сразу, чтобы кадровый цикл ниже
    // читал самую свежую медиану. Отдельный эффект, а не строка в кадровом
    // цикле: уровни приходят в своём темпе (20–60 мс), кадры — в своём (16 мс),
    // и смешивать эти темпы значило бы терять уровни между кадрами.
    LaunchedEffect(target) {
        window = (window + target).takeLast(LevelWindow).toFloatArray()
    }

    // Кадровый цикл. Живёт, пока волна включена; каждый кадр делает один
    // шаг к медиане окна. Длительность шага — из [smoothingForFrame], то
    // есть из ползунка настроек: 0.6 — отклик за 2–3 кадра, 0.05 — текучая
    // лава. Реальное время между кадрами измеряется и передаётся в шаг,
    // поэтому на 120 Гц волна не «двигается вдвое быстрее», чем на 60.
    LaunchedEffect(enabled) {
        if (!enabled) {
            window = FloatArray(LevelWindow)
            animator.snapTo(0f)
            return@LaunchedEffect
        }
        var lastFrame = withFrameNanos { it }
        while (true) {
            // withFrameNanos даёт только метку времени: его колбэк — НЕ
            // suspend-контекст, и звать snapTo внутри него нельзя. Поэтому
            // кадр сначала «считывается», потом — снаружи колбэка — шаг.
            val now = withFrameNanos { it }
            val delta = ((now - lastFrame).coerceAtLeast(1L)) / 1_000_000_000f
            lastFrame = now
            val stable = median(window)
            val current = animator.value
            val rising = stable >= current
            val step = smoothingForFrame(smoothing, delta, rising)
            val next = current + (stable - current) * step
            if (next != current) animator.snapTo(next)
        }
    }

    return animator.value
}

/** Размер окна медианы: три чанка гасят одиночный выброс почти без задержки. */
private const val LevelWindow = 3

/** Медиана окна уровней: устойчива к одиночным всплескам, в отличие от среднего. */
private fun median(values: FloatArray): Float {
    if (values.isEmpty()) return 0f
    val sorted = values.clone()
    sorted.sort()
    return sorted[sorted.size / 2]
}

/**
 * Оператор для одновременного описания входа и выхода в [AnimatedContent].
 *
 * Локальное определение вместо импорта: в проекте он больше нигде не нужен,
 * а тащить расширение в общий файл ради одного места — лишняя сущность.
 */
private infix fun EnterTransition.togetherWith(exit: ExitTransition): ContentTransform =
    ContentTransform(targetContentEnter = this, initialContentExit = exit)
