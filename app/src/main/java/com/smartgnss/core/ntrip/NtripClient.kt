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

    /** Optional diagnostic sink — receives the caster's response header and events. */
    var onLog: ((String) -> Unit)? = null

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
            var sawData = false
            while (coroutineContext.isActive) {
                val n = input.read(buf)
                if (n < 0) { onLog?.invoke("[caster] stream closed by server"); break }
                if (n > 0) {
                    if (!sawData) { sawData = true; onLog?.invoke("[caster] first RTCM bytes received ✓") }
                    onRtcm(buf.copyOf(n))
                }
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
            append("Host: ${config.host}:${config.port}\r\n")
            // A widely-accepted NTRIP client identifier (some casters reject unknown agents).
            append("User-Agent: NTRIP GNSSInternetRadio/1.4.10\r\n")
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
        // Surface the caster's actual first line for diagnostics (X-Survey shows this too).
        val firstLine = header.lineSequence().firstOrNull()?.trim().orEmpty()
        onLog?.invoke("[caster] reply: $firstLine")
        // A sourcetable reply means the mountpoint was wrong, not a data stream.
        if (header.contains("SOURCETABLE", ignoreCase = true)) {
            onLog?.invoke("[caster] returned a SOURCETABLE → mount point not served to this account")
            return false
        }
        return header.contains("ICY 200") || header.contains("200 OK")
    }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        output = null
        if (_state.value == NtripState.STREAMING) _state.value = NtripState.IDLE
    }
}
