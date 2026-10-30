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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Single source of truth for the live receiver session. Screens observe its
 * flows; it owns the transport, folds NMEA into [fix], and pumps NTRIP
 * corrections into the receiver.
 */
class ReceiverViewModel(app: Application) : AndroidViewModel(app) {

    val license = LicenseManager(app)
    val survey = SurveyStore(app)
    val projects = com.smartgnss.core.project.ProjectStore(app)
    val audio = com.smartgnss.core.audio.AudioFeedback(app)

    private val parser = NmeaParser()

    private val _fix = MutableStateFlow(GnssFix())
    val fix = _fix.asStateFlow()

    /** Rolling buffer of raw bytes received from the receiver, for diagnostics. */
    private val _rawLog = MutableStateFlow("")
    val rawLog = _rawLog.asStateFlow()
    fun clearRawLog() { _rawLog.value = "" }
    private fun appendRaw(bytes: ByteArray) {
        val s = String(bytes, Charsets.ISO_8859_1)
        val combined = _rawLog.value + s
        _rawLog.value = if (combined.length > 60_000)
            combined.substring(combined.length - 60_000) else combined
    }

    private val _link = MutableStateFlow(LinkState.DISCONNECTED)
    val link = _link.asStateFlow()

    private val _linkLabel = MutableStateFlow("")
    val linkLabel = _linkLabel.asStateFlow()

    private val _ntripState = MutableStateFlow(NtripState.IDLE)
    val ntripState = _ntripState.asStateFlow()

    /** Total RTCM bytes received from the caster this session. */
    private val _rtcmBytes = MutableStateFlow(0L)
    val rtcmBytes = _rtcmBytes.asStateFlow()

    /** elapsedRealtime() of the last RTCM chunk, or 0 if none yet. The UI uses
     *  this to spin the "receiving corrections" indicator and show data age. */
    private val _lastRtcmAt = MutableStateFlow(0L)
    val lastRtcmAt = _lastRtcmAt.asStateFlow()

    /** How many GGA lines we've pushed up to the caster (VRS uplink health). */
    private val _ggaUplinks = MutableStateFlow(0)
    val ggaUplinks = _ggaUplinks.asStateFlow()

    // --- Coordinate system / geoid / stakeout state --------------------------

    val settings = com.smartgnss.core.settings.SettingsStore(app)

    private val _crs = MutableStateFlow(
        settings.loadCrs()?.let { s ->
            // Auto-heal: if the saved system shares a name with a built-in
            // template, use the current (corrected) template parameters. This
            // replaces an earlier, less-accurate "Palestine" saved before the
            // X-Survey-verified parameters were added.
            CoordinateSystem(
                com.smartgnss.core.geo.CrsDefinition.templates.firstOrNull { it.name == s.name } ?: s
            )
        } ?: CoordinateSystem.DEFAULT
    )
    val crs = _crs.asStateFlow()
    fun setCrs(c: CoordinateSystem) { _crs.value = c; settings.saveCrs(c.def) }

    private val _geoid = MutableStateFlow(settings.loadGeoid())
    val geoid = _geoid.asStateFlow()
    fun setGeoid(g: GeoidModel) { _geoid.value = g; settings.saveGeoid(g) }

    /** Pole / instrument height (m) — the measured height to the antenna
     *  reference point. Persisted so it survives restarts. */
    private val _antennaHeight = MutableStateFlow(settings.loadAntennaHeight())
    val antennaHeight = _antennaHeight.asStateFlow()
    fun setAntennaHeight(h: Double) { _antennaHeight.value = h; settings.saveAntennaHeight(h) }

    /** Antenna phase-centre offset (m): the fixed vertical distance from the
     *  antenna reference point (ARP) up to the L1 phase centre. It depends on the
     *  antenna model, not on the survey, so it is set once and added to the pole
     *  height when reducing to the ground mark. */
    private val _phaseCenterOffset = MutableStateFlow(
        com.smartgnss.core.device.DeviceModel.fromName(settings.loadDeviceModel()).phaseCenter
    )
    val phaseCenterOffset = _phaseCenterOffset.asStateFlow()
    fun setPhaseCenterOffset(h: Double) { _phaseCenterOffset.value = h; settings.savePhaseCenter(h) }

