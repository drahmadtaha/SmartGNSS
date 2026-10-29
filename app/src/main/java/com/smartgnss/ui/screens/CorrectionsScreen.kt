package com.smartgnss.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.smartgnss.core.license.Feature
import com.smartgnss.core.ntrip.NtripConfig
import com.smartgnss.core.ntrip.NtripState
import com.smartgnss.ui.ReceiverViewModel

@Composable
fun CorrectionsScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    // Feature-gated: only licenses with RTK_NTRIP can use corrections.
    if (!vm.license.has(Feature.RTK_NTRIP)) {
        LockedFeature("RTK corrections", modifier)
        return
    }

    val state by vm.ntripState.collectAsState()
    val saved = vm.ntripDefaults
    var host by remember { mutableStateOf(saved?.host ?: "") }
    var port by remember { mutableStateOf(saved?.port?.toString() ?: "2101") }
    var mount by remember { mutableStateOf(saved?.mountPoint ?: "") }
    var user by remember { mutableStateOf(saved?.username ?: "") }
    var pass by remember { mutableStateOf(saved?.password ?: "") }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {

        Text("NTRIP corrections", style = MaterialTheme.typography.titleLarge)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Status: ${state.name}", style = MaterialTheme.typography.titleMedium,
                    color = when (state) {
                        NtripState.STREAMING -> MaterialTheme.colorScheme.primary
                        NtripState.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    })
                Text(
                    when (state) {
                        NtripState.STREAMING -> "Streaming RTCM to the receiver."
                        NtripState.CONNECTING -> "Connecting to caster…"
                        NtripState.ERROR -> "Check host, mountpoint, and credentials."
                        NtripState.IDLE -> "Not streaming."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        OutlinedTextField(host, { host = it }, label = { Text("Caster host") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(port, { port = it.filter(Char::isDigit) },
            label = { Text("Port") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(mount, { mount = it }, label = { Text("Mount point") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(user, { user = it }, label = { Text("Username") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pass, { pass = it }, label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth())

        val mounts by vm.mountPoints.collectAsState()
        OutlinedButton(
            onClick = { vm.fetchMountPoints(host.trim(), port.toIntOrNull() ?: 2101, user.trim(), pass) },
            enabled = host.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Get mountpoints") }
        if (mounts.isNotEmpty()) {
            Text("${mounts.size} mountpoints — tap to select",
                style = MaterialTheme.typography.bodySmall)
            mounts.take(60).forEach { mp ->
                Card(
                    Modifier.fillMaxWidth().clickable { mount = mp.mount },
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(mp.mount, style = MaterialTheme.typography.titleSmall)
                        Text("${mp.format} · ${mp.identifier}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (state == NtripState.IDLE || state == NtripState.ERROR) {
            Button(
                onClick = {
                    val p = port.toIntOrNull() ?: 2101
                    vm.saveNtrip(host.trim(), p, mount.trim(), user.trim(), pass)
                    vm.startNtrip(
                        NtripConfig(
                            host = host.trim(),
                            port = p,
                            mountPoint = mount.trim(),
                            username = user.trim(),
                            password = pass,
                        )
                    )
                },
                enabled = host.isNotBlank() && mount.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start corrections") }
        } else {
            Button(onClick = { vm.stopNtrip() }, modifier = Modifier.fillMaxWidth()) {
                Text("Stop corrections")
            }
        }
    }
}

@Composable
fun LockedFeature(name: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(name, style = MaterialTheme.typography.titleLarge)
        Text("This feature isn't included in your current license.",
            style = MaterialTheme.typography.bodyMedium)
        Text("Contact your supplier for an upgraded activation code.",
            style = MaterialTheme.typography.bodySmall)
    }
}
