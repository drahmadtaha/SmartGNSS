package com.smartgnss.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.smartgnss.ui.theme.Pill
import com.smartgnss.core.license.Feature
import com.smartgnss.core.nmea.FixQuality
import com.smartgnss.core.ntrip.NtripConfig
import com.smartgnss.core.ntrip.NtripState
import com.smartgnss.ui.ReceiverViewModel

/**
 * NTRIP / CORS configuration, laid out X-Survey style: a compact status header
 * with a spinning "receiving corrections" indicator on top, then a tidy account
 * form (host+port on one row, mountpoint with inline fetch, user+password), and
 * one big connect/disconnect action.
 */
@Composable
fun CorrectionsScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    if (!vm.license.has(Feature.RTK_NTRIP)) {
        LockedFeature("RTK corrections", modifier)
        return
    }

    val state by vm.ntripState.collectAsState()
    val fix by vm.fix.collectAsState()
    val rtcmBytes by vm.rtcmBytes.collectAsState()
    val ggaUplinks by vm.ggaUplinks.collectAsState()
    val lastRtcmAt by vm.lastRtcmAt.collectAsState()

    // Tick a few times a second so "flowing" / data age stay live.
    val now by produceState(initialValue = android.os.SystemClock.elapsedRealtime()) {
        while (true) {
            value = android.os.SystemClock.elapsedRealtime()
            kotlinx.coroutines.delay(400)
        }
    }
    val ageMs = if (lastRtcmAt == 0L) Long.MAX_VALUE else (now - lastRtcmAt)
    val flowing = state == NtripState.STREAMING && ageMs < 4_000

    val saved = vm.ntripDefaults
    var host by remember { mutableStateOf(saved?.host ?: "") }
    var port by remember { mutableStateOf(saved?.port?.toString() ?: "2101") }
    var mount by remember { mutableStateOf(saved?.mountPoint ?: "") }
    var user by remember { mutableStateOf(saved?.username ?: "") }
    var pass by remember { mutableStateOf(saved?.password ?: "") }
    var showPass by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---- Phone internet status (NTRIP needs a live connection) ---------
        val net = rememberNetStatus()
        val online = net != NetStatus.OFFLINE
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(net.label, if (online) Color(0xFF17A34A) else Color(0xFFEF4746))
            Spacer(Modifier.width(10.dp))
            Text(
                if (online) "Internet connected — ready for corrections"
                else "No internet — connect to Wi-Fi or mobile data to stream NTRIP",
                style = MaterialTheme.typography.bodySmall,
                color = if (online) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
            )
        }

        // ---- Status header with rotating indicator -------------------------
        StatusHeader(state, fix.quality, flowing, rtcmBytes, ggaUplinks, ageMs)

        // ---- CORS / NTRIP account form -------------------------------------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CORS / NTRIP account", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        host, { host = it }, label = { Text("IP / Domain") },
                        singleLine = true, modifier = Modifier.weight(2f),
                    )
                    OutlinedTextField(
                        port, { port = it.filter(Char::isDigit) }, label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        mount, { mount = it }, label = { Text("Mount point") },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = {
                            vm.fetchMountPoints(host.trim(), port.toIntOrNull() ?: 2101, user.trim(), pass)
                        },
                        enabled = host.isNotBlank(),
                    ) { Text("Get") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        user, { user = it }, label = { Text("User") },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        pass, { pass = it }, label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    if (showPass) "Hide password" else "Show password",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { showPass = !showPass },
                )
            }
        }

        // ---- Mountpoint picker (only after fetch) --------------------------
        val mounts by vm.mountPoints.collectAsState()
        if (mounts.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${mounts.size} mount points — tap to select",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Column(
                        Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        mounts.take(80).forEach { mp ->
                            val selected = mp.mount == mount
                            Card(
                                Modifier.fillMaxWidth().clickable { mount = mp.mount },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(mp.mount, style = MaterialTheme.typography.titleSmall)
                                    Text("${mp.format} · ${mp.identifier}",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- Connect / disconnect ------------------------------------------
        if (state == NtripState.IDLE || state == NtripState.ERROR) {
            Button(
                onClick = {
                    val p = port.toIntOrNull() ?: 2101
                    vm.saveNtrip(host.trim(), p, mount.trim(), user.trim(), pass)
                    vm.startNtrip(
                        NtripConfig(
                            host = host.trim(), port = p, mountPoint = mount.trim(),
                            username = user.trim(), password = pass,
                        )
                    )
                },
                enabled = host.isNotBlank() && mount.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Connect") }
        } else {
            Button(
                onClick = { vm.stopNtrip() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Disconnect") }
        }

        // Diagnostic hint when streaming but nothing comes back.
        if (state == NtripState.STREAMING && rtcmBytes == 0L && ggaUplinks >= 2) {
            Text(
                "Connected and sending your position, but the caster hasn't sent any " +
                    "corrections yet. If this persists, the mount point may be wrong or " +
                    "the account not authorised for it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Top status block: spinning ring + headline + live counters. */
@Composable
private fun StatusHeader(
    state: NtripState,
    quality: FixQuality,
    flowing: Boolean,
    rtcmBytes: Long,
    ggaUplinks: Int,
    ageMs: Long,
) {
    val (short, qColor) = qualityBadge(quality)
    val ringColor = if (flowing) MaterialTheme.colorScheme.primary else qColor

    val headline = when {
        flowing -> "Receiving corrections"
        state == NtripState.STREAMING -> "Waiting for corrections…"
        state == NtripState.CONNECTING -> "Connecting to caster…"
        state == NtripState.ERROR -> "Connection failed"
        else -> "Not connected"
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (flowing) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CorrectionIndicator(
                flowing = flowing,
                centerText = short,
                accent = ringColor,
                diameter = 64,
            )
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(headline, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (state == NtripState.ERROR) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface)
                Text("Solution: ${quality.label}", style = MaterialTheme.typography.bodyMedium,
                    color = qColor, fontWeight = FontWeight.Medium)
                val rtcmTxt = formatBytes(rtcmBytes)
                val ageTxt = if (flowing && ageMs != Long.MAX_VALUE)
                    " · age ${"%.1f".format(ageMs / 1000.0)} s" else ""
                Text("RTCM in: $rtcmTxt$ageTxt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("GGA sent up: $ggaUplinks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Short badge text + colour for a fix quality. */
@Composable
private fun qualityBadge(q: FixQuality): Pair<String, Color> = when (q) {
    FixQuality.RTK_FIXED -> "FIX" to Color(0xFF17A34A)   // green
    FixQuality.RTK_FLOAT -> "FLT" to Color(0xFFEA8A00)   // amber
    FixQuality.DGPS      -> "DGP" to Color(0xFF2563EB)   // blue
    FixQuality.GPS       -> "SGL" to Color(0xFF6B7280)   // grey
    FixQuality.PPS       -> "PPS" to Color(0xFF6B7280)
    FixQuality.ESTIMATED -> "DR"  to Color(0xFF6B7280)
    else                 -> "—"   to MaterialTheme.colorScheme.outline
}

private fun formatBytes(b: Long): String = when {
    b <= 0 -> "0 B"
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
    else -> "%.1f MB".format(b / (1024.0 * 1024.0))
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
