package com.smartgnss.core.nmea

/** RTK quality indicator from the GGA sentence (field 6). */
enum class FixQuality(val code: Int, val label: String) {
    NO_FIX(0, "No fix"),
    GPS(1, "Single"),
    DGPS(2, "DGPS"),
    PPS(3, "PPS"),
    RTK_FIXED(4, "RTK Fixed"),
    RTK_FLOAT(5, "RTK Float"),
    ESTIMATED(6, "Dead reckoning"),
    MANUAL(7, "Manual"),
    SIMULATION(8, "Simulation");

    companion object {
        fun from(code: Int?): FixQuality = entries.firstOrNull { it.code == code } ?: NO_FIX
    }
}

/** One satellite as reported in GSV. */
data class Satellite(
    val prn: Int,
    val elevation: Int?,   // degrees
    val azimuth: Int?,     // degrees
    val snr: Int?,         // dB-Hz, null if not tracked
    val constellation: String,
)

/**
 * The live solution shown on the Status screen. Every NMEA burst updates a
 * copy of this; nothing here is mutable state the UI has to reason about.
 */
data class GnssFix(
    val utc: String? = null,
    val latitude: Double? = null,     // decimal degrees, + N / - S
    val longitude: Double? = null,    // decimal degrees, + E / - W
    val altitudeMsl: Double? = null,  // metres above mean sea level
    val geoidSep: Double? = null,     // metres
    val quality: FixQuality = FixQuality.NO_FIX,
    val satellitesUsed: Int = 0,
    val hdop: Double? = null,
    val pdop: Double? = null,
    val vdop: Double? = null,
    val speedKmh: Double? = null,
    val headingDeg: Double? = null,
    val diffAgeSec: Double? = null,   // age of RTK corrections
    val baseStationId: String? = null,
    val satellitesInView: List<Satellite> = emptyList(),
)
