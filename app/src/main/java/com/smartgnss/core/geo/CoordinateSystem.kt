package com.smartgnss.core.geo

/**
 * A runtime coordinate system built from an editable [CrsDefinition]. [toGrid]
 * takes a WGS 84 position from the receiver, applies the datum transformation
 * the definition selects, and projects to grid East/North.
 */
class CoordinateSystem(val def: CrsDefinition) {

    val name: String get() = def.name

    private val ellipsoid = Ellipsoid(def.ellipsoidName, def.a, def.invF)

    private val projection = TransverseMercator(
        ellipsoid = ellipsoid,
        lat0 = def.lat0, lon0 = def.lon0, k0 = def.k0,
        falseEast = def.falseEast, falseNorth = def.falseNorth,
    )

    private fun effectiveDatum(): Datum = when (def.transform) {
        TransformType.THREE_PARAM ->
            Datum(def.name, ellipsoid, def.dx, def.dy, def.dz, 0.0, 0.0, 0.0, 0.0)
        TransformType.BURSA_WOLF ->
            Datum(def.name, ellipsoid, def.dx, def.dy, def.dz, def.rx, def.ry, def.rz, def.scalePpm)
        TransformType.COORDINATE_FRAME ->
            Datum(def.name, ellipsoid, def.dx, def.dy, def.dz, -def.rx, -def.ry, -def.rz, def.scalePpm)
        TransformType.NONE ->
            Datum(def.name, ellipsoid)
    }

    /** WGS 84 lat/lon/ellipsoidal-height → grid East/North (metres). */
    fun toGrid(latWgs: Double, lonWgs: Double, hWgs: Double): Pair<Double, Double> {
        val (lat, lon) = if (def.transform == TransformType.NONE) {
            latWgs to lonWgs
        } else {
            val wgsXyz = Datum.WGS84.geodeticToXyz(latWgs, lonWgs, hWgs)
            val local = effectiveDatum().fromWgs84(wgsXyz)
            val (la, lo, _) = effectiveDatum().xyzToGeodetic(local)
            la to lo
        }
        return projection.forward(lat, lon)
    }

    companion object {
        val UTM_36N = CoordinateSystem(CrsDefinition.UTM_36N)
        val UTM_37N = CoordinateSystem(CrsDefinition.UTM_37N)
        val PALESTINE_GRID = CoordinateSystem(CrsDefinition.PALESTINE_GRID)
        val ISRAELI_TM = CoordinateSystem(CrsDefinition.ISRAELI_TM)

        val all = CrsDefinition.templates.map { CoordinateSystem(it) }

        fun utmForLongitude(lonDeg: Double): CoordinateSystem {
            val zone = ((lonDeg + 180.0) / 6.0).toInt() + 1
            val cm = zone * 6.0 - 183.0
            return CoordinateSystem(
                CrsDefinition(
                    "UTM Zone ${zone}N (WGS84)", "WGS 84", 6378137.0, 298.257223563,
                    TransformType.NONE, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    0.0, cm, 0.9996, 500000.0, 0.0, true,
                )
            )
        }
    }
}