    /** Total vertical offset from the ground mark to the antenna phase centre. */
    fun effectiveAntennaHeight(): Double = _antennaHeight.value + _phaseCenterOffset.value

    /** Selected receiver model (Cube RTK / M10). Persisted. */
    private val _deviceModel = MutableStateFlow(
        com.smartgnss.core.device.DeviceModel.fromName(settings.loadDeviceModel())
    )
    val deviceModel = _deviceModel.asStateFlow()
    fun setDeviceModel(m: com.smartgnss.core.device.DeviceModel) {
        _deviceModel.value = m; settings.saveDeviceModel(m.name)
        // Each model has its own antenna phase-centre offset.
        setPhaseCenterOffset(m.phaseCenter)
    }

    /** Run the IMU tilt-calibration step (sent while the user performs the motion). */
    fun tiltCalibrationStep(step: Int) =
        sendCommand(com.smartgnss.core.device.Um982.calibrationStep(step))

    /** Live NTRIP form state. Hoisted here so it survives screen navigation and
     *  is persisted on every edit (the screen's own state was lost on leaving). */
    private val _ntripForm = MutableStateFlow(
        settings.loadNtrip()?.let {
            NtripForm(it.host, it.port.toString(), it.mountPoint, it.username, it.password)
        } ?: NtripForm()
    )
    val ntripForm = _ntripForm.asStateFlow()

    fun updateNtripForm(f: NtripForm) {
        _ntripForm.value = f
        settings.saveNtrip(
            com.smartgnss.core.settings.NtripSaved(
                f.host.trim(), f.port.toIntOrNull() ?: 2101, f.mount.trim(), f.user.trim(), f.pass
            )
        )
    }

    fun saveNtrip(host: String, port: Int, mount: String, user: String, pass: String) {
        updateNtripForm(NtripForm(host, port.toString(), mount, user, pass))
    }

    // --- NTRIP account library (several casters) -----------------------------
    private val _ntripAccounts = MutableStateFlow(settings.loadNtripAccounts())
    val ntripAccounts = _ntripAccounts.asStateFlow()

    /** Save the current form as a named account (overwrites one with the same name). */
    fun saveNtripAccount(name: String) {
        val f = _ntripForm.value
        val acc = com.smartgnss.core.settings.NtripAccount(
            name.trim(), f.host.trim(), f.port.toIntOrNull() ?: 2101,
            f.mount.trim(), f.user.trim(), f.pass,
        )
        val list = _ntripAccounts.value.filterNot { it.name.equals(acc.name, true) } + acc
        _ntripAccounts.value = list
        settings.saveNtripAccounts(list)
    }

    fun deleteNtripAccount(name: String) {
        val list = _ntripAccounts.value.filterNot { it.name == name }
        _ntripAccounts.value = list
        settings.saveNtripAccounts(list)
    }

