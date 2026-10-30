package com.smartgnss.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.smartgnss.ui.ReceiverViewModel
import com.smartgnss.ui.theme.SectionHeader

@Composable
fun StatusScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val fix by vm.fix.collectAsState()

    Column(modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // Shared X-Survey solution panel (quality · N/E/H · HRMS/VRMS/PDOP).
        SurveyStatusPanel(vm)

        SectionHeader("Geographic position")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Latitude", fix.latitude?.let { "%.8f°".format(it) } ?: "—")
                Field("Longitude", fix.longitude?.let { "%.8f°".format(it) } ?: "—")
                Field("Altitude (MSL)", fix.altitudeMsl?.let { "%.3f m".format(it) } ?: "—")
                Field("Geoid sep.", fix.geoidSep?.let { "%.3f m".format(it) } ?: "—")
                Field("HDOP / VDOP",
                    "${fix.hdop?.let { "%.2f".format(it) } ?: "—"} / " +
                        "${fix.vdop?.let { "%.2f".format(it) } ?: "—"}")
                Field("Correction age", fix.diffAgeSec?.let { "%.1f s".format(it) } ?: "—")
                Field("Base station", fix.baseStationId ?: "—")
                Field("UTC", fix.utc ?: "—")
            }
        }

        SectionHeader("Satellites in view (${fix.satellitesInView.size})")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
private fun Field(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace)
    }
}
