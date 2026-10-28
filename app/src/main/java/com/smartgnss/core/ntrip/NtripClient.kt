package com.smartgnss.core.ntrip

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.coroutineContext

data class NtripConfig(
    val host: String,
    val port: Int = 2101,
    val mountPoint: String,
    val username: String = "",
    val password: String = "",
)

enum class NtripState { IDLE, CONNECTING, STREAMING, ERROR }

/**
 * Minimal NTRIP v1 client. It connects to a caster, authenticates, and streams
 * RTCM3 correction bytes back through [onRtcm] — you forward those straight to
 * the receiver's [Transport.write] to get an RTK fix.
 *
 * Call [sendGga] periodically with the receiver's current GGA line so the caster
 * can serve a VRS / nearest-base stream for your position.
 */
class NtripClient(private val config: NtripConfig) {

    private val _state = MutableStateFlow(NtripState.IDLE)
    val state = _state.asStateFlow()

    private var socket: Socket? = null
    private var output: OutputStream? = null

    /**
     * Blocks (on an IO dispatcher) for the life of the stream, delivering RTCM
     * bytes to [onRtcm]. Cancel the coroutine to stop. Throws on connect error.
     */
    suspend fun stream(onRtcm: (ByteArray) -> Unit) = withContext(Dispatchers.IO) {
        _state.value = NtripState.CONNECTING
        try {
            val sock = Socket()
            sock.connect(InetSocketAddress(config.host, config.port), 10_000)
            sock.soTimeout = 30_000
            socket = sock
            output = sock.getOutputStream()
            val input = sock.getInputStream()

            sendRequest(output!!)
            if (!readOkHeader(input)) {
                _state.value = NtripState.ERROR
                error("Caster rejected request (bad mountpoint or credentials)")
            }

            _state.value = NtripState.STREAMING
            val buf = ByteArray(4096)
            while (coroutineContext.isActive) {
                val n = input.read(buf)
                if (n < 0) break
                if (n > 0) onRtcm(buf.copyOf(n))
            }
        } catch (e: Exception) {
            _state.value = NtripState.ERROR
            throw e
        } finally {
            close()
        }
    }

    /** Push the receiver's latest GGA sentence up to the caster (VRS support). */
    fun sendGga(ggaLine: String) {
        try {
            val line = if (ggaLine.endsWith("\r\n")) ggaLine else ggaLine.trimEnd() + "\r\n"
            output?.write(line.toByteArray(Charsets.US_ASCII))
            output?.flush()
        } catch (_: Exception) { /* non-fatal */ }
    }

    private fun sendRequest(out: OutputStream) {
        val auth = Base64.encodeToString(
            "${config.username}:${config.password}".toByteArray(), Base64.NO_WRAP
        )
        val req = buildString {
            append("GET /${config.mountPoint} HTTP/1.0\r\n")
            append("User-Agent: NTRIP SmartGNSS/1.0\r\n")
            append("Accept: */*\r\n")
            if (config.username.isNotEmpty())
                append("Authorization: Basic $auth\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        out.write(req.toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    private fun readOkHeader(input: InputStream): Boolean {
        val sb = StringBuilder()
        val b = ByteArray(1)
        // Read until the end of the HTTP/ICY header block.
        while (sb.length < 4096) {
            if (input.read(b) < 0) break
            sb.append(b[0].toInt().toChar())
            if (sb.contains("\r\n\r\n") || sb.contains("\n\n")) break
        }
        val header = sb.toString()
        // A sourcetable reply means the mountpoint was wrong, not a data stream.
        if (header.contains("SOURCETABLE", ignoreCase = true)) return false
        return header.contains("ICY 200") || header.contains("200 OK")
    }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        output = null
        if (_state.value == NtripState.STREAMING) _state.value = NtripState.IDLE
    }
}
