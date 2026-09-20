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
import kotlin.math.abs
import kotlin.math.roundToLong

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
 *
 * Спад при этом идёт быстрее подъёма (см. [smoothingStep] в `AmaliaWaveform`): пока звук есть,
 * волна должна успевать за ним точно, а когда он оборвался — садиться без
 * хвоста, но не рывком. Это разделение и есть ответ на требование «спад
 * быстрый, но плавный, не мгновенный и не медленный».
 *
 * ════════════════════════════════════════════════════════════════════════
 *  ЖДЁМ МЕДИАНУ, А НЕ ЕДИНИЧНЫЙ ВСПЛЕСК
 * ════════════════════════════════════════════════════════════════════════
 *
 * Одиночный чанк может прийти с громкостью 0.9 просто потому, что рядом
 * хлопнули дверью. Гнать эту цифру прямо в волну — значит показать то,
 * чего в речи не было. Поэтому цель вычисляется как **медиана последних
 * [LevelWindow] значений**: она устойчива к одиночным выбросам и при этом
 * точно описывает установившийся уровень — в отличие от скользящего
 * среднего, которое «размазывает» согласные.
 *
 * Окно живёт в изменяемом состоянии и обновляется в [LaunchedEffect] на
 * каждый новый уровень. Это не костыль: медиана — функция от истории, а
 * история по определению не может быть вычислена из одного текущего кадра.
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
        // весь компонент, а не по одной на элемент внутри. Это единственная
        // бесконечная анимация, которая осталась здесь: она принадлежит
        // микрофону — элементу покоя, — а не волне, которая обязана молчать
        // вместе со звуком.
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
 * Плавно догоняет пришедший уровень громкости и держит строй в тишине.
 *
 * ## Почему [Animatable], а не пересчёт в композиции
 *
 * Значение обновляется эффектом при изменении [level]. Если бы уровень
 * пересчитывался прямо в теле composable, каждый кадр громкости тянул бы
 * за собой рекомпозицию всего поддерева волны. [Animatable] живёт в
 * состоянии анимации и обновляется вне композиции: Canvas перерисовывается,
 * но не пересобирается.
 *
 * ## Почему длительность считается от расстояния
 *
 * Фиксированное время для всех переходов давало бы одинаково вялый отклик
 * на тихий слог и на громкий вскрик. Здесь длительность пропорциональна
 * расстоянию до цели: резкий хлопок доходит быстро, тихая речь — мягко.
 * Это поведение аналогового индикатора, и оно читается естественнее
 * постоянного времени анимации.
 *
 * ## Почему спад короче подъёма
 *
 * При базовом сглаживании 0.22 полное падение занимает 119 мс против
 * 310 мс подъёма, а малый скачок между слогами — 52 мс против 135. Это и
 * есть требуемое поведение: волна успевает прорисовать каждое слово, но
 * после него не оставляет затухающего хвоста. Мгновенным такой спад не
 * выглядит (порог, после которого изменение читается как скачок, — 100 мс),
 * а медленным уж точно: 119 мс на полный ход полосы.
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

    // Медианное окно. Хранится в обычном состоянии: пять чисел, пересборка
    // массива дешевле, чем ловить мутации в общем буфере. Значение окна
    // намеренно НЕ участвует в ключах эффектов — оно и есть их результат.
    var window by remember { mutableStateOf(FloatArray(LevelWindow)) }

    LaunchedEffect(enabled) {
        // При выключении волна обязана уйти в ноль, иначе последняя высота
        // «застынет» и при следующем включении полосы прыгнут.
        if (!enabled) {
            window = FloatArray(LevelWindow)
            animator.snapTo(0f)
        }
    }

    // Ключ — сам уровень и признак включённости. Пока уровень не менялся,
    // эффект не перезапускается, и анимация успевает доиграть до цели: иначе
    // частые рекомпозиции отрывали бы полосы от значения, к которому они идут.
    LaunchedEffect(target, enabled) {
        if (!enabled) return@LaunchedEffect
        window = (window + target).takeLast(LevelWindow).toFloatArray()
        val stable = median(window)
        val distance = abs(stable - animator.value)
        // Направление берётся по разнице цели и текущего значения: спад и
        // подъём имеют разную скорость, и определять направление по знаку
        // производной уровня ненадёжно — цель уже прошла через медиану.
        val rising = stable >= animator.value
        val baseMs = MinDurationMs + (1f - smoothing) * SpanMs * distance.coerceAtMost(1f)
        // Спад короче подъёма ровно во столько раз, во сколько различаются
        // шаги сглаживания (ожидаемое значение — DecayShare из 2.6 с
        // ослаблением к краям диапазона настроек).
        val decayRatio = smoothingStep(smoothing, rising = false) /
            smoothingStep(smoothing, rising = true)
        val durationMs = (if (rising) baseMs else baseMs / decayRatio)
            .roundToLong()
            .coerceIn(MinDurationMs / 2, MaxDurationMs)
        animator.animateTo(
            targetValue = stable,
            animationSpec = tween(durationMillis = durationMs.toInt(), easing = LinearEasing),
        )
    }

    return animator.value
}

/** Медиана окна уровней: устойчива к одиночным всплескам, в отличие от среднего. */
private fun median(values: FloatArray): Float {
    if (values.isEmpty()) return 0f
    val sorted = values.clone()
    sorted.sort()
    return sorted[sorted.size / 2]
}

/** Размер окна медианы: пять чанков ≈ 100 мс истории при кадре 20 мс. */
private const val LevelWindow = 5

/** Нижняя граница длительности анимации уровня, миллисекунды. */
private const val MinDurationMs = 60L

/** Разброс длительности поверх минимума: 320 мс, уменьшается сглаживанием. */
private const val SpanMs = 320f

/** Верхняя граница длительности: дольше — уже не реакция, а инерция. */
private const val MaxDurationMs = 420L

/**
 * Оператор для одновременного описания входа и выхода в [AnimatedContent].
 *
 * Локальное определение вместо импорта: в проекте он больше нигде не нужен,
 * а тащить расширение в общий файл ради одного места — лишняя сущность.
 */
private infix fun EnterTransition.togetherWith(exit: ExitTransition): ContentTransform =
    ContentTransform(targetContentEnter = this, initialContentExit = exit)
