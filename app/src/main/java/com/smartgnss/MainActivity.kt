package com.smartgnss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.smartgnss.ui.screens.AreaScreen
import com.smartgnss.ui.screens.CommunicationScreen
import com.smartgnss.ui.screens.CoordinateSystemScreen
import com.smartgnss.ui.screens.CorrectionsScreen
import com.smartgnss.ui.screens.DataTerminalScreen
import com.smartgnss.ui.screens.InverseScreen
import com.smartgnss.ui.screens.PointDatabaseScreen
import com.smartgnss.ui.screens.ProjectsScreen
import com.smartgnss.ui.screens.RoverScreen
import com.smartgnss.ui.screens.SettingsScreen
import com.smartgnss.ui.screens.SkyPlotScreen
import com.smartgnss.ui.screens.StakeoutScreen
import com.smartgnss.ui.screens.StatusScreen
import com.smartgnss.ui.screens.SurveyScreen
import com.smartgnss.ui.shell.MenuEntry
import com.smartgnss.ui.shell.MenuGrid
import com.smartgnss.ui.shell.PlaceholderScreen
import com.smartgnss.ui.shell.SolutionBar
import com.smartgnss.ui.shell.SplashScreen
import com.smartgnss.ui.theme.SmartGnssTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: ReceiverViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmartGnssTheme { Root(vm) } }
    }

    override fun onResume() {
        super.onResume()
        vm.license.refresh()
    }
}

@Composable
private fun Root(vm: ReceiverViewModel) {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1300); showSplash = false }

    if (showSplash) { SplashScreen(); return }

    val status by vm.license.status.collectAsState()
    if (status !is ActivationState.Activated) {
        ActivationScreen(vm, gate = true)
    } else {
        Shell(vm)
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    PROJECT("Project", Icons.Filled.Folder),
    DEVICE("Device", Icons.Filled.Bluetooth),
    SURVEY("Survey", Icons.Filled.Place),
    TOOL("Tool", Icons.Filled.Calculate),
}

