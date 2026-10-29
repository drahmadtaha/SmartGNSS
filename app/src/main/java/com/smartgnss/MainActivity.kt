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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.smartgnss.ui.screens.SettingsScreen
import com.smartgnss.ui.screens.SkyPlotScreen
import com.smartgnss.ui.screens.StakeoutScreen
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
                when (status) {
                    is ActivationState.Activated -> MainScaffold(vm)
                    else -> ActivationScreen(vm, gate = true)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.license.refresh()
    }
}

private enum class Screen(val label: String, val icon: ImageVector, val primary: Boolean) {
    STATUS("Status", Icons.Filled.Dashboard, true),
    CONNECT("Connect", Icons.Filled.Link, true),
    RTK("Corrections", Icons.Filled.SatelliteAlt, true),
    SURVEY("Survey", Icons.Filled.Place, true),
    STAKEOUT("Stake-out", Icons.Filled.PushPin, true),
    SKY("Sky plot", Icons.Filled.SatelliteAlt, false),
    SETTINGS("Settings", Icons.Filled.Settings, false),
    LICENSE("License", Icons.Filled.Key, false),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(vm: ReceiverViewModel) {
    var screen by remember { mutableStateOf(Screen.STATUS) }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart GNSS") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        Screen.entries.filter { !it.primary }.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label) },
                                leadingIcon = { Icon(s.icon, contentDescription = null) },
                                onClick = { screen = s; menuOpen = false },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Screen.entries.filter { it.primary }.forEach { s ->
                    NavigationBarItem(
                        selected = screen == s,
                        onClick = { screen = s },
                        icon = { Icon(s.icon, contentDescription = s.label) },
                        label = { Text(s.label) },
                    )
                }
            }
        },
    ) { padding ->
        val mod = Modifier.padding(padding)
        when (screen) {
            Screen.STATUS -> StatusScreen(vm, mod)
            Screen.CONNECT -> ConnectScreen(vm, mod)
            Screen.RTK -> CorrectionsScreen(vm, mod)
            Screen.SURVEY -> SurveyScreen(vm, mod)
            Screen.STAKEOUT -> StakeoutScreen(vm, mod)
            Screen.SKY -> SkyPlotScreen(vm, mod)
            Screen.SETTINGS -> SettingsScreen(vm, mod)
            Screen.LICENSE -> ActivationScreen(vm, gate = false, modifier = mod)
        }
    }
}
