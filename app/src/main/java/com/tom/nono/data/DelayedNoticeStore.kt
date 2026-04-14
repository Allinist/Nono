package com.tom.nono.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class DelayedNotice(
    val id: String,
    val ruleId: String,
    val appName: String,
    val packageName: String,
    val title: String,
    val text: String,
    val scheduledAtMillis: Long,
    val createdAtMillis: Long,
)

class DelayedNoticeStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadNotices(): List<DelayedNotice> {
        val raw = prefs.getString(KEY_NOTICES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toNotice())
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addNotice(notice: DelayedNotice) {
        val updated = loadNotices().filterNot { it.id == notice.id } + notice
        save(updated)
    }

    fun removeNotice(id: String) {
        save(loadNotices().filterNot { it.id == id })
    }

    private fun save(notices: List<DelayedNotice>) {
        val array = JSONArray()
        notices.sortedBy { it.scheduledAtMillis }.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_NOTICES, array.toString()).apply()
    }

    companion object {
        private const val PREF_NAME = "delayed_notice_store"
        private const val KEY_NOTICES = "delayed_notices_json"
    }
}

private fun DelayedNotice.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("ruleId", ruleId)
    put("appName", appName)
    put("packageName", packageName)
    put("title", title)
    put("text", text)
    put("scheduledAtMillis", scheduledAtMillis)
    put("createdAtMillis", createdAtMillis)
}

private fun JSONObject.toNotice(): DelayedNotice = DelayedNotice(
    id = optString("id"),
    ruleId = optString("ruleId"),
    appName = optString("appName"),
    packageName = optString("packageName"),
    title = optString("title"),
    text = optString("text"),
    scheduledAtMillis = optLong("scheduledAtMillis"),
    createdAtMillis = optLong("createdAtMillis"),
)
