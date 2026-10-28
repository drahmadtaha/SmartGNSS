package com.smartgnss.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.smartgnss.core.license.Feature
import com.smartgnss.core.nmea.FixQuality
import com.smartgnss.ui.ReceiverViewModel

@Composable
fun SurveyScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fix by vm.fix.collectAsState()
    val points by vm.survey.points.collectAsState()
    var name by remember { mutableStateOf("P1") }

    Column(modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {

        Text("Survey points", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(name, { name = it }, label = { Text("Point name") },
            modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val p = vm.survey.record(name, fix)
                    if (p != null) {
                        // Auto-increment a trailing number for the next point.
                        name = nextName(name)
                        Toast.makeText(context, "Recorded ${p.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No position fix yet", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = fix.latitude != null,
            ) { Text("Record point") }

            OutlinedButton(
                onClick = {
                    if (!vm.license.has(Feature.SURVEY_EXPORT)) {
                        Toast.makeText(context,
                            "Export not included in your license", Toast.LENGTH_LONG).show()
                    } else {
                        val csv = vm.survey.toCsv()
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_TEXT, csv)
                            putExtra(Intent.EXTRA_SUBJECT, "SmartGNSS points")
                        }
                        context.startActivity(Intent.createChooser(share, "Export points"))
                    }
                },
                enabled = points.isNotEmpty(),
            ) { Text("Export CSV") }
        }

        Text("Current fix: ${fix.quality.label}" +
            if (fix.quality == FixQuality.RTK_FIXED) "  ✓" else "",
            style = MaterialTheme.typography.bodySmall)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(points.reversed()) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall)
                        Text("%.8f, %.8f".format(p.latitude, p.longitude),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall)
                        Text("h ${p.altitude?.let { "%.3f m".format(it) } ?: "—"} · " +
                            "${p.quality.label} · HDOP ${p.hdop ?: "—"}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun nextName(name: String): String {
    val m = Regex("^(.*?)(\\d+)$").find(name) ?: return name
    val prefix = m.groupValues[1]
    val num = m.groupValues[2]
    val width = num.length
    val next = (num.toInt() + 1).toString().padStart(width, '0')
    return "$prefix$next"
}
