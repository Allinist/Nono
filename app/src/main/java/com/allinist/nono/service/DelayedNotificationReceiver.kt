package com.allinist.nono.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.allinist.nono.R
import com.allinist.nono.data.DelayedNoticeStore

class DelayedNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REPLAY_ORIGINAL_INTENT) {
            replayOriginalPendingIntent(context, intent)
            return
        }
        if (intent.action == ACTION_REPLAY_ACTION_INTENT) {
            replayActionPendingIntent(context, intent)
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty()
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val originalContentIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_ORIGINAL_CONTENT_INTENT, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_ORIGINAL_CONTENT_INTENT)
        }
        val originalActionIntents: List<PendingIntent> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_ORIGINAL_ACTION_INTENTS, PendingIntent::class.java)
                ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            (intent.getParcelableArrayListExtra<PendingIntent>(EXTRA_ORIGINAL_ACTION_INTENTS)
                ?: emptyList())
        }
        val originalActionTitles = intent.getStringArrayListExtra(EXTRA_ORIGINAL_ACTION_TITLES).orEmpty()
        val originalDeleteIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_ORIGINAL_DELETE_INTENT, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_ORIGINAL_DELETE_INTENT)
        }
        val originalBubbleIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_ORIGINAL_BUBBLE_INTENT, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_ORIGINAL_BUBBLE_INTENT)
        }
        val originalFullScreenIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_ORIGINAL_FULL_SCREEN_INTENT, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_ORIGINAL_FULL_SCREEN_INTENT)
        }
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
        val noticeId = intent.getStringExtra(EXTRA_NOTICE_ID).orEmpty()
        val notified = notifyReminder(
            context = context,
            title = title,
            text = text,
            appName = appName,
            packageName = packageName,
            originalContentIntent = originalContentIntent,
            originalActionIntents = originalActionIntents,
            originalActionTitles = originalActionTitles,
            originalDeleteIntent = originalDeleteIntent,
            originalBubbleIntent = originalBubbleIntent,
            originalFullScreenIntent = originalFullScreenIntent,
            requestCode = requestCode,
        )
        if (notified && noticeId.isNotBlank()) {
            DelayedNoticeStore(context).removeNotice(noticeId)
        }
    }

    companion object {
        const val CHANNEL_ID = "nono_delayed_notifications"
        const val ACTION_REPLAY_ORIGINAL_INTENT = "com.allinist.nono.action.REPLAY_ORIGINAL_INTENT"
        const val ACTION_REPLAY_ACTION_INTENT = "com.allinist.nono.action.REPLAY_ACTION_INTENT"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_ORIGINAL_CONTENT_INTENT = "original_content_intent"
        const val EXTRA_ORIGINAL_ACTION_INTENTS = "original_action_intents"
        const val EXTRA_ORIGINAL_ACTION_TITLES = "original_action_titles"
        const val EXTRA_ORIGINAL_DELETE_INTENT = "original_delete_intent"
        const val EXTRA_ORIGINAL_BUBBLE_INTENT = "original_bubble_intent"
        const val EXTRA_ORIGINAL_FULL_SCREEN_INTENT = "original_full_screen_intent"
        const val EXTRA_REQUEST_CODE = "request_code"
        const val EXTRA_NOTICE_ID = "notice_id"

        fun notifyReminder(
            context: Context,
            title: String,
            text: String,
            appName: String,
            packageName: String,
            originalContentIntent: PendingIntent?,
            originalActionIntents: List<PendingIntent>,
            originalActionTitles: List<String>,
            originalDeleteIntent: PendingIntent?,
            originalBubbleIntent: PendingIntent?,
            originalFullScreenIntent: PendingIntent?,
            requestCode: Int,
        ): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            val resendMode = com.allinist.nono.data.RuntimeTuningSettingsStore.load(context).resendTriggerPriority
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "延后通知",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Nono 延后提醒的通知通道"
                }
                manager.createNotificationChannel(channel)
            }

            val contentText = buildDisplayText(text)
            val displayTitle = buildDisplayTitle(title = title, appName = appName)
            val fallbackLaunchIntent = buildFallbackLaunchIntent(context, packageName)
            val directIntent = preferredOriginalIntent(
                resendMode = resendMode,
                originalContentIntent = originalContentIntent,
                originalBubbleIntent = originalBubbleIntent,
                originalFullScreenIntent = originalFullScreenIntent,
            )
            val clickIntent = when (resendMode) {
                com.allinist.nono.data.ResendTriggerPriority.FIDELITY_FIRST -> {
                    buildProxyReplayIntent(
                        context = context,
                        requestCode = requestCode,
                        packageName = packageName,
                        originalContentIntent = originalContentIntent,
                        originalBubbleIntent = originalBubbleIntent,
                        originalFullScreenIntent = originalFullScreenIntent,
                    )
                }

                com.allinist.nono.data.ResendTriggerPriority.REUSE_INTENT,
                com.allinist.nono.data.ResendTriggerPriority.BUBBLE_FIRST,
                com.allinist.nono.data.ResendTriggerPriority.FULL_SCREEN_FIRST
                -> directIntent

                com.allinist.nono.data.ResendTriggerPriority.PROXY_REPLAY -> {
                    buildProxyReplayIntent(
                        context = context,
                        requestCode = requestCode,
                        packageName = packageName,
                        originalContentIntent = originalContentIntent,
                        originalBubbleIntent = originalBubbleIntent,
                        originalFullScreenIntent = originalFullScreenIntent,
                    )
                }

                else -> null
            } ?: fallbackLaunchIntent?.let {
                PendingIntent.getActivity(
                    context,
                    requestCode,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo_light)
                .setContentTitle(displayTitle)
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setContentIntent(clickIntent)
                .setAutoCancel(true)
            if (resendMode == com.allinist.nono.data.ResendTriggerPriority.FIDELITY_FIRST) {
                originalActionIntents.forEachIndexed { index, pendingIntent ->
                    val actionTitle = originalActionTitles.getOrNull(index).orEmpty().ifBlank { "操作${index + 1}" }
                    builder.addAction(
                        0,
                        actionTitle,
                        buildProxyActionIntent(
                            context = context,
                            requestCode = requestCode,
                            packageName = packageName,
                            actionIndex = index,
                            pendingIntent = pendingIntent,
                        ),
                    )
                }
                if (originalDeleteIntent != null) {
                    builder.setDeleteIntent(
                        buildProxyDeleteIntent(
                            context = context,
                            requestCode = requestCode,
                            packageName = packageName,
                            originalDeleteIntent = originalDeleteIntent,
                        ),
                    )
                }
            }
            val notification = builder.build()

            manager.notify(requestCode, notification)
            return true
        }

        fun buildDisplayTitle(title: String, appName: String): String = when {
            appName.isNotBlank() && title.isNotBlank() -> "$appName·$title"
            appName.isNotBlank() -> appName
            else -> title.ifBlank { "延后通知" }
        }

        fun buildDisplayText(text: String): String = text.ifBlank { "你有一条延后提醒" }

        fun buildFallbackLaunchIntent(context: Context, packageName: String): Intent? =
            packageName
                .takeIf { it.isNotBlank() }
                ?.let { context.packageManager.getLaunchIntentForPackage(it) }
                ?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }

        private fun preferredOriginalIntent(
            resendMode: com.allinist.nono.data.ResendTriggerPriority,
            originalContentIntent: PendingIntent?,
            originalBubbleIntent: PendingIntent?,
            originalFullScreenIntent: PendingIntent?,
        ): PendingIntent? = when (resendMode) {
            com.allinist.nono.data.ResendTriggerPriority.FIDELITY_FIRST -> {
                originalContentIntent
            }

            com.allinist.nono.data.ResendTriggerPriority.BUBBLE_FIRST -> {
                originalBubbleIntent ?: originalContentIntent ?: originalFullScreenIntent
            }

            com.allinist.nono.data.ResendTriggerPriority.FULL_SCREEN_FIRST -> {
                originalFullScreenIntent ?: originalContentIntent ?: originalBubbleIntent
            }

            else -> {
                originalContentIntent ?: originalBubbleIntent ?: originalFullScreenIntent
            }
        }

        private fun buildProxyReplayIntent(
            context: Context,
            requestCode: Int,
            packageName: String,
            originalContentIntent: PendingIntent?,
            originalBubbleIntent: PendingIntent?,
            originalFullScreenIntent: PendingIntent?,
        ): PendingIntent {
            val replayIntent = Intent(context, DelayedNotificationReceiver::class.java).apply {
                action = ACTION_REPLAY_ORIGINAL_INTENT
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_ORIGINAL_CONTENT_INTENT, originalContentIntent)
                putExtra(EXTRA_ORIGINAL_BUBBLE_INTENT, originalBubbleIntent)
                putExtra(EXTRA_ORIGINAL_FULL_SCREEN_INTENT, originalFullScreenIntent)
                putExtra(EXTRA_REQUEST_CODE, requestCode)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                replayIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun buildProxyActionIntent(
            context: Context,
            requestCode: Int,
            packageName: String,
            actionIndex: Int,
            pendingIntent: PendingIntent,
        ): PendingIntent {
            val replayIntent = Intent(context, DelayedNotificationReceiver::class.java).apply {
                action = ACTION_REPLAY_ACTION_INTENT
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_REQUEST_CODE, requestCode)
                putExtra(EXTRA_ORIGINAL_CONTENT_INTENT, pendingIntent)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode * 100 + actionIndex + 1,
                replayIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun buildProxyDeleteIntent(
            context: Context,
            requestCode: Int,
            packageName: String,
            originalDeleteIntent: PendingIntent?,
        ): PendingIntent {
            val replayIntent = Intent(context, DelayedNotificationReceiver::class.java).apply {
                action = ACTION_REPLAY_ACTION_INTENT
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_REQUEST_CODE, requestCode)
                putExtra(EXTRA_ORIGINAL_CONTENT_INTENT, originalDeleteIntent)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode * 100,
                replayIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun replayOriginalPendingIntent(context: Context, intent: Intent) {
            val originalContentIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_ORIGINAL_CONTENT_INTENT, PendingIntent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_ORIGINAL_CONTENT_INTENT)
            }
            val originalBubbleIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_ORIGINAL_BUBBLE_INTENT, PendingIntent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_ORIGINAL_BUBBLE_INTENT)
            }
            val originalFullScreenIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_ORIGINAL_FULL_SCREEN_INTENT, PendingIntent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_ORIGINAL_FULL_SCREEN_INTENT)
            }
            val replayIntent = preferredOriginalIntent(
                resendMode = com.allinist.nono.data.RuntimeTuningSettingsStore.load(context).resendTriggerPriority,
                originalContentIntent = originalContentIntent,
                originalBubbleIntent = originalBubbleIntent,
                originalFullScreenIntent = originalFullScreenIntent,
            )
            val replayed = runCatching {
                replayIntent?.send()
                replayIntent != null
            }.getOrDefault(false)
            if (!replayed) {
                buildFallbackLaunchIntent(context, intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty())
                    ?.let { launchIntent ->
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(launchIntent) }
                    }
            }
        }

        private fun replayActionPendingIntent(context: Context, intent: Intent) {
            val replayTarget = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_ORIGINAL_CONTENT_INTENT, PendingIntent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_ORIGINAL_CONTENT_INTENT)
            }
            val replayed = runCatching {
                replayTarget?.send()
                replayTarget != null
            }.getOrDefault(false)
            if (!replayed) {
                buildFallbackLaunchIntent(context, intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty())
                    ?.let { launchIntent ->
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(launchIntent) }
                    }
            }
        }
    }
}
