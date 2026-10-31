package com.smartgnss.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.webkit.WebView
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.smartgnss.ui.ReceiverViewModel
import com.smartgnss.ui.StakeTarget
import com.smartgnss.ui.theme.SurveyColors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private enum class MapSource(val label: String, val url: String) {
    GOOGLE_SAT("Satellite (Google)", "https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}"),
    GOOGLE_STD("Standard (Google)", "https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}"),
    OSM("OpenStreetMap", "https://tile.openstreetmap.org/{z}/{x}/{y}.png"),
}

@Composable
fun StakeoutScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val fix by vm.fix.collectAsState()
    val target by vm.stakeTarget.collectAsState()
    val crs by vm.crs.collectAsState()
    val points by vm.survey.points.collectAsState()
    val tilt by vm.tiltMode.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    var mapSource by remember { mutableStateOf(MapSource.GOOGLE_SAT) }
    var showMapMenu by remember { mutableStateOf(false) }

    val heading by rememberDeviceHeading()
    val cur = vm.gridFor(fix)
    val t = target

    val dE = if (t != null && cur != null) t.east - cur.east else null
    val dN = if (t != null && cur != null) t.north - cur.north else null
    val dist = if (dE != null && dN != null) hypot(dE, dN) else null
    val azimuth = if (dE != null && dN != null)
        (Math.toDegrees(atan2(dE, dN)).let { if (it < 0) it + 360 else it }) else null
    val curElev = cur?.orthometricHeight
    val cutFill = if (t?.designElev != null && curElev != null) curElev - t.designElev!! else null
    val onPoint = dist != null && dist < 0.03

    Column(modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {

        // --- Target + stakeout type -----------------------------------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Target: ", style = MaterialTheme.typography.titleSmall)
                    Text(t?.name ?: "none", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { showPicker = true }, enabled = points.isNotEmpty()) {
                        Text("From database", maxLines = 1, softWrap = false)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Type:", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StakeTypeChip("Normal", selected = !tilt, icon = null) { vm.setTiltMode(false) }
                    StakeTypeChip("Tilt", selected = tilt,
                        icon = if (fix.tiltValid) com.smartgnss.R.drawable.ic_tilt_ok
                               else com.smartgnss.R.drawable.ic_tilt_warn) {
                        vm.setTiltMode(true)
                    }
                }
            }
        }

        if (t == null) {
            Text("Pick a target point from the database to begin.",
                style = MaterialTheme.typography.bodyMedium)
        } else {
            // --- Compact guidance (no forward/back/left/right) ------------
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Guide("Distance", dist?.let { "%.3f m".format(it) } ?: "—",
                            big = true, accent = onPoint)
                        Guide("Azimuth", azimuth?.let { "%.1f°".format(it) } ?: "—")
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Guide(if ((dN ?: 0.0) >= 0) "Go North" else "Go South",
                            dN?.let { "%.3f m".format(abs(it)) } ?: "—")
                        Guide(if ((dE ?: 0.0) >= 0) "Go East" else "Go West",
                            dE?.let { "%.3f m".format(abs(it)) } ?: "—")
                    }
                    Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (cutFill != null)
                            Guide(if (cutFill >= 0) "Cut" else "Fill", "%.3f m".format(abs(cutFill)))
                        Guide("Status", if (onPoint) "ON POINT" else "staking", accent = onPoint)
                    }
                }
            }

            // --- View toggle + map-source picker --------------------------
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ToggleChip("Compass", !showMap) { showMap = false }
                ToggleChip("Map", showMap) { showMap = true }
                Spacer(Modifier.weight(1f))
                if (showMap) {
                    Box {
                        OutlinedButton(onClick = { showMapMenu = true }) {
                            Icon(Icons.Filled.Layers,
                                contentDescription = "Map source", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(mapSource.label.substringBefore(" "), maxLines = 1, softWrap = false)
                        }
                        DropdownMenu(showMapMenu, onDismissRequest = { showMapMenu = false }) {
                            MapSource.entries.forEach { s ->
                                DropdownMenuItem(text = { Text(s.label) },
                                    onClick = { mapSource = s; showMapMenu = false })
                            }
                        }
                    }
                }
            }

            // --- Main view (fills remaining height) -----------------------
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (!showMap) {
                    StakeCompass(azimuth, dist, onPoint, heading, Modifier.fillMaxSize())
                } else {
                    StakeMap(fix.latitude, fix.longitude, t.lat, t.lon, mapSource, Modifier.fillMaxSize())
                    if (t.lat == null)
                        Text("Typed target has no map position — pick from database.",
                            style = MaterialTheme.typography.bodySmall, color = Color.White,
                            modifier = Modifier.align(Alignment.TopCenter)
                                .background(Color(0x99000000)).padding(4.dp))
                }
            }

            // --- Stakeout audio -------------------------------------------
            val distState = rememberUpdatedState(dist)
            val onPointState = rememberUpdatedState(onPoint)
            LaunchedEffect(Unit) {
                while (true) {
                    val d = distState.value; val op = onPointState.value
                    if (op || d == null) kotlinx.coroutines.delay(200)
                    else if (d in 0.03..3.0) {
                        val near = 1.0 - (d / 3.0)
                        vm.audio.stakeApproach((0.35 + 0.65 * near).toFloat())
                        kotlinx.coroutines.delay((120 + (d / 3.0) * 780).toLong().coerceIn(120, 900))
                    } else kotlinx.coroutines.delay(300)
                }
            }
            LaunchedEffect(onPoint) {
                if (onPoint) vm.audio.stakeOnPointStart() else vm.audio.stakeOnPointStop()
            }
            DisposableEffect(Unit) { onDispose { vm.audio.stakeOnPointStop() } }
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
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    vm.beepClick()
                                    vm.setStakeTarget(StakeTarget(p.name, en.first, en.second,
                                        lat = p.latitude, lon = p.longitude, designElev = p.altitude))
                                    showPicker = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(p.name, style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, softWrap = false, modifier = Modifier.width(64.dp))
                            Text("E ${"%.3f".format(en.first)}  N ${"%.3f".format(en.second)}",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Close") } },
        )
    }
}

