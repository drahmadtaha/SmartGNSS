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
 * Bluetooth Low Energy link over the Nordic UART Service (NUS) — the profile
 * most BLE GNSS receivers expose. If your receiver uses different UUIDs, pass
 * them in. Uses the classic (pre-API-33) characteristic callbacks so it runs on
 * every supported Android version.
 */
@Suppress("DEPRECATION")
class BleTransport(
    private val context: Context,
    private val device: BluetoothDevice,
    private val serviceUuid: UUID = NUS_SERVICE,
    private val writeUuid: UUID = NUS_RX,
    private val notifyUuid: UUID = NUS_TX,
) : Transport {

    private val _state = MutableStateFlow(LinkState.DISCONNECTED)
    override val state = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val incoming = _incoming.asSharedFlow()

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var mtu = 20
    private val ready = CompletableDeferred<Unit>()

    @get:SuppressLint("MissingPermission")
    override val description: String
        get() = "BLE ${device.name ?: device.address}"

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _state.value = LinkState.DISCONNECTED
                if (!ready.isCompleted) ready.completeExceptionally(IllegalStateException("BLE disconnected"))
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(serviceUuid)
            if (service == null) {
                if (!ready.isCompleted) ready.completeExceptionally(IllegalStateException("UART service not found"))
                return
            }
            writeChar = service.getCharacteristic(writeUuid)
            val notifyChar = service.getCharacteristic(notifyUuid)
            if (notifyChar == null) {
                if (!ready.isCompleted) ready.completeExceptionally(IllegalStateException("Notify characteristic not found"))
                return
            }
            g.requestMtu(185)
            g.setCharacteristicNotification(notifyChar, true)
            val cccd = notifyChar.getDescriptor(CCCD)
            if (cccd != null) {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(cccd)
            }
            _state.value = LinkState.CONNECTED
            if (!ready.isCompleted) ready.complete(Unit)
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            if (newMtu > 0) mtu = newMtu
        }

        // Pre-Android-13 callback.
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (ch.uuid == notifyUuid) {
                val value = ch.value ?: return
                if (value.isNotEmpty()) _incoming.tryEmit(value.copyOf())
            }
        }

        // Android 13+ callback (API 33) with the value delivered directly.
        override fun onCharacteristicChanged(
            g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray
        ) {
            if (ch.uuid == notifyUuid && value.isNotEmpty()) _incoming.tryEmit(value.copyOf())
        }
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
