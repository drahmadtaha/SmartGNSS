package com.smartgnss.core.geo

import org.json.JSONObject

/** Datum transformation method used to go from WGS 84 to the local datum. */
enum class TransformType(val label: String) {
    NONE("None (WGS 84)"),
    THREE_PARAM("3-Parameter (Molodensky)"),
    BURSA_WOLF("Bursa-Wolf (7-param)"),
    COORDINATE_FRAME("Coordinate Frame (7-param)"),
}

/**
 * A fully editable coordinate-system definition: ellipsoid, datum transformation
 * (with its 7 parameters) and a Transverse Mercator grid. Everything the
 * Coordinate System editor exposes lives here, and it serialises to JSON so
 * custom systems persist and can be shared.
 */
data class CrsDefinition(
    val name: String,
    val ellipsoidName: String,
    val a: Double,
    val invF: Double,
    val transform: TransformType,
    val dx: Double, val dy: Double, val dz: Double,
    val rx: Double, val ry: Double, val rz: Double, val scalePpm: Double,
    val lat0: Double, val lon0: Double, val k0: Double,
    val falseEast: Double, val falseNorth: Double,
    val builtin: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name); put("ell", ellipsoidName); put("a", a); put("invF", invF)
        put("tf", transform.name)
        put("dx", dx); put("dy", dy); put("dz", dz)
        put("rx", rx); put("ry", ry); put("rz", rz); put("s", scalePpm)
        put("lat0", lat0); put("lon0", lon0); put("k0", k0)
        put("fe", falseEast); put("fn", falseNorth)
    }

    companion object {
        fun fromJson(o: JSONObject): CrsDefinition = CrsDefinition(
            name = o.getString("name"),
            ellipsoidName = o.optString("ell", "WGS 84"),
            a = o.getDouble("a"), invF = o.getDouble("invF"),
            transform = runCatching { TransformType.valueOf(o.getString("tf")) }
                .getOrDefault(TransformType.NONE),
            dx = o.optDouble("dx", 0.0), dy = o.optDouble("dy", 0.0), dz = o.optDouble("dz", 0.0),
            rx = o.optDouble("rx", 0.0), ry = o.optDouble("ry", 0.0), rz = o.optDouble("rz", 0.0),
            scalePpm = o.optDouble("s", 0.0),
            lat0 = o.optDouble("lat0", 0.0), lon0 = o.optDouble("lon0", 0.0),
            k0 = o.optDouble("k0", 1.0),
            falseEast = o.optDouble("fe", 0.0), falseNorth = o.optDouble("fn", 0.0),
        )

        // Built-in templates ("download template" picks from these, then edit).
        val UTM_36N = CrsDefinition("UTM Zone 36N (WGS84)", "WGS 84", 6378137.0, 298.257223563,
            TransformType.NONE, 0.0,0.0,0.0,0.0,0.0,0.0,0.0, 0.0, 33.0, 0.9996, 500000.0, 0.0, true)
        val UTM_37N = CrsDefinition("UTM Zone 37N (WGS84)", "WGS 84", 6378137.0, 298.257223563,
            TransformType.NONE, 0.0,0.0,0.0,0.0,0.0,0.0,0.0, 0.0, 39.0, 0.9996, 500000.0, 0.0, true)
        val PALESTINE_GRID = CrsDefinition("Palestine Grid 1923", "Clarke 1880 (RGS)",
            6378249.145, 293.465, TransformType.BURSA_WOLF,
            -275.722, 94.7824, 340.894, -8.001, -4.42, -11.821, 1.0,
            31.73409694, 35.21208055, 1.0, 170251.555, 1126867.909, true)
        // Palestine / Israel TM grid (IG05/12 on GRS80), verified against X-Survey
        // to < 2 cm. NOTE: the engine treats the 7 params as local->WGS84 and
        // inverts them, and X-Survey's published set is the WGS84->local
        // Coordinate-Frame transform, so here the translations and scale are
        // negated and the rotations kept as published — this combination makes
        // our BURSA_WOLF path reproduce X-Survey's grid exactly.
        val PALESTINE_ITM = CrsDefinition("Palestine (Israel TM)", "GRS 80",
            6378137.0, 298.257222101, TransformType.BURSA_WOLF,
            24.0024, 17.1032, 17.8444, -0.33009, -1.85269, 1.66969, -5.425,
            31.734393611, 35.204516944, 1.0000067, 169529.584, 126907.390, true)
        // Kept under its common name too; identical verified parameters.
        val ISRAELI_TM = PALESTINE_ITM.copy(name = "Israeli TM Grid (ITM)")

        val templates = listOf(PALESTINE_ITM, ISRAELI_TM, UTM_36N, UTM_37N, PALESTINE_GRID)
    }
}
