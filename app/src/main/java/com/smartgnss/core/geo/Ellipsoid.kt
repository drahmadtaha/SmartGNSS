package com.smartgnss.core.geo

import kotlin.math.sqrt

/**
 * A reference ellipsoid, defined by its semi-major axis [a] and inverse
 * flattening [invF]. Everything the projections need (flattening, eccentricity)
 * is derived here once.
 */
data class Ellipsoid(
    val name: String,
    val a: Double,
    val invF: Double,
) {
    val f: Double = if (invF == 0.0) 0.0 else 1.0 / invF
    val b: Double = a * (1.0 - f)
    val e2: Double = f * (2.0 - f)              // first eccentricity squared
    val e: Double = sqrt(e2)
    val ep2: Double = e2 / (1.0 - e2)           // second eccentricity squared

    companion object {
        val WGS84 = Ellipsoid("WGS 84", 6378137.0, 298.257223563)
        val GRS80 = Ellipsoid("GRS 80", 6378137.0, 298.257222101)
        val CLARKE1880_RGS = Ellipsoid("Clarke 1880 (RGS)", 6378249.145, 293.465)
        val INTERNATIONAL1924 = Ellipsoid("International 1924", 6378388.0, 297.0)

        val all = listOf(WGS84, GRS80, CLARKE1880_RGS, INTERNATIONAL1924)
    }
}
