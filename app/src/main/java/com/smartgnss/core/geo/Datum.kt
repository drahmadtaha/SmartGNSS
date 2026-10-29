package com.smartgnss.core.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A geocentric Cartesian coordinate (metres). */
data class Xyz(val x: Double, val y: Double, val z: Double)

/**
 * A geodetic datum: a reference [ellipsoid] plus the 7 Helmert parameters that
 * map THIS datum's geocentric coordinates to WGS 84 (Bursa–Wolf / position-vector
 * convention). Translations in metres, rotations in arc-seconds, scale in ppm.
 *
 * For a datum already realised in WGS 84 (e.g. the GNSS output itself) all seven
 * parameters are zero.
 */
data class Datum(
    val name: String,
    val ellipsoid: Ellipsoid,
    val dx: Double = 0.0, val dy: Double = 0.0, val dz: Double = 0.0,
    val rx: Double = 0.0, val ry: Double = 0.0, val rz: Double = 0.0,
    val scalePpm: Double = 0.0,
) {
    /** Geodetic (deg, deg, m on THIS ellipsoid) → geocentric XYZ. */
    fun geodeticToXyz(latDeg: Double, lonDeg: Double, h: Double): Xyz {
        val lat = Math.toRadians(latDeg); val lon = Math.toRadians(lonDeg)
        val e = ellipsoid
        val n = e.a / sqrt(1.0 - e.e2 * sin(lat) * sin(lat))
        val x = (n + h) * cos(lat) * cos(lon)
        val y = (n + h) * cos(lat) * sin(lon)
        val z = (n * (1.0 - e.e2) + h) * sin(lat)
        return Xyz(x, y, z)
    }

    /** Geocentric XYZ → geodetic (deg, deg, m) on THIS ellipsoid (Bowring). */
    fun xyzToGeodetic(p: Xyz): Triple<Double, Double, Double> {
        val e = ellipsoid
        val lon = atan2(p.y, p.x)
        val r = sqrt(p.x * p.x + p.y * p.y)
        val theta = atan2(p.z * e.a, r * e.b)
        val lat = atan2(
            p.z + e.ep2 * e.b * sin(theta) * sin(theta) * sin(theta),
            r - e.e2 * e.a * cos(theta) * cos(theta) * cos(theta)
        )
        val n = e.a / sqrt(1.0 - e.e2 * sin(lat) * sin(lat))
        val h = r / cos(lat) - n
        return Triple(Math.toDegrees(lat), Math.toDegrees(lon), h)
    }

    /** Apply the 7-parameter Helmert: this datum's XYZ → WGS 84 XYZ. */
    fun toWgs84(p: Xyz): Xyz = helmert(p, +1)

    /** Inverse Helmert: WGS 84 XYZ → this datum's XYZ. */
    fun fromWgs84(p: Xyz): Xyz = helmert(p, -1)

    private fun helmert(p: Xyz, dir: Int): Xyz {
        val s = 1.0 + dir * scalePpm * 1e-6
        val arc = Math.PI / 180.0 / 3600.0
        val Rx = dir * rx * arc; val Ry = dir * ry * arc; val Rz = dir * rz * arc
        val tx = dir * dx; val ty = dir * dy; val tz = dir * dz
        // Position-vector rotation (small-angle), applied about the geocentre.
        val x = s * (p.x - Rz * p.y + Ry * p.z) + tx
        val y = s * (Rz * p.x + p.y - Rx * p.z) + ty
        val z = s * (-Ry * p.x + Rx * p.y + p.z) + tz
        return Xyz(x, y, z)
    }

    companion object {
        val WGS84 = Datum("WGS 84", Ellipsoid.WGS84)

        // NOTE: local-datum Helmert parameters vary by published realisation.
        // These are common working values — replace with your official set.
        val PALESTINE_1923 = Datum(
            "Palestine 1923", Ellipsoid.CLARKE1880_RGS,
            dx = -275.722, dy = 94.7824, dz = 340.894,
            rx = -8.001, ry = -4.42, rz = -11.821, scalePpm = 1.0
        )
        val ISRAEL_1993 = Datum(
            "Israel 1993 (GRS80)", Ellipsoid.GRS80,
            dx = -24.0024, dy = -17.1032, dz = -17.8444,
            rx = -0.33077, ry = -1.85269, rz = 1.66969, scalePpm = 5.4262
        )

        val all = listOf(WGS84, PALESTINE_1923, ISRAEL_1993)
    }
}