    /** Load a saved account into the active form. */
    fun selectNtripAccount(a: com.smartgnss.core.settings.NtripAccount) {
        updateNtripForm(NtripForm(a.host, a.port.toString(), a.mountPoint, a.username, a.password))
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

    /** Toggle tilt and send AT+TILT with the current pole length (mm). */
    fun setTiltMode(on: Boolean) {
        _tiltMode.value = on
        val poleMm = (_antennaHeight.value * 1000).toInt().coerceIn(100, 6000)
        com.smartgnss.core.device.Um982.tilt(on, poleMm).forEach { sendCommand(it) }
        // Read back the state and device type so the responses show in the terminal.
        sendCommand("AT+TILT=?")
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
        // Reduce heights to the ground mark by removing the full antenna offset
        // (pole height + phase-centre offset).
        val antH = effectiveAntennaHeight()
        val groundEllip = hEllipsoidal - antH
        val ortho = if (sep != null) hEllipsoidal - sep - antH else null
        return GridResult(e, n, groundEllip, ortho)
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
                    appendRaw(bytes)
                    detectTiltState(bytes)
                    parser.feed(bytes).forEach { _fix.value = it }
                }
            }
            try {
                t.connect()
                // Once connected, ask the receiver to output NMEA/GGA so height
                // and full position flow (the ICE app does this on connect too).
                kotlinx.coroutines.delay(1200)
                com.smartgnss.core.device.Um982.enableOutput().forEach { sendCommand(it) }
                // Read the receiver's actual tilt state so we don't disable it.
                kotlinx.coroutines.delay(300)
                sendCommand("AT+TILT=?")
            } catch (_: Exception) { _link.value = LinkState.ERROR }
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
        _rtcmBytes.value = 0L
        _lastRtcmAt.value = 0L
        _ggaUplinks.value = 0
        val client = NtripClient(config)
        client.onLog = { appendRaw((it + "\n").toByteArray(Charsets.ISO_8859_1)) }
        ntrip = client
        // RTCM is forwarded to the receiver through this channel so chunks reach
        // the Bluetooth link in order, on a background thread.
        val rtcmChannel = kotlinx.coroutines.channels.Channel<ByteArray>(
            kotlinx.coroutines.channels.Channel.UNLIMITED
        )
        ntripJob = viewModelScope.launch {
            launch { client.state.collect { _ntripState.value = it } }
            launch(kotlinx.coroutines.Dispatchers.IO) {
                for (chunk in rtcmChannel) {
                    try { transport?.write(chunk) } catch (_: Exception) {}
                }
            }
            // Push our GGA up to the caster only once the stream is up, immediately
            // and then every second — VRS / nearest-base mountpoints send no
            // corrections until they have a fresh position (this is what X-Survey does).
            // MUST run on IO: a socket write on the main thread throws
            // NetworkOnMainThreadException, which silently dropped every uplink.
            launch(kotlinx.coroutines.Dispatchers.IO) {
                while (true) {
                    if (_ntripState.value == NtripState.STREAMING) {
                        val g = lastGga
                        if (g != null) {
                            client.sendGga(g)
                            _ggaUplinks.value += 1
                            // Show the exact (cleaned) GGA only on the first few sends.
                            if (_ggaUplinks.value <= 2)
                                appendRaw(">> GGA uplink: $g\n".toByteArray(Charsets.ISO_8859_1))
                            else
                                appendRaw(">> [GGA uplink to caster]\n".toByteArray(Charsets.ISO_8859_1))
                        } else {
                            appendRaw(">> [streaming but no GGA from receiver yet]\n".toByteArray(Charsets.ISO_8859_1))
                        }
                        kotlinx.coroutines.delay(1_000)
                    } else {
                        kotlinx.coroutines.delay(300)
                    }
                }
            }
            try {
                client.stream { rtcm ->
                    _rtcmBytes.value += rtcm.size
                    _lastRtcmAt.value = android.os.SystemClock.elapsedRealtime()
                    if (_rtcmBytes.value % 2048 < rtcm.size) // log roughly every ~2 KB
                        appendRaw("[RTCM from caster → receiver: ${_rtcmBytes.value} B]\n"
                            .toByteArray(Charsets.ISO_8859_1))
                    rtcmChannel.trySend(rtcm)
                }
            } catch (_: Exception) { _ntripState.value = NtripState.ERROR }
            finally { rtcmChannel.close() }
        }
    }

    fun stopNtrip() {
        ntripJob?.cancel(); ntripJob = null
        ntrip?.close(); ntrip = null
        _ntripState.value = NtripState.IDLE
        _lastRtcmAt.value = 0L
    }

    /** Send an arbitrary command line to the receiver (config console). */
    fun sendCommand(line: String) {
        val data = (line.trimEnd() + "\r\n").toByteArray(Charsets.US_ASCII)
        appendRaw("\n>> $line\n".toByteArray(Charsets.ISO_8859_1))
        viewModelScope.launch { transport?.write(data) }
    }

    /** Sync the app's tilt toggle to the receiver's real state from its
     *  "+TILT=<on>,<pole>" reply, so SmartGNSS never silently disables a tilt
     *  that was enabled/calibrated in another app. */
    private val tiltRespBuf = StringBuilder()
    private fun detectTiltState(bytes: ByteArray) {
        tiltRespBuf.append(String(bytes, Charsets.ISO_8859_1))
        if (tiltRespBuf.length > 400) tiltRespBuf.delete(0, tiltRespBuf.length - 400)
        val t = tiltRespBuf.toString()
        val i = t.lastIndexOf("+TILT=")
        if (i >= 0 && i + 6 < t.length) {
            when (t[i + 6]) {
                '1' -> _tiltMode.value = true
                '0' -> _tiltMode.value = false
            }
        }
    }

    private fun extractGga(bytes: ByteArray) {
        for (b in bytes) {
            val c = b.toInt().toChar()
            if (c == '\n' || c == '\r') {
                cleanGga(ggaBuffer.toString())?.let { lastGga = it }
                ggaBuffer.setLength(0)
            } else {
                ggaBuffer.append(c)
                // The UM982 interleaves binary logs with NMEA; keep only the tail
                // from the last '$' so a GGA that follows binary isn't lost.
                if (ggaBuffer.length > 1024) {
                    val d = ggaBuffer.lastIndexOf("$")
                    if (d > 0) ggaBuffer.delete(0, d) else ggaBuffer.setLength(0)
                }
            }
        }
    }

    /**
     * Pull a clean, checksum-valid GGA sentence out of a line that may carry a
     * binary prefix (UM982 binary logs). Mirrors what the receiver's own apps do:
     * take from the last '$' and validate before sending to the caster — a VRS
     * caster silently ignores a GGA with junk in front or a bad checksum.
     */
    private fun cleanGga(line: String): String? {
        val dollar = line.lastIndexOf('$')
        if (dollar < 0) return null
        val s = line.substring(dollar)
        val star = s.indexOf('*')
        if (star < 2 || star + 3 > s.length) return null
        val body = s.substring(1, star)
        if (body.length < 6 || !body.contains("GGA,")) return null
        var cs = 0
        for (ch in body) cs = cs xor ch.code
        val given = s.substring(star + 1, star + 3).toIntOrNull(16) ?: return null
        if (cs != given) return null
        // Normalise the talker to GP: many VRS casters only parse "$GPGGA" and
        // silently ignore "$GNGGA"/"$GLGGA" (which the UM982 sends). Recompute
        // the checksum for the rewritten sentence.
        val gpBody = "GP" + body.substring(2)   // e.g. GNGGA,... -> GPGGA,...
        var cs2 = 0
        for (ch in gpBody) cs2 = cs2 xor ch.code
        return "\$%s*%02X".format(gpBody, cs2)
    }

    init {
        // All flows are initialised above this point, so wiring them here is safe.
        survey.loadProject(projects.current.value)
        viewModelScope.launch {
            projects.current.collect { survey.loadProject(it) }
        }
        // Speak the solution quality whenever it changes while connected.
        viewModelScope.launch {
            combine(
                _fix.map { it.quality }.distinctUntilChanged(),
                _link,
            ) { q, link -> q to link }
                .collect { (q, link) ->
                    if (link == LinkState.CONNECTED) audio.speak(q.label)
                }
        }
    }

    override fun onCleared() { disconnect(); audio.release() }

    /** Convenience hooks for the UI. */
    fun beepClick() = audio.click()
    fun beepMeasure() = audio.measure()
}

/** NTRIP connection form fields, kept as text so editing is lossless. */
data class NtripForm(
    val host: String = "",
    val port: String = "2101",
    val mount: String = "",
    val user: String = "",
    val pass: String = "",
)

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
