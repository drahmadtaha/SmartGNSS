package com.smartgnss.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.smartgnss.ui.ReceiverViewModel
import com.smartgnss.ui.StakeTarget
import com.smartgnss.ui.theme.SurveyColors
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private enum class MapSource(val label: String) { GOOGLE_SAT("Satellite"), GOOGLE_STD("Standard"), OSM("OpenStreetMap") }

private fun tileSourceFor(s: MapSource) = when (s) {
    MapSource.OSM -> TileSourceFactory.MAPNIK
    MapSource.GOOGLE_SAT -> googleSource(true)
    MapSource.GOOGLE_STD -> googleSource(false)
}

private fun googleSource(sat: Boolean) = object : OnlineTileSourceBase(
    if (sat) "GoogleSat" else "GoogleStd", 0, 21, 256, "",
    arrayOf("https://mt1.google.com/vt/")) {
    override fun getTileURLString(idx: Long): String {
        val lyr = if (sat) "s" else "m"
        return "https://mt1.google.com/vt/lyrs=$lyr&x=${MapTileIndex.getX(idx)}" +
            "&y=${MapTileIndex.getY(idx)}&z=${MapTileIndex.getZoom(idx)}"
    }
}

@Composable
fun StakeoutScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fix by vm.fix.collectAsState()
    val target by vm.stakeTarget.collectAsState()
    val crs by vm.crs.collectAsState()
    val points by vm.survey.points.collectAsState()
    val tilt by vm.tiltMode.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    var mapSource by remember { mutableStateOf(MapSource.GOOGLE_SAT) }
    var showMapMenu by remember { mutableStateOf(false) }
    var showCalib by remember { mutableStateOf(false) }
    var showMarkers by remember { mutableStateOf(true) }

    val heading by rememberDeviceHeading()
    val cur = vm.gridFor(fix)
    val t = target

    fun selectPoint(p: com.smartgnss.core.survey.SurveyPoint) {
        val en = crs.toGrid(p.latitude, p.longitude, p.ellipsoidalHeight ?: p.altitude ?: 0.0)
        vm.setStakeTarget(StakeTarget(p.name, en.first, en.second,
            lat = p.latitude, lon = p.longitude, designElev = p.altitude))
    }
    fun step(delta: Int) {
        if (points.isEmpty()) return
        val idx = points.indexOfFirst { it.name == t?.name }
        val next = ((if (idx < 0) 0 else idx) + delta).mod(points.size)
        selectPoint(points[next])
    }

    val dE = if (t != null && cur != null) t.east - cur.east else null
    val dN = if (t != null && cur != null) t.north - cur.north else null
    val dist = if (dE != null && dN != null) hypot(dE, dN) else null
    val azimuth = if (dE != null && dN != null)
        (Math.toDegrees(atan2(dE, dN)).let { if (it < 0) it + 360 else it }) else null
    val curElev = cur?.orthometricHeight
    val cutFill = if (t?.designElev != null && curElev != null) curElev - t.designElev!! else null
    val onPoint = dist != null && dist < 0.03

    Column(modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {

        // --- Target + Prev/Next + type (compact) ----------------------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Target: ", style = MaterialTheme.typography.titleSmall)
                    Text(t?.name ?: "none", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.beepClick(); step(-1) }, enabled = points.isNotEmpty(),
                        modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Filled.NavigateBefore, "Previous point")
                    }
                    IconButton(onClick = { vm.beepClick(); step(1) }, enabled = points.isNotEmpty(),
                        modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Filled.NavigateNext, "Next point")
                    }
                    OutlinedButton(onClick = { showPicker = true }, enabled = points.isNotEmpty(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("From database", maxLines = 1, softWrap = false)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Type:", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StakeTypeChip("Normal", !tilt, null) { vm.setTiltMode(false) }
                    StakeTypeChip("Tilt", tilt,
                        if (fix.tiltValid) com.smartgnss.R.drawable.ic_tilt_ok
                        else com.smartgnss.R.drawable.ic_tilt_warn) { vm.setTiltMode(true) }
                }
            }
        }

        if (t == null) {
            Text("Pick a target point from the database (or use ◀ ▶) to begin.",
                style = MaterialTheme.typography.bodyMedium)
        } else {
            // --- Guidance (compact, no fwd/back/left/right) ---------------
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Guide("Distance", dist?.let { "%.3f m".format(it) } ?: "—", true, onPoint)
                        Guide("Azimuth", azimuth?.let { "%.1f°".format(it) } ?: "—")
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Guide(if ((dN ?: 0.0) >= 0) "Go North" else "Go South",
                            dN?.let { "%.3f m".format(abs(it)) } ?: "—")
                        Guide(if ((dE ?: 0.0) >= 0) "Go East" else "Go West",
                            dE?.let { "%.3f m".format(abs(it)) } ?: "—")
                    }
                    Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        if (cutFill != null)
                            Guide(if (cutFill >= 0) "Cut" else "Fill", "%.3f m".format(abs(cutFill)))
                        Guide("Status", if (onPoint) "ON POINT" else "staking", accent = onPoint)
                    }
                }
            }

            // --- Toggle + layers/calibrate/markers ------------------------
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ToggleChip("Compass", !showMap) { showMap = false }
                ToggleChip("Map", showMap) { showMap = true }
                Spacer(Modifier.weight(1f))
                if (showMap) {
                    Box {
                        OutlinedButton(onClick = { showMapMenu = true },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                            Icon(Icons.Filled.Layers, "Map source", Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp)); Text(mapSource.label, maxLines = 1, softWrap = false)
                        }
                        DropdownMenu(showMapMenu, onDismissRequest = { showMapMenu = false }) {
                            MapSource.entries.forEach { s ->
                                DropdownMenuItem(text = { Text(s.label) },
                                    onClick = { mapSource = s; showMapMenu = false })
                            }
                        }
                    }
                } else {
                    OutlinedButton(onClick = { showCalib = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Icon(Icons.Filled.Explore, "Calibrate", Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp)); Text("Calibrate", maxLines = 1, softWrap = false)
                    }
                }
            }

            // --- Main view ------------------------------------------------
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (!showMap) {
                    StakeCompass(azimuth, dist, onPoint, heading, Modifier.fillMaxSize())
                } else {
                    StakeMapOsm(fix.latitude, fix.longitude, t.lat, t.lon, mapSource,
                        showMarkers, { showMarkers = !showMarkers }, Modifier.fillMaxSize())
                }
            }

            // --- Audio ----------------------------------------------------
            val distState = rememberUpdatedState(dist)
            val onPointState = rememberUpdatedState(onPoint)
            LaunchedEffect(Unit) {
                while (true) {
                    val d = distState.value; val op = onPointState.value
                    if (op || d == null) kotlinx.coroutines.delay(200)
                    else if (d in 0.03..3.0) {
                        vm.audio.stakeApproach((0.35 + 0.65 * (1.0 - d / 3.0)).toFloat())
                        kotlinx.coroutines.delay((120 + (d / 3.0) * 780).toLong().coerceIn(120, 900))
                    } else kotlinx.coroutines.delay(300)
                }
            }
            LaunchedEffect(onPoint) {
                if (onPoint) vm.audio.stakeOnPointStart() else vm.audio.stakeOnPointStop()
            }
            DisposableEffect(Unit) { onDispose { vm.audio.stakeOnPointStop() } }
        }

        // --- Measure bar: record the staked position as a point -----------
        Surface(tonalElevation = 3.dp, shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val canMeasure = fix.latitude != null
                Box(contentAlignment = Alignment.Center,
                    modifier = Modifier.size(52.dp).clip(CircleShape)
                        .background(if (canMeasure) SurveyColors.fixedGreen else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = canMeasure) {
                            val p = vm.recordPoint(nextFreeName(vm.survey.points.value, "PT1"), "")
                            if (p != null) { vm.beepMeasure()
                                Toast.makeText(context, "Recorded ${p.name}", Toast.LENGTH_SHORT).show()
                            } else vm.beepClick()
                        }) {
                    Icon(Icons.Filled.GpsFixed, "Measure", tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Text("Measure point", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text("${points.size} saved", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Select target from database") },
            text = {
                if (points.isEmpty()) Text("No saved points.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(points.reversed()) { p ->
                        val en = crs.toGrid(p.latitude, p.longitude, p.ellipsoidalHeight ?: p.altitude ?: 0.0)
                        Row(Modifier.fillMaxWidth()
                            .clickable { vm.beepClick(); selectPoint(p); showPicker = false }
                            .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(p.name, style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, softWrap = false, modifier = Modifier.width(64.dp))
                            Text("E ${"%.3f".format(en.first)}  N ${"%.3f".format(en.second)}",
                                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Close") } },
        )
    }

    if (showCalib) CompassCalibrationDialog { showCalib = false }
}

/** Native osmdroid map with current/target markers, a line between them, and
 *  zoom / fit / locate / markers-toggle controls. */
@Composable
private fun StakeMapOsm(curLat: Double?, curLon: Double?, tLat: Double?, tLon: Double?,
                        source: MapSource, showMarkers: Boolean, toggleMarkers: () -> Unit,
                        modifier: Modifier) {
    val ctx = LocalContext.current
    val mapRef = remember { arrayOfNulls<MapView>(1) }
    val tMarker = remember { arrayOfNulls<Marker>(1) }
    val cMarker = remember { arrayOfNulls<Marker>(1) }
    val lineRef = remember { arrayOfNulls<Polyline>(1) }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                Configuration.getInstance().apply {
                    userAgentValue = c.packageName
                    osmdroidBasePath = c.cacheDir
                    osmdroidTileCache = java.io.File(c.cacheDir, "osmtiles")
                }
                MapView(c).apply {
                    setMultiTouchControls(true)
                    setTileSource(tileSourceFor(source))
                    controller.setZoom(18.0)
                    if (tLat != null && tLon != null) controller.setCenter(GeoPoint(tLat, tLon))
                    else if (curLat != null && curLon != null) controller.setCenter(GeoPoint(curLat, curLon))
                    mapRef[0] = this
                    onResume()
                }
            },
            update = { m ->
                m.setTileSource(tileSourceFor(source))
                // Target flag
                if (showMarkers && tLat != null && tLon != null) {
                    val mk = tMarker[0] ?: Marker(m).also {
                        it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        it.icon = ContextCompat.getDrawable(ctx, com.smartgnss.R.drawable.ic_flag_red)
                        it.setInfoWindow(null); tMarker[0] = it; m.overlays.add(it)
                    }
                    mk.position = GeoPoint(tLat, tLon)
                } else tMarker[0]?.let { m.overlays.remove(it); tMarker[0] = null }
                // Current position dot
                if (showMarkers && curLat != null && curLon != null) {
                    val mk = cMarker[0] ?: Marker(m).also {
                        it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        it.icon = dotDrawable(0xFF0A6CBF.toInt())
                        it.setInfoWindow(null); cMarker[0] = it; m.overlays.add(it)
                    }
                    mk.position = GeoPoint(curLat, curLon)
                } else cMarker[0]?.let { m.overlays.remove(it); cMarker[0] = null }
                // Line
                if (showMarkers && tLat != null && tLon != null && curLat != null && curLon != null) {
                    val ln = lineRef[0] ?: Polyline(m).also {
                        it.outlinePaint.color = 0xFFD32F2F.toInt(); it.outlinePaint.strokeWidth = 7f
                        lineRef[0] = it; m.overlays.add(it)
                    }
                    ln.setPoints(listOf(GeoPoint(curLat, curLon), GeoPoint(tLat, tLon)))
                } else lineRef[0]?.let { m.overlays.remove(it); lineRef[0] = null }
                m.invalidate()
            },
            onRelease = { it.onDetach() },
        )

        // --- Map controls ---------------------------------------------
        Column(Modifier.align(Alignment.CenterEnd).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MapBtn(Icons.Filled.Add, "Zoom in") { mapRef[0]?.controller?.zoomIn() }
            MapBtn(Icons.Filled.Remove, "Zoom out") { mapRef[0]?.controller?.zoomOut() }
            MapBtn(Icons.Filled.ZoomOutMap, "Fit both") {
                val m = mapRef[0] ?: return@MapBtn
                if (curLat != null && curLon != null && tLat != null && tLon != null) {
                    val box = BoundingBox.fromGeoPoints(
                        listOf(GeoPoint(curLat, curLon), GeoPoint(tLat, tLon)))
                    m.zoomToBoundingBox(box.increaseByScale(1.6f), true, 60)
                } else if (tLat != null && tLon != null) m.controller.animateTo(GeoPoint(tLat, tLon))
            }
            MapBtn(Icons.Filled.MyLocation, "Locate me") {
                if (curLat != null && curLon != null)
                    mapRef[0]?.controller?.animateTo(GeoPoint(curLat, curLon))
            }
            MapBtn(Icons.Filled.Layers, if (showMarkers) "Hide markers" else "Show markers",
                tint = if (showMarkers) SurveyColors.brandBlue else null) { toggleMarkers() }
        }
    }
}

@Composable
private fun MapBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String,
                   tint: Color? = null, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape)
        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint ?: MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

private fun dotDrawable(color: Int): android.graphics.drawable.Drawable =
    android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(color); setStroke(4, 0xFFFFFFFF.toInt()); setSize(34, 34)
    }

@Composable
private fun rememberDeviceHeading(): State<Float> {
    val ctx = LocalContext.current
    val heading = remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            val rot = FloatArray(9); val ori = FloatArray(3)
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                var az = Math.toDegrees(ori[0].toDouble()).toFloat()
                if (az < 0) az += 360f
                heading.floatValue = az
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }
    return heading
}

