package com.tom.nono.data

import android.content.Context
import androidx.annotation.RawRes
import com.tom.nono.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DEFAULT_REMOTE_URL = "https://timor.tech/api/holiday/year"

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
    val debugInfo: String = "",
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
        val remoteUrl = normalizeRemoteUrl(config.remoteUrl.trim())
        if (remoteUrl.isBlank()) {
            return HolidaySyncResult(
                success = false,
                message = "请先填写在线日历地址",
                debugInfo = "remoteUrl=<blank>",
            )
        }

        return runCatching {
            val years = listOf(referenceYear, referenceYear + 1)
            val statuses = linkedMapOf<LocalDate, Boolean>()
            val syncedYears = mutableListOf<Int>()
            val failedYears = mutableListOf<Int>()
            val debugLines = mutableListOf("remoteUrl=$remoteUrl")

            years.forEach { year ->
                runCatching { fetchYearStatuses(remoteUrl, year) }
                    .onSuccess { yearStatuses ->
                        debugLines += "year=$year count=${yearStatuses.size}"
                        if (yearStatuses.isNotEmpty()) {
                            syncedYears += year
                            yearStatuses.forEach { (date, isWorkday) ->
                                statuses[date] = isWorkday
                            }
                        } else {
                            failedYears += year
                        }
                    }
                    .onFailure { error ->
                        debugLines += "year=$year error=${error::class.simpleName}:${error.message.orEmpty()}"
                        failedYears += year
                    }
            }

            if (statuses.isEmpty()) {
                val message = "同步失败：没有拿到可用的工作日数据"
                saveConfig(config.copy(remoteUrl = remoteUrl, lastSyncMessage = message))
                return@runCatching HolidaySyncResult(
                    success = false,
                    message = message,
                    debugInfo = debugLines.joinToString("\n"),
                )
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

            val syncMessage = if (failedYears.isEmpty()) {
                "已同步 ${syncedYears.joinToString()} 中国工作日历"
            } else {
                "已同步 ${syncedYears.joinToString()}，${failedYears.joinToString()} 获取失败"
            }

            saveConfig(
                config.copy(
                    remoteUrl = remoteUrl,
                    lastSyncAtMillis = now,
                    lastSyncMessage = syncMessage,
                ),
            )

            HolidaySyncResult(
                success = true,
                message = syncMessage,
                syncedAtMillis = now,
                debugInfo = debugLines.joinToString("\n"),
            )
        }.getOrElse { error ->
            val message = error.message?.takeIf { it.isNotBlank() } ?: "同步失败"
            saveConfig(config.copy(remoteUrl = remoteUrl, lastSyncMessage = message))
            HolidaySyncResult(
                success = false,
                message = message,
                debugInfo = "remoteUrl=$remoteUrl\nfatal=${error::class.simpleName}:${error.message.orEmpty()}",
            )
        }
    }

    private fun normalizeRemoteUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return DEFAULT_REMOTE_URL
        return rawUrl
            .replace("\\s+".toRegex(), "")
            .replace("/batch", "/year")
            .removeSuffix("/")
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
        val candidateUrls = buildList {
            val normalizedBase = baseUrl.trimEnd('/')
            add("$normalizedBase/$year/?type=Y&week=Y")
            add("$normalizedBase/$year?type=Y&week=Y")
            if (normalizedBase.startsWith("https://timor.tech")) {
                val httpBase = normalizedBase.replaceFirst("https://", "http://")
                add("$httpBase/$year/?type=Y&week=Y")
                add("$httpBase/$year?type=Y&week=Y")
            }
        }

        val errors = mutableListOf<String>()
        candidateUrls.forEach { query ->
            runCatching {
                val connection = (URL(query).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "Nono/1.0 (Android)")
                }
                connection.useJsonBody(query)
            }.onSuccess { body ->
                val statusMap = parseYearStatusResponse(body, year)
                if (statusMap.isNotEmpty()) return statusMap
                errors += "url=$query empty-data"
            }.onFailure { error ->
                errors += "url=$query ${error::class.simpleName}:${error.message.orEmpty()}"
            }
        }

        error(errors.joinToString(" | "))
    }

    private fun parseYearStatusResponse(body: String, year: Int): Map<LocalDate, Boolean> {
        val root = JSONObject(body)
        if (root.optInt("code", -1) != 0) {
            error("在线日历返回异常: ${root.optString("msg").ifBlank { root.optString("message") }}")
        }

        val typeObject = root.optJSONObject("type")
            ?: root.optJSONObject("holiday")
            ?: root.optJSONObject("data")
            ?: return emptyMap()

        if (typeObject.length() == 0) return emptyMap()

        val statusMap = linkedMapOf<LocalDate, Boolean>()
        val keys = typeObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val normalizedDate = if (key.length == 5) "$year-$key" else key
            val rawValue = typeObject.opt(key)
            val type = when (rawValue) {
                is JSONObject -> rawValue.optInt("type", -1)
                is Number -> rawValue.toInt()
                else -> -1
            }
            if (type >= 0) {
                statusMap[LocalDate.parse(normalizedDate)] = type == 0 || type == 3
            }
        }
        return statusMap
    }

    private fun HttpURLConnection.useJsonBody(query: String): String {
        return try {
            val statusCode = responseCode
            val stream = if (statusCode in 200..299) inputStream else errorStream
            val body = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (statusCode !in 200..299) {
                error("HTTP $statusCode ${responseMessage.orEmpty()} body=${body.take(200)} url=$query")
            }
            body
        } catch (error: FileNotFoundException) {
            val statusCode = runCatching { responseCode }.getOrDefault(-1)
            val body = errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            error("HTTP $statusCode body=${body.take(200)} url=$query cause=${error.message.orEmpty()}")
        } finally {
            disconnect()
        }
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
