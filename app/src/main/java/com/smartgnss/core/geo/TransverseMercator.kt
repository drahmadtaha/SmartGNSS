package com.smartgnss.core.geo

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Projected grid coordinate: East, North (metres). */
data class Enh(val east: Double, val north: Double, val height: Double)

/**
 * Transverse Mercator forward projection (Snyder series, accurate to a few mm
 * within a normal zone width). Geodetic lat/lon on [ellipsoid] → grid E/N.
 *
 * @param lat0,lon0  origin latitude/longitude (degrees)
 * @param k0         scale factor on the central meridian
 * @param falseEast  false easting (m)
 * @param falseNorth false northing (m)
 */
class TransverseMercator(
    val ellipsoid: Ellipsoid,
    val lat0: Double,
    val lon0: Double,
    val k0: Double,
    val falseEast: Double,
    val falseNorth: Double,
) {
    fun forward(latDeg: Double, lonDeg: Double): Pair<Double, Double> {
        val a = ellipsoid.a
        val e2 = ellipsoid.e2
        val ep2 = ellipsoid.ep2
        val lat = Math.toRadians(latDeg)
        val lon = Math.toRadians(lonDeg)
        val lon0r = Math.toRadians(lon0)

        val sinLat = sin(lat); val cosLat = cos(lat)
        val nu = a / sqrt(1.0 - e2 * sinLat * sinLat)
        val t = tan(lat) * tan(lat)
        val c = ep2 * cosLat * cosLat
        val A = (lon - lon0r) * cosLat

        val m = meridionalArc(lat, a, e2)
        val m0 = meridionalArc(Math.toRadians(lat0), a, e2)

        val east = falseEast + k0 * nu * (
            A + (1 - t + c) * A * A * A / 6.0 +
            (5 - 18 * t + t * t + 72 * c - 58 * ep2) * Math.pow(A, 5.0) / 120.0
        )
        val north = falseNorth + k0 * (
            m - m0 + nu * tan(lat) * (
                A * A / 2.0 +
                (5 - t + 9 * c + 4 * c * c) * Math.pow(A, 4.0) / 24.0 +
                (61 - 58 * t + t * t + 600 * c - 330 * ep2) * Math.pow(A, 6.0) / 720.0
            )
        )
        return east to north
    }

    private fun meridionalArc(lat: Double, a: Double, e2: Double): Double {
        val e4 = e2 * e2
        val e6 = e4 * e2
        return a * (
            (1 - e2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * lat -
            (3 * e2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * sin(2 * lat) +
            (15 * e4 / 256 + 45 * e6 / 1024) * sin(4 * lat) -
            (35 * e6 / 3072) * sin(6 * lat)
        )
    }
}
