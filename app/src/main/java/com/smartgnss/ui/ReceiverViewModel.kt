package com.smartgnss.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartgnss.core.geo.CoordinateSystem
import com.smartgnss.core.geo.GeoidModel
import com.smartgnss.core.license.Feature
import com.smartgnss.core.license.LicenseManager
import com.smartgnss.core.nmea.GnssFix
import com.smartgnss.core.nmea.NmeaParser
import com.smartgnss.core.ntrip.MountPoint
import com.smartgnss.core.ntrip.NtripClient
import com.smartgnss.core.ntrip.NtripConfig
import com.smartgnss.core.ntrip.NtripSourceTable
import com.smartgnss.core.ntrip.NtripState
import com.smartgnss.core.survey.SurveyStore
import com.smartgnss.core.transport.LinkState
import com.smartgnss.core.transport.Transport
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single source of truth for the live receiver session. Screens observe its
 * flows; it owns the transport, folds NMEA into [fix], and pumps NTRIP
 * corrections into the receiver.
 */
class ReceiverViewModel(app: Application) : AndroidViewModel(app) {

    val license = LicenseManager(app)
    val survey = SurveyStore()
    val projects = com.smartgnss.core.project.ProjectStore(app)

    private val parser = NmeaParser()

    private val _fix = MutableStateFlow(GnssFix())
    val fix = _fix.asStateFlow()

    private val _link = MutableStateFlow(LinkState.DISCONNECTED)
    val link = _link.asStateFlow()

    private val _linkLabel = MutableStateFlow("")
    val linkLabel = _linkLabel.asStateFlow()

    private val _ntripState = MutableStateFlow(NtripState.IDLE)
    val ntripState = _ntripState.asStateFlow()

    // --- Coordinate system / geoid / stakeout state --------------------------

    val settings = com.smartgnss.core.settings.SettingsStore(app)

    private val _crs = MutableStateFlow(
        settings.loadCrs()?.let { CoordinateSystem(it) } ?: CoordinateSystem.UTM_36N
    )
    val crs = _crs.asStateFlow()
    fun setCrs(c: CoordinateSystem) { _crs.value = c; settings.saveCrs(c.def) }

    private val _geoid = MutableStateFlow(settings.loadGeoid())
    val geoid = _geoid.asStateFlow()
    fun setGeoid(g: GeoidModel) { _geoid.value = g; settings.saveGeoid(g) }

    /** NTRIP values to pre-fill the Corrections screen. */
    val ntripDefaults = settings.loadNtrip()
    fun saveNtrip(host: String, port: Int, mount: String, user: String, pass: String) {
        settings.saveNtrip(
            com.smartgnss.core.settings.NtripSaved(host, port, mount, user, pass)
        )
    }

    /** Copy a picked geoid file into app storage, load (GTX/ISG/SGF), and persist it. */
    fun loadGeoidFromUri(uri: android.net.Uri): String? {
        return try {
            val ctx = getApplication<android.app.Application>()
            val name = (uri.lastPathSegment ?: "geoid.dat").substringAfterLast('/')
            val dest = java.io.File(ctx.filesDir, "geoid_$name")
            val input = ctx.contentResolver.openInputStream(uri)
                ?: return "Cannot open file"
            input.use { inp -> dest.outputStream().use { inp.copyTo(it) } }
            setGeoid(GeoidModel.Grid.loadAny(dest))
            null
        } catch (e: Exception) { e.message ?: "Load failed" }
    }

    // --- Tilt measurement (UM982 IMU) ---------------------------------------

    private val _tiltMode = MutableStateFlow(false)
    val tiltMode = _tiltMode.asStateFlow()

    /** Toggle tilt compensation and send the matching UM982 commands. */
    fun setTiltMode(on: Boolean) {
        _tiltMode.value = on
        com.smartgnss.core.device.Um982.tilt(on).forEach { sendCommand(it) }
    }

    /** Send the UM982 rover + NMEA + heading configuration to the receiver. */
    fun configureReceiver() {
        com.smartgnss.core.device.Um982.configureRover().forEach { sendCommand(it) }
    }

    private val _mountPoints = MutableStateFlow<List<MountPoint>>(emptyList())
    val mountPoints = _mountPoints.asStateFlow()

