package com.smartgnss.core.nmea

/**
 * Incremental NMEA 0183 parser.
 *
 * Feed it raw bytes from the receiver with [feed]; it buffers partial lines,
 * validates checksums, folds each sentence into a running [GnssFix], and
 * returns the updated fix whenever something changed. GSV sentences accumulate
 * across their multiple messages before being published.
 */
class NmeaParser {

    private val lineBuf = StringBuilder()
    private var fix = GnssFix()

    // GSV accumulates over several sentences per constellation.
    private val gsvWork = mutableMapOf<String, MutableList<Satellite>>()
    private val gsvExpected = mutableMapOf<String, Int>()

    /** Returns a fresh [GnssFix] for every complete, valid sentence parsed. */
    fun feed(bytes: ByteArray): List<GnssFix> {
        val out = ArrayList<GnssFix>()
        for (b in bytes) {
            val c = b.toInt().toChar()
            if (c == '\n' || c == '\r') {
                if (lineBuf.isNotEmpty()) {
                    parseLine(lineBuf.toString())?.let { out.add(it) }
                    lineBuf.setLength(0)
                }
            } else {
                lineBuf.append(c)
                if (lineBuf.length > 200) lineBuf.setLength(0) // guard against junk
            }
        }
        return out
    }

    private fun parseLine(raw: String): GnssFix? {
        if (!raw.startsWith("$") || !checksumOk(raw)) return null
        val body = raw.substring(1, raw.indexOf('*').let { if (it < 0) raw.length else it })
        val f = body.split(',')
        if (f.isEmpty()) return null
        val type = f[0]                // e.g. GPGGA, GNGGA, GPGSV
        val talker = type.takeLast(3)  // GGA / GSA / GSV / RMC / VTG

        val changed = when (talker) {
            "GGA" -> parseGga(f)
            "GSA" -> parseGsa(f)
            "GSV" -> parseGsv(type, f)
            "RMC" -> parseRmc(f)
            "VTG" -> parseVtg(f)
            else -> false
        }
        return if (changed) fix else null
    }

    private fun parseGga(f: List<String>): Boolean {
        if (f.size < 15) return false
        fix = fix.copy(
            utc = f[1].ifBlank { null },
            latitude = coord(f[2], f[3]),
            longitude = coord(f[4], f[5]),
            quality = FixQuality.from(f[6].toIntOrNull()),
            satellitesUsed = f[7].toIntOrNull() ?: fix.satellitesUsed,
            hdop = f[8].toDoubleOrNull() ?: fix.hdop,
            altitudeMsl = f[9].toDoubleOrNull(),
            geoidSep = f[11].toDoubleOrNull(),
            diffAgeSec = f[13].toDoubleOrNull(),
            baseStationId = f[14].ifBlank { null },
        )
        return true
    }

    private fun parseGsa(f: List<String>): Boolean {
        if (f.size < 18) return false
        fix = fix.copy(
            pdop = f[15].toDoubleOrNull() ?: fix.pdop,
            hdop = f[16].toDoubleOrNull() ?: fix.hdop,
            vdop = f[17].toDoubleOrNull() ?: fix.vdop,
        )
        return true
    }

    private fun parseRmc(f: List<String>): Boolean {
        if (f.size < 9) return false
        val knots = f[7].toDoubleOrNull()
        fix = fix.copy(
            utc = f[1].ifBlank { fix.utc },
            latitude = coord(f[3], f[4]) ?: fix.latitude,
            longitude = coord(f[5], f[6]) ?: fix.longitude,
            speedKmh = knots?.let { it * 1.852 } ?: fix.speedKmh,
            headingDeg = f[8].toDoubleOrNull() ?: fix.headingDeg,
        )
        return true
    }

    private fun parseVtg(f: List<String>): Boolean {
        if (f.size < 8) return false
        fix = fix.copy(
            headingDeg = f[1].toDoubleOrNull() ?: fix.headingDeg,
            speedKmh = f[7].toDoubleOrNull() ?: fix.speedKmh,
        )
        return true
    }

    private fun parseGsv(type: String, f: List<String>): Boolean {
        if (f.size < 4) return false
        val constellation = constellationOf(type)
        val totalMsgs = f[1].toIntOrNull() ?: return false
        val msgNum = f[2].toIntOrNull() ?: return false
        if (msgNum == 1) gsvWork[constellation] = mutableListOf()
        val list = gsvWork.getOrPut(constellation) { mutableListOf() }

        var i = 4
        while (i + 3 < f.size) {
            val prn = f[i].toIntOrNull()
            if (prn != null) {
                list.add(
                    Satellite(
                        prn = prn,
                        elevation = f[i + 1].toIntOrNull(),
                        azimuth = f[i + 2].toIntOrNull(),
                        snr = f[i + 3].toIntOrNull(),
                        constellation = constellation,
                    )
                )
            }
            i += 4
        }
        gsvExpected[constellation] = totalMsgs
        if (msgNum == totalMsgs) {
            // Rebuild the combined in-view list across all constellations.
            val all = gsvWork.values.flatten()
            fix = fix.copy(satellitesInView = all)
            return true
        }
        return false
    }

    private fun constellationOf(type: String): String = when {
        type.startsWith("GP") -> "GPS"
        type.startsWith("GL") -> "GLONASS"
        type.startsWith("GA") -> "Galileo"
        type.startsWith("GB") || type.startsWith("BD") -> "BeiDou"
        type.startsWith("GQ") -> "QZSS"
        type.startsWith("GI") -> "NavIC"
        else -> "GNSS"
    }

    /** NMEA lat/lon are ddmm.mmmm + hemisphere; convert to signed decimal deg. */
    private fun coord(value: String, hemi: String): Double? {
        if (value.isBlank()) return null
        val dot = value.indexOf('.')
        if (dot < 3) return null
        val degDigits = if (dot >= 4) dot - 2 else 2
        val deg = value.substring(0, degDigits).toDoubleOrNull() ?: return null
        val min = value.substring(degDigits).toDoubleOrNull() ?: return null
        var dec = deg + min / 60.0
        if (hemi == "S" || hemi == "W") dec = -dec
        return dec
    }

    private fun checksumOk(raw: String): Boolean {
        val star = raw.indexOf('*')
        if (star < 0 || star + 3 > raw.length) return true // no checksum present → accept
        var cs = 0
        for (i in 1 until star) cs = cs xor raw[i].code
        val given = raw.substring(star + 1, star + 3).toIntOrNull(16) ?: return false
        return cs == given
    }
}
