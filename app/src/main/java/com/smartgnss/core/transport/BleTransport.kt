package com.smartgnss.core.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Bluetooth Low Energy link that works with any BLE-serial GNSS receiver.
 *
 * It does not assume a fixed profile: after connecting it discovers the GATT
 * services and auto-picks a NOTIFY characteristic (data coming from the
 * receiver) and a WRITE characteristic (commands/RTCM going to it). It prefers
 * the Nordic UART service when present, otherwise any service that exposes both
 * a notify and a write characteristic (FFE0/FFE1, custom profiles, etc.).
 */
@Suppress("DEPRECATION")
class BleTransport(
    private val context: Context,
    private val device: BluetoothDevice,
) : Transport {

    private val _state = MutableStateFlow(LinkState.DISCONNECTED)
    override val state = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val incoming = _incoming.asSharedFlow()

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyUuid: UUID? = null
    private var mtu = 20
    private val ready = CompletableDeferred<Unit>()

    @get:SuppressLint("MissingPermission")
    override val description: String
        get() = "BLE ${device.name ?: device.address}"

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _state.value = LinkState.DISCONNECTED
                    if (!ready.isCompleted)
                        ready.completeExceptionally(IllegalStateException("BLE disconnected (status $status)"))
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val (notify, write) = pickSerialCharacteristics(g)
            if (notify == null || write == null) {
                if (!ready.isCompleted)
                    ready.completeExceptionally(IllegalStateException("No serial characteristics found"))
                return
            }
            writeChar = write
            notifyUuid = notify.uuid
            // Prefer write-without-response for serial streaming when available.
            if (write.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                write.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            g.requestMtu(185)
            g.setCharacteristicNotification(notify, true)
            notify.getDescriptor(CCCD)?.let { cccd ->
                val indicate = notify.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                cccd.value = if (indicate) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(cccd)
            }
            _state.value = LinkState.CONNECTED
            if (!ready.isCompleted) ready.complete(Unit)
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            if (newMtu > 0) mtu = newMtu
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (ch.uuid == notifyUuid) ch.value?.takeIf { it.isNotEmpty() }?.let { _incoming.tryEmit(it.copyOf()) }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray
        ) {
            if (ch.uuid == notifyUuid && value.isNotEmpty()) _incoming.tryEmit(value.copyOf())
        }
    }

    /** Find a (notify, write) characteristic pair across the GATT services. */
    private fun pickSerialCharacteristics(
        g: BluetoothGatt
    ): Pair<BluetoothGattCharacteristic?, BluetoothGattCharacteristic?> {
        fun isNotify(c: BluetoothGattCharacteristic) =
            c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
        fun isWrite(c: BluetoothGattCharacteristic) =
            c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

        // 1. Nordic UART, if present.
        g.getService(NUS_SERVICE)?.let { s ->
            val n = s.getCharacteristic(NUS_TX); val w = s.getCharacteristic(NUS_RX)
            if (n != null && w != null) return n to w
        }
        // 2. A service that has both a notify and a write characteristic.
        for (s in g.services) {
            val n = s.characteristics.firstOrNull { isNotify(it) }
            val w = s.characteristics.firstOrNull { isWrite(it) }
            if (n != null && w != null) return n to w
        }
        // 3. Any notify + any write anywhere.
        val allChars = g.services.flatMap { it.characteristics }
        return allChars.firstOrNull { isNotify(it) } to allChars.firstOrNull { isWrite(it) }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect() {
        _state.value = LinkState.CONNECTING
        gatt = device.connectGatt(context, false, callback)
        val ok = withTimeoutOrNull(15_000) {
            try { ready.await(); true } catch (_: Exception) { false }
        }
        if (ok != true) {
            _state.value = LinkState.ERROR
            close()
            error("Could not establish BLE link")
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun write(data: ByteArray) {
        val ch = writeChar ?: return
        val g = gatt ?: return
        val chunk = (mtu - 3).coerceAtLeast(20)
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + chunk, data.size)
            ch.value = data.copyOfRange(offset, end)
            g.writeCharacteristic(ch)
            offset = end
        }
    }

    @SuppressLint("MissingPermission")
    override fun close() {
        try { gatt?.disconnect() } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}
        gatt = null
        writeChar = null
        _state.value = LinkState.DISCONNECTED
    }

    companion object {
        val NUS_SERVICE: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val NUS_RX: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val NUS_TX: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