/** Every reachable sub-screen, with its app-bar title. */
private enum class Sub(val title: String) {
    // Project
    PROJECTS("Projects"), COORD_SYS("Coordinate System"), CALIBRATE("Calibrate"),
    POINT_DB("Point Database"), CODE_LIB("Code Library"), IMPORT("Import"),
    EXPORT("Export / Share"), CLOUD("Cloud"), SETTINGS("Settings"),
    SURFACE("Surface / DTM"), RINEX("RINEX Convert"), LICENSE("License"),
    // Device
    COMMUNICATION("Communication"), ROVER("Rover"), BASE("Base"), STATIC("Static"),
    NMEA("NMEA Setting"), DEVICE_INFO("Device Info"), REGISTRATION("Registration"),
    DATA_TERMINAL("Data Terminal"), SATELLITES("Satellites"), NTRIP("NTRIP / Data Link"),
    // Survey
    POINT_SURVEY("Point Survey"), DETAIL_SURVEY("Detail Survey"),
    CONTROL_POINT("Control Point Survey"), POINT_STAKEOUT("Point Stakeout"),
    LINE_STAKEOUT("Line Stakeout"), CAD_STAKEOUT("CAD Stakeout"),
    ROAD_STAKEOUT("Road Stakeout"), GNSS_STATUS("GNSS Status"),
    // Tool
    INVERSE("Inverse / COGO"), AREA("Area & Perimeter"), COORD_CONV("Coord Converter"),
    CALCULATOR("Calculator"), ANGLE("Angle / Intersection"), VOLUME("Volume"),
    PARAM_CALC("Parameter Calc"), LOCALIZATION("Localization"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Shell(vm: ReceiverViewModel) {
    var tab by remember { mutableStateOf(Tab.PROJECT) }
    var sub by remember { mutableStateOf<Sub?>(null) }

    BackHandler(enabled = sub != null) { vm.beepClick(); sub = null }

    Scaffold(
        topBar = {
            Column {
                val barColors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                )
                if (sub == null) {
                    TopAppBar(title = { Text("Smart GNSS · ${tab.label}") }, colors = barColors)
                } else {
                    TopAppBar(
                        title = { Text(sub!!.title) },
                        navigationIcon = {
                            IconButton(onClick = { vm.beepClick(); sub = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = barColors,
                    )
                }
                SolutionBar(vm)
            }
        },
        bottomBar = {
            if (sub == null) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { vm.beepClick(); tab = t },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(),
                        )
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            val go: (Sub) -> Unit = { vm.beepClick(); sub = it }
            if (sub == null) {
                MenuGrid(menuFor(tab, go))
            } else {
                SubContent(vm, sub!!, onCrsApplied = { tab = Tab.PROJECT; sub = null })
            }
        }
    }
}

@Composable
private fun SubContent(vm: ReceiverViewModel, sub: Sub, onCrsApplied: () -> Unit) {
    when (sub) {
        Sub.PROJECTS -> ProjectsScreen(vm)
        Sub.COORD_SYS -> CoordinateSystemScreen(vm, onApplied = onCrsApplied)
        Sub.SETTINGS -> SettingsScreen(vm)
        Sub.POINT_DB -> PointDatabaseScreen(vm)
        Sub.POINT_SURVEY -> SurveyScreen(vm)
        Sub.POINT_STAKEOUT -> StakeoutScreen(vm)
        Sub.COMMUNICATION -> CommunicationScreen(vm)
        Sub.ROVER -> RoverScreen(vm)
        Sub.NTRIP -> CorrectionsScreen(vm)
        Sub.DATA_TERMINAL -> DataTerminalScreen(vm)
        Sub.SATELLITES -> SkyPlotScreen(vm)
        Sub.GNSS_STATUS -> StatusScreen(vm)
        Sub.LICENSE -> ActivationScreen(vm, gate = false)
        Sub.INVERSE -> InverseScreen()
        Sub.AREA -> AreaScreen(vm)
        else -> PlaceholderScreen(
            sub.title,
            "This screen is part of the next build stage. The structure is in place — " +
                "the full workflow will be added here."
        )
    }
}

private fun menuFor(tab: Tab, go: (Sub) -> Unit): List<MenuEntry> = when (tab) {
    Tab.PROJECT -> listOf(
        MenuEntry("Projects", Icons.Filled.Folder) { go(Sub.PROJECTS) },
        MenuEntry("Coordinate System", Icons.Filled.Public) { go(Sub.COORD_SYS) },
        MenuEntry("Calibrate", Icons.Filled.CenterFocusStrong) { go(Sub.CALIBRATE) },
        MenuEntry("Point Database", Icons.Filled.Storage) { go(Sub.POINT_DB) },
        MenuEntry("Code Library", Icons.Filled.LocalOffer) { go(Sub.CODE_LIB) },
        MenuEntry("Import", Icons.Filled.FileDownload) { go(Sub.IMPORT) },
        MenuEntry("Export / Share", Icons.Filled.Share) { go(Sub.EXPORT) },
        MenuEntry("Cloud", Icons.Filled.Cloud) { go(Sub.CLOUD) },
        MenuEntry("Settings", Icons.Filled.Settings) { go(Sub.SETTINGS) },
        MenuEntry("Surface / DTM", Icons.Filled.Terrain) { go(Sub.SURFACE) },
        MenuEntry("RINEX Convert", Icons.Filled.SwapHoriz) { go(Sub.RINEX) },
        MenuEntry("License", Icons.Filled.VpnKey) { go(Sub.LICENSE) },
    )
    Tab.DEVICE -> listOf(
        MenuEntry("Communication", Icons.Filled.Bluetooth) { go(Sub.COMMUNICATION) },
        MenuEntry("Rover", Icons.Filled.Place) { go(Sub.ROVER) },
        MenuEntry("Base", Icons.Filled.CellTower) { go(Sub.BASE) },
        MenuEntry("Static", Icons.Filled.Timer) { go(Sub.STATIC) },
        MenuEntry("NMEA Setting", Icons.Filled.Code) { go(Sub.NMEA) },
        MenuEntry("Device Info", Icons.Filled.Info) { go(Sub.DEVICE_INFO) },
        MenuEntry("Registration", Icons.Filled.VpnKey) { go(Sub.REGISTRATION) },
        MenuEntry("Data Terminal", Icons.Filled.Dns) { go(Sub.DATA_TERMINAL) },
        MenuEntry("NTRIP / Data Link", Icons.Filled.Router) { go(Sub.NTRIP) },
        MenuEntry("Satellites", Icons.Filled.SatelliteAlt) { go(Sub.SATELLITES) },
    )
    Tab.SURVEY -> listOf(
        MenuEntry("Point Survey", Icons.Filled.Place) { go(Sub.POINT_SURVEY) },
        MenuEntry("Detail Survey", Icons.Filled.Storage) { go(Sub.DETAIL_SURVEY) },
        MenuEntry("Control Point", Icons.Filled.ChangeHistory) { go(Sub.CONTROL_POINT) },
        MenuEntry("Point Stakeout", Icons.Filled.Flag) { go(Sub.POINT_STAKEOUT) },
        MenuEntry("Line Stakeout", Icons.Filled.Timeline) { go(Sub.LINE_STAKEOUT) },
        MenuEntry("CAD Stakeout", Icons.Filled.Layers) { go(Sub.CAD_STAKEOUT) },
        MenuEntry("Road Stakeout", Icons.Filled.Route) { go(Sub.ROAD_STAKEOUT) },
        MenuEntry("GNSS Status", Icons.Filled.SatelliteAlt) { go(Sub.GNSS_STATUS) },
    )
    Tab.TOOL -> listOf(
        MenuEntry("Inverse / COGO", Icons.Filled.Straighten) { go(Sub.INVERSE) },
        MenuEntry("Area & Perimeter", Icons.Filled.SquareFoot) { go(Sub.AREA) },
        MenuEntry("Coord Converter", Icons.Filled.SwapHoriz) { go(Sub.COORD_CONV) },
        MenuEntry("Calculator", Icons.Filled.Calculate) { go(Sub.CALCULATOR) },
        MenuEntry("Angle / Intersect", Icons.Filled.ChangeHistory) { go(Sub.ANGLE) },
        MenuEntry("Volume", Icons.Filled.Terrain) { go(Sub.VOLUME) },
        MenuEntry("Parameter Calc", Icons.Filled.Functions) { go(Sub.PARAM_CALC) },
        MenuEntry("Localization", Icons.Filled.MoreHoriz) { go(Sub.LOCALIZATION) },
    )
}
