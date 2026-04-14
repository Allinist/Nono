package com.tom.nono.data

import android.content.Context
import androidx.annotation.RawRes
import com.tom.nono.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DEFAULT_REMOTE_URL = "https://timor.tech/api/holiday/batch"

data class HolidayCalendarConfig(
    val useChinaWorkdayCalendar: Boolean = false,
    val remoteUrl: String = DEFAULT_REMOTE_URL,
    val lastSyncAtMillis: Long = 0L,
    val lastSyncMessage: String = "",
)

data class ManualDayOverride(
    val date: LocalDate,
    val isWorkday: Boolean,
    val note: String = "",
)

data class HolidayCalendarSnapshot(
    val version: String,
    val source: String,
    val workdays: Set<LocalDate>,
    val offdays: Set<LocalDate>,
    val updatedAtMillis: Long,
)

data class HolidaySyncResult(
    val success: Boolean,
    val message: String,
    val syncedAtMillis: Long = 0L,
)

class HolidayCalendarStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadConfig(): HolidayCalendarConfig {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return HolidayCalendarConfig()
        return runCatching { JSONObject(raw).toConfig() }.getOrDefault(HolidayCalendarConfig())
    }

    fun saveConfig(config: HolidayCalendarConfig) {
        prefs.edit().putString(KEY_CONFIG, config.toJson().toString()).commit()
    }

    fun loadOverrides(): List<ManualDayOverride> {
        val raw = prefs.getString(KEY_OVERRIDES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toOverride())
                }
            }.sortedBy { it.date }
        }.getOrDefault(emptyList())
    }

    fun addOverride(override: ManualDayOverride) {
        val updated = loadOverrides()
            .filterNot { it.date == override.date }
            .plus(override)
            .sortedBy { it.date }
        saveOverrides(updated)
    }

    fun removeOverride(date: LocalDate) {
        saveOverrides(loadOverrides().filterNot { it.date == date })
    }

    fun loadSnapshot(): HolidayCalendarSnapshot {
        val syncedRaw = prefs.getString(KEY_SYNCED_SNAPSHOT, null)
        val synced = syncedRaw?.let { runCatching { JSONObject(it).toSnapshot() }.getOrNull() }
        return synced ?: loadBundledSnapshot()
    }

    fun isWorkingDate(date: LocalDate): Boolean {
        val config = loadConfig()
        if (!config.useChinaWorkdayCalendar) {
            return date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        }

        val override = loadOverrides().firstOrNull { it.date == date }
        if (override != null) return override.isWorkday

        val snapshot = loadSnapshot()
        if (date in snapshot.workdays) return true
        if (date in snapshot.offdays) return false

        return date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }

    fun syncNow(referenceYear: Int = Year.now().value): HolidaySyncResult {
        val config = loadConfig()
        val remoteUrl = config.remoteUrl.trim()
        if (remoteUrl.isBlank()) {
            return HolidaySyncResult(success = false, message = "请先填写在线日历地址")
        }

        return runCatching {
            val years = listOf(referenceYear, referenceYear + 1)
            val statuses = linkedMapOf<LocalDate, Boolean>()

            years.forEach { year ->
                fetchYearStatuses(remoteUrl, year).forEach { (date, isWorkday) ->
                    statuses[date] = isWorkday
                }
            }

            val now = System.currentTimeMillis()
            val snapshot = HolidayCalendarSnapshot(
                version = "sync-$referenceYear",
                source = remoteUrl,
                workdays = statuses.filterValues { it }.keys,
                offdays = statuses.filterValues { !it }.keys,
                updatedAtMillis = now,
            )
            saveSnapshot(snapshot)
            saveConfig(
                config.copy(
                    lastSyncAtMillis = now,
                    lastSyncMessage = "已同步 ${years.first()}-${years.last()} 中国工作日历",
                ),
            )
            HolidaySyncResult(
                success = true,
                message = "已同步 ${years.first()}-${years.last()} 中国工作日历",
                syncedAtMillis = now,
            )
        }.getOrElse { error ->
            val message = error.message?.takeIf { it.isNotBlank() } ?: "同步失败"
            saveConfig(config.copy(lastSyncMessage = message))
            HolidaySyncResult(success = false, message = message)
        }
    }

    private fun saveOverrides(overrides: List<ManualDayOverride>) {
        val array = JSONArray()
        overrides.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_OVERRIDES, array.toString()).commit()
    }

    private fun loadBundledSnapshot(): HolidayCalendarSnapshot =
        context.resources.openRawResource(BUNDLED_RESOURCE).bufferedReader().use(BufferedReader::readText)
            .let(::JSONObject)
            .toSnapshot()

    private fun saveSnapshot(snapshot: HolidayCalendarSnapshot) {
        prefs.edit().putString(KEY_SYNCED_SNAPSHOT, snapshot.toJson().toString()).commit()
    }

    private fun fetchYearStatuses(baseUrl: String, year: Int): Map<LocalDate, Boolean> {
        val dates = generateSequence(LocalDate.of(year, 1, 1)) { current ->
            current.plusDays(1).takeIf { it.year == year }
        }.toList()

        val statusMap = linkedMapOf<LocalDate, Boolean>()
        dates.chunked(50).forEach { batch ->
            val query = buildString {
                append(baseUrl.trimEnd('/'))
                append("?type=Y")
                batch.forEach { date ->
                    append("&d=")
                    append(date)
                }
            }
            val connection = (URL(query).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
            }
            val body = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            connection.disconnect()

            val root = JSONObject(body)
            if (root.optInt("code", -1) != 0) {
                error("在线日历返回异常")
            }

            val typeObject = root.optJSONObject("type") ?: error("在线日历缺少 type 字段")
            batch.forEach { date ->
                val type = typeObject.optJSONObject(date.toString())?.optInt("type", -1) ?: -1
                statusMap[date] = type == 0 || type == 3
            }
        }
        return statusMap
    }

    companion object {
        private const val PREF_NAME = "holiday_calendar_store"
        private const val KEY_CONFIG = "holiday_calendar_config"
        private const val KEY_SYNCED_SNAPSHOT = "holiday_calendar_snapshot"
        private const val KEY_OVERRIDES = "holiday_calendar_overrides"
        @RawRes
        private val BUNDLED_RESOURCE = R.raw.china_workday_bundle
    }
}

