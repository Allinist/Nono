package com.tom.nono.data

import android.content.Context

enum class ResendTriggerPriority {
    NORMAL_ONLY,
    NORMAL_FIRST,
    FIDELITY_FIRST,
    REUSE_INTENT,
    PROXY_REPLAY,
    BUBBLE_FIRST,
    FULL_SCREEN_FIRST,
    ALARM_ONLY,
    ALARM_FIRST,
}

data class RuntimeTuningSettings(
    val rebindIntervalMs: Long = RuntimeTuningSettingsStore.DEFAULT_REBIND_INTERVAL_MS,
    val activeCheckIntervalMs: Long = RuntimeTuningSettingsStore.DEFAULT_ACTIVE_CHECK_INTERVAL_MS,
    val idleCheckIntervalMs: Long = RuntimeTuningSettingsStore.DEFAULT_IDLE_CHECK_INTERVAL_MS,
    val resendTriggerPriority: ResendTriggerPriority = ResendTriggerPriority.NORMAL_FIRST,
)

object RuntimeTuningSettingsStore {
    private const val PREF_NAME = "runtime_tuning_settings"
    private const val KEY_REBIND_INTERVAL_MS = "rebind_interval_ms"
    private const val KEY_ACTIVE_CHECK_INTERVAL_MS = "active_check_interval_ms"
    private const val KEY_IDLE_CHECK_INTERVAL_MS = "idle_check_interval_ms"
    private const val KEY_RESEND_TRIGGER_PRIORITY = "resend_trigger_priority"

    const val DEFAULT_REBIND_INTERVAL_MS = 90_000L
    const val DEFAULT_ACTIVE_CHECK_INTERVAL_MS = 60_000L
    const val DEFAULT_IDLE_CHECK_INTERVAL_MS = 180_000L

    private const val MIN_INTERVAL_SEC = 10
    private const val MAX_INTERVAL_SEC = 600

    fun load(context: Context): RuntimeTuningSettings {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val priority = prefs.getString(KEY_RESEND_TRIGGER_PRIORITY, ResendTriggerPriority.NORMAL_FIRST.name)
            ?.let { runCatching { ResendTriggerPriority.valueOf(it) }.getOrNull() }
            ?: ResendTriggerPriority.NORMAL_FIRST
        return RuntimeTuningSettings(
            rebindIntervalMs = prefs.getLong(KEY_REBIND_INTERVAL_MS, DEFAULT_REBIND_INTERVAL_MS),
            activeCheckIntervalMs = prefs.getLong(KEY_ACTIVE_CHECK_INTERVAL_MS, DEFAULT_ACTIVE_CHECK_INTERVAL_MS),
            idleCheckIntervalMs = prefs.getLong(KEY_IDLE_CHECK_INTERVAL_MS, DEFAULT_IDLE_CHECK_INTERVAL_MS),
            resendTriggerPriority = priority,
        )
    }

    fun saveIntervalsSeconds(
        context: Context,
        rebindIntervalSec: Int,
        activeCheckIntervalSec: Int,
        idleCheckIntervalSec: Int,
    ) {
        val rebind = rebindIntervalSec.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC).toLong() * 1000L
        val active = activeCheckIntervalSec.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC).toLong() * 1000L
        val idle = idleCheckIntervalSec.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC).toLong() * 1000L
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_REBIND_INTERVAL_MS, rebind)
            .putLong(KEY_ACTIVE_CHECK_INTERVAL_MS, active)
            .putLong(KEY_IDLE_CHECK_INTERVAL_MS, idle)
            .apply()
    }

    fun saveResendTriggerPriority(context: Context, priority: ResendTriggerPriority) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RESEND_TRIGGER_PRIORITY, priority.name)
            .apply()
    }
}
