package com.smartgnss.core.license

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * Produces the stable per-device id that DEVICE-bound codes are tied to.
 *
 * Uses ANDROID_ID (per-app, survives app updates, resets on factory reset) and
 * hashes it so what the customer reads out and what you sign is a clean,
 * fixed-length token rather than the raw system id.
 */
object DeviceIdentity {

    @SuppressLint("HardwareIds")
    fun deviceId(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        val digest = MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray())
        // First 10 bytes → 16 Base32 chars: short enough to read over the phone.
        return Base32.encode(digest.copyOf(10))
    }

    /** "SG-XXXX-XXXX-XXXX-XXXX" — what you ask the customer to send you. */
    fun deviceCode(context: Context): String =
        "SG-" + deviceId(context).chunked(4).joinToString("-")
}
