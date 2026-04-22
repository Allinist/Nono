package com.allinist.nono.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

class RuleStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadRules(): List<NotificationRule> {
        val raw = prefs.getString(KEY_RULES, null) ?: return emptyList()
        return parseRules(raw) ?: emptyList()
    }

    fun saveRules(rules: List<NotificationRule>) {
        prefs.edit().putString(KEY_RULES, serializeRules(rules)).apply()
    }

    fun serializeRules(rules: List<NotificationRule>): String {
        val array = JSONArray()
        rules.forEach { array.put(it.toJson()) }
        return array.toString(2)
    }

    fun parseRules(raw: String): List<NotificationRule>? = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                add(array.getJSONObject(index).toRule())
            }
        }
    }.getOrNull()

    fun importRules(raw: String): Boolean {
        val parsed = parseRules(raw) ?: return false
        saveRules(parsed)
        return true
    }

    companion object {
        private const val PREF_NAME = "notification_rules"
        private const val KEY_RULES = "rules_json"
    }
}

private fun NotificationRule.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("appName", appName)
    put("packageName", packageName)
    put("note", note)
    put("enabled", enabled)
    put("mode", mode.name)
    put("remindAtMinutes", remindAtMinutes)
    put("soundMode", soundMode.name)
    put("startMinutes", startMinutes)
    put("endMinutes", endMinutes)
    put("allDay", allDay)
    put("dayMode", dayMode.name)
    put("manualEnabled", manualEnabled)
    put("targets", JSONArray(normalizedTargets()))
    put("days", JSONArray(activeDays.map { it.name }))
}

private fun JSONObject.toRule(): NotificationRule = NotificationRule(
    id = optString("id"),
    appName = optString("appName"),
    packageName = optString("packageName"),
    note = optString("note"),
    enabled = optBoolean("enabled", true),
    mode = optString("mode")
        .takeIf { it.isNotBlank() }
        ?.let { RuleMode.valueOf(it) }
        ?: RuleMode.BLOCK,
    remindAtMinutes = optInt("remindAtMinutes", 9 * 60),
    soundMode = optString("soundMode")
        .takeIf { it.isNotBlank() }
        ?.let { DeviceSoundMode.valueOf(it) }
        ?: DeviceSoundMode.KEEP,
    startMinutes = optInt("startMinutes", 9 * 60),
    endMinutes = optInt("endMinutes", 18 * 60),
    allDay = optBoolean("allDay", false),
    dayMode = optString("dayMode")
        .takeIf { it.isNotBlank() }
        ?.let { RuleDayMode.valueOf(it) }
        ?: RuleDayMode.WORKDAY,
    manualEnabled = optBoolean("manualEnabled", false),
    activeDays = optJSONArray("days")?.let { jsonArray ->
        buildSet {
            for (index in 0 until jsonArray.length()) {
                add(DayOfWeek.valueOf(jsonArray.getString(index)))
            }
        }
    } ?: defaultDays(),
    targets = optJSONArray("targets")?.toStringList().orEmpty(),
)

private fun JSONObject.defaultDays(): Set<DayOfWeek> = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
)

private fun JSONArray.toStringList(): List<String> = buildList {
    for (index in 0 until length()) {
        add(getString(index))
    }
}
