package com.smartgnss.core.survey

import com.smartgnss.core.nmea.FixQuality
import com.smartgnss.core.nmea.GnssFix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One recorded survey point. */
data class SurveyPoint(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val quality: FixQuality,
    val hdop: Double?,
    val timestampMs: Long,
)

/** In-memory collection of recorded points with CSV export. */
class SurveyStore {
    private val _points = MutableStateFlow<List<SurveyPoint>>(emptyList())
    val points = _points.asStateFlow()

    fun record(name: String, fix: GnssFix): SurveyPoint? {
        val lat = fix.latitude ?: return null
        val lon = fix.longitude ?: return null
        val p = SurveyPoint(
            name = name,
            latitude = lat,
            longitude = lon,
            altitude = fix.altitudeMsl,
            quality = fix.quality,
            hdop = fix.hdop,
            timestampMs = System.currentTimeMillis(),
        )
        _points.value = _points.value + p
        return p
    }

    fun clear() { _points.value = emptyList() }

    fun toCsv(): String = buildString {
        appendLine("name,latitude,longitude,altitude,quality,hdop,timestamp_ms")
        _points.value.forEach {
            appendLine(
                "${it.name},${it.latitude},${it.longitude}," +
                "${it.altitude ?: ""},${it.quality.label},${it.hdop ?: ""},${it.timestampMs}"
            )
        }
    }
}
