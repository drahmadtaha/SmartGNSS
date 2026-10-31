package com.smartgnss.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
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
import androidx.compose.ui.draw.clip
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
    // Antenna/pole height is persisted in the ViewModel so it survives navigation.
    var antH by remember { mutableStateOf("%.3f".format(vm.antennaHeight.value)) }
    var detail by remember { mutableStateOf<SurveyPoint?>(null) }
    var showExport by remember { mutableStateOf(false) }
    var showTiltCal by remember { mutableStateOf(false) }

    val canMeasure = fix.latitude != null

    Column(modifier.fillMaxSize()) {

        // --- Scrolling content ------------------------------------------------
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Mode toggle: Normal <-> Tilt
            item {
                Card(
                    Modifier.fillMaxWidth().clickable {
                        vm.beepClick()
                        val turningOn = !tilt
                        vm.setTiltMode(turningOn)
                        if (turningOn) showTiltCal = true
                    },
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
            }

            // Name / code / antenna (X-Survey keeps these above the readout).
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") },
                        singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(code, { code = it }, label = { Text("Code") },
                        singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(antH, { antH = it; it.toDoubleOrNull()?.let(vm::setAntennaHeight) },
                        label = { Text("Ant (m)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }

            // Shared X-Survey solution panel (B/L/H · E/N/Elev · tilt rows).
            item { SurveyStatusPanel(vm) }

            item { com.smartgnss.ui.theme.SectionHeader("Collected points (${points.size})") }

            items(points.reversed()) { p ->
                Card(Modifier.fillMaxWidth().clickable { detail = p }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                Pill(p.quality.label, fixQualityColor(p.quality))
                                if (p.tiltCompensated) Pill("TILT", SurveyColors.fixedGreen)
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

        // --- Fixed bottom action bar: round Measure button + Export ----------
        androidx.compose.material3.Surface(
            tonalElevation = 3.dp, shadowElevation = 10.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.foundation.layout.Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(66.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            if (canMeasure) SurveyColors.fixedGreen
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable(enabled = canMeasure) {
                            val p = vm.recordPoint(name, code)
                            if (p != null) {
                                vm.beepMeasure()
                                name = nextName(name)
                                Toast.makeText(context, "Recorded ${p.name}", Toast.LENGTH_SHORT).show()
                            } else {
                                vm.beepClick()
                                Toast.makeText(context, "No position fix yet", Toast.LENGTH_SHORT).show()
                            }
                        },
                ) {
                    Icon(Icons.Filled.GpsFixed, contentDescription = "Measure point",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(30.dp))
                }
                Column {
                    Text("Measure", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(if (vm.usingTiltPosition(fix)) "tilt-compensated point"
                         else "vertical pole",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.weight(1f))

                androidx.compose.foundation.layout.Box {
                    OutlinedButton(
                        onClick = {
                            if (!vm.license.has(Feature.SURVEY_EXPORT))
                                Toast.makeText(context, "Export not in your license", Toast.LENGTH_LONG).show()
                            else showExport = true
                        },
                        enabled = points.isNotEmpty(),
                    ) { Text("Export") }
                    androidx.compose.material3.DropdownMenu(
                        expanded = showExport, onDismissRequest = { showExport = false }
                    ) {
                        val proj: (SurveyPoint) -> Pair<Double, Double>? =
                            { p -> crs.toGrid(p.latitude, p.longitude, p.altitude ?: 0.0) }
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("CSV (.csv)") },
                            onClick = { showExport = false
                                shareText(context, "points.csv", "text/csv", vm.survey.toCsv(proj)) })
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Text (.txt)") },
                            onClick = { showExport = false
                                shareText(context, "points.txt", "text/plain", vm.survey.toTxt(proj)) })
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("AutoCAD (.dxf)") },
                            onClick = { showExport = false
                                shareText(context, "points.dxf", "application/dxf", vm.survey.toDxf(proj)) })
                    }
                }
            }
        }
    }

    detail?.let { p -> PointDetailDialog(p, crs.toGrid(p.latitude, p.longitude, p.altitude ?: 0.0)) { detail = null } }

    if (showTiltCal) TiltCalibrationDialog(vm) { showTiltCal = false }
}

/** Guided IMU tilt initialization with a live tilt-geometry diagram and results. */
@Composable
private fun TiltCalibrationDialog(vm: ReceiverViewModel, onClose: () -> Unit) {
    val fix by vm.fix.collectAsState()
    val poleLen = vm.effectiveAntennaHeight()
    val alpha = fix.tiltAngleDeg
    val ready = fix.tiltValid && alpha != null
    // Horizontal lean offset Δa = h_GNSS · sin(α).
    val deltaA = alpha?.let { poleLen * kotlin.math.sin(Math.toRadians(it)) }
    val drawAngle = (alpha ?: 20.0).coerceIn(0.0, 60.0)

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Tilt initialization") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Hold the pole vertical and steady, then gently rotate the receiver " +
                    "and walk a few metres until the IMU aligns. Keep an RTK Fixed solution.",
                    style = MaterialTheme.typography.bodySmall)

                TiltDiagram(drawAngle, ready, Modifier.fillMaxWidth().height(150.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.smartgnss.ui.theme.StatTile("Tilt α",
                        alpha?.let { "%.1f°".format(it) } ?: "—", Modifier.weight(1f),
                        accent = if (ready) SurveyColors.fixedGreen else null)
                    com.smartgnss.ui.theme.StatTile("Proj. angle",
                        fix.projectionAngleDeg?.let { "%.0f°".format(it) } ?: "—", Modifier.weight(1f))
                    com.smartgnss.ui.theme.StatTile("Offset Δa",
                        deltaA?.let { "%.3f m".format(it) } ?: "—", Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.beepClick(); vm.enableTiltOnReceiver() },
                        modifier = Modifier.weight(1f)) { Text("Enable on receiver") }
                    OutlinedButton(onClick = { vm.beepClick(); vm.disableTiltOnReceiver() },
                        modifier = Modifier.weight(1f)) { Text("Disable") }
                }

                Text(
                    when {
                        ready && fix.tiltConverged ->
                            "IMU converged — tilt is live. You can measure while tilted."
                        ready ->
                            "Tilt is live (IMU still converging — rotate/walk a few metres to finish)."
                        else ->
                            "Status: reading tilt… If it stays blank, enable + calibrate tilt in " +
                            "the ICE app, then connect here (this screen only reads tilt, it won't " +
                            "turn it off)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ready) SurveyColors.fixedGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}

/** Small diagram: vertical reference, tilted pole at angle α, antenna, ground. */
@Composable
private fun TiltDiagram(angleDeg: Double, ready: Boolean, modifier: Modifier = Modifier) {
    val blue = SurveyColors.brandBlue
    val green = SurveyColors.fixedGreen
    val gray = androidx.compose.ui.graphics.Color(0xFF9AA0A6)
    androidx.compose.foundation.Canvas(modifier) {
        val cx = size.width / 2f
        val gy = size.height * 0.9f
        val L = size.height * 0.78f
        val a = Math.toRadians(angleDeg)
        // ground line
        drawLine(gray, androidx.compose.ui.geometry.Offset(cx - size.width * 0.32f, gy),
            androidx.compose.ui.geometry.Offset(cx + size.width * 0.32f, gy), 3f)
        // vertical reference (dashed)
        drawLine(gray, androidx.compose.ui.geometry.Offset(cx, gy),
            androidx.compose.ui.geometry.Offset(cx, gy - L), 3f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        // tilted pole
        val ex = cx + (L * kotlin.math.sin(a)).toFloat()
        val ey = gy - (L * kotlin.math.cos(a)).toFloat()
        val pole = if (ready) green else blue
        drawLine(pole, androidx.compose.ui.geometry.Offset(cx, gy),
            androidx.compose.ui.geometry.Offset(ex, ey), 11f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(pole, 15f, androidx.compose.ui.geometry.Offset(ex, ey))   // antenna
        // angle arc at the ground pivot
        val r = L * 0.3f
        drawArc(pole, startAngle = -90f, sweepAngle = angleDeg.toFloat(), useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(cx - r, gy - r),
            size = androidx.compose.ui.geometry.Size(2 * r, 2 * r),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
        drawCircle(androidx.compose.ui.graphics.Color(0xFF1A2330), 6f,
            androidx.compose.ui.geometry.Offset(cx, gy))
    }
}

@Composable
internal fun PointDetailDialog(p: SurveyPoint, en: Pair<Double, Double>, onClose: () -> Unit) {
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
                DetailRow("Altitude (MSL)", p.altitude?.let { "%.4f m".format(it) } ?: "—")
                DetailRow("East", "%.4f m".format(en.first))
                DetailRow("North", "%.4f m".format(en.second))
                DetailRow("Ground elev", p.altitude?.let { "%.4f m".format(it - p.antennaHeight) } ?: "—")
                DetailRow("Measured H", p.altitude?.let { "%.4f m".format(it) } ?: "—")
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
                DetailRow("Projection angle", p.projectionAngleDeg?.let { "%.1f°".format(it) } ?: "—")
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

/** Write [content] to a cache file and open the share sheet for it. */
private fun shareText(
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

private fun nextName(name: String): String {
    val m = Regex("^(.*?)(\\d+)$").find(name) ?: return name
    val width = m.groupValues[2].length
    val next = (m.groupValues[2].toInt() + 1).toString().padStart(width, '0')
    return "${m.groupValues[1]}$next"
}
