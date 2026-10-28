package com.smartgnss.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartgnss.core.license.Feature
import com.smartgnss.core.license.LicenseManager
import com.smartgnss.core.nmea.GnssFix
import com.smartgnss.core.nmea.NmeaParser
import com.smartgnss.core.ntrip.NtripClient
import com.smartgnss.core.ntrip.NtripConfig
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

    private val parser = NmeaParser()

    private val _fix = MutableStateFlow(GnssFix())
    val fix = _fix.asStateFlow()

    private val _link = MutableStateFlow(LinkState.DISCONNECTED)
    val link = _link.asStateFlow()

    private val _linkLabel = MutableStateFlow("")
    val linkLabel = _linkLabel.asStateFlow()

    private val _ntripState = MutableStateFlow(NtripState.IDLE)
    val ntripState = _ntripState.asStateFlow()

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
