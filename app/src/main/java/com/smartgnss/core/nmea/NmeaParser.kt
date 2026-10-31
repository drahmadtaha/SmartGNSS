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

    // Satellites used, accumulated across the GSA sentences of one epoch
    // (receivers that omit GGA still report used SVs in GSA).
    private val gsaPrns = HashSet<Int>()

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
                // Unicore '#' logs (e.g. #SLANTAPA) run ~160 chars — allow headroom.
                if (lineBuf.length > 300) lineBuf.setLength(0) // guard against junk
            }
        }
        return out
    }

    private fun parseLine(raw: String): GnssFix? {
        // Unicore proprietary '#' logs (CRC32, not NMEA XOR) carry the IMU tilt.
        if (raw.startsWith("#")) {
            val changed = when {
                raw.startsWith("#SLANTAPA") -> parseSlantApa(raw)
                raw.startsWith("#SLANTSTATUSA") -> parseSlantStatus(raw)
                else -> false
            }
            return if (changed) fix else null
        }
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
            "GST" -> parseGst(f)
            "HPR", "TRA" -> parseHpr(f)   // UM982 heading/pitch/roll
            "SXT" -> parseKsxt(f)         // $KSXT: position + heading/pitch/roll
            "FPD" -> parseFpd(f)          // $GPFPD: heading/pitch/roll
            "FMI" -> parseFmi(f)          // $GPFMI: ICE M10 IMU tilt (tiltX/tiltY rad)
            else -> false
        }
        return if (changed) fix else null
    }

    /** GST: position-error statistics → horizontal / vertical RMS. */
    private fun parseGst(f: List<String>): Boolean {
        if (f.size < 9) return false
        val sdLat = f[6].toDoubleOrNull()
        val sdLon = f[7].toDoubleOrNull()
        val sdAlt = f[8].toDoubleOrNull()
        val hr = if (sdLat != null && sdLon != null)
            kotlin.math.hypot(sdLat, sdLon) else fix.hrms
        fix = fix.copy(hrms = hr, vrms = sdAlt ?: fix.vrms)
        return true
    }

    /** Tilt (angle from vertical) from pitch & roll in degrees. */
    private fun tiltFrom(p: Double?, r: Double?): Double? {
        if (p == null || r == null) return null
        val pr = Math.toRadians(p); val rr = Math.toRadians(r)
        return Math.toDegrees(kotlin.math.acos(kotlin.math.cos(pr) * kotlin.math.cos(rr)))
    }

    private fun setAttitude(h: Double?, p: Double?, r: Double?) {
        val tilt = tiltFrom(p, r)
        fix = fix.copy(
            headingDeg = h ?: fix.headingDeg,
            pitchDeg = p ?: fix.pitchDeg, rollDeg = r ?: fix.rollDeg,
            tiltAngleDeg = tilt ?: fix.tiltAngleDeg, tiltValid = tilt != null,
        )
    }

    /** HPR/TRA: heading, pitch, roll (deg). */
    private fun parseHpr(f: List<String>): Boolean {
        if (f.size < 5) return false
        setAttitude(f[2].toDoubleOrNull(), f[3].toDoubleOrNull(), f[4].toDoubleOrNull())
        return true
    }

    /** $KSXT,time,lon,lat,hgt,heading,pitch,track,speed,roll,… */
    private fun parseKsxt(f: List<String>): Boolean {
        if (f.size < 10) return false
        setAttitude(f[5].toDoubleOrNull(), f[6].toDoubleOrNull(), f[9].toDoubleOrNull())
        return true
    }

    /** $GPFPD,week,time,heading,pitch,roll,lat,lon,alt,… */
    private fun parseFpd(f: List<String>): Boolean {
        if (f.size < 6) return false
        setAttitude(f[3].toDoubleOrNull(), f[4].toDoubleOrNull(), f[5].toDoubleOrNull())
        return true
    }

    /**
     * $GPFMI — ICE M10/Cube IMU sentence:
     * $GPFMI,time,lat,lon,height,tiltX,tiltY,heading,acc,sats,…,fix,status
     * tiltX/tiltY are tilt components in radians; tilt angle from vertical is
     * θ = hypot(tiltX, tiltY). Heading is 9.99999 when invalid (single antenna).
     */
    private fun parseFmi(f: List<String>): Boolean {
        if (f.size < 7) return false
        val tx = f[5].toDoubleOrNull()
        val ty = f[6].toDoubleOrNull()
        val tiltDeg = if (tx != null && ty != null)
            Math.toDegrees(kotlin.math.hypot(tx, ty)) else null
        val hdg = f[7].toDoubleOrNull()?.takeIf { it in 0.0..360.0 }
        fix = fix.copy(
            tiltAngleDeg = tiltDeg ?: fix.tiltAngleDeg,
            tiltValid = tiltDeg != null,
            headingDeg = hdg ?: fix.headingDeg,
        )
        return true
    }

    /**
     * #SLANTAPA — UM982 pole-attitude / tilt-compensation log (this is what
     * ICE's own apps and X-Survey read for tilt; it is NOT an NMEA '$' sentence).
     *
     *   #SLANTAPA,<header…>;a,b,week,ms,lat,lon,hgt,sdLat,sdLon,sdHgt,
     *             tilt,projAngle,…*<crc32>
     *
     * The body after ';' holds (0-based): [4]=lat [5]=lon [6]=ellipsoidal hgt
     * of the pole tip, [7..9]=std devs, [10]=tilt angle (deg from vertical),
     * [11]=projection angle (azimuth of the lean, deg). The position is already
     * tilt- and pole-length-compensated by the receiver.
     */
    private fun parseSlantApa(raw: String): Boolean {
        val semi = raw.indexOf(';')
        if (semi < 0) return false
        val star = raw.indexOf('*', semi)
        val dataStr = raw.substring(semi + 1, if (star < 0) raw.length else star)
        val d = dataStr.split(',')
        if (d.size < 12) return false
        val tilt = d[10].toDoubleOrNull()
        val proj = d[11].toDoubleOrNull()
        val lat = d[4].toDoubleOrNull()
        val lon = d[5].toDoubleOrNull()
        val hgt = d[6].toDoubleOrNull()
        // While the IMU is still converging the receiver streams an all-zero
        // solution (lat/lon/tilt = 0); trusting it would peg the position at
        // (0,0). Accept only a solution with a plausible latitude/longitude —
        // this is firmware-independent (no reliance on the status flag's meaning).
        val valid = lat != null && lon != null && lat.isFinite() && lon.isFinite() &&
            kotlin.math.abs(lat) > 1.0 && kotlin.math.abs(lon) > 1.0
        if (!valid) {
            // Drop any stale compensated position so the app falls back to GGA.
            if (fix.tiltValid || fix.tiltCompLat != null) {
                fix = fix.copy(
                    tiltValid = false, tiltCompLat = null,
                    tiltCompLon = null, tiltCompAltEllip = null,
                )
                return true
            }
            return false
        }
        fix = fix.copy(
            tiltAngleDeg = tilt ?: fix.tiltAngleDeg,
            tiltValid = true,
            projectionAngleDeg = proj ?: fix.projectionAngleDeg,
            tiltCompLat = lat,
            tiltCompLon = lon,
            tiltCompAltEllip = hgt ?: fix.tiltCompAltEllip,
        )
        return true
    }

    /**
     * #SLANTSTATUSA — IMU convergence/initialisation state. The body after ';'
     * begins with a word: "CONVERGENCE" while still initialising, and reports
     * "INITIALIZED"/"OK" once ready for tilt survey. We treat anything that is
     * not plain CONVERGENCE as converged/ready.
     */
    private fun parseSlantStatus(raw: String): Boolean {
        val semi = raw.indexOf(';')
        if (semi < 0) return false
        val star = raw.indexOf('*', semi)
        val body = raw.substring(semi + 1, if (star < 0) raw.length else star)
        val word = body.substringBefore(',').trim().uppercase()
        val converged = word.isNotEmpty() &&
            word != "CONVERGENCE" && word != "INITIALIZING" && word != "NONE"
        if (converged == fix.tiltConverged) return false
        fix = fix.copy(tiltConverged = converged)
        return true
    }

    private fun parseGga(f: List<String>): Boolean {
        if (f.size < 15) return false
        // Some receivers (incl. ICE/UM982) always report 000 sats in GGA — in
        // that case keep the GSA-derived count instead of zeroing it.
        val ggaSats = f[7].toIntOrNull() ?: 0
        if (ggaSats > 0) gsaPrns.clear()
        fix = fix.copy(
            utc = f[1].ifBlank { null },
            latitude = coord(f[2], f[3]),
            longitude = coord(f[4], f[5]),
            quality = FixQuality.from(f[6].toIntOrNull()),
            satellitesUsed = if (ggaSats > 0) ggaSats else fix.satellitesUsed,
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
        // Fields 3..14 are up to 12 used-satellite PRNs.
        for (i in 3..14) if (i < f.size) f[i].toIntOrNull()?.let { gsaPrns.add(it) }
        val fixType = f[2].toIntOrNull() ?: 0
        fix = fix.copy(
            pdop = f[15].toDoubleOrNull() ?: fix.pdop,
            hdop = f[16].toDoubleOrNull() ?: fix.hdop,
            vdop = f[17].toDoubleOrNull() ?: fix.vdop,
            satellitesUsed = maxOf(fix.satellitesUsed, gsaPrns.size),
            // Basic fix if the receiver sends no GGA quality (RMC may upgrade it).
            quality = if (fix.quality == FixQuality.NO_FIX && fixType >= 2)
                FixQuality.GPS else fix.quality,
        )
        return true
    }

    private fun parseRmc(f: List<String>): Boolean {
        if (f.size < 9) return false
        // Finalise the epoch's used-satellite count from the GSA sentences.
        if (gsaPrns.isNotEmpty()) {
            fix = fix.copy(satellitesUsed = gsaPrns.size)
            gsaPrns.clear()
        }
        val knots = f[7].toDoubleOrNull()
        // Position mode indicator (NMEA 2.3+), last data field: A/D/R/F/E/N.
        val posMode = f.getOrNull(12)?.firstOrNull()
        val rmcQuality = when (posMode) {
            'R' -> FixQuality.RTK_FIXED
            'F' -> FixQuality.RTK_FLOAT
            'D' -> FixQuality.DGPS
            'A' -> FixQuality.GPS
            'E' -> FixQuality.ESTIMATED
            'N' -> FixQuality.NO_FIX
            else -> null
        }
        fix = fix.copy(
            utc = f[1].ifBlank { fix.utc },
            latitude = coord(f[3], f[4]) ?: fix.latitude,
            longitude = coord(f[5], f[6]) ?: fix.longitude,
            speedKmh = knots?.let { it * 1.852 } ?: fix.speedKmh,
            headingDeg = f[8].toDoubleOrNull() ?: fix.headingDeg,
            // Use the RMC mode only when the receiver sends no GGA quality.
            quality = rmcQuality?.takeIf {
                fix.quality == FixQuality.NO_FIX || fix.quality == FixQuality.GPS
            } ?: fix.quality,
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
