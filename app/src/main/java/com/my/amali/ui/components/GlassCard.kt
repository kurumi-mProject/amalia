package com.my.amali.ui.components

import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * GlassCard — фирменная карточка Амалии в стиле Liquid Glass.
 *
 * На API 31+ внутренние декоративные блики размываются настоящим
 * RenderEffect.createBlurEffect, создавая живое стекло. На API < 31
 * используется корректный фолбэк: полупрозрачная заливка и мягкая
 * градиентная подсветка без blur.
 *
 * Карточка адаптируется под обе темы: в Liquid Glass она тёмная
 * полупрозрачная, в Биофильной — мягкая светлая с тёплым свечением.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val surface = MaterialTheme.colorScheme.surface
    val stroke = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val secondary = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surface.copy(alpha = 0.72f))
            .border(1.dp, stroke, shape),
    ) {
        // Декоративный слой бликов. На API 31+ он размывается —
        // создаёт эффект стекла за контентом.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = RenderEffect
                            .createBlurEffect(28f, 28f, Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-24).dp, y = (-30).dp)
                    .size(150.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(highlight, Color.Transparent),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 28.dp, y = 34.dp)
                    .size(130.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(secondary, Color.Transparent),
                        ),
                    ),
            )
        }
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}
