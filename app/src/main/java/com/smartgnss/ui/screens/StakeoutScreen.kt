package com.smartgnss.ui.screens

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.webkit.WebView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.smartgnss.core.transport.LinkState
import com.smartgnss.ui.ReceiverViewModel
import com.smartgnss.ui.StakeTarget
import com.smartgnss.ui.theme.SurveyColors
import com.smartgnss.ui.theme.fixQualityColor
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Stake-out navigation (X-Survey style): a compact solution strip, a guidance
 * panel (forward/back, left/right, azimuth, distance, cut/fill), and either a
 * N-up compass or a satellite map with a line from the current position to the
 * target flag. Audio gets louder as you close in.
 */
@Composable
fun StakeoutScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val fix by vm.fix.collectAsState()
    val target by vm.stakeTarget.collectAsState()
    val crs by vm.crs.collectAsState()
    val points by vm.survey.points.collectAsState()
    val link by vm.link.collectAsState()
    var name by remember { mutableStateOf("STK1") }
    var east by remember { mutableStateOf("") }
    var north by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }

    val cur = vm.gridFor(fix)
    val t = target

    // Deltas from current ground position to the target grid point.
    val dE = if (t != null && cur != null) t.east - cur.east else null
    val dN = if (t != null && cur != null) t.north - cur.north else null
    val dist = if (dE != null && dN != null) hypot(dE, dN) else null
    val azimuth = if (dE != null && dN != null)
        (Math.toDegrees(atan2(dE, dN)).let { if (it < 0) it + 360 else it }) else null
    // Forward/right relative to the GNSS course (when moving).
    val heading = fix.headingDeg
    val moving = (fix.speedKmh ?: 0.0) > 1.0 && heading != null
    val fwd = if (dist != null && azimuth != null && moving)
        dist * cos(Math.toRadians(azimuth - heading!!)) else null
    val right = if (dist != null && azimuth != null && moving)
        dist * sin(Math.toRadians(azimuth - heading!!)) else null
    // Cut/fill vs design elevation.
    val curElev = cur?.orthometricHeight
    val cutFill = if (t?.designElev != null && curElev != null) curElev - t.designElev!! else null
    val onPoint = dist != null && dist < 0.03

    Column(modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {

        // --- Compact solution strip ---------------------------------------
        val connected = link == LinkState.CONNECTED
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            com.smartgnss.ui.theme.Pill(if (connected) fix.quality.label else "No receiver",
                if (connected) fixQualityColor(fix.quality) else SurveyColors.singleGray)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Text("H ${fix.hrms?.let { "%.3f".format(it) } ?: "—"}  " +
                "V ${fix.vrms?.let { "%.3f".format(it) } ?: "—"}  " +
                "${fix.satellitesUsed} sat",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // --- Target bar ---------------------------------------------------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Target: ", style = MaterialTheme.typography.titleSmall)
                    Text(t?.name ?: "none",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary)
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { showPicker = true }, enabled = points.isNotEmpty()) {
                        Text("From database", maxLines = 1, softWrap = false)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(east, { east = it }, label = { Text("East (m)") },
                        singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(north, { north = it }, label = { Text("North (m)") },
                        singleLine = true, modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            val e = east.toDoubleOrNull(); val n = north.toDoubleOrNull()
                            if (e != null && n != null) vm.setStakeTarget(StakeTarget(name, e, n))
                        },
                        enabled = east.toDoubleOrNull() != null && north.toDoubleOrNull() != null,
                    ) { Text("Set") }
                }
            }
        }

        if (t == null) {
            Text("Select a target point (from database) or type East/North to begin.",
                style = MaterialTheme.typography.bodyMedium)
        } else {
            // --- Compact guidance strip -----------------------------------
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (cutFill != null)
                            Guide(if (cutFill >= 0) "Cut" else "Fill", "%.3f m".format(abs(cutFill)))
                        if (fwd != null) Guide(if (fwd >= 0) "Fwd" else "Back", "%.2f".format(abs(fwd)))
                        if (right != null) Guide(if (right >= 0) "Right" else "Left", "%.2f".format(abs(right)))
                    }
                }
            }

            // --- View toggle: compass / map -------------------------------
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ToggleChip("Compass", !showMap) { showMap = false }
                ToggleChip("Map", showMap) { showMap = true }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text(
                    when {
                        dist == null -> "no fix"
                        onPoint -> "ON POINT ✓"
                        else -> "%s %.2f • %s %.2f".format(
                            if ((dN ?: 0.0) >= 0) "N" else "S", abs(dN ?: 0.0),
                            if ((dE ?: 0.0) >= 0) "E" else "W", abs(dE ?: 0.0))
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = if (onPoint) SurveyColors.fixedGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // --- Main view (gets all remaining height) --------------------
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (!showMap) {
                    StakeCompass(azimuth, dist, onPoint, Modifier.fillMaxSize())
                } else {
                    StakeMap(fix.latitude, fix.longitude, t.lat, t.lon, Modifier.fillMaxSize())
                    if (t.lat == null)
                        Text("Typed E/N target has no map position — pick from database.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.TopCenter)
                                .background(Color(0x99000000)).padding(4.dp))
                }
            }

            // --- Stakeout audio -------------------------------------------
            // btn_trim_centre beeps while 3 cm ≤ dist < 3 m (faster as you near);
            // on point (< 3 cm) that stops and sce_waring loops continuously.
            val distState = androidx.compose.runtime.rememberUpdatedState(dist)
            val onPointState = androidx.compose.runtime.rememberUpdatedState(onPoint)
            LaunchedEffect(Unit) {
                while (true) {
                    val d = distState.value; val op = onPointState.value
                    if (op || d == null) kotlinx.coroutines.delay(200)
                    else if (d in 0.03..3.0) {
                        // Louder and faster as the point gets closer.
                        val near = 1.0 - (d / 3.0)            // 0 at 3 m … ~1 near point
                        vm.audio.stakeApproach((0.35 + 0.65 * near).toFloat())
                        kotlinx.coroutines.delay((120 + (d / 3.0) * 780).toLong().coerceIn(120, 900))
                    } else kotlinx.coroutines.delay(300)
                }
            }
            LaunchedEffect(onPoint) {
                if (onPoint) vm.audio.stakeOnPointStart() else vm.audio.stakeOnPointStop()
            }
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose { vm.audio.stakeOnPointStop() }
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
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    vm.beepClick()
                                    name = p.name
                                    east = "%.4f".format(en.first)
                                    north = "%.4f".format(en.second)
                                    vm.setStakeTarget(StakeTarget(p.name, en.first, en.second,
                                        lat = p.latitude, lon = p.longitude,
                                        designElev = p.altitude))
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
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) { Text(label, color = fg, style = MaterialTheme.typography.labelLarge) }
}

