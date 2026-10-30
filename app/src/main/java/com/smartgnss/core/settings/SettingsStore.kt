package com.smartgnss.core.settings

import android.content.Context
import com.smartgnss.core.geo.CrsDefinition
import com.smartgnss.core.geo.GeoidModel
import org.json.JSONObject
import java.io.File

/** Saved NTRIP connection, pre-filled next time the Corrections screen opens. */
data class NtripSaved(
    val host: String,
    val port: Int,
    val mountPoint: String,
    val username: String,
    val password: String,
)

/**
 * Persists the app's working settings — coordinate system, geoid model and NTRIP
 * connection — so a job continues exactly where it was left after a restart.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("smartgnss_settings", Context.MODE_PRIVATE)

    // --- Coordinate system ---------------------------------------------------
    fun saveCrs(def: CrsDefinition) {
        prefs.edit().putString(KEY_CRS, def.toJson().toString()).apply()
    }

    fun loadCrs(): CrsDefinition? = try {
        prefs.getString(KEY_CRS, null)?.let { CrsDefinition.fromJson(JSONObject(it)) }
    } catch (_: Exception) { null }

    // --- Geoid ---------------------------------------------------------------
    fun saveGeoid(model: GeoidModel) {
        val e = prefs.edit()
        when (model) {
            is GeoidModel.None -> e.putString(KEY_GEOID_TYPE, "none")
            is GeoidModel.Constant -> e.putString(KEY_GEOID_TYPE, "const")
                .putFloat(KEY_GEOID_N, model.n.toFloat())
            is GeoidModel.Grid -> e.putString(KEY_GEOID_TYPE, "grid")
                .putString(KEY_GEOID_PATH, model.sourcePath)
        }
        e.apply()
    }

    fun loadGeoid(): GeoidModel = when (prefs.getString(KEY_GEOID_TYPE, "none")) {
        "const" -> GeoidModel.Constant(prefs.getFloat(KEY_GEOID_N, 0f).toDouble())
        "grid" -> {
            val path = prefs.getString(KEY_GEOID_PATH, null)
            val file = path?.let { File(it) }
            if (file != null && file.exists())
                runCatching { GeoidModel.Grid.loadAny(file) }.getOrDefault(GeoidModel.None)
            else GeoidModel.None
        }
        else -> GeoidModel.None
    }

    // --- NTRIP ---------------------------------------------------------------
    fun saveNtrip(s: NtripSaved) {
        prefs.edit()
            .putString(KEY_N_HOST, s.host).putInt(KEY_N_PORT, s.port)
            .putString(KEY_N_MOUNT, s.mountPoint)
            .putString(KEY_N_USER, s.username).putString(KEY_N_PASS, s.password)
            .apply()
    }

    // --- Antenna / pole height ----------------------------------------------
    fun saveAntennaHeight(h: Double) {
        prefs.edit().putFloat(KEY_ANT_H, h.toFloat()).apply()
    }
    fun loadAntennaHeight(): Double = prefs.getFloat(KEY_ANT_H, 2.0f).toDouble()

    fun savePhaseCenter(h: Double) { prefs.edit().putFloat(KEY_PHASE, h.toFloat()).apply() }
    fun loadPhaseCenter(): Double = prefs.getFloat(KEY_PHASE, 0.0f).toDouble()

    fun loadNtrip(): NtripSaved? {
        val host = prefs.getString(KEY_N_HOST, null) ?: return null
        return NtripSaved(
            host = host,
            port = prefs.getInt(KEY_N_PORT, 2101),
            mountPoint = prefs.getString(KEY_N_MOUNT, "") ?: "",
            username = prefs.getString(KEY_N_USER, "") ?: "",
            password = prefs.getString(KEY_N_PASS, "") ?: "",
        )
    }

    companion object {
        private const val KEY_CRS = "crs_json"
        private const val KEY_GEOID_TYPE = "geoid_type"
        private const val KEY_GEOID_N = "geoid_n"
        private const val KEY_GEOID_PATH = "geoid_path"
        private const val KEY_N_HOST = "ntrip_host"
        private const val KEY_N_PORT = "ntrip_port"
        private const val KEY_N_MOUNT = "ntrip_mount"
        private const val KEY_N_USER = "ntrip_user"
        private const val KEY_N_PASS = "ntrip_pass"
        private const val KEY_ANT_H = "antenna_height"
        private const val KEY_PHASE = "phase_center"
    }
}
