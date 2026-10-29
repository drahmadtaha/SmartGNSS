package com.smartgnss.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.smartgnss.core.transport.BleTransport
import com.smartgnss.core.transport.BluetoothTransport
import com.smartgnss.core.transport.LinkState
import com.smartgnss.core.transport.TcpTransport
import com.smartgnss.core.transport.UsbSerialTransport
import com.smartgnss.ui.ReceiverViewModel
import kotlinx.coroutines.delay

private enum class ConnKind { BLUETOOTH, BLE, WIFI, USB }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("MissingPermission")
@Composable
fun CommunicationScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val link by vm.link.collectAsState()
    val label by vm.linkLabel.collectAsState()

    var manufacturer by remember { mutableStateOf("Smart-GNSS") }
    var kind by remember { mutableStateOf(ConnKind.BLE) }
    var refresh by remember { mutableStateOf(0) }
    var tcpHost by remember { mutableStateOf("") }
    var tcpPort by remember { mutableStateOf("9000") }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Status: ${link.name}", style = MaterialTheme.typography.titleMedium,
                    color = if (link == LinkState.CONNECTED) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface)
                if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (link == LinkState.CONNECTED || link == LinkState.CONNECTING) {
                    OutlinedButton(onClick = { vm.disconnect() }) { Text("Disconnect") }
                }
            }
        }

        Field("Device type")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = true, onClick = {}, label = { Text("GNSS Receiver") })
        }

        Field("Manufacturer")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Smart-GNSS", "Hi-Target", "Generic NMEA", "Other").forEach { m ->
                FilterChip(selected = manufacturer == m, onClick = { manufacturer = m },
                    label = { Text(m) })
            }
        }

        Field("Connection type")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(kind == ConnKind.BLE, { kind = ConnKind.BLE }, label = { Text("BLE") })
            FilterChip(kind == ConnKind.BLUETOOTH, { kind = ConnKind.BLUETOOTH }, label = { Text("Bluetooth") })
            FilterChip(kind == ConnKind.WIFI, { kind = ConnKind.WIFI }, label = { Text("Wi-Fi") })
            FilterChip(kind == ConnKind.USB, { kind = ConnKind.USB }, label = { Text("USB") })
        }

        if (!hasBtPerm(context) && (kind == ConnKind.BLE || kind == ConnKind.BLUETOOTH)) {
            Text("Bluetooth permission is needed to scan and connect.",
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = { permLauncher.launch(btPermissions()) },
                modifier = Modifier.fillMaxWidth()) { Text("Grant Bluetooth permission") }
        }

        when (kind) {
            ConnKind.BLE -> BleSection(vm, context) { permLauncher.launch(btPermissions()) }
            ConnKind.BLUETOOTH -> ClassicSection(vm, context, refresh) { refresh++ }
            ConnKind.WIFI -> {
                Field("Network address")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(tcpHost, { tcpHost = it }, label = { Text("Host / IP") },
                        modifier = Modifier.weight(2f))
                    OutlinedTextField(tcpPort, { tcpPort = it.filter(Char::isDigit) },
                        label = { Text("Port") }, modifier = Modifier.weight(1f))
                }
                Button(
                    onClick = { tcpPort.toIntOrNull()?.let { vm.connect(TcpTransport(tcpHost.trim(), it)) } },
                    enabled = tcpHost.isNotBlank() && tcpPort.toIntOrNull() != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Connect over TCP") }
            }
            ConnKind.USB -> {
                val drivers = remember(refresh) { UsbSerialTransport.available(context) }
                Field("USB devices")
                OutlinedButton(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth()) { Text("Refresh") }
                if (drivers.isEmpty()) {
                    Text("No USB-serial device detected.", style = MaterialTheme.typography.bodySmall)
                } else drivers.forEach { drv ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(drv.device.productName ?: "USB serial", Modifier.weight(1f))
                            Button(onClick = { vm.connect(UsbSerialTransport(context, drv)) }) { Text("Connect") }
                        }
                    }
                }
            }
        }
    }
}

/** BLE: live scan for nearby (unpaired) receivers, plus any already paired. */
@SuppressLint("MissingPermission")
@Composable
private fun BleSection(vm: ReceiverViewModel, context: Context, requestPerm: () -> Unit) {
    val adapter = remember {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    val found = remember { mutableStateListOf<android.bluetooth.BluetoothDevice>() }
    var scanning by remember { mutableStateOf(false) }

    val callback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                if (found.none { it.address == dev.address }) found.add(dev)
            }
        }
    }

    DisposableEffect(scanning) {
        val scanner = adapter?.bluetoothLeScanner
        if (scanning && scanner != null) runCatching { scanner.startScan(callback) }
        onDispose { runCatching { scanner?.stopScan(callback) } }
    }
    LaunchedEffect(scanning) { if (scanning) { delay(12_000); scanning = false } }

    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = {
                if (!hasScanPerm(context)) { requestPerm(); return@Button }
                found.clear(); scanning = true
            },
            modifier = Modifier.weight(1f),
        ) { Text(if (scanning) "Scanning…" else "Scan for BLE receivers") }
        if (scanning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
    }

    // Paired BLE devices too (some receivers end up bonded).
    val bonded = remember(scanning) {
        adapter?.bondedDevices?.toList().orEmpty()
    }
    val all = (found + bonded).distinctBy { it.address }

    if (all.isEmpty()) {
        Text("Tap scan, then pick your receiver (e.g. ICEM10_BLE_…). BLE devices do not need pairing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Field("Found devices")
        all.forEach { dev ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(dev.name ?: "(unnamed)")
                        Text(dev.address, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { scanning = false; vm.connect(BleTransport(context, dev)) }) {
                        Text("Connect")
                    }
                }
            }
        }
    }
}

/** Classic Bluetooth (SPP): paired devices. */
@SuppressLint("MissingPermission")
@Composable
private fun ClassicSection(vm: ReceiverViewModel, context: Context, refresh: Int, onRefresh: () -> Unit) {
    OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh paired") }
    val bonded = remember(refresh) {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mgr.adapter?.bondedDevices?.toList().orEmpty()
    }
    Field("Paired devices")
    if (bonded.isEmpty()) {
        Text("No paired devices. Pair a classic-Bluetooth receiver in Android settings first.",
            style = MaterialTheme.typography.bodySmall)
    } else bonded.forEach { dev ->
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dev.name ?: "Unknown")
                    Text(dev.address, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { vm.connect(BluetoothTransport(dev)) }) { Text("Connect") }
            }
        }
    }
}

@Composable
private fun Field(label: String) {
    Text(label, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun hasBtPerm(context: Context): Boolean {
    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH
    return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
}

private fun hasScanPerm(context: Context): Boolean {
    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.ACCESS_FINE_LOCATION
    return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
}

private fun btPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