/** N-up compass: ring with degree ticks, an arrow pointing to the target, and a
 *  flag at the target (held at a fixed radius; the numeric distance is exact). */
@Composable
private fun StakeCompass(azimuthDeg: Double?, distM: Double?, onPoint: Boolean, modifier: Modifier) {
    val ctx = LocalContext.current
    val flagRed = remember { BitmapFactory.decodeResource(ctx.resources, com.smartgnss.R.drawable.ic_flag_red) }
    val flagGreen = remember { BitmapFactory.decodeResource(ctx.resources, com.smartgnss.R.drawable.ic_flag_green) }
    val ringColor = Color(0xFF2B2B2B)
    val tickColor = Color(0xFF9AA0A6)
    val red = Color(0xFFD32F2F)
    val blue = SurveyColors.brandBlue
    val green = SurveyColors.fixedGreen

    Card(modifier) {
        Canvas(Modifier.fillMaxSize().padding(16.dp)) {
            val cx = size.width / 2f; val cy = size.height / 2f
            val R = minOf(cx, cy) * 0.92f
            drawCircle(ringColor, R, Offset(cx, cy), style = Stroke(6f))

            val paint = android.graphics.Paint().apply {
                isAntiAlias = true; textSize = R * 0.085f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            // Degree ticks every 30°, cardinal letters in red.
            for (deg in 0 until 360 step 30) {
                val a = Math.toRadians((deg - 90).toDouble())
                val outer = R; val inner = R - R * 0.06f
                val x1 = cx + outer * cos(a).toFloat(); val y1 = cy + outer * sin(a).toFloat()
                val x2 = cx + inner * cos(a).toFloat(); val y2 = cy + inner * sin(a).toFloat()
                drawLine(tickColor, Offset(x1, y1), Offset(x2, y2), 3f)
                val lr = R - R * 0.17f
                val lx = cx + lr * cos(a).toFloat(); val ly = cy + lr * sin(a).toFloat() + paint.textSize / 3
                val label = when (deg) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> "$deg°" }
                paint.color = if (deg % 90 == 0) red.toArgb() else tickColor.toArgb()
                paint.isFakeBoldText = deg % 90 == 0
                drawContext.canvas.nativeCanvas.drawText(label, lx, ly, paint)
            }

            // You (centre).
            drawCircle(blue, R * 0.045f, Offset(cx, cy))

            if (azimuthDeg != null && distM != null) {
                val a = Math.toRadians(azimuthDeg - 90)
                val rr = R * 0.72f
                val tx = cx + rr * cos(a).toFloat(); val ty = cy + rr * sin(a).toFloat()
                // Arrow from centre to target.
                drawLine(if (onPoint) green else red, Offset(cx, cy), Offset(tx, ty), 10f)
                // Flag bitmap at the target.
                val img = if (onPoint) flagGreen else flagRed
                val fw = R * 0.22f
                if (img != null) drawContext.canvas.nativeCanvas.drawBitmap(
                    img,
                    null,
                    android.graphics.RectF(tx - fw / 2, ty - fw, tx + fw / 2, ty),
                    null,
                )
                // Distance in the centre.
                paint.color = (if (onPoint) green else ringColor).toArgb()
                paint.textSize = R * 0.14f; paint.isFakeBoldText = true
                drawContext.canvas.nativeCanvas.drawText(
                    "%.3f m".format(distM), cx, cy + R * 0.32f, paint)
            }
        }
    }
}

