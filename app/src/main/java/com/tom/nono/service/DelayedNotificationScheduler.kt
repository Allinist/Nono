package com.tom.nono.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tom.nono.data.DelayedNotice
import com.tom.nono.data.DelayedNoticeStore
import com.tom.nono.data.HolidayCalendarStore
import com.tom.nono.data.RuleDayMode
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import android.service.notification.StatusBarNotification
import kotlin.math.absoluteValue

object DelayedNotificationScheduler {
    fun schedule(
        context: Context,
        sbn: StatusBarNotification,
        ruleId: String,
        appName: String,
        packageName: String,
        title: String,
        text: String,
        remindAtMinutes: Int,
        activeDays: Set<DayOfWeek>,
        dayMode: RuleDayMode,
        manualEnabled: Boolean,
    ) {
        val requestCode = (sbn.key.hashCode() + sbn.postTime.hashCode()).absoluteValue
        val scheduledAtMillis = nextTriggerAtMillis(context, remindAtMinutes, activeDays, dayMode, manualEnabled)
        val intent = Intent(context, DelayedNotificationReceiver::class.java).apply {
            putExtra(DelayedNotificationReceiver.EXTRA_TITLE, title)
            putExtra(DelayedNotificationReceiver.EXTRA_TEXT, text)
            putExtra(DelayedNotificationReceiver.EXTRA_APP_NAME, appName)
            putExtra(DelayedNotificationReceiver.EXTRA_REQUEST_CODE, requestCode)
            putExtra(DelayedNotificationReceiver.EXTRA_NOTICE_ID, requestCode.toString())
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        runCatching {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduledAtMillis,
                    pendingIntent,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduledAtMillis,
                    pendingIntent,
                )
            }
        }.getOrElse {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                scheduledAtMillis,
                pendingIntent,
            )
        }

        DelayedNoticeStore(context).addNotice(
            DelayedNotice(
                id = requestCode.toString(),
                ruleId = ruleId,
                appName = appName,
                packageName = packageName,
                title = title,
                text = text,
                scheduledAtMillis = scheduledAtMillis,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun nextTriggerAtMillis(
        context: Context,
        remindAtMinutes: Int,
        activeDays: Set<DayOfWeek>,
        dayMode: RuleDayMode,
        manualEnabled: Boolean,
    ): Long {
        val now = LocalDateTime.now()
        val reminderTime = LocalTime.of(remindAtMinutes / 60, remindAtMinutes % 60)
        val calendarStore = HolidayCalendarStore(context)
        for (dayOffset in 0..30) {
            val candidate = now.toLocalDate().plusDays(dayOffset.toLong()).atTime(reminderTime)
            if (candidate.dayOfWeek !in activeDays) continue
            val isWorkingDate = calendarStore.isWorkingDate(candidate.toLocalDate())
            val dateMatched = when (dayMode) {
                RuleDayMode.WORKDAY -> isWorkingDate
                RuleDayMode.RESTDAY -> !isWorkingDate
                RuleDayMode.ALL -> true
                RuleDayMode.MANUAL -> manualEnabled
            }
            if (!dateMatched) continue
            if (candidate.isAfter(now)) {
                return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        return now.plusDays(1).withHour(reminderTime.hour).withMinute(reminderTime.minute)
            .withSecond(0).withNano(0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
