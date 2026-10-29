package com.smartgnss.core.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Wi-Fi / TCP link — for receivers that expose their NMEA stream over a network
 * socket (common for Wi-Fi base/rover setups and data bridges). Point it at the
 * receiver's IP and port.
 */
class TcpTransport(
    private val host: String,
    private val port: Int,
) : Transport {

    private val _state = MutableStateFlow(LinkState.DISCONNECTED)
    override val state = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val incoming = _incoming.asSharedFlow()

    private var socket: Socket? = null
    private var out: OutputStream? = null
    private var reader: Thread? = null
    @Volatile private var running = false

    override val description: String get() = "TCP $host:$port"

    override suspend fun connect() = withContext(Dispatchers.IO) {
        _state.value = LinkState.CONNECTING
        try {
            val sock = Socket()
            sock.connect(InetSocketAddress(host, port), 10_000)
            socket = sock
            out = sock.getOutputStream()
            running = true
            _state.value = LinkState.CONNECTED
            startReader(sock.getInputStream())
        } catch (e: Exception) {
            _state.value = LinkState.ERROR
            close()
            throw e
        }
    }

    private fun startReader(input: InputStream) {
        reader = thread(name = "tcp-reader", isDaemon = true) {
            val buf = ByteArray(4096)
            try {
                while (running) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n > 0) _incoming.tryEmit(buf.copyOf(n))
                }
            } catch (_: Exception) {
                if (running) _state.value = LinkState.ERROR
            }
        }
    }

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        out?.write(data)
        out?.flush()
        Unit
    }

    override fun close() {
        running = false
        try { reader?.interrupt() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        out = null
        _state.value = LinkState.DISCONNECTED
    }
}
