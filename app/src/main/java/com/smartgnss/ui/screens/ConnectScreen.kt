package com.smartgnss.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.smartgnss.core.transport.BluetoothTransport
import com.smartgnss.core.transport.LinkState
import com.smartgnss.core.transport.UsbSerialTransport
import com.smartgnss.ui.ReceiverViewModel

@SuppressLint("MissingPermission")
@Composable
fun ConnectScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val link by vm.link.collectAsState()
    var refresh by remember { mutableStateOf(0) }

    Column(modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Text("Receiver connection", style = MaterialTheme.typography.titleLarge)
        Text("State: ${link.name}", style = MaterialTheme.typography.bodyMedium)

        if (link == LinkState.CONNECTED || link == LinkState.CONNECTING) {
            Button(onClick = { vm.disconnect() }, modifier = Modifier.fillMaxWidth()) {
                Text("Disconnect")
            }
        }

        OutlinedButton(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth()) {
            Text("Refresh device list")
        }

        // --- USB-serial receivers -------------------------------------------
        Text("USB", style = MaterialTheme.typography.titleSmall)
        val usbDrivers = remember(refresh) { UsbSerialTransport.available(context) }
        if (usbDrivers.isEmpty()) {
            Text("No USB-serial device detected.",
                style = MaterialTheme.typography.bodySmall)
        } else {
            usbDrivers.forEach { driver ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(driver.device.productName
                            ?: "USB serial (VID ${driver.device.vendorId})")
                        Button(onClick = {
                            vm.connect(UsbSerialTransport(context, driver))
                        }) { Text("Connect") }
                    }
                }
            }
        }

        // --- Bluetooth (classic SPP) receivers ------------------------------
        Text("Bluetooth (paired)", style = MaterialTheme.typography.titleSmall)
        if (!hasBtPermission(context)) {
            Text("Grant Bluetooth permission in Android settings to see paired receivers.",
                style = MaterialTheme.typography.bodySmall)
        } else {
            val bonded = remember(refresh) {
                val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE)
                        as BluetoothManager
                mgr.adapter?.bondedDevices?.toList().orEmpty()
            }
            if (bonded.isEmpty()) {
                Text("No paired Bluetooth devices. Pair your receiver in Android settings first.",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                bonded.forEach { device ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(device.name ?: "Unknown")
                            Text(device.address, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = {
                                vm.connect(BluetoothTransport(device))
                            }) { Text("Connect") }
                        }
                    }
                }
            }
        }
    }
}

private fun hasBtPermission(context: Context): Boolean {
    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH
    return ContextCompat.checkSelfPermission(context, perm) ==
        PackageManager.PERMISSION_GRANTED
}
