package com.smartgnss.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartgnss.core.nmea.FixQuality
import com.smartgnss.core.nmea.GnssFix
import com.smartgnss.core.transport.LinkState
import com.smartgnss.ui.ReceiverViewModel

@Composable
fun StatusScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val fix by vm.fix.collectAsState()
    val link by vm.link.collectAsState()
    val label by vm.linkLabel.collectAsState()

    Column(modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        QualityBanner(fix.quality, link, label)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Latitude", fix.latitude?.let { "%.8f°".format(it) } ?: "—")
                Field("Longitude", fix.longitude?.let { "%.8f°".format(it) } ?: "—")
                Field("Altitude (MSL)", fix.altitudeMsl?.let { "%.3f m".format(it) } ?: "—")
                Field("Satellites used", fix.satellitesUsed.toString())
                Field("HDOP", fix.hdop?.let { "%.2f".format(it) } ?: "—")
                Field("PDOP / VDOP",
                    "${fix.pdop?.let { "%.2f".format(it) } ?: "—"} / " +
                    "${fix.vdop?.let { "%.2f".format(it) } ?: "—"}")
                Field("Correction age", fix.diffAgeSec?.let { "%.1f s".format(it) } ?: "—")
                Field("Base station", fix.baseStationId ?: "—")
                Field("UTC", fix.utc ?: "—")
            }
        }

        Text("Satellites in view (${fix.satellitesInView.size})",
            style = MaterialTheme.typography.titleSmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(fix.satellitesInView.sortedByDescending { it.snr ?: -1 }) { sat ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${sat.constellation} ${sat.prn}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall)
                    Text("el ${sat.elevation ?: "-"}°  az ${sat.azimuth ?: "-"}°  " +
                        "${sat.snr ?: 0} dB",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun QualityBanner(q: FixQuality, link: LinkState, label: String) {
    val color = when (q) {
        FixQuality.RTK_FIXED -> Color(0xFF1B9E4B)
        FixQuality.RTK_FLOAT -> Color(0xFFE0A100)
        FixQuality.DGPS, FixQuality.PPS -> Color(0xFF0A6CBF)
        FixQuality.GPS -> Color(0xFF6B7280)
        else -> Color(0xFFB00020)
    }
    Box(
        Modifier.fillMaxWidth()
            .background(color, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(q.label, color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall)
            Text(
                when (link) {
                    LinkState.CONNECTED -> "Connected · $label"
                    LinkState.CONNECTING -> "Connecting…"
                    LinkState.ERROR -> "Link error"
                    LinkState.DISCONNECTED -> "No receiver connected"
                },
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace)
    }
}
