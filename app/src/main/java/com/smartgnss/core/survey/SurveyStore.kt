package com.smartgnss.core.survey

import android.content.Context
import com.smartgnss.core.nmea.FixQuality
import com.smartgnss.core.nmea.GnssFix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * One recorded survey point, capturing the full solution state at the moment of
 * measurement: position, quality, satellite count, precision, DOPs and tilt.
 */
data class SurveyPoint(
    val name: String,
    val code: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,         // orthometric (MSL) at the antenna, from receiver
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
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name); put("code", code)
        put("lat", latitude); put("lon", longitude)
        put("alt", altitude ?: JSONObject.NULL)
        put("q", quality.code)
        put("su", satellitesUsed); put("st", satellitesTracked)
        put("hrms", hrms ?: JSONObject.NULL); put("vrms", vrms ?: JSONObject.NULL)
        put("pdop", pdop ?: JSONObject.NULL); put("hdop", hdop ?: JSONObject.NULL)
        put("vdop", vdop ?: JSONObject.NULL); put("age", ageSec ?: JSONObject.NULL)
        put("tilt", tiltAngleDeg ?: JSONObject.NULL); put("hdg", headingDeg ?: JSONObject.NULL)
        put("ant", antennaHeight); put("ts", timestampMs)
    }

    companion object {
        private fun JSONObject.dOrNull(k: String): Double? =
            if (isNull(k)) null else optDouble(k)

        fun fromJson(o: JSONObject): SurveyPoint = SurveyPoint(
            name = o.getString("name"), code = o.optString("code"),
            latitude = o.getDouble("lat"), longitude = o.getDouble("lon"),
            altitude = o.dOrNull("alt"),
            quality = FixQuality.from(o.optInt("q", 0)),
            satellitesUsed = o.optInt("su"), satellitesTracked = o.optInt("st"),
            hrms = o.dOrNull("hrms"), vrms = o.dOrNull("vrms"),
            pdop = o.dOrNull("pdop"), hdop = o.dOrNull("hdop"), vdop = o.dOrNull("vdop"),
            ageSec = o.dOrNull("age"), tiltAngleDeg = o.dOrNull("tilt"),
            headingDeg = o.dOrNull("hdg"),
            antennaHeight = o.optDouble("ant", 0.0), timestampMs = o.optLong("ts"),
        )
    }
}

/**
 * Persisted collection of recorded points, scoped per project. Points are saved
 * to SharedPreferences under the open project's name, so they survive restarts
 * and switching jobs. Exports to CSV, plain text and DXF.
 */
class SurveyStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("smartgnss_points", Context.MODE_PRIVATE)
    private var projectKey = "default"

    private val _points = MutableStateFlow(load(projectKey))
    val points = _points.asStateFlow()

    /** Switch to a project's point set (called when the open project changes). */
    fun loadProject(name: String?) {
        projectKey = name ?: "default"
        _points.value = load(projectKey)
    }

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
            name = name, code = code, latitude = lat, longitude = lon,
            altitude = fix.altitudeMsl, quality = fix.quality,
            satellitesUsed = fix.satellitesUsed, satellitesTracked = fix.satellitesTracked,
            hrms = fix.hrms, vrms = fix.vrms,
            pdop = fix.pdop, hdop = fix.hdop, vdop = fix.vdop,
            ageSec = fix.diffAgeSec,
            tiltAngleDeg = if (tiltMode) fix.tiltAngleDeg else null,
            headingDeg = fix.headingDeg, antennaHeight = antennaHeight,
            timestampMs = System.currentTimeMillis(),
        )
        _points.value = _points.value + p
        save()
        return p
    }

    fun delete(p: SurveyPoint) {
        _points.value = _points.value.filterNot { it === p || it.timestampMs == p.timestampMs && it.name == p.name }
        save()
    }

    fun clear() { _points.value = emptyList(); save() }

    private fun keyFor(project: String) = "pts_$project"

    private fun load(project: String): List<SurveyPoint> = try {
        val raw = prefs.getString(keyFor(project), null) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).map { SurveyPoint.fromJson(arr.getJSONObject(it)) }
    } catch (_: Exception) { emptyList() }

    private fun save() {
        val arr = JSONArray()
        _points.value.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(keyFor(projectKey), arr.toString()).apply()
    }

    // --- Exports -------------------------------------------------------------

    private val dtFmt get() = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)

    /** Ground orthometric elevation (measured MSL − antenna height). */
    private fun ground(p: SurveyPoint): Double? = p.altitude?.minus(p.antennaHeight)

    /** Full-attribute CSV; [project] supplies grid East/North per point. */
    fun toCsv(project: (SurveyPoint) -> Pair<Double, Double>?): String = buildString {
        appendLine(
            "name,code,date_time,latitude,longitude,east,north,ortho_h,ant_height," +
                "quality,sats_used,sats_tracked,hrms,vrms,pdop,hdop,vdop,age_s,tilt_deg,heading_deg"
        )
        _points.value.forEach { p ->
            val en = project(p)
            appendLine(buildString {
                append(p.name).append(',').append(p.code).append(',')
                append(dtFmt.format(java.util.Date(p.timestampMs))).append(',')
                append(p.latitude).append(',').append(p.longitude).append(',')
                append(en?.first?.let { "%.4f".format(it) } ?: "").append(',')
                append(en?.second?.let { "%.4f".format(it) } ?: "").append(',')
                append(ground(p)?.let { "%.4f".format(it) } ?: "").append(',')
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

    /** Simple aligned text: Name  East  North  Elev  Code (common for total stations). */
    fun toTxt(project: (SurveyPoint) -> Pair<Double, Double>?): String = buildString {
        _points.value.forEach { p ->
            val en = project(p)
            appendLine(
                "%-10s %14s %14s %10s  %s".format(
                    p.name,
                    en?.first?.let { "%.4f".format(it) } ?: "-",
                    en?.second?.let { "%.4f".format(it) } ?: "-",
                    ground(p)?.let { "%.4f".format(it) } ?: "-",
                    p.code,
                )
            )
        }
    }

    /** Minimal DXF (R12): a POINT and a TEXT label per station on layer POINTS. */
    fun toDxf(project: (SurveyPoint) -> Pair<Double, Double>?): String = buildString {
        appendLine("0"); appendLine("SECTION"); appendLine("2"); appendLine("ENTITIES")
        _points.value.forEach { p ->
            val en = project(p) ?: return@forEach
            val x = en.first; val y = en.second; val z = ground(p) ?: 0.0
            // POINT
            appendLine("0"); appendLine("POINT")
            appendLine("8"); appendLine("POINTS")
            appendLine("10"); appendLine("%.4f".format(x))
            appendLine("20"); appendLine("%.4f".format(y))
            appendLine("30"); appendLine("%.4f".format(z))
            // TEXT label (name)
            appendLine("0"); appendLine("TEXT")
            appendLine("8"); appendLine("POINT_LABELS")
            appendLine("10"); appendLine("%.4f".format(x + 0.2))
            appendLine("20"); appendLine("%.4f".format(y + 0.2))
            appendLine("30"); appendLine("%.4f".format(z))
            appendLine("40"); appendLine("0.5")
            appendLine("1"); appendLine(p.name)
        }
        appendLine("0"); appendLine("ENDSEC"); appendLine("0"); appendLine("EOF")
    }
}
