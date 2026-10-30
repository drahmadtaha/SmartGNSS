package com.smartgnss.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.smartgnss.ui.ReceiverViewModel
import java.io.File

/** Raw serial terminal: shows bytes coming from the receiver and sends commands. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun DataTerminalScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val raw by vm.rawLog.collectAsState()
    var cmd by remember { mutableStateOf("") }
    val scroll = rememberScrollState()

    LaunchedEffect(raw) { scroll.scrollTo(scroll.maxValue) }

    Column(modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {

        Text("Data Terminal", style = MaterialTheme.typography.titleLarge)
        Text("Live data from the receiver. Use it to confirm the GPS is sending NMEA.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Console
        Column(
            Modifier.fillMaxWidth().weight(1f)
                .background(Color(0xFF0C1117))
                .padding(10.dp)
                .verticalScroll(scroll),
        ) {
            Text(
                raw.ifEmpty { "Waiting for data…\n\nIf this stays empty, the receiver isn't sending on the connected characteristic." },
                color = Color(0xFF8AE28A),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }

        // Quick commands
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { vm.configureReceiver() }, label = { Text("Configure UM982") })
            AssistChip(onClick = { vm.sendCommand("GPGGA 1") }, label = { Text("GPGGA 1") })
            AssistChip(onClick = { vm.sendCommand("MODE ROVER") }, label = { Text("MODE ROVER") })
            AssistChip(onClick = { vm.sendCommand("VERSION") }, label = { Text("VERSION") })
            AssistChip(onClick = { vm.sendCommand("SAVECONFIG") }, label = { Text("SAVECONFIG") })
        }

        // Command input
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = cmd, onValueChange = { cmd = it },
                label = { Text("Send command") }, singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { if (cmd.isNotBlank()) { vm.sendCommand(cmd.trim()); cmd = "" } },
                enabled = cmd.isNotBlank()) { Text("Send") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    runCatching {
                        val f = File(context.cacheDir, "smartgnss_log.txt")
                        f.writeText(raw)
                        val uri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", f
                        )
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Smart GNSS log")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(send, "Share log")
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Save & share log") }
            OutlinedButton(onClick = { vm.clearRawLog() }) { Text("Clear") }
        }
    }
}
