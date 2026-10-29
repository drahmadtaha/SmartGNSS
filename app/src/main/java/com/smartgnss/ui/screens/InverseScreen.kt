package com.smartgnss.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.smartgnss.ui.theme.LabeledValue
import kotlin.math.atan2
import kotlin.math.hypot

/** COGO inverse: distance, grid azimuth and ΔN/ΔE between two grid points. */
@Composable
fun InverseScreen(modifier: Modifier = Modifier) {
    var n1 by remember { mutableStateOf("") }
    var e1 by remember { mutableStateOf("") }
    var n2 by remember { mutableStateOf("") }
    var e2 by remember { mutableStateOf("") }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Inverse (point to point)", style = MaterialTheme.typography.titleLarge)

        Text("From point", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(n1, { n1 = it }, label = { Text("North 1") }, modifier = Modifier.weight(1f))
            OutlinedTextField(e1, { e1 = it }, label = { Text("East 1") }, modifier = Modifier.weight(1f))
        }
        Text("To point", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(n2, { n2 = it }, label = { Text("North 2") }, modifier = Modifier.weight(1f))
            OutlinedTextField(e2, { e2 = it }, label = { Text("East 2") }, modifier = Modifier.weight(1f))
        }

        val dn = (n2.toDoubleOrNull() ?: 0.0) - (n1.toDoubleOrNull() ?: 0.0)
        val de = (e2.toDoubleOrNull() ?: 0.0) - (e1.toDoubleOrNull() ?: 0.0)
        val ready = listOf(n1, e1, n2, e2).all { it.toDoubleOrNull() != null }
        val dist = hypot(de, dn)
        var az = Math.toDegrees(atan2(de, dn))
        if (az < 0) az += 360.0

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Result", style = MaterialTheme.typography.titleMedium)
                if (!ready) {
                    Text("Enter all four coordinates.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LabeledValue("Distance", "%.3f m".format(dist))
                    LabeledValue("Azimuth", "%.4f°".format(az))
                    LabeledValue("ΔNorth", "%+.3f m".format(dn))
                    LabeledValue("ΔEast", "%+.3f m".format(de))
                }
            }
        }
        Text("Grid azimuth measured clockwise from North.",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
