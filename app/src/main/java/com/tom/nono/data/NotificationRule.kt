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

    fun isInWorkingWindow(nowDay: DayOfWeek, nowTime: LocalTime, isWorkingDate: Boolean = nowDay in activeDays): Boolean {
        if (!isWorkingDate) return false
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
                appName = "\u5fae\u4fe1",
                packageName = "com.tencent.mm",
                note = "\u793a\u4f8b\u8054\u7cfb\u4eba\u767d\u540d\u5355",
                mode = RuleMode.BLOCK,
                remindAtMinutes = 9 * 60,
                soundMode = DeviceSoundMode.KEEP,
                targets = listOf("\u8001\u677f", "\u9879\u76ee\u7fa4"),
            ),
            NotificationRule(
                appName = "\u5929\u67a2",
                packageName = "",
                note = "\u975e\u5de5\u4f5c\u65f6\u95f4\u6574\u5305\u5c4f\u853d",
                mode = RuleMode.BLOCK,
                remindAtMinutes = 9 * 60,
                soundMode = DeviceSoundMode.VIBRATE,
                targets = listOf("*"),
            ),
        )
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