/** Satellite map (Esri World Imagery via Leaflet, no API key) with the current
 *  position, the target flag, and a line between them. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun StakeMap(curLat: Double?, curLon: Double?, tLat: Double?, tLon: Double?, modifier: Modifier) {
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
                    settings.mixedContentMode =
                        android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    setBackgroundColor(android.graphics.Color.DKGRAY)
                    webChromeClient = android.webkit.WebChromeClient()
                    webRef[0] = this
                    loadDataWithBaseURL(
                        "https://smartgnss.local/",
                        mapHtml(tLat, tLon), "text/html", "utf-8", null)
                }
            },
            // After layout, Leaflet needs its size recalculated or tiles stay blank.
            update = { w -> w.postDelayed({ w.evaluateJavascript("fixSize();", null) }, 400) },
        )
    }
    // Push live current position to the map.
    LaunchedEffect(curLat, curLon) {
        val w = webRef[0]
        if (w != null && curLat != null && curLon != null) {
            w.evaluateJavascript("setCurrent($curLat,$curLon);", null)
        }
    }
}

private fun mapHtml(tLat: Double?, tLon: Double?): String {
    val centerLat = tLat ?: 31.9
    val centerLon = tLon ?: 35.2
    val targetJs = if (tLat != null && tLon != null)
        "var tgt=[$tLat,$tLon]; L.marker(tgt).addTo(map).bindPopup('Target'); " +
        "line=L.polyline([tgt,tgt],{color:'#D32F2F',weight:3}).addTo(map);"
    else "var tgt=null; line=null;"
    return """
<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css"/>
<style>html,body,#map{height:100%;margin:0}</style></head><body><div id="map"></div>
<script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
<script>
var map=L.map('map').setView([$centerLat,$centerLon],18);
L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
 {maxZoom:21,attribution:'Esri'}).addTo(map);
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
