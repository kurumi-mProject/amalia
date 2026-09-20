package com.my.amali.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.my.amali.ui.theme.LocalAmaliaPalette
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.amaliaShadow
import com.my.amali.ui.theme.glassSurface
import kotlinx.coroutines.delay

/**
 * ════════════════════════════════════════════════════════════════════════
 *  CircadianLamp — «какой сейчас свет» одной точкой
 * ════════════════════════════════════════════════════════════════════════
 *
 * ## Задача
 *
 * Пользователь должен видеть, что интерфейс живёт по времени суток — и не
 * должен читать об этом абзац. Значит, нужен ровно один визуальный якорь:
 * **точка**, цвет которой и есть ответ.
 *
 * ## Почему точка, а не глиф лампы
 *
 * Первая версия ставила в пилюлю иконку лампы слева от текста. В свёрнутом
 * виде это давало две проблемы сразу: глиф прижимался к левому краю широкой
 * пилюли (то есть стоял не по центру), а сам силуэт лампы в 15dp читался
 * как случайное пятно. Точка решает обе: она центрируется тривиально,
 * а её смысл — не «лампа», а **цвет света**, то есть ровно то, что нужно
 * сообщить.
 *
 * ## Почему цвет непрерывный, а не «тёплый/холодный»
 *
 * Раньше выбор был бинарным: `if (palette.isWarm) auroras.first() else
 * auroras.last()`. Это давало два состояния на сутки, и переход между ними
 * происходил скачком в тот момент, когда CCT пересекала порог 3400 K.
 *
 * Здесь цвет собирается **из самой температуры**: две опорные точки палитры
 * времени суток смешиваются по положению текущей CCT внутри диапазона
 * `CCT_NIGHT..CCT_NOON`. Градиент получается непрерывным, потому что и
 * CCT меняется непрерывно — от 2000 K глубокой ночью до 5600 K в полдень.
 * Точка в 05:40, 07:10 и 19:30 будет **трёх разных цветов**, а не двух.
 *
 * ## Поведение
 *
 *  1. при появлении экрана точка сама раскрывается один раз — иначе
 *     пользователь не знает, что внутри есть текст;
 *  2. тап раскрывает немедленно, повторный тап сворачивает сразу;
 *  3. раскрытая точка через [AutoCollapseMs] сворачивается сама и **тем же
 *     путём назад**.
 *
 * @param label словесная характеристика света («Закат», «Тёплая лампа»).
 * @param cct цветовая температура в кельвинах.
 * @param autoExpandOnStart раскрыть один раз при появлении экрана.
 */
