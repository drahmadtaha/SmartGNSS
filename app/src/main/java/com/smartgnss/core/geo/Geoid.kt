package com.smartgnss.core.geo

import java.io.DataInputStream
import java.io.File
import kotlin.math.floor

/**
 * Geoid separation N (metres): the gap between the ellipsoid and mean sea level.
 * Orthometric (sea-level) height H = h − N, where h is the ellipsoidal height
 * the receiver reports.
 *
 * Three modes:
 *  - [None]: N = 0 (grid heights equal ellipsoidal heights).
 *  - [Constant]: a single N you enter for a local area.
 *  - [Grid]: a loaded GTX grid (e.g. EGM2008, or your local geoid) with bilinear
 *    interpolation — drop your own .gtx file on the device and load it.
 */
sealed interface GeoidModel {
    fun separation(latDeg: Double, lonDeg: Double): Double?
    val label: String

    data object None : GeoidModel {
        override fun separation(latDeg: Double, lonDeg: Double) = 0.0
        override val label = "None (N = 0)"
    }

    data class Constant(val n: Double) : GeoidModel {
        override fun separation(latDeg: Double, lonDeg: Double) = n
        override val label = "Constant N = %.3f m".format(n)
    }

    /**
     * A GTX grid: header of 4 big-endian doubles (south lat, west lon, dlat, dlon
     * in degrees) + 2 big-endian int32 (rows, cols), then rows×cols big-endian
     * float32 separations, ordered south→north then west→east.
     */
    class Grid(
        private val latOrigin: Double,
        private val lonOrigin: Double,
        private val dLat: Double,
        private val dLon: Double,
        private val rows: Int,
        private val cols: Int,
        private val values: FloatArray,
        override val label: String,
    ) : GeoidModel {

        override fun separation(latDeg: Double, lonDeg: Double): Double? {
            val fRow = (latDeg - latOrigin) / dLat
            val fCol = (lonDeg - lonOrigin) / dLon
            if (fRow < 0 || fCol < 0 || fRow > rows - 1 || fCol > cols - 1) return null
            val r0 = floor(fRow).toInt().coerceIn(0, rows - 2)
            val c0 = floor(fCol).toInt().coerceIn(0, cols - 2)
            val tr = fRow - r0; val tc = fCol - c0
            val v00 = at(r0, c0); val v01 = at(r0, c0 + 1)
            val v10 = at(r0 + 1, c0); val v11 = at(r0 + 1, c0 + 1)
            val top = v00 * (1 - tc) + v01 * tc
            val bot = v10 * (1 - tc) + v11 * tc
            return top * (1 - tr) + bot * tr
        }

        private fun at(row: Int, col: Int): Double = values[row * cols + col].toDouble()

        companion object {
            fun load(file: File): Grid {
                DataInputStream(file.inputStream().buffered()).use { s ->
                    val latO = s.readDouble()
                    val lonO = s.readDouble()
                    val dLat = s.readDouble()
                    val dLon = s.readDouble()
                    val rows = s.readInt()
                    val cols = s.readInt()
                    require(rows in 1..100_000 && cols in 1..100_000) {
                        "Unreasonable grid size ${rows}x$cols — is this a GTX file?"
                    }
                    val values = FloatArray(rows * cols)
                    for (i in values.indices) values[i] = s.readFloat()
                    return Grid(latO, lonO, dLat, dLon, rows, cols, values, file.name)
                }
            }
        }
    }
}
