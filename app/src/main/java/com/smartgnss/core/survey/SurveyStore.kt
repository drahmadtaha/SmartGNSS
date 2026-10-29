package com.smartgnss.core.survey

import com.smartgnss.core.nmea.FixQuality
import com.smartgnss.core.nmea.GnssFix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One recorded survey point, capturing the full solution state at the moment of
 * measurement: position, quality, satellite count, precision, DOPs and tilt.
 */
data class SurveyPoint(
    val name: String,
    val code: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,         // orthometric (MSL) from receiver
    val quality: FixQuality,
    val satellitesUsed: Int,
    val satellitesTracked: Int,
    val hrms: Double?,
    val vrms: Double?,
    val pdop: Double?,
    val hdop: Double?,
    val vdop: Double?,
    val ageSec: Double?,
    val tiltAngleDeg: Double?,     // null if measured in normal (non-tilt) mode
    val headingDeg: Double?,
    val antennaHeight: Double,
    val timestampMs: Long,
)

/** In-memory collection of recorded points with full-attribute CSV export. */
class SurveyStore {
    private val _points = MutableStateFlow<List<SurveyPoint>>(emptyList())
    val points = _points.asStateFlow()

    fun record(
        name: String,
        code: String,
        fix: GnssFix,
        antennaHeight: Double,
        tiltMode: Boolean,
    ): SurveyPoint? {
        val lat = fix.latitude ?: return null
        val lon = fix.longitude ?: return null
        val p = SurveyPoint(
            name = name,
            code = code,
            latitude = lat,
            longitude = lon,
            altitude = fix.altitudeMsl,
            quality = fix.quality,
            satellitesUsed = fix.satellitesUsed,
            satellitesTracked = fix.satellitesTracked,
            hrms = fix.hrms,
            vrms = fix.vrms,
            pdop = fix.pdop,
            hdop = fix.hdop,
            vdop = fix.vdop,
            ageSec = fix.diffAgeSec,
            tiltAngleDeg = if (tiltMode) fix.tiltAngleDeg else null,
            headingDeg = fix.headingDeg,
            antennaHeight = antennaHeight,
            timestampMs = System.currentTimeMillis(),
        )
        _points.value = _points.value + p
        return p
    }

    fun clear() { _points.value = emptyList() }

    /** CSV with all attributes; [project] supplies grid East/North per point. */
    fun toCsv(project: (SurveyPoint) -> Pair<Double, Double>?): String = buildString {
        appendLine(
            "name,code,date_time,latitude,longitude,east,north,ortho_h,ant_height," +
                "quality,sats_used,sats_tracked,hrms,vrms,pdop,hdop,vdop,age_s,tilt_deg,heading_deg"
        )
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        _points.value.forEach { p ->
            val en = project(p)
            appendLine(buildString {
                append(p.name).append(',')
                append(p.code).append(',')
                append(fmt.format(java.util.Date(p.timestampMs))).append(',')
                append(p.latitude).append(',').append(p.longitude).append(',')
                append(en?.first?.let { "%.4f".format(it) } ?: "").append(',')
                append(en?.second?.let { "%.4f".format(it) } ?: "").append(',')
                append(p.altitude ?: "").append(',')
                append(p.antennaHeight).append(',')
                append(p.quality.label).append(',')
                append(p.satellitesUsed).append(',').append(p.satellitesTracked).append(',')
                append(p.hrms ?: "").append(',').append(p.vrms ?: "").append(',')
                append(p.pdop ?: "").append(',').append(p.hdop ?: "").append(',').append(p.vdop ?: "").append(',')
                append(p.ageSec ?: "").append(',')
                append(p.tiltAngleDeg?.let { "%.1f".format(it) } ?: "").append(',')
                append(p.headingDeg?.let { "%.1f".format(it) } ?: "")
            })
        }
    }
}
