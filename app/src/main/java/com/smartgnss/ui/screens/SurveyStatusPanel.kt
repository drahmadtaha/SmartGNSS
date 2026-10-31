package com.smartgnss.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import com.smartgnss.ui.theme.SurveyColors
import com.smartgnss.ui.theme.fixQualityColor
import kotlin.math.abs

/**
 * X-Survey–style live solution panel reused across the survey screens.
 *
 * Mirrors the X-Survey "Point Survey" readout: a coloured solution header
 * (quality · HRMS/VRMS · sats · age), a two-column geographic/grid block —
 * B/L/H (lat, lon, ellipsoidal height) on the left and E/N/Elev on the right —
 * and, when tilt is active, the Tilt Angle and Projection Angle rows.
 */
@Composable
fun SurveyStatusPanel(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val fix by vm.fix.collectAsState()
    val link by vm.link.collectAsState()
    val crs by vm.crs.collectAsState()
    val connected = link == LinkState.CONNECTED
    val color = if (connected) fixQualityColor(fix.quality) else SurveyColors.singleGray
    val grid = vm.gridFor(fix)

    // Use the tilt-compensated ground point for B/L too when tilt is active, so
    // the geographic and grid columns describe the same mark.
    val tiltPos = vm.usingTiltPosition(fix)
    val dispLat = if (tiltPos) fix.tiltCompLat else fix.latitude
    val dispLon = if (tiltPos) fix.tiltCompLon else fix.longitude

    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            // --- Compact solution header: quality + TILT, then H/V (one line) --
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(if (connected) fix.quality.label else "No receiver", color)
                if (tiltPos) {
                    Spacer(Modifier.padding(start = 6.dp))
                    Pill("TILT", SurveyColors.fixedGreen)
                }
                Spacer(Modifier.weight(1f))
                HeaderStat("H", fix.hrms?.let { "%.3f".format(it) } ?: "—")
                HeaderStat("V", fix.vrms?.let { "%.3f".format(it) } ?: "—")
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // --- Geographic (small) + grid (large) rows, aligned ------------
            // Surveyors read the grid E/N/Elev, so those are the large primary
            // values; lat/lon/ellipsoidal (B/L/H) are the small secondary set.
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CoordPairRow(
                    "B", dispLat?.let { dmsLat(it) } ?: "—", null,
                    "E", grid?.east?.let { "%.3f".format(it) } ?: "—", "m")
                CoordPairRow(
                    "L", dispLon?.let { dmsLon(it) } ?: "—", null,
                    "N", grid?.north?.let { "%.3f".format(it) } ?: "—", "m")
                CoordPairRow(
                    "H", grid?.ellipsoidalHeight?.let { "%.3f".format(it) } ?: "—", "m",
                    "Elev", grid?.orthometricHeight?.let { "%.3f".format(it) } ?: "—", "m")
            }

            // --- Tilt rows (X-Survey "Tilt Angle" / "Projection Angle") -------
            if (fix.tiltValid) {
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LabelValueRow("Tilt Angle",
                        fix.tiltAngleDeg?.let { dms(it) } ?: "—")
                    LabelValueRow("Projection Angle",
                        fix.projectionAngleDeg?.let { dms(it) } ?: "—")
                }
            }

            Text(crs.name, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, bottom = 8.dp, top = 2.dp))
        }
    }
}

@Composable
private fun HeaderStat(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 12.dp)) {
        Text("$label:", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
            maxLines = 1, softWrap = false,
            modifier = Modifier.padding(start = 3.dp))
    }
}

/** One aligned line with a small geographic value on the left (B/L/H) and a
 *  large grid value on the right (E/N/Elev), sharing the same baseline row. */
@Composable
private fun CoordPairRow(
    lLabel: String, lValue: String, lUnit: String?,
    rLabel: String, rValue: String, rUnit: String?,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        // Left — small secondary (lat/lon/ellipsoidal).
        Row(Modifier.weight(0.92f), verticalAlignment = Alignment.Bottom) {
            Text(lLabel, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(16.dp))
            Text(lValue, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, softWrap = false)
            if (lUnit != null) Text(lUnit, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 1.dp, bottom = 1.dp))
        }
        Spacer(Modifier.width(6.dp))
        // Right — large primary (grid E/N/Elev).
        Row(Modifier.weight(1.08f), verticalAlignment = Alignment.Bottom) {
            Text(rLabel, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                maxLines = 1, softWrap = false,
                modifier = Modifier.width(38.dp))
            Text(rValue, fontSize = 20.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, softWrap = false)
            if (rUnit != null) Text(" $rUnit", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 1.dp, bottom = 3.dp))
        }
    }
}

@Composable
private fun LabelValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, softWrap = false)
        Spacer(Modifier.weight(1f))
        Text(value, fontFamily = FontFamily.Monospace,
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, softWrap = false)
    }
}

// --- Degree/minute/second formatting (X-Survey style) ----------------------

/** Generic angle → D°MM'SS.ssss" (used for tilt / projection angles). */
internal fun dms(deg: Double): String {
    val neg = deg < 0
    var a = abs(deg)
    var d = a.toInt()
    var m = ((a - d) * 60).toInt()
    var s = (a - d - m / 60.0) * 3600.0
    if (s >= 59.99995) { s = 0.0; m += 1 }
    if (m >= 60) { m -= 60; d += 1 }
    return "%s%d°%02d'%07.4f\"".format(if (neg) "-" else "", d, m, s)
}

/** Latitude → DD°MM'SS.sssss" plus N/S hemisphere. */
internal fun dmsLat(deg: Double): String =
    dmsHemi(deg, if (deg >= 0) "N" else "S")

/** Longitude → DDD°MM'SS.sssss" plus E/W hemisphere. */
internal fun dmsLon(deg: Double): String =
    dmsHemi(deg, if (deg >= 0) "E" else "W")

private fun dmsHemi(deg: Double, hemi: String): String {
    val a = abs(deg)
    var d = a.toInt()
    var m = ((a - d) * 60).toInt()
    var s = (a - d - m / 60.0) * 3600.0
    // 3 decimals of arc-second ≈ 0.03 m — compact enough for one line; full
    // precision is kept in the stored decimal-degree coordinate and exports.
    if (s >= 59.9995) { s = 0.0; m += 1 }
    if (m >= 60) { m -= 60; d += 1 }
    return "%d°%02d'%06.3f\"%s".format(d, m, s, hemi)
}
