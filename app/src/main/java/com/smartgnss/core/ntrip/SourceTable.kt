package com.smartgnss.core.ntrip

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/** One caster stream (mountpoint) from the NTRIP source table. */
data class MountPoint(
    val mount: String,
    val identifier: String,
    val format: String,
    val lat: Double?,
    val lon: Double?,
)

/**
 * Fetches and parses an NTRIP caster's source table — the list of available
 * mountpoints — so the user can pick one instead of typing it. A GET on "/"
 * (no mountpoint) returns the table.
 */
object NtripSourceTable {

    suspend fun fetch(
        host: String,
        port: Int,
        username: String = "",
        password: String = "",
    ): List<MountPoint> = withContext(Dispatchers.IO) {
        Socket().use { sock ->
            sock.connect(InetSocketAddress(host, port), 10_000)
            sock.soTimeout = 15_000
            val out = sock.getOutputStream()
            val auth = Base64.encodeToString(
                "$username:$password".toByteArray(), Base64.NO_WRAP
            )
            val req = buildString {
                append("GET / HTTP/1.0\r\n")
                append("User-Agent: NTRIP SmartGNSS/1.0\r\n")
                append("Accept: */*\r\n")
                if (username.isNotEmpty()) append("Authorization: Basic $auth\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(req.toByteArray(Charsets.US_ASCII)); out.flush()

            val text = sock.getInputStream().readBytes().toString(Charsets.ISO_8859_1)
            parse(text)
        }
    }

    private fun parse(response: String): List<MountPoint> {
        val result = ArrayList<MountPoint>()
        for (line in response.split("\n")) {
            if (!line.startsWith("STR;")) continue
            val f = line.split(";")
            if (f.size < 4) continue
            result.add(
                MountPoint(
                    mount = f.getOrElse(1) { "" },
                    identifier = f.getOrElse(2) { "" },
                    format = f.getOrElse(3) { "" },
                    lat = f.getOrNull(9)?.toDoubleOrNull(),
                    lon = f.getOrNull(10)?.toDoubleOrNull(),
                )
            )
        }
        return result
    }
}
