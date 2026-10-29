package com.smartgnss.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.smartgnss.core.nmea.Satellite
import com.smartgnss.ui.ReceiverViewModel
import kotlin.math.cos
import kotlin.math.sin

/** Polar sky plot: zenith at centre, horizon at the rim, satellites by az/el. */
@Composable
fun SkyPlotScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val fix by vm.fix.collectAsState()
    val sats = fix.satellitesInView

    Column(modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sky plot", style = MaterialTheme.typography.titleLarge)
        Text("${sats.size} satellites in view · ${fix.satellitesUsed} used",
            style = MaterialTheme.typography.bodyMedium)

        Canvas(
            Modifier.fillMaxWidth().aspectRatio(1f)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = minOf(cx, cy) * 0.9f
            val ringColor = Color(0xFF9AA0A6)

            // Elevation rings at 0 / 30 / 60 degrees.
            for (el in intArrayOf(0, 30, 60)) {
                val rr = radius * (90 - el) / 90f
                drawCircle(ringColor, rr, Offset(cx, cy), style = Stroke(2f))
            }
            // Cross hairs.
            drawLine(ringColor, Offset(cx, cy - radius), Offset(cx, cy + radius), 2f)
            drawLine(ringColor, Offset(cx - radius, cy), Offset(cx + radius, cy), 2f)

            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.GRAY
                textSize = radius * 0.08f
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.apply {
                drawText("N", cx - paint.textSize / 3, cy - radius - 6f, paint)
                drawText("S", cx - paint.textSize / 3, cy + radius + paint.textSize, paint)
                drawText("E", cx + radius + 6f, cy + paint.textSize / 3, paint)
                drawText("W", cx - radius - paint.textSize, cy + paint.textSize / 3, paint)
            }

            for (sat in sats) {
                val el = sat.elevation ?: continue
                val az = sat.azimuth ?: continue
                val rr = radius * (90 - el).coerceIn(0, 90) / 90f
                val a = Math.toRadians((az - 90).toDouble()) // 0 az = up (North)
                val x = cx + rr * cos(a).toFloat()
                val y = cy + rr * sin(a).toFloat()
                drawCircle(snrColor(sat.snr), radius * 0.035f, Offset(x, y))
                drawContext.canvas.nativeCanvas.drawText(
                    sat.prn.toString(), x + 8f, y + 4f, paint
                )
            }
        }

        Text("Colour = signal strength (red low · green strong)",
            style = MaterialTheme.typography.bodySmall)
        SnrLegend(sats)
    }
}

@Composable
private fun SnrLegend(sats: List<Satellite>) {
    val byConst = sats.groupBy { it.constellation }
    Column {
        byConst.forEach { (name, list) ->
            val tracked = list.count { (it.snr ?: 0) > 0 }
            Text("$name: ${list.size} in view, $tracked tracked",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun snrColor(snr: Int?): Color = when {
    snr == null || snr == 0 -> Color(0xFF9AA0A6)
    snr < 25 -> Color(0xFFB00020)
    snr < 35 -> Color(0xFFE0A100)
    else -> Color(0xFF1B9E4B)
}
