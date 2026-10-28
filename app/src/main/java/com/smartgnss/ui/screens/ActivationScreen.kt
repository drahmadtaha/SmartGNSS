package com.smartgnss.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.smartgnss.core.license.ActivationResult
import com.smartgnss.core.license.ActivationState
import com.smartgnss.core.license.Feature
import com.smartgnss.ui.ReceiverViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivationScreen(
    vm: ReceiverViewModel,
    gate: Boolean,
    modifier: Modifier = Modifier,
) {
    val status by vm.license.status.collectAsState()
    val clipboard = LocalClipboardManager.current
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Smart GNSS", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (gate) "Enter your activation code to start."
            else "License",
            style = MaterialTheme.typography.bodyMedium,
        )

        // Current activation state
        when (val s = status) {
            is ActivationState.Activated -> ActivatedCard(s)
            is ActivationState.Lapsed -> StateCard("Not active", s.reason, error = true)
            ActivationState.NotActivated -> StateCard("Not activated", "No code on this device.")
        }

        // Device code — what the customer sends to the factory
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This device's code", style = MaterialTheme.typography.labelLarge)
                Text(
                    vm.license.deviceCode,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(vm.license.deviceCode))
                }) { Text("Copy device code") }
                Text(
                    "Send this to your supplier to receive an activation code.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Enter / paste an activation code
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Activation code") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        Button(
            onClick = {
                message = when (val r = vm.license.activate(code)) {
                    is ActivationResult.Valid -> "Activated — thank you."
                    ActivationResult.BadFormat -> "That code isn't readable. Check for typos."
                    ActivationResult.BadSignature -> "This code is not genuine."
                    is ActivationResult.WrongDevice ->
                        "This code is for another unit (expected ${r.expected})."
                    is ActivationResult.Expired -> "This code has expired."
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = code.isNotBlank(),
        ) { Text("Activate") }

        OutlinedButton(onClick = {
            clipboard.getText()?.let { code = it.text }
        }) { Text("Paste from clipboard") }

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium)
        }

        if (!gate && status is ActivationState.Activated) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.license.deactivate() }) {
                Text("Remove license from this device")
            }
        }
    }
}

@Composable
private fun ActivatedCard(s: ActivationState.Activated) {
    val lic = s.license
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Activated", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            Text("Licensed to: ${lic.issuedTo.ifBlank { "—" }}")
            Text("License ID: ${lic.licenseId}")
            Text("Expires: " + if (lic.perpetual) "never"
                else SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .format(Date(lic.expiryEpochDays * 86_400_000L)))
            Text("Features:", style = MaterialTheme.typography.labelLarge)
            val feats = buildList {
                if (lic.hasFeature(Feature.RTK_NTRIP)) add("RTK / NTRIP")
                if (lic.hasFeature(Feature.RAW_LOGGING)) add("Raw logging")
                if (lic.hasFeature(Feature.SURVEY_EXPORT)) add("Survey export")
                if (lic.hasFeature(Feature.STATIC)) add("Static sessions")
            }
            Text(if (feats.isEmpty()) "Base" else feats.joinToString(", "))
        }
    }
}

@Composable
private fun StateCard(title: String, body: String, error: Boolean = false) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = if (error) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
