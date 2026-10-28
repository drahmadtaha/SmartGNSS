package com.smartgnss.core.license

/** How an activation code is tied to one unit. You pick this when you issue the code. */
enum class BindMode { DEVICE, RECEIVER, ANY }

/** Feature bits. Combine into the `feat` bitmask when issuing a code. */
object Feature {
    const val BASE          = 1 shl 0   // app runs at all
    const val RTK_NTRIP     = 1 shl 1   // NTRIP corrections / RTK
    const val RAW_LOGGING   = 1 shl 2   // raw observation logging
    const val SURVEY_EXPORT = 1 shl 3   // point export (CSV/DXF)
    const val STATIC        = 1 shl 4   // static session recording
    const val ALL           = -1        // everything
}

/**
 * The verified contents of an activation code. This is what the signature
 * protects — a customer cannot change any field without invalidating it.
 */
data class License(
    val version: Int,
    val licenseId: String,
    val issuedTo: String,
    val bindMode: BindMode,
    val bindingValue: String,   // android-id, receiver serial, or "" for ANY
    val features: Int,
    val expiryEpochDays: Long,  // 0 = perpetual
) {
    val perpetual: Boolean get() = expiryEpochDays == 0L

    fun hasFeature(bit: Int): Boolean = features == Feature.ALL || (features and bit) != 0

    fun expired(todayEpochDays: Long): Boolean =
        !perpetual && todayEpochDays > expiryEpochDays
}

/** Result of validating a pasted/loaded code against this specific unit. */
sealed interface ActivationResult {
    data class Valid(val license: License) : ActivationResult
    data object BadFormat : ActivationResult
    data object BadSignature : ActivationResult
    data class WrongDevice(val expected: String, val actual: String) : ActivationResult
    data class Expired(val onEpochDays: Long) : ActivationResult
}
