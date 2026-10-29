package com.smartgnss.core.device

/**
 * Command set for the Unicore UM982 board inside ICE GPS receivers.
 *
 * These are the standard Unicore ASCII commands; each is sent as a line (CR/LF
 * appended by the transport layer). They are grouped so the app can configure a
 * rover, set up NMEA + heading output, and turn tilt (IMU) on and off.
 *
 * Adjust the strings here if a firmware build expects a different syntax — this
 * is the one place the receiver protocol lives.
 */
object Um982 {

    /** Put the board into rover (RTK) mode. */
    const val MODE_ROVER = "MODE ROVER"

    /** NMEA output at 1 Hz on the current port (position, sats, precision). */
    val nmeaOutput = listOf(
        "GPGGA 1",   // position, fix quality, HDOP, altitude
        "GPGSA 1",   // DOP + active satellites
        "GPGSV 1",   // satellites in view + SNR
        "GPRMC 1",   // date/time, speed, course
        "GPGST 1",   // position-error statistics (HRMS / VRMS)
    )

    /** Dual-antenna heading + pitch/roll output for tilt. */
    const val HEADING_OUTPUT = "GPHPR 1"

    /** Enable the IMU so GGA carries the tilt-compensated position. */
    const val TILT_ENABLE = "CONFIG INS ENABLE"

    /** Disable tilt compensation (back to normal, pole-vertical measuring). */
    const val TILT_DISABLE = "CONFIG INS DISABLE"

    /** Persist the current configuration across power cycles. */
    const val SAVE = "SAVECONFIG"

    /** Full configuration sequence for a rover with NMEA + heading. */
    fun configureRover(): List<String> =
        listOf(MODE_ROVER) + nmeaOutput + HEADING_OUTPUT

    fun tilt(on: Boolean): List<String> =
        if (on) listOf(TILT_ENABLE, HEADING_OUTPUT) else listOf(TILT_DISABLE)
}
