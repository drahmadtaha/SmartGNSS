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

    /** Dual-antenna heading + pitch/roll output for tilt.
     *  Unicore's rate field is the period in SECONDS, so 1 = 1 Hz, 0.1 = 10 Hz. */
    const val HEADING_OUTPUT = "GPHPR 1"           // 1 Hz for normal use
    const val HEADING_OUTPUT_10HZ = "GPHPR 0.1"    // 10 Hz for tilt measurement

    // --- ICE / CUBERTK AT-command layer (wraps the Unicore core) -------------
    // The ICE receiver also speaks AT commands (AT+AUTH activates it; +VER/+DTYPE
    // are AT replies). Tilt/IMU is controlled here, not via Unicore CONFIG INS.
    /** Enable the pole-tilt IMU with the pole length in mm: AT+TILT=1,<poleMm>.
     *  (Confirmed from the ICE M10 integration dossier — 1800 = 1.8 m pole.) */
    fun tiltEnable(poleMm: Int) = "AT+TILT=1,$poleMm"
    /** Disable the pole-tilt IMU. */
    const val TILT_DISABLE = "AT+TILT=0,0"
    /** Query the antenna phase-centre offset (device replies +PHASECENTEROFFSET=mm). */
    const val QUERY_PHASE_CENTER = "AT+PHASECENTEROFFSET=?"
    /** IMU calibration step 1 (hold the pole vertical and steady). */
    const val CALIB_STEP1 = "AT+CALIBRATIONSTEP1"
    /** IMU calibration step 2 (after rotating / shaking the receiver). */
    const val CALIB_STEP2 = "AT+CALIBRATIONSTEP2"
    /** Query device type / firmware (AT replies: +DTYPE=…, +VER=…). */
    const val QUERY_TYPE = "AT+DTYPE=?"

    /** Persist the current configuration across power cycles. */
    const val SAVE = "SAVECONFIG"

    /** Stop all current output logs (clears binary logs that flood the BT link). */
    const val UNLOG_ALL = "UNLOGALL"

    /** Full configuration sequence for a rover: clear binary logs, set rover mode,
     *  then enable only the NMEA + heading sentences the app needs. */
    fun configureRover(): List<String> =
        listOf(UNLOG_ALL, MODE_ROVER) + nmeaOutput + HEADING_OUTPUT

    /** Just turn on the NMEA + heading output (no mode change) — safe to send
     *  automatically on connect so GGA/height starts flowing. */
    fun enableOutput(): List<String> = nmeaOutput + HEADING_OUTPUT

    /** Turn tilt on/off via the ICE AT layer. Enabling with the pole length starts
     *  the $GPFMI IMU stream (tilt angle). */
    fun tilt(on: Boolean, poleMm: Int = 1800): List<String> =
        if (on) listOf(tiltEnable(poleMm)) else listOf(TILT_DISABLE)

    /** Full IMU calibration sequence step commands, sent as the user is guided
     *  through the motion. */
    fun calibrationStep(step: Int): String = if (step <= 1) CALIB_STEP1 else CALIB_STEP2
}
