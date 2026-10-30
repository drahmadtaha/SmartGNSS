package com.smartgnss.core.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // Serialise BLE writes: Android allows only one outstanding GATT write, so
    // each chunk must wait for onCharacteristicWrite before the next is sent.
    private val writeMutex = Mutex()
    @Volatile private var writeSignal: CompletableDeferred<Boolean>? = null

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
            emitGattDump(g)   // show the receiver's real BLE layout in the terminal
            val (notify, write) = pickSerialCharacteristics(g)
            if (notify == null || write == null) {
                if (!ready.isCompleted)
                    ready.completeExceptionally(IllegalStateException("No serial characteristics found"))
                return
            }
            _incoming.tryEmit(
                "[BLE] using notify=${notify.uuid} write=${write.uuid}\n".toByteArray()
            )
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

        override fun onCharacteristicWrite(
            g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int
        ) {
            // Release the next chunk once this one has actually been sent.
            writeSignal?.complete(status == BluetoothGatt.GATT_SUCCESS)
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

    private fun isNotify(c: BluetoothGattCharacteristic) =
        c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
            BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
    private fun isWrite(c: BluetoothGattCharacteristic) =
        c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

    /** Find a (notify, write) characteristic pair across the GATT services. */
    @SuppressLint("MissingPermission")
    private fun pickSerialCharacteristics(
        g: BluetoothGatt
    ): Pair<BluetoothGattCharacteristic?, BluetoothGattCharacteristic?> {
        // 1. Known serial-service UUIDs first, picking chars by property.
        for (su in KNOWN_SERIAL_SERVICES) {
            val s = g.getService(su) ?: continue
            val n = s.characteristics.firstOrNull { isNotify(it) }
            val w = s.characteristics.firstOrNull { isWrite(it) }
            if (n != null && w != null) return n to w
        }
        // 2. Any non-standard service that has both a notify and a write char.
        for (s in g.services) {
            if (s.uuid.toString().startsWith("00001800") ||   // Generic Access
                s.uuid.toString().startsWith("00001801") ||   // Generic Attribute
                s.uuid.toString().startsWith("0000180a") ||   // Device Information
                s.uuid.toString().startsWith("0000180f")) continue // Battery
            val n = s.characteristics.firstOrNull { isNotify(it) }
            val w = s.characteristics.firstOrNull { isWrite(it) }
            if (n != null && w != null) return n to w
        }
        // 3. Any notify + any write anywhere.
        val allChars = g.services.flatMap { it.characteristics }
        return allChars.firstOrNull { isNotify(it) } to allChars.firstOrNull { isWrite(it) }
    }

    /** Emit the full GATT layout into the data stream so it shows in the terminal. */
    @SuppressLint("MissingPermission")
    private fun emitGattDump(g: BluetoothGatt) {
        val sb = StringBuilder("[BLE] discovered services:\n")
        for (s in g.services) {
            sb.append("  SVC ").append(s.uuid).append('\n')
            for (c in s.characteristics) {
                val f = buildString {
                    if (isNotify(c)) append('N')
                    if (isWrite(c)) append('W')
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) append('R')
                }
                sb.append("    CHR ").append(c.uuid).append(" [").append(f).append("]\n")
            }
        }
        _incoming.tryEmit(sb.toString().toByteArray())
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
    override suspend fun write(data: ByteArray) = writeMutex.withLock {
        val ch = writeChar ?: return@withLock
        val g = gatt ?: return@withLock
        val chunk = (mtu - 3).coerceAtLeast(20)
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + chunk, data.size)
            val part = data.copyOfRange(offset, end)
            val signal = CompletableDeferred<Boolean>()
            writeSignal = signal
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(ch, part, ch.writeType) ==
                    BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run { ch.value = part; g.writeCharacteristic(ch) }
            }
            // Wait for this chunk to be sent before queuing the next, so Android
            // doesn't drop it (one outstanding GATT write at a time).
            if (ok) withTimeoutOrNull(800) { signal.await() }
            else delay(15)   // queue was busy; small backoff and continue
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
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // BLE-serial service UUIDs used by common GNSS receivers (from the ICE
        // and X-Survey apps). Tried in order; characteristics picked by property.
        val KNOWN_SERIAL_SERVICES: List<UUID> = listOf(
            UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"), // Nordic UART
            UUID.fromString("49535343-FE7D-4AE5-8FA9-9FAFD205E455"), // Microchip/ISSC Transparent UART
            UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB"), // FFF0 serial
            UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB"), // FFE0 (HM-10) serial
            UUID.fromString("258EAFA5-E914-47DA-95CA-C5AB0DC85B11"), // custom (ICE / X-Survey)
        )
    }
}
