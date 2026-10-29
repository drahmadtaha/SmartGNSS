package com.smartgnss.core.license

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Owns the activation state for the whole app: holds the current validated
 * license (if any), persists the accepted code, and re-checks it on every
 * launch (so an expired code stops working even offline).
 *
 * UI observes [status]; feature gates call [has].
 */
class LicenseManager(
    private val context: Context,
    private val verifier: LicenseVerifier = LicenseVerifier(),
) {
    private val prefs = context.getSharedPreferences("smartgnss_license", Context.MODE_PRIVATE)

    /** Dedicated activation file, kept in the app's external files dir so it
     *  survives app-data clears and is locatable via a file manager. */
    private val licenseFile =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "SmartGNSS_activation.key")

    /** Where the activation code is stored on this device. */
    val licenseLocation: String get() = licenseFile.absolutePath

    private val _status = MutableStateFlow(load())
    val status: StateFlow<ActivationState> = _status.asStateFlow()

    val deviceCode: String get() = DeviceIdentity.deviceCode(context)

    /** Try to activate with a pasted/scanned code. On success it is persisted. */
    fun activate(code: String, receiverSerial: String? = null): ActivationResult {
        val result = verifier.verify(
            code = code.trim(),
            deviceId = DeviceIdentity.deviceId(context),
            receiverSerial = receiverSerial,
            todayEpochDays = todayEpochDays(),
        )
        if (result is ActivationResult.Valid) {
            prefs.edit().putString(KEY_CODE, code.trim()).apply()
            runCatching { licenseFile.writeText(code.trim()) }
            _status.value = ActivationState.Activated(result.license)
        }
        return result
    }

    fun deactivate() {
        prefs.edit().remove(KEY_CODE).apply()
        runCatching { licenseFile.delete() }
        _status.value = ActivationState.NotActivated
    }

    fun has(feature: Int): Boolean =
        (_status.value as? ActivationState.Activated)?.license?.hasFeature(feature) == true

    /** Re-validate the stored code (call on resume, or after the clock changes). */
    fun refresh(receiverSerial: String? = null) {
        _status.value = load(receiverSerial)
    }

    private fun load(receiverSerial: String? = null): ActivationState {
        // Prefer prefs; fall back to the activation file (e.g. after a data clear).
        var code = prefs.getString(KEY_CODE, null)
        if (code == null && licenseFile.exists()) {
            code = runCatching { licenseFile.readText().trim() }.getOrNull()
            if (!code.isNullOrEmpty()) prefs.edit().putString(KEY_CODE, code).apply()
        }
        if (code.isNullOrEmpty()) return ActivationState.NotActivated
        return when (val r = verifier.verify(
            code, DeviceIdentity.deviceId(context), receiverSerial, todayEpochDays()
        )) {
            is ActivationResult.Valid -> ActivationState.Activated(r.license)
            is ActivationResult.Expired -> ActivationState.Lapsed("License expired")
            else -> ActivationState.Lapsed("Stored code no longer valid")
        }
    }

    private fun todayEpochDays(): Long = System.currentTimeMillis() / 86_400_000L

    companion object { private const val KEY_CODE = "code" }
}

sealed interface ActivationState {
    data object NotActivated : ActivationState
    data class Activated(val license: License) : ActivationState
    data class Lapsed(val reason: String) : ActivationState
}
