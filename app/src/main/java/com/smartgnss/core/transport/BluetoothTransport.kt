package com.smartgnss.core.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Classic Bluetooth SPP link — the way most survey receivers expose their
 * serial stream wirelessly. Pass in an already-bonded [BluetoothDevice]
 * (picked on the Connect screen).
 */
class BluetoothTransport(
    private val device: BluetoothDevice,
) : Transport {

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val _state = MutableStateFlow(LinkState.DISCONNECTED)
    override val state = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val incoming = _incoming.asSharedFlow()

    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null
    private var reader: Thread? = null
    @Volatile private var running = false

    @SuppressLint("MissingPermission")
    override val description: String
        get() = "${device.name ?: "Bluetooth"} (${device.address})"

    @SuppressLint("MissingPermission")
    override suspend fun connect() = withContext(Dispatchers.IO) {
        _state.value = LinkState.CONNECTING
        try {
            val sock = device.createRfcommSocketToServiceRecord(sppUuid)
            sock.connect()
            socket = sock
            out = sock.outputStream
            running = true
            _state.value = LinkState.CONNECTED
            startReader(sock.inputStream)
        } catch (e: Exception) {
            _state.value = LinkState.ERROR
            close()
            throw e
        }
    }

    private fun startReader(input: InputStream) {
        reader = thread(name = "bt-reader", isDaemon = true) {
            val buf = ByteArray(2048)
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
