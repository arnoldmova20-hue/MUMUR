package com.example.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class BubbleSpec(
    val xFraction: Float,
    val yFraction: Float,
    val radiusFraction: Float,
    val phase: Float,
    val driftAmplitude: Float,
    val color: Color,
    val alpha: Float
)

@Composable
fun FloatingBubblesBackground(
    modifier: Modifier = Modifier,
    intense: Boolean = false
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val transition = rememberInfiniteTransition(label = "murmur_bubbles")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bubble_phase"
    )

    val bubbles = remember(intense, primary, secondary, tertiary) {
        val boost = if (intense) 1.45f else 1f
        listOf(
            BubbleSpec(0.16f, 0.18f, 0.18f, 0.0f, 0.03f, primary, 0.11f * boost),
            BubbleSpec(0.84f, 0.14f, 0.14f, 1.2f, 0.025f, tertiary, 0.10f * boost),
            BubbleSpec(0.75f, 0.48f, 0.22f, 2.4f, 0.02f, secondary, 0.09f * boost),
            BubbleSpec(0.20f, 0.65f, 0.15f, 3.7f, 0.03f, tertiary, 0.08f * boost),
            BubbleSpec(0.82f, 0.84f, 0.19f, 4.9f, 0.025f, primary, 0.10f * boost),
            BubbleSpec(0.45f, 0.36f, 0.09f, 5.4f, 0.02f, primary, 0.07f * boost)
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val minDim = size.minDimension
        bubbles.forEach { b ->
            val dx = sin(progress + b.phase) * size.width * b.driftAmplitude
            val dy = cos(progress + b.phase * 0.8f) * size.height * b.driftAmplitude
            val center = Offset(
                x = size.width * b.xFraction + dx,
                y = size.height * b.yFraction + dy
            )
            val radius = minDim * b.radiusFraction
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        b.color.copy(alpha = b.alpha),
                        b.color.copy(alpha = b.alpha * 0.35f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
            drawCircle(
                color = b.color.copy(alpha = (b.alpha * 1.15f).coerceAtMost(0.30f)),
                radius = radius * 0.55f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f)
            )
        }
    }
}
