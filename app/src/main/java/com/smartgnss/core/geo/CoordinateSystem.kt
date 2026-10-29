package com.smartgnss.core.geo

/**
 * A full map projection the user can survey in: a [datum] plus a Transverse
 * Mercator grid. [toGrid] takes a WGS 84 position straight from the GNSS
 * receiver, applies the datum shift (if any), and returns grid East/North.
 *
 * Add your own belts here — one entry per published CRS.
 */
class CoordinateSystem(
    val name: String,
    val datum: Datum,
    private val lat0: Double,
    private val lon0: Double,
    private val k0: Double,
    private val falseEast: Double,
    private val falseNorth: Double,
) {
    private val projection = TransverseMercator(
        ellipsoid = datum.ellipsoid,
        lat0 = lat0, lon0 = lon0, k0 = k0,
        falseEast = falseEast, falseNorth = falseNorth,
    )

    /** WGS 84 lat/lon/ellipsoidal-height → grid East/North (metres). */
    fun toGrid(latWgs: Double, lonWgs: Double, hWgs: Double): Pair<Double, Double> {
        val (lat, lon) = if (datum === Datum.WGS84) {
            latWgs to lonWgs
        } else {
            // WGS84 geodetic → WGS84 geocentric → local geocentric → local geodetic
            val wgsXyz = Datum.WGS84.geodeticToXyz(latWgs, lonWgs, hWgs)
            val localXyz = datum.fromWgs84(wgsXyz)
            val (la, lo, _) = datum.xyzToGeodetic(localXyz)
            la to lo
        }
        return projection.forward(lat, lon)
    }

    companion object {
        val UTM_36N = CoordinateSystem(
            "UTM zone 36N (WGS84)", Datum.WGS84,
            lat0 = 0.0, lon0 = 33.0, k0 = 0.9996,
            falseEast = 500000.0, falseNorth = 0.0,
        )
        val UTM_37N = CoordinateSystem(
            "UTM zone 37N (WGS84)", Datum.WGS84,
            lat0 = 0.0, lon0 = 39.0, k0 = 0.9996,
            falseEast = 500000.0, falseNorth = 0.0,
        )

        // Palestine Grid 1923 (EPSG:28193), Clarke 1880 / Palestine 1923 datum.
        val PALESTINE_GRID = CoordinateSystem(
            "Palestine Grid 1923", Datum.PALESTINE_1923,
            lat0 = 31.73409694, lon0 = 35.21208055, k0 = 1.0,
            falseEast = 170251.555, falseNorth = 1126867.909,
        )

        // Israeli TM Grid (ITM, EPSG:2039), GRS80 / Israel 1993 datum.
        val ISRAELI_TM = CoordinateSystem(
            "Israeli TM Grid (ITM)", Datum.ISRAEL_1993,
            lat0 = 31.734393611, lon0 = 35.204516944, k0 = 1.0000067,
            falseEast = 219529.584, falseNorth = 626907.390,
        )

        val all = listOf(UTM_36N, UTM_37N, PALESTINE_GRID, ISRAELI_TM)

        /** Pick the UTM zone that contains a longitude (northern hemisphere). */
        fun utmForLongitude(lonDeg: Double): CoordinateSystem {
            val zone = ((lonDeg + 180.0) / 6.0).toInt() + 1
            val centralMeridian = zone * 6.0 - 183.0
            return CoordinateSystem(
                "UTM zone ${zone}N (WGS84)", Datum.WGS84,
                lat0 = 0.0, lon0 = centralMeridian, k0 = 0.9996,
                falseEast = 500000.0, falseNorth = 0.0,
            )
        }
    }
}