private fun HolidayCalendarConfig.toJson(): JSONObject = JSONObject().apply {
    put("useChinaWorkdayCalendar", useChinaWorkdayCalendar)
    put("remoteUrl", remoteUrl)
    put("lastSyncAtMillis", lastSyncAtMillis)
    put("lastSyncMessage", lastSyncMessage)
}

private fun JSONObject.toConfig(): HolidayCalendarConfig = HolidayCalendarConfig(
    useChinaWorkdayCalendar = optBoolean("useChinaWorkdayCalendar", false),
    remoteUrl = optString("remoteUrl").ifBlank { DEFAULT_REMOTE_URL },
    lastSyncAtMillis = optLong("lastSyncAtMillis", 0L),
    lastSyncMessage = optString("lastSyncMessage"),
)

private fun ManualDayOverride.toJson(): JSONObject = JSONObject().apply {
    put("date", date.toString())
    put("isWorkday", isWorkday)
    put("note", note)
}

private fun JSONObject.toOverride(): ManualDayOverride = ManualDayOverride(
    date = LocalDate.parse(getString("date")),
    isWorkday = optBoolean("isWorkday", false),
    note = optString("note"),
)

private fun HolidayCalendarSnapshot.toJson(): JSONObject = JSONObject().apply {
    put("version", version)
    put("source", source)
    put("updatedAtMillis", updatedAtMillis)
    put("workdays", JSONArray(workdays.sorted().map { it.toString() }))
    put("offdays", JSONArray(offdays.sorted().map { it.toString() }))
}

private fun JSONObject.toSnapshot(): HolidayCalendarSnapshot = HolidayCalendarSnapshot(
    version = optString("version").ifBlank { "builtin-weekday-fallback" },
    source = optString("source").ifBlank { "builtin" },
    workdays = optJSONArray("workdays").toLocalDateSet(),
    offdays = optJSONArray("offdays").toLocalDateSet(),
    updatedAtMillis = optLong("updatedAtMillis", 0L),
)

private fun JSONArray?.toLocalDateSet(): Set<LocalDate> = buildSet {
    if (this@toLocalDateSet == null) return@buildSet
    for (index in 0 until this@toLocalDateSet.length()) {
        add(LocalDate.parse(this@toLocalDateSet.getString(index), DateTimeFormatter.ISO_LOCAL_DATE))
    }
}

fun HolidayCalendarConfig.lastSyncLabel(): String {
    if (lastSyncAtMillis <= 0L) return "尚未同步"
    val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(java.util.Date(lastSyncAtMillis))
    return "上次同步 $date"
}
