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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.smartgnss.core.transport.LinkState
import com.smartgnss.ui.ReceiverViewModel
import com.smartgnss.ui.theme.SectionHeader

/**
 * Device Info — a consolidated readout of the connected receiver, mirroring the
 * information X-Survey shows on its Device Info page: identity (model, serial,
 * firmware), antenna (pole + phase centre), tilt/IMU state, and the live
 * solution summary.
 */
@Composable
fun DeviceInfoScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val fix by vm.fix.collectAsState()
    val link by vm.link.collectAsState()
    val linkLabel by vm.linkLabel.collectAsState()
    val model by vm.deviceModel.collectAsState()
    val autoDetected by vm.modelAutoDetected.collectAsState()
    val devInfo by vm.deviceInfo.collectAsState()
    val phase by vm.phaseCenterOffset.collectAsState()
    val phaseFromDevice by vm.phaseCenterFromDevice.collectAsState()
    val antH by vm.antennaHeight.collectAsState()
    val tilt by vm.tiltMode.collectAsState()

    // +VER reply is "version,serial,extra" (e.g. 3.0.0,ICECCBA97011618,0).
    val verParts = devInfo?.split(',') ?: emptyList()
    val firmware = verParts.getOrNull(0)?.trim()
    val serial = verParts.getOrNull(1)?.trim()

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {

        SectionHeader("Identity")
        InfoCard {
            InfoRow("Model", model.label + if (autoDetected) "  (auto-detected)" else "")
            InfoRow("Serial number", serial ?: "—")
            InfoRow("Firmware", firmware ?: "—")
            InfoRow("Connection",
                if (link == LinkState.CONNECTED) (linkLabel.ifBlank { "Connected" }) else "Not connected")
            InfoRow("Raw +VER", devInfo ?: "—")
        }

        SectionHeader("Antenna")
        InfoCard {
            InfoRow("Pole / antenna height", "%.3f m".format(antH))
            InfoRow("Phase-centre offset",
                "%.4f m".format(phase) + if (phaseFromDevice) "  (from receiver)" else "  (model default)")
            InfoRow("Effective height", "%.4f m".format(vm.effectiveAntennaHeight()))
        }

        SectionHeader("Tilt / IMU")
        InfoCard {
            InfoRow("Tilt mode", if (tilt) "ON" else "OFF")
            InfoRow("IMU state", fix.tiltStatus ?: "—")
            InfoRow("Convergence", if (fix.tiltValid) "Ready (100%)" else "${fix.tiltProgress}%")
            InfoRow("Tilt angle",
                if (fix.tiltValid) fix.tiltAngleDeg?.let { "%.2f°".format(it) } ?: "—" else "—")
            InfoRow("Projection angle",
                if (fix.tiltValid) fix.projectionAngleDeg?.let { "%.2f°".format(it) } ?: "—" else "—")
        }

        SectionHeader("Solution")
        InfoCard {
            InfoRow("Status", if (link == LinkState.CONNECTED) fix.quality.label else "No receiver")
            InfoRow("Satellites", "${fix.satellitesUsed} used / ${fix.satellitesTracked} tracked / ${fix.satellitesInView.size} in view")
            InfoRow("HRMS / VRMS",
                "${fix.hrms?.let { "%.3f".format(it) } ?: "—"} / ${fix.vrms?.let { "%.3f".format(it) } ?: "—"} m")
            InfoRow("PDOP / HDOP / VDOP",
                "${fix.pdop?.fmt2() ?: "—"} / ${fix.hdop?.fmt2() ?: "—"} / ${fix.vdop?.fmt2() ?: "—"}")
            InfoRow("Correction age", fix.diffAgeSec?.let { "%.1f s".format(it) } ?: "—")
            InfoRow("Base station ID", fix.baseStationId ?: "—")
            InfoRow("UTC", fix.utc ?: "—")
        }
    }
}

@Composable
internal fun InfoCard(content: @Composable Column.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp),
            content = content)
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace)
    }
}

internal fun Double.fmt2(): String = "%.2f".format(this)
