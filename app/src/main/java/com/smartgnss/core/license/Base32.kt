package com.smartgnss.core.license

/**
 * RFC 4648 Base32 (no padding), used for activation codes because it is
 * case-insensitive and avoids ambiguous characters when typed by hand.
 * The issuer tool (Python) uses the exact same alphabet.
 */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val REVERSE = IntArray(128) { -1 }.apply {
        ALPHABET.forEachIndexed { i, c -> this[c.code] = i }
    }

    fun encode(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                sb.append(ALPHABET[(buffer shr bits) and 0x1F])
            }
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        return sb.toString()
    }

    /** Accepts dashes/spaces and any case; returns null on an illegal character. */
    fun decode(code: String): ByteArray? {
        val clean = code.uppercase().filter { it != '-' && it != ' ' }
        val out = ArrayList<Byte>(clean.length * 5 / 8)
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val v = if (c.code < 128) REVERSE[c.code] else -1
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}
