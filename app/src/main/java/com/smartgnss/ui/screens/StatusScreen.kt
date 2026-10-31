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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.smartgnss.core.transport.LinkState
import com.smartgnss.ui.ReceiverViewModel
import com.smartgnss.ui.theme.SectionHeader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GNSS Status — full position and solution readout (X-Survey "Detail Survey" /
 * status parity): geographic (BLH, both notations), grid (ENU), geocentric
 * (ECEF XYZ), velocity, precision/DOP, data-link, and the satellite list.
 */
@Composable
fun StatusScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val fix by vm.fix.collectAsState()
    val crs by vm.crs.collectAsState()
    val link by vm.link.collectAsState()
    val ntrip by vm.ntripState.collectAsState()
    val rtcm by vm.rtcmBytes.collectAsState()
    val lastRtcm by vm.lastRtcmAt.collectAsState()
    val uplinks by vm.ggaUplinks.collectAsState()
    val grid = vm.gridFor(fix)

    val ellip = fix.latitude?.let { (fix.altitudeMsl ?: 0.0) + (fix.geoidSep ?: 0.0) }
    val ecef = if (fix.latitude != null && fix.longitude != null && ellip != null)
        toEcef(fix.latitude!!, fix.longitude!!, ellip) else null

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {

        SurveyStatusPanel(vm)

        SectionHeader("Geographic (WGS84)")
        InfoCard {
            InfoRow("Latitude", fix.latitude?.let { dmsLat(it) } ?: "—")
            InfoRow("Longitude", fix.longitude?.let { dmsLon(it) } ?: "—")
            InfoRow("Lat (dec)", fix.latitude?.let { "%.9f°".format(it) } ?: "—")
            InfoRow("Lon (dec)", fix.longitude?.let { "%.9f°".format(it) } ?: "—")
            InfoRow("Ellipsoidal h", ellip?.let { "%.4f m".format(it) } ?: "—")
            InfoRow("Altitude (MSL)", fix.altitudeMsl?.let { "%.4f m".format(it) } ?: "—")
            InfoRow("Geoid sep.", fix.geoidSep?.let { "%.4f m".format(it) } ?: "—")
        }

        SectionHeader("Grid — ${crs.name}")
        InfoCard {
            InfoRow("East", grid?.east?.let { "%.4f m".format(it) } ?: "—")
            InfoRow("North", grid?.north?.let { "%.4f m".format(it) } ?: "—")
            InfoRow("Elevation", grid?.orthometricHeight?.let { "%.4f m".format(it) } ?: "—")
            InfoRow("Ellipsoidal H", grid?.ellipsoidalHeight?.let { "%.4f m".format(it) } ?: "—")
        }

        SectionHeader("Geocentric (ECEF)")
        InfoCard {
            InfoRow("X", ecef?.get(0)?.let { "%.4f m".format(it) } ?: "—")
            InfoRow("Y", ecef?.get(1)?.let { "%.4f m".format(it) } ?: "—")
            InfoRow("Z", ecef?.get(2)?.let { "%.4f m".format(it) } ?: "—")
        }

        SectionHeader("Velocity")
        InfoCard {
            InfoRow("Speed", fix.speedKmh?.let { "%.2f km/h".format(it) } ?: "—")
            InfoRow("Heading / course", fix.headingDeg?.let { "%.1f°".format(it) } ?: "—")
        }

        SectionHeader("Precision & solution")
        InfoCard {
            InfoRow("Solution", if (link == LinkState.CONNECTED) fix.quality.label else "No receiver")
            InfoRow("HRMS / VRMS",
                "${fix.hrms?.let { "%.4f".format(it) } ?: "—"} / ${fix.vrms?.let { "%.4f".format(it) } ?: "—"} m")
            InfoRow("PDOP / HDOP / VDOP",
                "${fix.pdop?.fmt2() ?: "—"} / ${fix.hdop?.fmt2() ?: "—"} / ${fix.vdop?.fmt2() ?: "—"}")
            InfoRow("Satellites", "${fix.satellitesUsed} used / ${fix.satellitesTracked} tracked")
            InfoRow("Correction age", fix.diffAgeSec?.let { "%.1f s".format(it) } ?: "—")
            InfoRow("Base station ID", fix.baseStationId ?: "—")
            InfoRow("UTC", fix.utc ?: "—")
        }

        SectionHeader("Data link")
        InfoCard {
            InfoRow("Receiver link", link.name)
            InfoRow("NTRIP", ntrip.name)
            InfoRow("RTCM received", "%,d B".format(rtcm))
            InfoRow("Last RTCM",
                if (lastRtcm > 0L) "%.0f s ago".format((android.os.SystemClock.elapsedRealtime() - lastRtcm) / 1000.0)
                else "—")
            InfoRow("GGA uplinks", uplinks.toString())
        }

        SectionHeader("Satellites in view (${fix.satellitesInView.size})")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("System / PRN", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("el / az / SNR", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                fix.satellitesInView.sortedByDescending { it.snr ?: -1 }.forEach { sat ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${sat.constellation} ${sat.prn}",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall)
                        Text("${sat.elevation ?: "-"}°  ${sat.azimuth ?: "-"}°  ${sat.snr ?: 0} dB",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** Geodetic (deg, deg, ellipsoidal m) → WGS84 ECEF X/Y/Z (m). */
private fun toEcef(latDeg: Double, lonDeg: Double, h: Double): DoubleArray {
    val a = 6378137.0
    val f = 1.0 / 298.257223563
    val e2 = f * (2 - f)
    val lat = Math.toRadians(latDeg)
    val lon = Math.toRadians(lonDeg)
    val n = a / sqrt(1 - e2 * sin(lat) * sin(lat))
    val x = (n + h) * cos(lat) * cos(lon)
    val y = (n + h) * cos(lat) * sin(lon)
    val z = (n * (1 - e2) + h) * sin(lat)
    return doubleArrayOf(x, y, z)
}
