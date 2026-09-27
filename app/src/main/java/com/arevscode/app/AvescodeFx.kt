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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * Performance rule:
 * - Only the page background animates.
 * - Card borders are static gradients.
 * - No infinite animation is created per card.
 */
@Composable
fun AvescodeAnimatedBackdrop() {
    val transition = rememberInfiniteTransition(label = "avescode-backdrop")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "backdrop-shift"
    )

    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color(0xFF06080D))

        val w = size.width
        val h = size.height

        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = 0.055f),
            radius = w * 0.52f,
            center = Offset(
                x = w * (0.18f + 0.32f * shift),
                y = h * 0.12f
            )
        )

        drawCircle(
            color = Color(0xFF7C4DFF).copy(alpha = 0.065f),
            radius = w * 0.50f,
            center = Offset(
                x = w * (0.82f - 0.30f * shift),
                y = h * 0.78f
            )
        )

        drawCircle(
            color = Color(0xFFFF2DCB).copy(alpha = 0.035f),
            radius = w * 0.38f,
            center = Offset(
                x = w * 0.50f,
                y = h * (0.44f + 0.10f * shift)
            )
        )
    }
}

@Composable
fun AvescodeRgbEdge(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    /*
     * Static edge: this avoids spawning an animation per surface/card.
     * The page-level backdrop above provides the motion.
     */
    val brush = Brush.linearGradient(
        listOf(
            Color(0xFF00E5FF).copy(alpha = 0.55f),
            Color(0xFF7C4DFF).copy(alpha = 0.50f),
            Color(0xFFFF2DCB).copy(alpha = 0.45f)
        )
    )

    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                val inset = 1.dp.toPx()
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(inset, inset),
                    size = Size(
                        size.width - inset * 2f,
                        size.height - inset * 2f
                    ),
                    cornerRadius = CornerRadius(
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
        modifier = modifier
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(17.dp)),
            content = content
        )
    }
}
