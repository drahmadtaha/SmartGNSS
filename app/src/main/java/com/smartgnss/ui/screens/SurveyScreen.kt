package com.smartgnss.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.smartgnss.core.license.Feature
import com.smartgnss.core.survey.SurveyPoint
import com.smartgnss.ui.ReceiverViewModel
import com.smartgnss.ui.theme.LabeledValue
import com.smartgnss.ui.theme.Pill
import com.smartgnss.ui.theme.SurveyColors
import com.smartgnss.ui.theme.fixQualityColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SurveyScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fix by vm.fix.collectAsState()
    val points by vm.survey.points.collectAsState()
    val crs by vm.crs.collectAsState()
    val tilt by vm.tiltMode.collectAsState()
    var name by remember { mutableStateOf("PT1") }
    var code by remember { mutableStateOf("") }
    var antH by remember { mutableStateOf("2.000") }
    var detail by remember { mutableStateOf<SurveyPoint?>(null) }

    Column(modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // Mode toggle: Normal <-> Tilt
        Card(
            Modifier.fillMaxWidth().clickable { vm.setTiltMode(!tilt) },
            colors = CardDefaults.cardColors(
                containerColor = if (tilt) SurveyColors.fixedGreen.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant
            ),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ScreenRotation, contentDescription = "Tilt",
                    tint = if (tilt) SurveyColors.fixedGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(if (tilt) "Tilt measurement — ON" else "Normal measurement",
                        style = MaterialTheme.typography.titleSmall)
                    Text(if (tilt)
                        "Tilt ${fix.tiltAngleDeg?.let { "%.1f°".format(it) } ?: "—"} · tap to switch to normal"
                    else "Pole must be vertical · tap to enable tilt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Pill(if (tilt) "TILT" else "NORMAL",
                    if (tilt) SurveyColors.fixedGreen else SurveyColors.singleGray)
            }
        }

        // Shared X-Survey solution panel (quality · N/E/H · HRMS/VRMS/PDOP).
        SurveyStatusPanel(vm)

        // Name / code / antenna
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.weight(1f))
            OutlinedTextField(code, { code = it }, label = { Text("Code") }, modifier = Modifier.weight(1f))
            OutlinedTextField(antH, { antH = it }, label = { Text("Ant (m)") }, modifier = Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val p = vm.survey.record(name, code, fix, antH.toDoubleOrNull() ?: 0.0, tilt)
                    if (p != null) { name = nextName(name); Toast.makeText(context, "Recorded ${p.name}", Toast.LENGTH_SHORT).show() }
                    else Toast.makeText(context, "No position fix yet", Toast.LENGTH_SHORT).show()
                },
                enabled = fix.latitude != null,
                modifier = Modifier.weight(1f),
            ) { Text("Record point") }

            OutlinedButton(
                onClick = {
                    if (!vm.license.has(Feature.SURVEY_EXPORT)) {
                        Toast.makeText(context, "Export not in your license", Toast.LENGTH_LONG).show()
                    } else {
                        val csv = vm.survey.toCsv { p -> crs.toGrid(p.latitude, p.longitude, p.altitude ?: 0.0) }
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_TEXT, csv)
                            putExtra(Intent.EXTRA_SUBJECT, "SmartGNSS points")
                        }
                        context.startActivity(Intent.createChooser(share, "Export points"))
                    }
                },
                enabled = points.isNotEmpty(),
            ) { Text("Export") }
        }

        com.smartgnss.ui.theme.SectionHeader("Collected points (${points.size})")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(points.reversed()) { p ->
                Card(Modifier.fillMaxWidth().clickable { detail = p }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                Pill(p.quality.label, fixQualityColor(p.quality))
                                if (p.tiltAngleDeg != null) Pill("TILT", SurveyColors.fixedGreen)
                            }
                            Text("%.8f, %.8f".format(p.latitude, p.longitude),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall)
                            Text("HRMS ${p.hrms?.let { "%.3f".format(it) } ?: "—"} · ${p.satellitesUsed} sats · ${p.code.ifBlank { "—" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    detail?.let { p -> PointDetailDialog(p, crs.toGrid(p.latitude, p.longitude, p.altitude ?: 0.0)) { detail = null } }
}

@Composable
private fun PointDetailDialog(p: SurveyPoint, en: Pair<Double, Double>, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        title = { Text(p.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DetailRow("Code", p.code.ifBlank { "—" })
                DetailRow("Date / time", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(p.timestampMs)))
                DetailRow("Latitude", "%.8f°".format(p.latitude))
                DetailRow("Longitude", "%.8f°".format(p.longitude))
                DetailRow("East", "%.4f m".format(en.first))
                DetailRow("North", "%.4f m".format(en.second))
                DetailRow("Ortho H", p.altitude?.let { "%.4f m".format(it) } ?: "—")
                DetailRow("Antenna h", "%.3f m".format(p.antennaHeight))
                DetailRow("Solution", p.quality.label)
                DetailRow("Satellites", "${p.satellitesUsed} used / ${p.satellitesTracked} tracked")
                DetailRow("HRMS / VRMS",
                    "${p.hrms?.let { "%.3f".format(it) } ?: "—"} / ${p.vrms?.let { "%.3f".format(it) } ?: "—"} m")
                DetailRow("PDOP / HDOP / VDOP",
                    "${p.pdop?.let { "%.2f".format(it) } ?: "—"} / " +
                        "${p.hdop?.let { "%.2f".format(it) } ?: "—"} / " +
                        "${p.vdop?.let { "%.2f".format(it) } ?: "—"}")
                DetailRow("Correction age", p.ageSec?.let { "%.1f s".format(it) } ?: "—")
                DetailRow("Tilt", p.tiltAngleDeg?.let { "%.1f°".format(it) } ?: "normal")
                DetailRow("Heading", p.headingDeg?.let { "%.1f°".format(it) } ?: "—")
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

private fun nextName(name: String): String {
    val m = Regex("^(.*?)(\\d+)$").find(name) ?: return name
    val width = m.groupValues[2].length
    val next = (m.groupValues[2].toInt() + 1).toString().padStart(width, '0')
    return "${m.groupValues[1]}$next"
}