@Composable
private fun Guide(label: String, value: String, big: Boolean = false, accent: Boolean = false) {
    Column {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, softWrap = false)
        Text(value, fontFamily = FontFamily.Monospace,
            fontSize = if (big) 17.sp else 13.sp,
            fontWeight = if (big) FontWeight.Bold else FontWeight.SemiBold,
            color = if (accent) SurveyColors.fixedGreen else MaterialTheme.colorScheme.onSurface,
            maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 7.dp)) {
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StakeTypeChip(label: String, selected: Boolean, icon: Int?, onClick: () -> Unit) {
    val bg = if (selected) SurveyColors.fixedGreen.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant
    Row(Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (icon != null) androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(icon),
            contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, color = if (selected) SurveyColors.fixedGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun StakeCompass(azimuthDeg: Double?, distM: Double?, onPoint: Boolean,
                         headingDeg: Float, modifier: Modifier) {
    val ctx = LocalContext.current
    val flagRed = remember { BitmapFactory.decodeResource(ctx.resources, com.smartgnss.R.drawable.ic_flag_red) }
    val flagGreen = remember { BitmapFactory.decodeResource(ctx.resources, com.smartgnss.R.drawable.ic_flag_green) }
    val ringColor = Color(0xFF2B2B2B); val tickColor = Color(0xFF9AA0A6)
    val red = Color(0xFFD32F2F); val blue = SurveyColors.brandBlue; val green = SurveyColors.fixedGreen

    Card(modifier) {
        Canvas(Modifier.fillMaxSize().padding(16.dp)) {
            val cx = size.width / 2f; val cy = size.height / 2f
            val R = minOf(cx, cy) * 0.92f
            val rot = -headingDeg
            drawCircle(ringColor, R, Offset(cx, cy), style = Stroke(6f))
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true; textSize = R * 0.085f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            for (deg in 0 until 360 step 30) {
                val a = Math.toRadians((deg + rot - 90).toDouble())
                val x1 = cx + R * cos(a).toFloat(); val y1 = cy + R * sin(a).toFloat()
                val x2 = cx + (R - R * 0.06f) * cos(a).toFloat(); val y2 = cy + (R - R * 0.06f) * sin(a).toFloat()
                drawLine(tickColor, Offset(x1, y1), Offset(x2, y2), 3f)
                val lr = R - R * 0.17f
                val lx = cx + lr * cos(a).toFloat(); val ly = cy + lr * sin(a).toFloat() + paint.textSize / 3
                val label = when (deg) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> "$deg°" }
                paint.color = if (deg % 90 == 0) red.toArgb() else tickColor.toArgb()
                paint.isFakeBoldText = deg % 90 == 0
                drawContext.canvas.nativeCanvas.drawText(label, lx, ly, paint)
            }
            drawCircle(blue, R * 0.045f, Offset(cx, cy))
            if (azimuthDeg != null && distM != null) {
                val a = Math.toRadians(azimuthDeg + rot - 90)
                val rr = R * 0.72f
                val tx = cx + rr * cos(a).toFloat(); val ty = cy + rr * sin(a).toFloat()
                drawLine(if (onPoint) green else red, Offset(cx, cy), Offset(tx, ty), 10f)
                val img = if (onPoint) flagGreen else flagRed
                val fw = R * 0.22f
                if (img != null) drawContext.canvas.nativeCanvas.drawBitmap(img, null,
                    android.graphics.RectF(tx - fw / 2, ty - fw, tx + fw / 2, ty), null)
                paint.color = (if (onPoint) green else ringColor).toArgb()
                paint.textSize = R * 0.14f; paint.isFakeBoldText = true
                drawContext.canvas.nativeCanvas.drawText("%.3f m".format(distM), cx, cy + R * 0.32f, paint)
            }
        }
    }
}

@Composable
private fun CompassCalibrationDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Compass calibration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("The compass rotation uses your phone's magnetometer. If the arrow " +
                    "drifts, points the wrong way, or lags, calibrate the sensor: hold the " +
                    "phone and move it through a figure-8 in the air a few times, rotating " +
                    "your wrist, until it settles.",
                    style = MaterialTheme.typography.bodyMedium)
                Figure8Demo(Modifier.fillMaxWidth())
                Text("Keep away from metal, magnets and magnetic cases. Without a magnetometer " +
                    "the dial stays north-up (the arrow still points to the point).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Got it") } },
    )
}

@Composable
private fun Figure8Demo(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "fig8")
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(2600,
                easing = androidx.compose.animation.core.LinearEasing)),
        label = "theta",
    )
    val blue = SurveyColors.brandBlue; val faint = Color(0xFF9AA0A6)
    Canvas(modifier.padding(8.dp).fillMaxWidth().height(120.dp)) {
        val cx = size.width / 2f; val cy = size.height / 2f
        val ax = minOf(cx, cy) * 1.4f; val ay = minOf(cx, cy) * 0.8f
        val path = androidx.compose.ui.graphics.Path()
        var first = true; var th = 0f
        while (th <= (2 * Math.PI).toFloat() + 0.05f) {
            val x = cx + ax * sin(th.toDouble()).toFloat()
            val y = cy + ay * sin(2 * th.toDouble()).toFloat()
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
            th += 0.1f
        }
        drawPath(path, faint, style = Stroke(4f))
        val dx = cx + ax * sin(t.toDouble()).toFloat()
        val dy = cy + ay * sin(2 * t.toDouble()).toFloat()
        drawCircle(blue, minOf(cx, cy) * 0.14f, Offset(dx, dy))
    }
}
