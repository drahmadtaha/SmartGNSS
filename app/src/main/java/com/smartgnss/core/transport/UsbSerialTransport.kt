package com.smartgnss.core.transport

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Wired USB-serial link (FTDI / CP210x / CH340 / PL2303 cables) using the
 * usb-serial-for-android library. [baudRate] defaults to 115200, the common
 * speed for GNSS boards; expose it in settings if your instruments differ.
 */
class UsbSerialTransport(
    private val context: Context,
    private val driver: UsbSerialDriver,
    private val baudRate: Int = 115200,
) : Transport {

    private val _state = MutableStateFlow(LinkState.DISCONNECTED)
    override val state = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val incoming = _incoming.asSharedFlow()

    private var port: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private val executor = Executors.newSingleThreadExecutor()

    override val description: String
        get() = "USB ${driver.device.productName ?: "serial"} " +
                "(VID ${driver.device.vendorId}/PID ${driver.device.productId})"

    override suspend fun connect() = withContext(Dispatchers.IO) {
        _state.value = LinkState.CONNECTING
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        if (!manager.hasPermission(driver.device)) {
            val pi = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_MUTABLE
            )
            manager.requestPermission(driver.device, pi)
            _state.value = LinkState.ERROR
            error("USB permission not granted yet — approve the dialog and reconnect")
        }
        val connection = manager.openDevice(driver.device)
            ?: run { _state.value = LinkState.ERROR; error("Cannot open USB device") }

        val p = driver.ports.first()
        p.open(connection)
        p.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        port = p

        ioManager = SerialInputOutputManager(p, object : SerialInputOutputManager.Listener {
            override fun onNewData(data: ByteArray) { _incoming.tryEmit(data) }
            override fun onRunError(e: Exception) { _state.value = LinkState.ERROR }
        }).also { executor.submit(it) }

        _state.value = LinkState.CONNECTED
    }

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        port?.write(data, WRITE_TIMEOUT_MS)
        Unit
    }

    override fun close() {
        try { ioManager?.stop() } catch (_: Exception) {}
        try { port?.close() } catch (_: Exception) {}
        port = null
        ioManager = null
        _state.value = LinkState.DISCONNECTED
    }

    companion object {
        const val ACTION_USB_PERMISSION = "com.smartgnss.USB_PERMISSION"
        private const val WRITE_TIMEOUT_MS = 2000

        /** All USB-serial receivers currently attached, ready to connect. */
        fun available(context: Context): List<UsbSerialDriver> {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            return UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        }
    }
}
