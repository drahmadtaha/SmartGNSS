package com.smartgnss.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import com.smartgnss.core.geo.CoordinateSystem
import com.smartgnss.core.geo.GeoidModel
import com.smartgnss.ui.ReceiverViewModel
import java.io.File

/** Pick the working coordinate system and geoid model. */
@Composable
fun SettingsScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val crs by vm.crs.collectAsState()
    val geoid by vm.geoid.collectAsState()
    var constN by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    val gridPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val err = vm.loadGeoidFromUri(uri)
            message = err?.let { "Could not load grid: $it" } ?: "Geoid grid loaded."
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        com.smartgnss.ui.theme.SectionHeader("Receiver model")
        val model by vm.deviceModel.collectAsState()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.smartgnss.core.device.DeviceModel.entries.forEach { m ->
                if (m == model)
                    Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(m.label) }
                else
                    OutlinedButton(onClick = { vm.setDeviceModel(m) },
                        modifier = Modifier.weight(1f)) { Text(m.label) }
            }
        }

        com.smartgnss.ui.theme.SectionHeader("Coordinate system")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) {
                CoordinateSystem.all.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = c.name == crs.name, onClick = { vm.setCrs(c) })
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = c.name == crs.name, onClick = { vm.setCrs(c) })
                        Text(c.name, Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        com.smartgnss.ui.theme.SectionHeader("Geoid model")
        Text("Current: ${geoid.label}", style = MaterialTheme.typography.bodyMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.setGeoid(GeoidModel.None) },
                    modifier = Modifier.fillMaxWidth()) { Text("None (N = 0)") }

                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(constN, { constN = it },
                        label = { Text("Constant N (m)") }, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        constN.toDoubleOrNull()?.let { vm.setGeoid(GeoidModel.Constant(it)) }
                    }, enabled = constN.toDoubleOrNull() != null) { Text("Apply") }
                }

                Button(onClick = { gridPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Load geoid grid (.gtx / .isg / .sgf / .ggf)")
                }
                Text("Load an EGM2008 or local geoid grid (GTX, ISG, South SGF, or GGF). " +
                    "It is copied into the app and reloaded automatically next time.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        com.smartgnss.ui.theme.SectionHeader("Antenna")
        val phase by vm.phaseCenterOffset.collectAsState()
        var phaseStr by remember { mutableStateOf("%.4f".format(phase)) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Phase-centre offset (m)", style = MaterialTheme.typography.titleSmall)
                Text("Fixed vertical distance from the antenna reference point up to the " +
                    "L1 phase centre (from the antenna datasheet). Added to the pole height " +
                    "when reducing to the ground mark.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(phaseStr, { phaseStr = it }, label = { Text("Offset (m)") },
                        singleLine = true, modifier = Modifier.weight(1f))
                    Button(onClick = { phaseStr.toDoubleOrNull()?.let { vm.setPhaseCenterOffset(it) } },
                        enabled = phaseStr.toDoubleOrNull() != null) { Text("Set") }
                }
                Text("Total antenna offset now: %.4f m (pole %.3f + phase %.4f)".format(
                    vm.effectiveAntennaHeight(), vm.antennaHeight.value, phase),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}
