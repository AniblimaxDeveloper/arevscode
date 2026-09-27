package com.arevscode.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme

@Composable
fun AvescodeAnimatedBackdrop() {
    val transition = rememberInfiniteTransition(label = "avescode-rgb")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rgb-shift"
    )

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        drawRect(
            color = Color(0xFF06080D)
        )

        val w = size.width
        val h = size.height

        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = 0.08f),
            radius = w * 0.58f,
            center = Offset(
                x = w * (0.18f + 0.40f * shift),
                y = h * 0.14f
            )
        )

        drawCircle(
            color = Color(0xFF7C4DFF).copy(alpha = 0.09f),
            radius = w * 0.56f,
            center = Offset(
                x = w * (0.82f - 0.35f * shift),
                y = h * 0.78f
            )
        )

        drawCircle(
            color = Color(0xFFFF2DCB).copy(alpha = 0.055f),
            radius = w * 0.45f,
            center = Offset(
                x = w * 0.50f,
                y = h * (0.42f + 0.16f * shift)
            )
        )
    }
}

@Composable
fun AvescodeRgbEdge(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "avescode-edge")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "edge-shift"
    )

    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                val brush = Brush.sweepGradient(
                    0f to Color(0xFF00E5FF).copy(alpha = 0.85f),
                    0.33f to Color(0xFF7C4DFF).copy(alpha = 0.85f),
                    0.66f to Color(0xFFFF2DCB).copy(alpha = 0.85f),
                    1f to Color(0xFF00E5FF).copy(alpha = 0.85f)
                )

                val inset = 1.dp.toPx()
                drawRoundRect(
                    brush = brush,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        inset * shift,
                        inset
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - inset * 2f,
                        size.height - inset * 2f
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        18.dp.toPx(),
                        18.dp.toPx()
                    )
                )
            }
            .padding(1.dp),
        content = content
    )
}

@Composable
fun AvescodeSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    AvescodeRgbEdge(
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(17.dp)),
            content = content
        )
    }
}
