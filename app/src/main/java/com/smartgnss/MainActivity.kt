package com.smartgnss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.smartgnss.core.license.ActivationState
import com.smartgnss.ui.ReceiverViewModel
import com.smartgnss.ui.screens.ActivationScreen
import com.smartgnss.ui.screens.ConnectScreen
import com.smartgnss.ui.screens.CorrectionsScreen
import com.smartgnss.ui.screens.StatusScreen
import com.smartgnss.ui.screens.SurveyScreen
import com.smartgnss.ui.theme.SmartGnssTheme

class MainActivity : ComponentActivity() {

    private val vm: ReceiverViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartGnssTheme {
                val status by vm.license.status.collectAsState()
                // The app is gated on activation: nothing works until a valid
                // code is entered, which is how you control distribution.
                when (status) {
                    is ActivationState.Activated -> MainScaffold(vm)
                    else -> ActivationScreen(vm, gate = true)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.license.refresh()   // re-check expiry even if the app stayed open
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    STATUS("Status", Icons.Filled.Dashboard),
    CONNECT("Connect", Icons.Filled.Link),
    RTK("Corrections", Icons.Filled.SatelliteAlt),
    SURVEY("Survey", Icons.Filled.Place),
    LICENSE("License", Icons.Filled.Key),
}

@Composable
private fun MainScaffold(vm: ReceiverViewModel) {
    var tab by remember { mutableStateOf(Tab.STATUS) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        }
    ) { padding ->
        val mod = Modifier.padding(padding)
        when (tab) {
            Tab.STATUS -> StatusScreen(vm, mod)
            Tab.CONNECT -> ConnectScreen(vm, mod)
            Tab.RTK -> CorrectionsScreen(vm, mod)
            Tab.SURVEY -> SurveyScreen(vm, mod)
            Tab.LICENSE -> ActivationScreen(vm, gate = false, modifier = mod)
        }
    }
}
