package com.smartgnss.core.device

/**
 * The ICE GNSS receiver model in use. Both are Unicore-core receivers with the
 * ICE AT-command layer, but commands/behaviour can differ, so the selected model
 * lets the app tailor configuration per device.
 */
enum class DeviceModel(val label: String, val phaseCenter: Double) {
    // phaseCenter = fixed vertical distance from the antenna reference point up
    // to the L1 phase centre (metres), supplied by the manufacturer.
    CUBE_RTK("Cube RTK", 0.0710),
    M10("M10 GNSS", 0.0512);

    companion object {
        fun fromName(name: String?): DeviceModel =
            entries.firstOrNull { it.name == name } ?: CUBE_RTK

        /** Map the receiver's own AT+DTYPE reply (e.g. "CUBERTK", "M10") to a
         *  model, so the app can auto-detect the connected device. Returns null
         *  if the type string isn't recognised. */
        fun fromDtype(dtype: String?): DeviceModel? {
            val d = dtype?.trim()?.uppercase() ?: return null
            return when {
                d.contains("CUBE") -> CUBE_RTK
                d.contains("M10") -> M10
                else -> null
            }
        }
    }
}
