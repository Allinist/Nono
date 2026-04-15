package com.tom.nono.service

import android.app.NotificationManager
import android.media.AudioManager
import android.app.Notification
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.tom.nono.data.DeviceSoundMode
import com.tom.nono.data.HolidayCalendarStore
import com.tom.nono.data.RuleMode
import com.tom.nono.data.RuleStore
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.absoluteValue

class AppNotificationGateService : NotificationListenerService() {
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "onListenerConnected")
        recordListenerDiagnostic("listener_connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "onListenerDisconnected")
        recordListenerDiagnostic("listener_disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        Log.d(TAG, "onNotificationPosted pkg=${sbn.packageName} key=${sbn.key}")
        runCatching { handleNotificationPosted(sbn) }
            .onFailure { error ->
                Log.e(TAG, "handleNotificationPosted failed", error)
                recordListenerDiagnostic("error:${error::class.simpleName}:${error.message.orEmpty()}", sbn.packageName)
            }
    }

    private fun handleNotificationPosted(sbn: StatusBarNotification) {
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

        val selectedRule = matchedRules.firstOrNull()

        if (selectedRule == null) {
            Log.d(TAG, "no matched rule for pkg=${sbn.packageName}")
            val stagePkg = rules.count { it.enabled && it.packageName.equals(sbn.packageName, ignoreCase = true) }
            val stageDayMode = rules.count {
                it.enabled &&
                    it.packageName.equals(sbn.packageName, ignoreCase = true) &&
                    it.matchesDayContext(isWorkingDate)
            }
            val stageWeekday = rules.count {
                it.enabled &&
                    it.packageName.equals(sbn.packageName, ignoreCase = true) &&
                    it.matchesDayContext(isWorkingDate) &&
                    now.dayOfWeek in it.activeDays
            }
            val stageTarget = rules.count {
                it.enabled &&
                    it.packageName.equals(sbn.packageName, ignoreCase = true) &&
                    it.matchesDayContext(isWorkingDate) &&
                    now.dayOfWeek in it.activeDays &&
                    notificationText.matchesTargets(it.normalizedTargets())
            }
            val diag = "no_match total=${rules.size} pkg=$stagePkg dayMode=$stageDayMode weekday=$stageWeekday target=$stageTarget"
            recordListenerDiagnostic(diag, sbn.packageName)
            return
        }

        val aggressiveCancel = selectedRule.normalizedTargets().isEmpty() || selectedRule.normalizedTargets().contains("*")
        if (selectedRule.mode != RuleMode.ALLOW) {
            cancelByBestEffort(sbn, aggressiveCancel = aggressiveCancel)
            // OEM bridge/wearable sync can race with first cancel, retry shortly.
            mainHandler.postDelayed({ cancelByBestEffort(sbn, aggressiveCancel = aggressiveCancel) }, 80L)
            mainHandler.postDelayed({ cancelByBestEffort(sbn, aggressiveCancel = aggressiveCancel) }, 260L)
        }

        Log.i(TAG, "matched rule=${selectedRule.id} mode=${selectedRule.mode} pkg=${sbn.packageName}")
        recordListenerDiagnostic("matched mode=${selectedRule.mode} rule=${selectedRule.id}", sbn.packageName)

        applySoundMode(selectedRule.soundMode)

        when (selectedRule.mode) {
            RuleMode.ALLOW -> {
                emitObservedNotification(sbn, selectedRule.appName)
            }
            RuleMode.BLOCK -> Unit
            RuleMode.DELAY -> {
                DelayedNotificationScheduler.schedule(
                    context = this,
                    sbn = sbn,
                    ruleId = selectedRule.id,
                    appName = selectedRule.appName,
                    packageName = selectedRule.packageName,
                    title = extractTitle(sbn),
                    text = extractDisplayText(sbn),
                    remindAtMinutes = selectedRule.remindAtMinutes,
                    activeDays = selectedRule.activeDays,
                    dayMode = selectedRule.dayMode,
                    manualEnabled = selectedRule.manualEnabled,
                )
            }
            RuleMode.COLLECT_ONLY -> {
                DelayedNotificationScheduler.collectOnly(
                    context = this,
                    sbn = sbn,
                    ruleId = selectedRule.id,
                    appName = selectedRule.appName,
                    packageName = selectedRule.packageName,
                    title = extractTitle(sbn),
                    text = extractDisplayText(sbn),
                )
            }
        }
    }

    private fun emitObservedNotification(sbn: StatusBarNotification, appName: String) {
        val requestCode = (sbn.key.hashCode() + sbn.postTime.hashCode()).absoluteValue
        DelayedNotificationReceiver.notifyReminder(
            context = this,
            title = extractTitle(sbn),
            text = extractDisplayText(sbn),
            appName = appName.ifBlank { resolveAppLabel(sbn.packageName) },
            packageName = sbn.packageName,
            originalContentIntent = sbn.notification.contentIntent,
            requestCode = requestCode,
        )
    }

    private fun cancelByBestEffort(sbn: StatusBarNotification, aggressiveCancel: Boolean) {
        runCatching { cancelNotification(sbn.key) }
        @Suppress("DEPRECATION")
        runCatching { cancelNotification(sbn.packageName, sbn.tag, sbn.id) }
        if (aggressiveCancel) {
            runCatching {
                activeNotifications
                    .filter { it.packageName == sbn.packageName }
                    .forEach { active -> cancelNotification(active.key) }
            }
        }
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
        val pieces = mutableListOf<String>()

        fun add(piece: CharSequence?) {
            val text = piece?.toString()?.trim().orEmpty()
            if (text.isNotBlank()) pieces.add(text)
        }

        add(extras.safeGetCharSequence(Notification.EXTRA_TITLE))
        add(extras.safeGetCharSequence(Notification.EXTRA_CONVERSATION_TITLE))
        add(extras.safeGetCharSequence(Notification.EXTRA_TITLE_BIG))
        add(extras.safeGetCharSequence(Notification.EXTRA_TEXT))
        add(extras.safeGetCharSequence(Notification.EXTRA_BIG_TEXT))
        add(extras.safeGetCharSequence(Notification.EXTRA_SUB_TEXT))
        add(extras.safeGetCharSequence(Notification.EXTRA_SUMMARY_TEXT))
        add(extras.safeGetCharSequence(Notification.EXTRA_INFO_TEXT))
        add(sbn.notification.tickerText)
        extras.safeGetCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add(it) }
        extras.safeGetParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { item ->
            val bundle = item as? Bundle ?: return@forEach
            add(bundle.getCharSequence("sender"))
            add(bundle.getCharSequence("text"))
        }
        add(resolveAppLabel(sbn.packageName))
        add(sbn.packageName)

        return pieces.distinct().joinToString(separator = "\n")
    }

    private fun extractTitle(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        val candidates = listOf(
            extras.safeGetCharSequence(Notification.EXTRA_TITLE)?.toString(),
            extras.safeGetCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            extras.safeGetCharSequence(Notification.EXTRA_TITLE_BIG)?.toString(),
            resolveAppLabel(sbn.packageName),
        )
        return candidates.firstOrNull { !it.isNullOrBlank() }.orEmpty()
    }

    private fun extractDisplayText(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        val candidates = buildList {
            add(extras.safeGetCharSequence(Notification.EXTRA_TEXT)?.toString())
            add(extras.safeGetCharSequence(Notification.EXTRA_BIG_TEXT)?.toString())
            add(extras.safeGetCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString())
            add(extras.safeGetCharSequence(Notification.EXTRA_SUB_TEXT)?.toString())
            extras.safeGetCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add(it.toString()) }
            extras.safeGetParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { item ->
                val bundle = item as? Bundle ?: return@forEach
                val sender = bundle.getCharSequence("sender")?.toString().orEmpty().trim()
                val text = bundle.getCharSequence("text")?.toString().orEmpty().trim()
                if (text.isNotBlank()) {
                    add(if (sender.isNotBlank()) "$sender: $text" else text)
                }
            }
        }
            .mapNotNull { it?.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return candidates.joinToString(" | ").ifBlank { buildNotificationBlob(sbn) }
    }

    private fun resolveAppLabel(packageName: String): String =
        runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)

    private fun recordListenerDiagnostic(event: String, packageName: String = "") {
        getSharedPreferences(DIAG_PREF, MODE_PRIVATE)
            .edit()
            .putLong(DIAG_LAST_TS, System.currentTimeMillis())
            .putString(DIAG_LAST_EVENT, event)
            .putString(DIAG_LAST_PACKAGE, packageName)
            .putString(DIAG_PROCESS, android.os.Process.myPid().toString())
            .commit()
    }

    companion object {
        private const val TAG = "NonoListener"
        const val DIAG_PREF = "nono_listener_diag"
        const val DIAG_LAST_TS = "last_ts"
        const val DIAG_LAST_EVENT = "last_event"
        const val DIAG_LAST_PACKAGE = "last_package"
        const val DIAG_PROCESS = "pid"
    }
}

private fun String.matchesTargets(targets: List<String>): Boolean {
    if (targets.isEmpty()) return true
    if (targets.any { it == "*" }) return true
    val source = lowercase(Locale.ROOT)
    return targets.any { target -> source.contains(target.lowercase(Locale.ROOT)) }
}

private fun Bundle.safeGetCharSequence(key: String): CharSequence? =
    runCatching { getCharSequence(key) }.getOrNull()

private fun Bundle.safeGetCharSequenceArray(key: String): Array<CharSequence>? =
    runCatching { getCharSequenceArray(key) }.getOrNull()

private fun Bundle.safeGetParcelableArray(key: String): Array<android.os.Parcelable>? =
    runCatching { getParcelableArray(key) }.getOrNull()
