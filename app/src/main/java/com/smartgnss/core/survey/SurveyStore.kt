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
    val ellipsoidalHeight: Double? = null, // ellipsoidal height at the ground mark (WGS84)
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
    val projectionAngleDeg: Double? = null, // tilt-direction azimuth (X-Survey "Projection Angle")
    // True when the stored position is the tilt-compensated ground point from
    // #SLANTAPA (pole lean + length already removed by the receiver): the height
    // is already at the mark, so no antenna offset is subtracted again.
    val tiltCompensated: Boolean = false,
    val antennaHeight: Double,
    val timestampMs: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name); put("code", code)
        put("lat", latitude); put("lon", longitude)
        put("alt", altitude ?: JSONObject.NULL)
        put("eh", ellipsoidalHeight ?: JSONObject.NULL)
        put("q", quality.code)
        put("su", satellitesUsed); put("st", satellitesTracked)
        put("hrms", hrms ?: JSONObject.NULL); put("vrms", vrms ?: JSONObject.NULL)
        put("pdop", pdop ?: JSONObject.NULL); put("hdop", hdop ?: JSONObject.NULL)
        put("vdop", vdop ?: JSONObject.NULL); put("age", ageSec ?: JSONObject.NULL)
        put("tilt", tiltAngleDeg ?: JSONObject.NULL); put("hdg", headingDeg ?: JSONObject.NULL)
        put("proj", projectionAngleDeg ?: JSONObject.NULL)
        put("tcomp", tiltCompensated)
        put("ant", antennaHeight); put("ts", timestampMs)
    }

    companion object {
        private fun JSONObject.dOrNull(k: String): Double? =
            if (isNull(k)) null else optDouble(k)

        fun fromJson(o: JSONObject): SurveyPoint = SurveyPoint(
            name = o.getString("name"), code = o.optString("code"),
            latitude = o.getDouble("lat"), longitude = o.getDouble("lon"),
            altitude = o.dOrNull("alt"),
            ellipsoidalHeight = o.dOrNull("eh"),
            quality = FixQuality.from(o.optInt("q", 0)),
            satellitesUsed = o.optInt("su"), satellitesTracked = o.optInt("st"),
            hrms = o.dOrNull("hrms"), vrms = o.dOrNull("vrms"),
            pdop = o.dOrNull("pdop"), hdop = o.dOrNull("hdop"), vdop = o.dOrNull("vdop"),
            ageSec = o.dOrNull("age"), tiltAngleDeg = o.dOrNull("tilt"),
            headingDeg = o.dOrNull("hdg"), projectionAngleDeg = o.dOrNull("proj"),
            tiltCompensated = o.optBoolean("tcomp", false),
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

    /**
     * Record a point. [lat]/[lon]/[altitudeMsl] are the final mark position the
     * caller has already resolved (tilt-compensated ground point when tilt is on,
     * else the GGA antenna position); [tiltCompensated] marks which so the height
     * is not reduced by the antenna offset a second time.
     */
    fun record(
        name: String,
        code: String,
        fix: GnssFix,
        antennaHeight: Double,
        tiltMode: Boolean,
        lat: Double? = fix.latitude,
        lon: Double? = fix.longitude,
        altitudeMsl: Double? = fix.altitudeMsl,
        ellipsoidalHeight: Double? = null,
        tiltCompensated: Boolean = false,
    ): SurveyPoint? {
        val la = lat ?: return null
        val lo = lon ?: return null
        val p = SurveyPoint(
            name = name, code = code, latitude = la, longitude = lo,
            altitude = altitudeMsl, ellipsoidalHeight = ellipsoidalHeight,
            quality = fix.quality,
            satellitesUsed = fix.satellitesUsed, satellitesTracked = fix.satellitesTracked,
            hrms = fix.hrms, vrms = fix.vrms,
            pdop = fix.pdop, hdop = fix.hdop, vdop = fix.vdop,
            ageSec = fix.diffAgeSec,
            // Record the tilt angle whenever the receiver provides one (not only
            // when the mode toggle is on), so it's never silently dropped.
            tiltAngleDeg = fix.tiltAngleDeg,
            headingDeg = fix.headingDeg,
            projectionAngleDeg = fix.projectionAngleDeg,
            tiltCompensated = tiltCompensated,
            antennaHeight = antennaHeight,
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

    /** Import points from a CSV (our own export format, or name,lat,lon[,h,code]).
     *  Returns how many rows were added. */
    fun importCsv(text: String): Int {
        val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return 0
        val header = lines.first().lowercase()
        val hasHeader = header.contains("name") && (header.contains("lat") || header.contains("east"))
        val cols = if (hasHeader) header.split(',').map { it.trim() } else emptyList()
        fun idx(name: String) = cols.indexOfFirst { it == name }
        val iName = idx("name"); val iCode = idx("code")
        val iLat = idx("latitude"); val iLon = idx("longitude"); val iH = idx("ortho_h")
        val added = ArrayList<SurveyPoint>()
        lines.drop(if (hasHeader) 1 else 0).forEach { line ->
            val f = line.split(',')
            fun get(i: Int) = if (i in 0 until f.size) f[i].trim() else ""
            val lat: Double?; val lon: Double?; val nm: String; val cd: String; val h: Double?
            if (hasHeader && iLat >= 0 && iLon >= 0) {
                lat = get(iLat).toDoubleOrNull(); lon = get(iLon).toDoubleOrNull()
                nm = get(iName).ifBlank { "P${added.size + 1}" }; cd = get(iCode)
                h = if (iH >= 0) get(iH).toDoubleOrNull() else null
            } else {
                // Bare: name,lat,lon[,h,code]
                nm = get(0).ifBlank { "P${added.size + 1}" }
                lat = get(1).toDoubleOrNull(); lon = get(2).toDoubleOrNull()
                h = get(3).toDoubleOrNull(); cd = get(4)
            }
            if (lat != null && lon != null) {
                added += SurveyPoint(
                    name = nm, code = cd, latitude = lat, longitude = lon, altitude = h,
                    quality = FixQuality.NO_FIX, satellitesUsed = 0, satellitesTracked = 0,
                    hrms = null, vrms = null, pdop = null, hdop = null, vdop = null,
                    ageSec = null, tiltAngleDeg = null, headingDeg = null,
                    antennaHeight = 0.0, timestampMs = System.currentTimeMillis(),
                )
            }
        }
        if (added.isNotEmpty()) { _points.value = _points.value + added; save() }
        return added.size
    }

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

    /** Ground orthometric elevation at the mark. Tilt-compensated points are
     *  already at the ground, so the antenna offset is subtracted only for
     *  normal (vertical-pole) points. */
    private fun ground(p: SurveyPoint): Double? =
        if (p.tiltCompensated) p.altitude else p.altitude?.minus(p.antennaHeight)

    /** Full-attribute CSV; [project] supplies grid East/North per point. */
    fun toCsv(project: (SurveyPoint) -> Pair<Double, Double>?): String = buildString {
        appendLine(
            "name,code,date_time,latitude,longitude,ellip_h,ortho_h,east,north,ant_height," +
                "quality,sats_used,sats_tracked,hrms,vrms,pdop,hdop,vdop,age_s,tilt_deg,projection_deg,heading_deg"
        )
        _points.value.forEach { p ->
            val en = project(p)
            appendLine(buildString {
                append(p.name).append(',').append(p.code).append(',')
                append(dtFmt.format(java.util.Date(p.timestampMs))).append(',')
                append(p.latitude).append(',').append(p.longitude).append(',')
                append(p.ellipsoidalHeight?.let { "%.4f".format(it) } ?: "").append(',')
                append(ground(p)?.let { "%.4f".format(it) } ?: "").append(',')
                append(en?.first?.let { "%.4f".format(it) } ?: "").append(',')
                append(en?.second?.let { "%.4f".format(it) } ?: "").append(',')
                append(p.antennaHeight).append(',')
                append(p.quality.label).append(',')
                append(p.satellitesUsed).append(',').append(p.satellitesTracked).append(',')
                append(p.hrms ?: "").append(',').append(p.vrms ?: "").append(',')
                append(p.pdop ?: "").append(',').append(p.hdop ?: "").append(',').append(p.vdop ?: "").append(',')
                append(p.ageSec ?: "").append(',')
                append(p.tiltAngleDeg?.let { "%.1f".format(it) } ?: "").append(',')
                append(p.projectionAngleDeg?.let { "%.1f".format(it) } ?: "").append(',')
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
