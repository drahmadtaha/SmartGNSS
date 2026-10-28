package com.smartgnss.core.transport

import kotlinx.coroutines.flow.Flow

/** Connection state shared across all transports. */
enum class LinkState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/**
 * A bidirectional byte link to a GNSS receiver.
 *
 * The app only ever talks to a receiver through this interface, so adding a new
 * physical link (BLE, TCP, your own protocol) means adding one implementation
 * and nothing else changes.
 */
interface Transport {
    val state: Flow<LinkState>

    /** Raw bytes coming FROM the receiver (NMEA, status replies, etc.). */
    val incoming: Flow<ByteArray>

    suspend fun connect()

    /** Send bytes TO the receiver: config commands, or RTCM corrections. */
    suspend fun write(data: ByteArray)

    fun close()

    /** Human-readable description of what we're connected to. */
    val description: String
}