    private val _stakeTarget = MutableStateFlow<StakeTarget?>(null)
    val stakeTarget = _stakeTarget.asStateFlow()
    fun setStakeTarget(t: StakeTarget?) { _stakeTarget.value = t }

    /** Project the current fix into the selected grid, with both heights. */
    fun gridFor(fix: GnssFix): GridResult? {
        val lat = fix.latitude ?: return null
        val lon = fix.longitude ?: return null
        val hEllipsoidal = (fix.altitudeMsl ?: 0.0) + (fix.geoidSep ?: 0.0)
        val (e, n) = _crs.value.toGrid(lat, lon, hEllipsoidal)
        val sep = _geoid.value.separation(lat, lon)
        val ortho = if (sep != null) hEllipsoidal - sep else null
        return GridResult(e, n, hEllipsoidal, ortho)
    }

    fun fetchMountPoints(host: String, port: Int, user: String, pass: String) {
        viewModelScope.launch {
            _mountPoints.value = try {
                NtripSourceTable.fetch(host, port, user, pass)
            } catch (_: Exception) { emptyList() }
        }
    }

    private var transport: Transport? = null
    private var readJob: Job? = null
    private var ntripJob: Job? = null
    private var ntrip: NtripClient? = null
    private var lastGga: String? = null

    /** Last full GGA line we saw, so NTRIP VRS can be fed our position. */
    private val ggaBuffer = StringBuilder()

    fun connect(t: Transport) {
        disconnect()
        transport = t
        _linkLabel.value = t.description
        readJob = viewModelScope.launch {
            launch { t.state.collect { _link.value = it } }
            launch {
                t.incoming.collect { bytes ->
                    extractGga(bytes)
                    parser.feed(bytes).forEach { _fix.value = it }
                }
            }
            try { t.connect() } catch (_: Exception) { _link.value = LinkState.ERROR }
        }
    }

    fun disconnect() {
        stopNtrip()
        readJob?.cancel(); readJob = null
        transport?.close(); transport = null
        _link.value = LinkState.DISCONNECTED
        _linkLabel.value = ""
    }

    // --- NTRIP / RTK ---------------------------------------------------------

    fun startNtrip(config: NtripConfig) {
        if (!license.has(Feature.RTK_NTRIP)) return   // feature gate
        stopNtrip()
        val client = NtripClient(config)
        ntrip = client
        ntripJob = viewModelScope.launch {
            launch { client.state.collect { _ntripState.value = it } }
            // Periodically push our GGA up to the caster (VRS).
            launch {
                while (true) {
                    lastGga?.let { client.sendGga(it) }
                    kotlinx.coroutines.delay(10_000)
                }
            }
            try {
                client.stream { rtcm -> viewModelScope.launch { transport?.write(rtcm) } }
            } catch (_: Exception) { _ntripState.value = NtripState.ERROR }
        }
    }

    fun stopNtrip() {
        ntripJob?.cancel(); ntripJob = null
        ntrip?.close(); ntrip = null
        _ntripState.value = NtripState.IDLE
    }

    /** Send an arbitrary command line to the receiver (config console). */
    fun sendCommand(line: String) {
        val data = (line.trimEnd() + "\r\n").toByteArray(Charsets.US_ASCII)
        viewModelScope.launch { transport?.write(data) }
    }

    private fun extractGga(bytes: ByteArray) {
        for (b in bytes) {
            val c = b.toInt().toChar()
            if (c == '\n' || c == '\r') {
                val line = ggaBuffer.toString()
                if (line.contains("GGA")) lastGga = line
                ggaBuffer.setLength(0)
            } else {
                ggaBuffer.append(c)
                if (ggaBuffer.length > 200) ggaBuffer.setLength(0)
            }
        }
    }

    override fun onCleared() { disconnect() }
}

/** Grid projection of a fix: East/North plus ellipsoidal and orthometric height. */
data class GridResult(
    val east: Double,
    val north: Double,
    val ellipsoidalHeight: Double,
    val orthometricHeight: Double?,
)

/** A point to stake out, stored in the selected grid's East/North. */
data class StakeTarget(
    val name: String,
    val east: Double,
    val north: Double,
)
