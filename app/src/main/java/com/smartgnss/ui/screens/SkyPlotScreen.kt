package com.smartgnss.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Card
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
import com.smartgnss.ui.theme.SectionHeader
import kotlin.math.cos
import kotlin.math.sin

/** Polar sky plot: zenith at centre, horizon at the rim, satellites by az/el. */
@Composable
fun SkyPlotScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val fix by vm.fix.collectAsState()
    val sats = fix.satellitesInView

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {

        SurveyStatusPanel(vm)

        SectionHeader("Sky plot · ${sats.size} in view · ${fix.satellitesUsed} used")
        Card(Modifier.fillMaxWidth()) {
            Canvas(
                Modifier.fillMaxWidth().aspectRatio(1f).padding(16.dp)
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = minOf(cx, cy) * 0.9f
                val ringColor = Color(0xFF9AA0A6)

                for (el in intArrayOf(0, 30, 60)) {
                    val rr = radius * (90 - el) / 90f
                    drawCircle(ringColor, rr, Offset(cx, cy), style = Stroke(2f))
                }
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
                    val a = Math.toRadians((az - 90).toDouble())
                    val x = cx + rr * cos(a).toFloat()
                    val y = cy + rr * sin(a).toFloat()
                    drawCircle(snrColor(sat.snr), radius * 0.035f, Offset(x, y))
                    drawContext.canvas.nativeCanvas.drawText(
                        sat.prn.toString(), x + 8f, y + 4f, paint
                    )
                }
            }
        }

        SectionHeader("By constellation")
        ConstellationSummary(sats)

        SectionHeader("Satellites (${sats.size})")
        SatelliteTable(sats)
    }
}

/** Per-constellation counts: in view and tracked (SNR > 0). */
@Composable
private fun ConstellationSummary(sats: List<Satellite>) {
    val byConst = sats.groupBy { it.constellation }.toSortedMap()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("System", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("in view / tracked / avg SNR", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            byConst.forEach { (name, list) ->
                val tracked = list.filter { (it.snr ?: 0) > 0 }
                val avg = if (tracked.isNotEmpty()) tracked.mapNotNull { it.snr }.average() else 0.0
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, style = MaterialTheme.typography.bodyMedium)
                    Text("${list.size} / ${tracked.size} / ${"%.0f".format(avg)} dB",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Full satellite table: system · PRN · elevation · azimuth · SNR bar. */
@Composable
private fun SatelliteTable(sats: List<Satellite>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("Sat", Modifier.width(70.dp), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("El", Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Az", Modifier.width(52.dp), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("SNR", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            sats.sortedWith(compareBy({ it.constellation }, { it.prn })).forEach { sat ->
                Row(Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("${constShort(sat.constellation)}${sat.prn}", Modifier.width(70.dp),
                        fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text("${sat.elevation ?: "-"}°", Modifier.width(44.dp),
                        fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text("${sat.azimuth ?: "-"}°", Modifier.width(52.dp),
                        fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    // SNR bar
                    Box(Modifier.weight(1f).height(10.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)) {
                        val frac = ((sat.snr ?: 0).coerceIn(0, 50)) / 50f
                        Box(Modifier.fillMaxWidth(frac).height(10.dp).background(snrColor(sat.snr)))
                    }
                    Text("  ${sat.snr ?: 0}", Modifier.width(34.dp),
                        fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun constShort(c: String): String = when (c) {
    "GPS" -> "G"; "GLONASS" -> "R"; "Galileo" -> "E"; "BeiDou" -> "C"
    "QZSS" -> "J"; "NavIC" -> "I"; else -> "?"
}

private fun snrColor(snr: Int?): Color = when {
    snr == null || snr == 0 -> Color(0xFF9AA0A6)
    snr < 25 -> Color(0xFFB00020)
    snr < 35 -> Color(0xFFE0A100)
    else -> Color(0xFF1B9E4B)
}
