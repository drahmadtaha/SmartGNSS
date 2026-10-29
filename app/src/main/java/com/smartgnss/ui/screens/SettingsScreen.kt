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
        Text("Coordinate system", style = MaterialTheme.typography.titleLarge)
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

        Text("Geoid model", style = MaterialTheme.typography.titleLarge)
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
                    Text("Load geoid grid (.gtx / .isg)")
                }
                Text("Load an EGM2008 or local geoid grid (GTX or ISG). It is copied into " +
                    "the app and reloaded automatically next time.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}
