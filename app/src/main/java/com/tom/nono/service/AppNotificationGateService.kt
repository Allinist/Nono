package com.tom.nono.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.tom.nono.data.RuleStore
import java.time.LocalDateTime
import java.util.Locale

class AppNotificationGateService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val store = RuleStore(this)
        val rules = store.loadRules()
        val notificationText = buildNotificationBlob(sbn)

        val shouldBlock = rules.any { rule ->
            rule.enabled &&
                rule.packageName.equals(sbn.packageName, ignoreCase = true) &&
                rule.normalizedTargets().isNotEmpty() &&
                notificationText.matchesTargets(rule.normalizedTargets()) &&
                !rule.isInWorkingWindow(LocalDateTime.now().dayOfWeek, LocalDateTime.now().toLocalTime())
        }

        if (shouldBlock) {
            cancelNotification(sbn.key)
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
}

private fun String.matchesTargets(targets: List<String>): Boolean {
    if (targets.any { it == "*" }) return true
    val source = lowercase(Locale.ROOT)
    return targets.any { target -> source.contains(target.lowercase(Locale.ROOT)) }
}
