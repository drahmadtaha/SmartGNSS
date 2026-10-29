package com.smartgnss.core.project

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A survey project: a named job with its own creation time and coordinate system. */
data class Project(
    val name: String,
    val createdMs: Long,
    val crsName: String,
)

/**
 * Persists the list of projects and which one is open, in SharedPreferences.
 * Creating a project makes it the current one; opening switches; deleting removes
 * it (and clears "current" if it was open).
 */
class ProjectStore(context: Context) {

    private val prefs = context.getSharedPreferences("smartgnss_projects", Context.MODE_PRIVATE)

    private val _projects = MutableStateFlow(load())
    val projects = _projects.asStateFlow()

    private val _current = MutableStateFlow(prefs.getString(KEY_CURRENT, null))
    val current = _current.asStateFlow()

    fun create(name: String, crsName: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty() || _projects.value.any { it.name.equals(clean, true) }) return false
        val p = Project(clean, System.currentTimeMillis(), crsName)
        _projects.value = _projects.value + p
        _current.value = clean
        save()
        return true
    }

    fun open(name: String) {
        if (_projects.value.any { it.name == name }) {
            _current.value = name
            save()
        }
    }

    fun delete(name: String) {
        _projects.value = _projects.value.filterNot { it.name == name }
        if (_current.value == name) _current.value = _projects.value.firstOrNull()?.name
        save()
    }

    private fun load(): List<Project> = try {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Project(o.getString("name"), o.optLong("created"), o.optString("crs"))
        }
    } catch (_: Exception) { emptyList() }

    private fun save() {
        val arr = JSONArray()
        _projects.value.forEach { p ->
            arr.put(JSONObject().put("name", p.name).put("created", p.createdMs).put("crs", p.crsName))
        }
        prefs.edit()
            .putString(KEY_LIST, arr.toString())
            .putString(KEY_CURRENT, _current.value)
            .apply()
    }

    companion object {
        private const val KEY_LIST = "list"
        private const val KEY_CURRENT = "current"
    }
}