@Composable
fun CircadianLamp(
    label: String,
    cct: Int,
    modifier: Modifier = Modifier,
    autoExpandOnStart: Boolean = true,
) {
    val kelvinPart = "$cct K"
    val fullText = "$label · $kelvinPart"

    var expanded by remember(label) { mutableStateOf(false) }

    // Первое появление экрана: показать себя один раз, затем убраться.
    LaunchedEffect(label, autoExpandOnStart) {
        if (autoExpandOnStart) {
            expanded = true
            delay(AutoCollapseMs)
            expanded = false
        }
    }

    // Тап: раскрыть и через паузу свернуть. Повторный тап сворачивает сразу.
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(AutoCollapseMs)
            expanded = false
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "lampPress",
    )

    val palette = LocalAmaliaPalette.current
    val dotColor = circadianDotColor(cct = cct, warm = palette.auroras.firstOrNull(), cool = palette.auroras.lastOrNull())

    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .scale(pressScale)
            .amaliaShadow(
                elevation = if (expanded) 0.20f else 0.08f,
                shape = RoundedCornerShape(Radius.chip),
            )
            .glassSurface(
                shape = RoundedCornerShape(Radius.chip),
                fillAlpha = if (expanded) 0.56f else 0.42f,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { expanded = !expanded },
            )
            // Горизонтальные отступы подобраны так, чтобы **точка стояла
            // ровно по центру пилюли в свёрнутом виде**: 12dp симметрично
            // вокруг 12dp точки дают круглую пилюлю 36×36, а не вытянутую.
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics {
                role = Role.Button
                contentDescription = "$label, $kelvinPart"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // Точка света. Ореол рисуется тем же слоем и мягко уходит в ноль
        // к своим краям: так он не расширяет занимаемое место, и пилюля не
        // «дышит» размером при смене цвета.
        Box(
            modifier = Modifier
                .size(DotSize)
                .drawBehind {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val halo = radius * 2.4f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                dotColor.copy(alpha = 0.42f),
                                dotColor.copy(alpha = 0.16f),
                                dotColor.copy(alpha = 0f),
                            ),
                            center = center,
                            radius = halo,
                        ),
                        radius = halo,
                        center = center,
                    )
                    drawCircle(color = dotColor, radius = radius)
                },
        )

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(220, delayMillis = 60)) +
                expandHorizontally(
                    animationSpec = tween(260),
                    expandFrom = Alignment.Start,
                ),
            exit = fadeOut(tween(140)) +
                shrinkHorizontally(
                    animationSpec = tween(200),
                    shrinkTowards = Alignment.Start,
                ),
        ) {
            Text(
                text = fullText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/**
 * Цвет точки: смешение двух опор палитры по положению CCT в сутках.
 *
 * ## Почему линейная интерполяция, а не таблица
 *
 * Таблица «фаза → цвет» была бы списком из шести значений, между которыми
 * всё равно пришлось бы интерполировать. Здесь интерполяция первична: CCT
 * уже непрерывен (см. `CircadianEngine.cctFor`), поэтому достаточно
 * отобразить его диапазон на отрезок [0, 1] и смешать два цвета. Ни одной
 * точки перелома — значит, ни одного скачка цвета за сутки.
 *
 * @param cct текущая температура света.
 * @param warm опорный тёплый цвет палитры (ночная сторона).
 * @param cool опорный холодный цвет палитры (дневная сторона).
 */
private fun circadianDotColor(cct: Int, warm: Color?, cool: Color?): Color {
    val warmColor = warm ?: FallbackWarm
    val coolColor = cool ?: FallbackCool

    // Положение текущей температуры внутри суточного диапазона.
    val span = (CCT_NOON - CCT_NIGHT).toFloat().coerceAtLeast(1f)
    val t = ((cct - CCT_NIGHT).toFloat() / span).coerceIn(0f, 1f)

    // Смешиваем в два шага, добавляя промежуточный янтарный оттенок: прямая
    // интерполяция «оранжевый → голубой» проходит через мутно-серый, и в
    // середине дня точка выглядела бы грязной. Промежуточная опора держит
    // насыщенность на всём пути.
    return if (t < 0.5f) {
        lerpColor(warmColor, MidAmber, t * 2f)
    } else {
        lerpColor(MidAmber, coolColor, (t - 0.5f) * 2f)
    }
}

/** Линейное смешение двух цветов, [t] от 0 до 1. */
private fun lerpColor(from: Color, to: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * k,
        green = from.green + (to.green - from.green) * k,
        blue = from.blue + (to.blue - from.blue) * k,
        alpha = from.alpha + (to.alpha - from.alpha) * k,
    )
}

/** Размер самой точки света. */
private val DotSize = 12.dp

/** Сколько держать подпись раскрытой после тапа, миллисекунды. */
private const val AutoCollapseMs = 6_000L

/** Границы суточного диапазона температур — те же, что в `CircadianEngine`. */
private const val CCT_NIGHT = 2000
private const val CCT_NOON = 5600

/** Запасные цвета на случай, если палитра ещё не готова. */
private val FallbackWarm = Color(0xFFE8A65C)
private val FallbackCool = Color(0xFF9CC4F0)

/** Промежуточная опора: янтарь золотого часа. */
private val MidAmber = Color(0xFFF0C070)
