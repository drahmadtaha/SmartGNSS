package com.smartgnss.core.geo

import java.io.DataInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor

/**
 * Geoid separation N (metres): the gap between the ellipsoid and mean sea level.
 * Orthometric height H = h − N, where h is the ellipsoidal height.
 *
 * Supported grid formats: GTX (NOAA binary) and ISG (text). More vendor formats
 * (.ggf, .sgf, …) can be added to [Grid.loadAny] once their exact layout is known.
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

    /** A gridded geoid with bilinear interpolation, ordered south→north, west→east. */
    class Grid(
        private val latOrigin: Double,
        private val lonOrigin: Double,
        private val dLat: Double,
        private val dLon: Double,
        private val rows: Int,
        private val cols: Int,
        private val values: FloatArray,
        override val label: String,
        val sourcePath: String? = null,
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
            /** Load a geoid grid, choosing the parser by file extension or header. */
            fun loadAny(file: File): Grid {
                return when (file.extension.lowercase()) {
                    "gtx" -> loadGtx(file)
                    "isg" -> loadIsg(file)
                    "sgf" -> loadSgf(file)
                    else -> {
                        // Sniff the SOUTH magic, else try GTX then ISG.
                        val head = runCatching {
                            file.inputStream().use { val b = ByteArray(16); it.read(b); b }
                        }.getOrDefault(ByteArray(0))
                        if (head.size >= 15 && String(head, 0, 15) == "SOUTH GRID FILE") loadSgf(file)
                        else runCatching { loadGtx(file) }.getOrElse { loadIsg(file) }
                    }
                }
            }

            /**
             * South Grid File (.sgf): 256-byte header — 16-byte magic "SOUTH GRID
             * FILE", version, two 32-byte names, then little-endian doubles
             * [latMin, latMax, lonMin, lonMax, dLat, dLon] at offset 96 and int32
             * [nRows, nCols] at 144 — then nRows×nCols little-endian float32 geoid
             * separations ordered north→south, west→east from offset 256.
             */
            fun loadSgf(file: File): Grid {
                val bytes = file.readBytes()
                require(bytes.size >= 256 && String(bytes, 0, 15) == "SOUTH GRID FILE") {
                    "Not a SOUTH SGF file"
                }
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val latMin = bb.getDouble(96); val lonMin = bb.getDouble(112)
                val dLat = bb.getDouble(128); val dLon = bb.getDouble(136)
                val rows = bb.getInt(144); val cols = bb.getInt(148)
                require(rows in 1..100_000 && cols in 1..100_000) { "Bad SGF size ${rows}x$cols" }
                require(bytes.size >= 256 + rows * cols * 4) { "SGF data truncated" }

                val values = FloatArray(rows * cols)
                for (r in 0 until rows) for (c in 0 until cols) {
                    // stored north-first; flip to south-first for this Grid.
                    val src = (rows - 1 - r) * cols + c
                    values[r * cols + c] = bb.getFloat(256 + src * 4)
                }
                return Grid(latMin, lonMin, dLat, dLon, rows, cols, values,
                    file.name, file.absolutePath)
            }

            /** GTX: 4 big-endian doubles (south lat, west lon, dLat, dLon) + 2 int32
             *  (rows, cols) + rows×cols big-endian float32, south→north, west→east. */
            fun loadGtx(file: File): Grid {
                DataInputStream(file.inputStream().buffered()).use { s ->
                    val latO = s.readDouble(); val lonO = s.readDouble()
                    val dLat = s.readDouble(); val dLon = s.readDouble()
                    val rows = s.readInt(); val cols = s.readInt()
                    require(rows in 1..100_000 && cols in 1..100_000) {
                        "Not a GTX grid (${rows}x$cols)"
                    }
                    val values = FloatArray(rows * cols)
                    for (i in values.indices) values[i] = s.readFloat()
                    return Grid(latO, lonO, dLat, dLon, rows, cols, values,
                        file.name, file.absolutePath)
                }
            }

            /** ISG 2.0 text geoid: `key : value` header between begin/end_of_head,
             *  then nrows×ncols values ordered north→south, west→east. */
            fun loadIsg(file: File): Grid {
                val lines = file.readLines()
                val header = HashMap<String, String>()
                var dataStart = -1
                for ((i, raw) in lines.withIndex()) {
                    val line = raw.trim()
                    if (line.startsWith("end_of_head", true)) { dataStart = i + 1; break }
                    val c = line.indexOf(':')
                    if (c > 0) header[line.substring(0, c).trim().lowercase()] =
                        line.substring(c + 1).trim()
                }
                require(dataStart > 0) { "Not an ISG file (no header)" }
                fun d(k: String) = header[k]?.toDoubleOrNull()
                    ?: error("ISG header missing '$k'")
                fun n(k: String) = header[k]?.toIntOrNull() ?: error("ISG header missing '$k'")
                val latMin = d("lat min"); val lonMin = d("lon min")
                val dLat = d("delta lat"); val dLon = d("delta lon")
                val rows = n("nrows"); val cols = n("ncols")

                val nums = ArrayList<Float>(rows * cols)
                for (i in dataStart until lines.size) {
                    for (tok in lines[i].trim().split(Regex("\\s+"))) {
                        if (tok.isNotEmpty()) tok.toFloatOrNull()?.let { nums.add(it) }
                    }
                }
                require(nums.size >= rows * cols) { "ISG data short: ${nums.size} of ${rows * cols}" }

                // Flip north-first rows into south-first to match this Grid's ordering.
                val values = FloatArray(rows * cols)
                for (r in 0 until rows) for (c in 0 until cols) {
                    values[r * cols + c] = nums[(rows - 1 - r) * cols + c]
                }
                return Grid(latMin, lonMin, dLat, dLon, rows, cols, values,
                    file.name, file.absolutePath)
            }
        }
    }
}
