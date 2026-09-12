package com.my.amali.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.my.amali.ui.theme.Radius
import com.my.amali.ui.theme.Spacing
import com.my.amali.ui.theme.accentGlow

/**
 * GlassSlider — параметр-регулятор в стеклянной карточке.
 *
 * Дизайн: подпись слева, текущее значение справа акцентом, ниже трек.
 * Трек — тонкий (4dp) с акцентным градиентом активной части; бегунок —
 * стеклянный круг 22dp со свечением, что делает попадание пальцем
 * очевидным и визуально связывает регулятор с остальным интерфейсом.
 *
 * @param label название параметра.
 * @param valueText человекочитаемое текущее значение («×1.2», «68%»).
 * @param value текущее значение в [valueRange].
 * @param onValueChange вызывается при перетаскивании.
 * @param onValueChangeFinished вызывается один раз по отпусканию —
 *   именно здесь следует применять значение к системе.
 */
@Composable
fun GlassSlider(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    description: String? = null,
    enabled: Boolean = true,
) {
    val accent = MaterialTheme.colorScheme.primary

    GlassCard(modifier = modifier, cornerRadius = Radius.md) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .background(
                        color = accent.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(Radius.chip),
                    )
                    .padding(horizontal = Spacing.xs, vertical = 3.dp),
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                )
            }
        }

        Spacer(Modifier.height(Spacing.xs))

        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .accentGlow(color = accent, alpha = 0.45f, spread = 1.7f)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.secondary,
                                    accent,
                                ),
                            ),
                            shape = CircleShape,
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.42f),
                            shape = CircleShape,
                        )
                        .semantics { contentDescription = "$label: $valueText" },
                )
            },
            track = { sliderState ->
                val fraction = if (sliderState.valueRange.endInclusive ==
                    sliderState.valueRange.start
                ) {
                    0f
                } else {
                    ((sliderState.value - sliderState.valueRange.start) /
                        (sliderState.valueRange.endInclusive - sliderState.valueRange.start))
                        .coerceIn(0f, 1f)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(Radius.chip),
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        accent.copy(alpha = 0.65f),
                                        MaterialTheme.colorScheme.secondary,
                                    ),
                                ),
                                shape = RoundedCornerShape(Radius.chip),
                            ),
                    )
                }
            },
        )
    }
}
