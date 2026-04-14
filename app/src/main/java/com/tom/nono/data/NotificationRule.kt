package com.tom.nono.data

import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

data class NotificationRule(
    val id: String = UUID.randomUUID().toString(),
    val appName: String,
    val packageName: String,
    val enabled: Boolean = true,
    val startMinutes: Int = 9 * 60,
    val endMinutes: Int = 18 * 60,
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

    fun isInWorkingWindow(nowDay: DayOfWeek, nowTime: LocalTime): Boolean {
        if (nowDay !in activeDays) return false
        val nowMinutes = nowTime.hour * 60 + nowTime.minute
        return if (startMinutes <= endMinutes) {
            nowMinutes in startMinutes until endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    companion object {
        fun defaultRules(): List<NotificationRule> = listOf(
            NotificationRule(
                appName = "微信",
                packageName = "com.tencent.mm",
                targets = listOf("老板", "项目群"),
            ),
            NotificationRule(
                appName = "应用",
                packageName = "",
                targets = listOf("*"),
            ),
        )
    }
}

private val DayOfWeek.displayName: String
    get() = when (this) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }
