package com.smartgnss.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.smartgnss.core.geo.CoordinateSystem
import com.smartgnss.core.geo.CrsDefinition
import com.smartgnss.core.geo.GeoidModel
import com.smartgnss.core.geo.TransformType
import com.smartgnss.ui.ReceiverViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CoordinateSystemScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val crs by vm.crs.collectAsState()
    val geoid by vm.geoid.collectAsState()
    val d0 = crs.def

    var name by remember { mutableStateOf(d0.name) }
    var ellName by remember { mutableStateOf(d0.ellipsoidName) }
    var a by remember { mutableStateOf(d0.a.toString()) }
    var invF by remember { mutableStateOf(d0.invF.toString()) }
    var tf by remember { mutableStateOf(d0.transform) }
    var dx by remember { mutableStateOf(d0.dx.toString()) }
    var dy by remember { mutableStateOf(d0.dy.toString()) }
    var dz by remember { mutableStateOf(d0.dz.toString()) }
    var rx by remember { mutableStateOf(d0.rx.toString()) }
    var ry by remember { mutableStateOf(d0.ry.toString()) }
    var rz by remember { mutableStateOf(d0.rz.toString()) }
    var sc by remember { mutableStateOf(d0.scalePpm.toString()) }
    var lat0 by remember { mutableStateOf(d0.lat0.toString()) }
    var lon0 by remember { mutableStateOf(d0.lon0.toString()) }
    var k0 by remember { mutableStateOf(d0.k0.toString()) }
    var fe by remember { mutableStateOf(d0.falseEast.toString()) }
    var fn by remember { mutableStateOf(d0.falseNorth.toString()) }
    var constN by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    fun loadTemplate(t: CrsDefinition) {
        name = t.name; ellName = t.ellipsoidName; a = t.a.toString(); invF = t.invF.toString()
        tf = t.transform
        dx = t.dx.toString(); dy = t.dy.toString(); dz = t.dz.toString()
        rx = t.rx.toString(); ry = t.ry.toString(); rz = t.rz.toString(); sc = t.scalePpm.toString()
        lat0 = t.lat0.toString(); lon0 = t.lon0.toString(); k0 = t.k0.toString()
        fe = t.falseEast.toString(); fn = t.falseNorth.toString()
    }

    val gridPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val file = File(context.cacheDir, "geoid.gtx")
                context.contentResolver.openInputStream(uri)?.use { inp ->
                    file.outputStream().use { inp.copyTo(it) }
                }
                vm.setGeoid(GeoidModel.Grid.load(file)); message = "Geoid grid loaded."
            } catch (e: Exception) { message = "Could not load grid: ${e.message}" }
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Coordinate System", style = MaterialTheme.typography.titleLarge)
        Text("Active: ${crs.name}", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary)

        Text("Templates", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CrsDefinition.templates.forEach { t ->
                AssistChip(onClick = { loadTemplate(t); message = "Template loaded — edit and apply." },
                    label = { Text(t.name) })
            }
        }

        OutlinedTextField(name, { name = it }, label = { Text("System name") },
            modifier = Modifier.fillMaxWidth())

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ellipsoid", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(ellName, { ellName = it }, label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Num(a, { a = it }, "a (m)", Modifier.weight(1f))
                    Num(invF, { invF = it }, "1/f", Modifier.weight(1f))
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Datum transformation (to WGS 84)", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransformType.entries.forEach { t ->
                        FilterChip(selected = tf == t, onClick = { tf = t },
                            label = { Text(t.label) })
                    }
                }
                if (tf != TransformType.NONE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Num(dx, { dx = it }, "dX (m)", Modifier.weight(1f))
                        Num(dy, { dy = it }, "dY (m)", Modifier.weight(1f))
                        Num(dz, { dz = it }, "dZ (m)", Modifier.weight(1f))
                    }
                    if (tf != TransformType.THREE_PARAM) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Num(rx, { rx = it }, "rX (\")", Modifier.weight(1f))
                            Num(ry, { ry = it }, "rY (\")", Modifier.weight(1f))
                            Num(rz, { rz = it }, "rZ (\")", Modifier.weight(1f))
                        }
                        Num(sc, { sc = it }, "scale (ppm)", Modifier.fillMaxWidth())
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Projection — Transverse Mercator", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Num(lat0, { lat0 = it }, "Lat₀ (°)", Modifier.weight(1f))
                    Num(lon0, { lon0 = it }, "Lon₀ (°)", Modifier.weight(1f))
                }
                Num(k0, { k0 = it }, "Scale k₀", Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Num(fe, { fe = it }, "False East", Modifier.weight(1f))
                    Num(fn, { fn = it }, "False North", Modifier.weight(1f))
                }
            }
        }

        Button(
            onClick = {
                val def = CrsDefinition(
                    name = name.ifBlank { "Custom" }, ellipsoidName = ellName,
                    a = a.toDoubleOrNull() ?: 6378137.0, invF = invF.toDoubleOrNull() ?: 298.257223563,
                    transform = tf,
                    dx = dx.toDoubleOrNull() ?: 0.0, dy = dy.toDoubleOrNull() ?: 0.0, dz = dz.toDoubleOrNull() ?: 0.0,
                    rx = rx.toDoubleOrNull() ?: 0.0, ry = ry.toDoubleOrNull() ?: 0.0, rz = rz.toDoubleOrNull() ?: 0.0,
                    scalePpm = sc.toDoubleOrNull() ?: 0.0,
                    lat0 = lat0.toDoubleOrNull() ?: 0.0, lon0 = lon0.toDoubleOrNull() ?: 0.0,
                    k0 = k0.toDoubleOrNull() ?: 1.0,
                    falseEast = fe.toDoubleOrNull() ?: 0.0, falseNorth = fn.toDoubleOrNull() ?: 0.0,
                )
                vm.setCrs(CoordinateSystem(def)); message = "Applied “${def.name}”."
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Apply coordinate system") }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Geoid model", style = MaterialTheme.typography.titleSmall)
                Text("Current: ${geoid.label}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.setGeoid(GeoidModel.None) },
                        modifier = Modifier.weight(1f)) { Text("None") }
                    OutlinedButton(onClick = { gridPicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f)) { Text("Load .gtx") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Num(constN, { constN = it }, "Constant N (m)", Modifier.weight(1f))
                    Button(onClick = { constN.toDoubleOrNull()?.let { vm.setGeoid(GeoidModel.Constant(it)) } },
                        enabled = constN.toDoubleOrNull() != null) { Text("Set") }
                }
            }
        }

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Num(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
