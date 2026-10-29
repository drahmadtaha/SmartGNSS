package com.smartgnss.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartgnss.ui.ReceiverViewModel

private enum class DataLink { NTRIP, INTERNAL_RADIO, EXTERNAL_RADIO, NONE }

/**
 * Rover configuration. The correction data link lives here — selecting NTRIP
 * shows the full NTRIP client (caster, mountpoints, start) inline, which is how
 * a rover gets its RTK corrections.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoverScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    var link by remember { mutableStateOf(DataLink.NTRIP) }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Rover · Data Link", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(link == DataLink.NTRIP, { link = DataLink.NTRIP }, label = { Text("NTRIP (Internet)") })
                FilterChip(link == DataLink.INTERNAL_RADIO, { link = DataLink.INTERNAL_RADIO }, label = { Text("Internal Radio") })
                FilterChip(link == DataLink.EXTERNAL_RADIO, { link = DataLink.EXTERNAL_RADIO }, label = { Text("External Radio") })
                FilterChip(link == DataLink.NONE, { link = DataLink.NONE }, label = { Text("None (RTK off)") })
            }
        }

        when (link) {
            DataLink.NTRIP -> CorrectionsScreen(vm, Modifier.weight(1f))
            DataLink.INTERNAL_RADIO, DataLink.EXTERNAL_RADIO ->
                RadioNote("Radio link selected. Radio channel/protocol configuration will be added " +
                    "for your receiver's radio module in a later stage. Use NTRIP for now.")
            DataLink.NONE ->
                RadioNote("RTK corrections are off — the rover will output autonomous / single positions.")
        }
    }
}

@Composable
private fun RadioNote(text: String) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text, Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