/** Device heading (azimuth the phone's top points, 0 = north) from the rotation
 *  vector sensor. Falls back to 0 (north-up) when no sensor is present. */
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
        Text(label, fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
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
        .padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StakeTypeChip(label: String, selected: Boolean, icon: Int?, onClick: () -> Unit) {
    val bg = if (selected) SurveyColors.fixedGreen.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant
    Row(Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (icon != null) androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(icon),
            contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label,
            color = if (selected) SurveyColors.fixedGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Compass that rotates with the phone's heading: the arrow points toward the
 *  target in real space, so you turn until it points up, then walk forward. */
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
            // Rotate the whole dial so geographic north sits at -heading: N points
            // to real north as you turn the phone.
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun StakeMap(curLat: Double?, curLon: Double?, tLat: Double?, tLon: Double?,
                     source: MapSource, modifier: Modifier) {
    val webRef = remember { arrayOfNulls<WebView>(1) }
    Card(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    setBackgroundColor(android.graphics.Color.DKGRAY)
                    webChromeClient = android.webkit.WebChromeClient()
                    webRef[0] = this
                    loadDataWithBaseURL("https://smartgnss.local/",
                        mapHtml(tLat, tLon), "text/html", "utf-8", null)
                }
            },
            update = { w -> w.postDelayed({ w.evaluateJavascript("fixSize();", null) }, 400) },
        )
    }
    LaunchedEffect(source) {
        webRef[0]?.postDelayed(
            { webRef[0]?.evaluateJavascript("setTiles('${source.url}');", null) }, 300)
    }
    LaunchedEffect(curLat, curLon) {
        val w = webRef[0]
        if (w != null && curLat != null && curLon != null)
            w.evaluateJavascript("setCurrent($curLat,$curLon);", null)
    }
}

private fun mapHtml(tLat: Double?, tLon: Double?): String {
    val centerLat = tLat ?: 31.9; val centerLon = tLon ?: 35.2
    val targetJs = if (tLat != null && tLon != null)
        "var tgt=[$tLat,$tLon]; L.marker(tgt).addTo(map).bindPopup('Target'); " +
        "line=L.polyline([tgt,tgt],{color:'#D32F2F',weight:4}).addTo(map);"
    else "var tgt=null; line=null;"
    return """
<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css"/>
<style>html,body,#map{height:100%;margin:0;background:#444}</style></head><body><div id="map"></div>
<script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
<script>
var map=L.map('map').setView([$centerLat,$centerLon],18);
var tileLayer=null;
function setTiles(url){ if(tileLayer){map.removeLayer(tileLayer);} tileLayer=L.tileLayer(url,{maxZoom:21}).addTo(map); }
setTiles('https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}');
var line; $targetJs
var cur=null;
function fixSize(){ map.invalidateSize(true); }
window.addEventListener('resize', fixSize);
setTimeout(fixSize,300); setTimeout(fixSize,900);
function setCurrent(lat,lon){
  map.invalidateSize();
  if(!cur){cur=L.circleMarker([lat,lon],{radius:7,color:'#0A6CBF',fillColor:'#0A6CBF',fillOpacity:1}).addTo(map);}
  else{cur.setLatLng([lat,lon]);}
  if(line&&tgt){line.setLatLngs([[lat,lon],tgt]); map.fitBounds(L.latLngBounds([[lat,lon],tgt]).pad(0.5));}
  else{map.setView([lat,lon]);}
}
</script></body></html>""".trimIndent()
}
