package com.smartgnss.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.smartgnss.ui.ReceiverViewModel
import com.smartgnss.ui.StakeTarget
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Stake-out: navigate to a target grid point with live ΔE/ΔN, distance, bearing. */
@Composable
fun StakeoutScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val fix by vm.fix.collectAsState()
    val target by vm.stakeTarget.collectAsState()
    var name by remember { mutableStateOf("STK1") }
    var east by remember { mutableStateOf("") }
    var north by remember { mutableStateOf("") }

    val cur = vm.gridFor(fix)

    Column(modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Stake-out", style = MaterialTheme.typography.titleLarge)
        Text("Grid: ${vm.crs.collectAsState().value.name}",
            style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(name, { name = it }, label = { Text("Target name") },
            modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(east, { east = it }, label = { Text("Target East (m)") },
                modifier = Modifier.weight(1f))
            OutlinedTextField(north, { north = it }, label = { Text("Target North (m)") },
                modifier = Modifier.weight(1f))
        }
        Button(
            onClick = {
                val e = east.toDoubleOrNull(); val n = north.toDoubleOrNull()
                if (e != null && n != null) vm.setStakeTarget(StakeTarget(name, e, n))
            },
            enabled = east.toDoubleOrNull() != null && north.toDoubleOrNull() != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Set target") }

        val t = target
        if (t == null) {
            Text("Enter a target to begin.", style = MaterialTheme.typography.bodyMedium)
        } else if (cur == null) {
            Text("Waiting for a position fix…", style = MaterialTheme.typography.bodyMedium)
        } else {
            val dE = t.east - cur.east
            val dN = t.north - cur.north
            val dist = hypot(dE, dN)
            var brg = Math.toDegrees(atan2(dE, dN))
            if (brg < 0) brg += 360.0

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("To ${t.name}", style = MaterialTheme.typography.titleMedium)
                    Line("Distance", "%.3f m".format(dist))
                    Line("Bearing", "%.1f°".format(brg))
                    Line("Go North", "%+.3f m".format(dN))
                    Line("Go East", "%+.3f m".format(dE))
                    Text(guidance(dE, dN), style = MaterialTheme.typography.bodyMedium,
                        color = if (dist < 0.05) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface)
                }
            }
            DirectionArrow(brg)
        }
    }
}

@Composable
private fun DirectionArrow(bearingDeg: Double) {
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(cx, cy) * 0.8f
        drawCircle(Color(0xFF9AA0A6), r, Offset(cx, cy), style =
            androidx.compose.ui.graphics.drawscope.Stroke(3f))
        val a = Math.toRadians(bearingDeg - 90)
        val tip = Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
        drawLine(Color(0xFF0A6CBF), Offset(cx, cy), tip, 10f)
        drawCircle(Color(0xFF0A6CBF), 12f, tip)
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace)
    }
}

private fun guidance(dE: Double, dN: Double): String {
    val dist = hypot(dE, dN)
    if (dist < 0.05) return "On point ✓ (within 5 cm)"
    val ns = if (dN >= 0) "North" else "South"
    val ew = if (dE >= 0) "East" else "West"
    return "Move %.2f m %s and %.2f m %s".format(kotlin.math.abs(dN), ns, kotlin.math.abs(dE), ew)
}
