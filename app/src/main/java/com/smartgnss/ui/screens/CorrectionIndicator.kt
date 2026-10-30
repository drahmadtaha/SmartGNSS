package com.smartgnss.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * X-Survey–style correction indicator: a ring that spins while RTCM corrections
 * are actively flowing from the caster, with the current fix quality in the
 * centre. Blue + spinning = corrections arriving; grey + still = none.
 *
 * @param flowing true when RTCM bytes arrived within the last few seconds
 * @param centerText short status inside the ring (e.g. "FIX", "FLT", "SGL")
 * @param accent the ring colour (varies with fix quality)
 */
@Composable
fun CorrectionIndicator(
    flowing: Boolean,
    centerText: String,
    accent: Color,
    modifier: Modifier = Modifier,
    diameter: Int = 60,
) {
    val transition = rememberInfiniteTransition(label = "corr")
    // Continuous sweep rotation while corrections flow.
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ), label = "angle",
    )
    // Gentle breathing of the arc length so it reads as "alive".
    val sweep by transition.animateFloat(
        initialValue = 70f, targetValue = 290f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ), label = "sweep",
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val idleColor = MaterialTheme.colorScheme.outline

    Box(modifier.size(diameter.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter.dp)) {
            val stroke = size.minDimension * 0.10f
            val inset = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(
                size.width - stroke, size.height - stroke
            )
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            // Background track.
            drawArc(
                color = trackColor, startAngle = 0f, sweepAngle = 360f,
                useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (flowing) {
                // Spinning blue arc.
                drawArc(
                    color = accent, startAngle = angle, sweepAngle = sweep,
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            } else {
                // Static dotted-feel ring when idle: three short arcs.
                for (i in 0 until 3) {
                    drawArc(
                        color = idleColor, startAngle = i * 120f + 15f, sweepAngle = 40f,
                        useCenter = false, topLeft = topLeft, size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Text(
            centerText,
            color = if (flowing) accent else idleColor,
            fontSize = (diameter / 4.2f).sp,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
