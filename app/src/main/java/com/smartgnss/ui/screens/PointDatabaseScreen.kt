package com.smartgnss.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.smartgnss.core.survey.SurveyPoint
import com.smartgnss.ui.ReceiverViewModel
import com.smartgnss.ui.theme.Pill
import com.smartgnss.ui.theme.SectionHeader
import com.smartgnss.ui.theme.fixQualityColor

/**
 * Point database for the open project: the saved points with their grid
 * coordinates, plus Add (current position), Import (CSV) and Export (CSV/TXT/DXF).
 */
@Composable
fun PointDatabaseScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val points by vm.survey.points.collectAsState()
    val crs by vm.crs.collectAsState()
    val fix by vm.fix.collectAsState()
    var showExport by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<SurveyPoint?>(null) }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            val n = if (text != null) vm.survey.importCsv(text) else 0
            Toast.makeText(context, if (n > 0) "Imported $n points" else "No points imported",
                Toast.LENGTH_SHORT).show()
        }
    }

    val proj: (SurveyPoint) -> Pair<Double, Double>? =
        { p -> crs.toGrid(p.latitude, p.longitude, p.altitude ?: 0.0) }

    Column(modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {

        SectionHeader("Points in project (${points.size})")

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(points.reversed()) { p ->
                val en = proj(p)
                Card(Modifier.fillMaxWidth().clickable { vm.beepClick(); detail = p }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                if (p.quality != com.smartgnss.core.nmea.FixQuality.NO_FIX)
                                    Pill(p.quality.label, fixQualityColor(p.quality))
                                if (p.code.isNotBlank()) Text(p.code,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "E ${en?.first?.let { "%.3f".format(it) } ?: "—"}  " +
                                    "N ${en?.second?.let { "%.3f".format(it) } ?: "—"}",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "H ${p.altitude?.let { "%.3f".format(it - p.antennaHeight) } ?: "—"} m",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { vm.beepClick(); vm.survey.delete(p) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // Action row: Add · Import · Export
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    vm.beepClick()
                    val p = vm.survey.record("P${points.size + 1}", "", fix,
                        vm.effectiveAntennaHeight(), vm.tiltMode.value)
                    if (p != null) { vm.beepMeasure(); Toast.makeText(context, "Added ${p.name}", Toast.LENGTH_SHORT).show() }
                    else Toast.makeText(context, "No position fix", Toast.LENGTH_SHORT).show()
                },
                enabled = fix.latitude != null,
                modifier = Modifier.weight(1f),
            ) { Text("Add") }

            OutlinedButton(
                onClick = { vm.beepClick(); importer.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values")) },
                modifier = Modifier.weight(1f),
            ) { Text("Import") }

            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { vm.beepClick(); showExport = true },
                    enabled = points.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
                ) { Text("Export") }
                DropdownMenu(expanded = showExport, onDismissRequest = { showExport = false }) {
                    DropdownMenuItem(text = { Text("CSV (.csv)") }, onClick = {
                        showExport = false; sharePoints(context, "points.csv", "text/csv", vm.survey.toCsv(proj)) })
                    DropdownMenuItem(text = { Text("Text (.txt)") }, onClick = {
                        showExport = false; sharePoints(context, "points.txt", "text/plain", vm.survey.toTxt(proj)) })
                    DropdownMenuItem(text = { Text("AutoCAD (.dxf)") }, onClick = {
                        showExport = false; sharePoints(context, "points.dxf", "application/dxf", vm.survey.toDxf(proj)) })
                }
            }
        }
    }

    detail?.let { p ->
        PointDetailDialog(p, crs.toGrid(p.latitude, p.longitude, p.altitude ?: 0.0)) { detail = null }
    }
}

private fun sharePoints(
    context: android.content.Context, filename: String, mime: String, content: String,
) {
    runCatching {
        val f = java.io.File(context.cacheDir, filename)
        f.writeText(content)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", f
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Smart GNSS points")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share $filename"))
    }
}
