package com.smartgnss.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartgnss.core.transport.LinkState
import com.smartgnss.ui.ReceiverViewModel
import com.smartgnss.ui.theme.Pill
import com.smartgnss.ui.theme.StatTile
import com.smartgnss.ui.theme.SurveyColors
import com.smartgnss.ui.theme.fixQualityColor

/**
 * X-Survey–style live solution panel reused across the survey screens:
 * a coloured solution header (quality · sats · age), the big N / E / H readout
 * in the active coordinate system, and a row of HRMS / VRMS / PDOP tiles.
 */
@Composable
fun SurveyStatusPanel(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val fix by vm.fix.collectAsState()
    val link by vm.link.collectAsState()
    val crs by vm.crs.collectAsState()
    val connected = link == LinkState.CONNECTED
    val color = if (connected) fixQualityColor(fix.quality) else SurveyColors.singleGray
    val grid = vm.gridFor(fix)

    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            // Solution header strip.
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(if (connected) fix.quality.label else "No receiver", color)
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text("${fix.satellitesUsed} sats",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("  age ${fix.diffAgeSec?.let { "%.0fs".format(it) } ?: "—"}",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Big N / E / H.
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                CoordLine("N", grid?.north?.let { "%.3f".format(it) } ?: "—", "m")
                CoordLine("E", grid?.east?.let { "%.3f".format(it) } ?: "—", "m")
                CoordLine(
                    "H",
                    (grid?.orthometricHeight ?: grid?.ellipsoidalHeight)?.let { "%.3f".format(it) } ?: "—",
                    if (grid?.orthometricHeight != null) "m" else "m (ellip.)",
                )
                Text(crs.name, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Precision tiles.
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatTile("HRMS", fix.hrms?.let { "%.3f".format(it) } ?: "—",
                    Modifier.weight(1f))
                StatTile("VRMS", fix.vrms?.let { "%.3f".format(it) } ?: "—",
                    Modifier.weight(1f))
                StatTile("PDOP", fix.pdop?.let { "%.2f".format(it) } ?: "—",
                    Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CoordLine(label: String, value: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 10.dp))
        Text(value, fontSize = 26.sp, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
        Text(" $unit", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp))
    }
}
