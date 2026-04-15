package com.tom.nono.data

import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

enum class RuleMode {
    BLOCK,
    ALLOW,
    DELAY,
}

enum class DeviceSoundMode {
    KEEP,
    RING,
    VIBRATE,
    SILENT,
}

enum class RuleDayMode {
    WORKDAY,
    RESTDAY,
    ALL,
    MANUAL,
}

data class NotificationRule(
    val id: String = UUID.randomUUID().toString(),
    val appName: String,
    val packageName: String,
    val note: String = "",
    val enabled: Boolean = true,
    val mode: RuleMode = RuleMode.BLOCK,
    val remindAtMinutes: Int = 9 * 60,
    val soundMode: DeviceSoundMode = DeviceSoundMode.KEEP,
    val startMinutes: Int = 9 * 60,
    val endMinutes: Int = 18 * 60,
    val dayMode: RuleDayMode = RuleDayMode.WORKDAY,
    val manualEnabled: Boolean = false,
    val activeDays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    ),
    val targets: List<String> = emptyList(),
) {
    fun normalizedTargets(): List<String> = targets.map { it.trim() }.filter { it.isNotEmpty() }

    fun timeRangeLabel(): String = "%02d:%02d - %02d:%02d".format(
        startMinutes / 60,
        startMinutes % 60,
        endMinutes / 60,
        endMinutes % 60,
    )

    fun dayLabel(): String = activeDays
        .sortedBy { it.value }
        .joinToString(" ") { it.displayName }

    fun dayModeLabel(): String = when (dayMode) {
        RuleDayMode.WORKDAY -> "\u5de5\u4f5c"
        RuleDayMode.RESTDAY -> "\u4f11\u606f"
        RuleDayMode.ALL -> "\u5168\u90e8"
        RuleDayMode.MANUAL -> "\u5168\u90e8"
    }

    fun matchesDayContext(isWorkingDate: Boolean): Boolean = when (dayMode) {
        RuleDayMode.WORKDAY -> isWorkingDate
        RuleDayMode.RESTDAY -> !isWorkingDate
        RuleDayMode.ALL -> true
        RuleDayMode.MANUAL -> true
    }

    fun isInWorkingWindow(nowDay: DayOfWeek, nowTime: LocalTime): Boolean {
        val nowMinutes = nowTime.hour * 60 + nowTime.minute
        return if (startMinutes <= endMinutes) {
            nowMinutes in startMinutes until endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    companion object {
        fun defaultRules(): List<NotificationRule> = emptyList()
    }
}

private val DayOfWeek.displayName: String
    get() = when (this) {
        DayOfWeek.MONDAY -> "\u5468\u4e00"
        DayOfWeek.TUESDAY -> "\u5468\u4e8c"
        DayOfWeek.WEDNESDAY -> "\u5468\u4e09"
        DayOfWeek.THURSDAY -> "\u5468\u56db"
        DayOfWeek.FRIDAY -> "\u5468\u4e94"
        DayOfWeek.SATURDAY -> "\u5468\u516d"
        DayOfWeek.SUNDAY -> "\u5468\u65e5"
    }
