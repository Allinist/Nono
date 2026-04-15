package com.tom.nono.service

import android.app.NotificationManager
import android.media.AudioManager
import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.tom.nono.data.DeviceSoundMode
import com.tom.nono.data.HolidayCalendarStore
import com.tom.nono.data.RuleMode
import com.tom.nono.data.RuleStore
import java.time.LocalDateTime
import java.util.Locale

class AppNotificationGateService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val store = RuleStore(this)
        val holidayCalendarStore = HolidayCalendarStore(this)
        val rules = store.loadRules()
        val notificationText = buildNotificationBlob(sbn)
        val now = LocalDateTime.now()
        val isWorkingDate = holidayCalendarStore.isWorkingDate(now.toLocalDate())

        val matchedRules = rules.filter { rule ->
            val normalizedTargets = rule.normalizedTargets()
            rule.enabled &&
                rule.packageName.equals(sbn.packageName, ignoreCase = true) &&
                rule.matchesDayContext(isWorkingDate) &&
                now.dayOfWeek in rule.activeDays &&
                notificationText.matchesTargets(normalizedTargets) &&
                rule.isInWorkingWindow(
                    nowDay = now.dayOfWeek,
                    nowTime = now.toLocalTime(),
                )
        }

        val selectedRule = when {
            matchedRules.any { it.mode == RuleMode.ALLOW } -> matchedRules.first { it.mode == RuleMode.ALLOW }
            matchedRules.any { it.mode == RuleMode.DELAY } -> matchedRules.first { it.mode == RuleMode.DELAY }
            matchedRules.any { it.mode == RuleMode.BLOCK } -> matchedRules.first { it.mode == RuleMode.BLOCK }
            else -> null
        } ?: return

        applySoundMode(selectedRule.soundMode)

        when (selectedRule.mode) {
            RuleMode.ALLOW -> Unit
            RuleMode.BLOCK -> cancelByBestEffort(sbn)
            RuleMode.DELAY -> {
                cancelByBestEffort(sbn)
                DelayedNotificationScheduler.schedule(
                    context = this,
                    sbn = sbn,
                    ruleId = selectedRule.id,
                    appName = selectedRule.appName,
                    packageName = selectedRule.packageName,
                    title = extractTitle(sbn),
                    text = buildNotificationBlob(sbn),
                    remindAtMinutes = selectedRule.remindAtMinutes,
                    activeDays = selectedRule.activeDays,
                    dayMode = selectedRule.dayMode,
                    manualEnabled = selectedRule.manualEnabled,
                )
            }
        }
    }

    private fun cancelByBestEffort(sbn: StatusBarNotification) {
        runCatching { cancelNotification(sbn.key) }
        @Suppress("DEPRECATION")
        runCatching { cancelNotification(sbn.packageName, sbn.tag, sbn.id) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                if (sbn.notification.channelId != null) {
                    activeNotifications
                        .filter {
                            it.packageName == sbn.packageName &&
                                it.notification.channelId == sbn.notification.channelId
                        }
                        .forEach { active -> cancelNotification(active.key) }
                }
            }
        }
    }

    private fun applySoundMode(soundMode: DeviceSoundMode) {
        if (soundMode == DeviceSoundMode.KEEP) return

        val audioManager = getSystemService(AudioManager::class.java)
        if (audioManager.isVolumeFixed) return

        if (soundMode == DeviceSoundMode.SILENT || soundMode == DeviceSoundMode.VIBRATE) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                return
            }
        }

        audioManager.ringerMode = when (soundMode) {
            DeviceSoundMode.KEEP -> audioManager.ringerMode
            DeviceSoundMode.RING -> AudioManager.RINGER_MODE_NORMAL
            DeviceSoundMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
            DeviceSoundMode.SILENT -> AudioManager.RINGER_MODE_SILENT
        }
    }

    private fun buildNotificationBlob(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        val pieces = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            sbn.packageName,
        )

        return pieces.joinToString(separator = "\n")
    }

    private fun extractTitle(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        return extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    }
}

private fun String.matchesTargets(targets: List<String>): Boolean {
    if (targets.isEmpty()) return true
    if (targets.any { it == "*" }) return true
    val source = lowercase(Locale.ROOT)
    return targets.any { target -> source.contains(target.lowercase(Locale.ROOT)) }
}
