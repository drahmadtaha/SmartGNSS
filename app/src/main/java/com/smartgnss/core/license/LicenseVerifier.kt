package com.smartgnss.core.license

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Verifies activation codes entirely on-device, with no network call.
 *
 * An activation code is Base32( [2-byte payload length][payload JSON][ECDSA signature] ).
 * The signature is made with YOUR private key (kept on your side, in
 * licensing-tools/). The app holds only the matching PUBLIC key, so it can
 * check authenticity but can never mint a code — which is the whole point.
 *
 * Replace [PUBLIC_KEY_SPKI_BASE64] with your own key before production
 * (licensing-tools/README.md shows how).
 */
class LicenseVerifier(
    publicKeySpkiBase64: String = PUBLIC_KEY_SPKI_BASE64,
) {
    private val publicKey = run {
        val der = Base64.decode(publicKeySpkiBase64, Base64.DEFAULT)
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
    }

    /**
     * @param code         the activation code the customer entered/scanned
     * @param deviceId      this device's binding id (see DeviceIdentity)
     * @param receiverSerial the connected receiver's serial, or null if unknown
     * @param todayEpochDays System-independent "today" for the expiry check
     */
    fun verify(
        code: String,
        deviceId: String,
        receiverSerial: String?,
        todayEpochDays: Long,
    ): ActivationResult {
        val raw = Base32.decode(code) ?: return ActivationResult.BadFormat
        if (raw.size < 4) return ActivationResult.BadFormat

        val payloadLen = ((raw[0].toInt() and 0xFF) shl 8) or (raw[1].toInt() and 0xFF)
        if (payloadLen <= 0 || 2 + payloadLen > raw.size) return ActivationResult.BadFormat

        val payload = raw.copyOfRange(2, 2 + payloadLen)
        val signature = raw.copyOfRange(2 + payloadLen, raw.size)

        // 1. Authenticity — did we sign this exact payload?
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initVerify(publicKey)
        sig.update(payload)
        val ok = try { sig.verify(signature) } catch (_: Exception) { false }
        if (!ok) return ActivationResult.BadSignature

        // 2. Decode the (now-trusted) payload.
        val license = try { parse(String(payload, Charsets.UTF_8)) }
            catch (_: Exception) { return ActivationResult.BadFormat }

        // 3. Binding — is this code meant for THIS unit?
        when (license.bindMode) {
            BindMode.DEVICE -> if (!license.bindingValue.equals(deviceId, true))
                return ActivationResult.WrongDevice(license.bindingValue, deviceId)
            BindMode.RECEIVER -> {
                val serial = receiverSerial ?: ""
                if (!license.bindingValue.equals(serial, true))
                    return ActivationResult.WrongDevice(license.bindingValue, serial)
            }
            BindMode.ANY -> { /* not tied to hardware */ }
        }

        // 4. Expiry.
        if (license.expired(todayEpochDays))
            return ActivationResult.Expired(license.expiryEpochDays)

        return ActivationResult.Valid(license)
    }

    private fun parse(json: String): License {
        val o = JSONObject(json)
        return License(
            version = o.getInt("v"),
            licenseId = o.getString("lid"),
            issuedTo = o.optString("to"),
            bindMode = BindMode.valueOf(o.getString("bind")),
            bindingValue = o.optString("bid"),
            features = o.getInt("feat"),
            expiryEpochDays = o.optLong("exp", 0L),
        )
    }

    companion object {
        /**
         * Sample key — generated with the project. REPLACE with your own public
         * key (licensing-tools/keys/smartgnss_public.pem) before shipping, or
         * anyone with this project can issue codes that your app accepts.
         */
        const val PUBLIC_KEY_SPKI_BASE64 =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAET+ooT/98js2dTxZIaQ1eRKSCCXMe" +
            "4wiItutqzoT4XhshNmuu3+qnXzJ/pm9X1vQAiGvd1i2BhixGDEi77gV/Mg=="
    }
}
