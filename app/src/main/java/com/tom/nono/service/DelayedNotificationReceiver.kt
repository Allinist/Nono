package com.tom.nono.service

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
import com.tom.nono.R
import com.tom.nono.data.DelayedNoticeStore

class DelayedNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
        val noticeId = intent.getStringExtra(EXTRA_NOTICE_ID).orEmpty()
        val notified = notifyReminder(
            context = context,
            title = title,
            text = text,
            appName = appName,
            packageName = packageName,
            originalContentIntent = originalContentIntent,
            requestCode = requestCode,
        )
        if (notified && noticeId.isNotBlank()) {
            DelayedNoticeStore(context).removeNotice(noticeId)
        }
    }

    companion object {
        const val CHANNEL_ID = "nono_delayed_notifications"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_ORIGINAL_CONTENT_INTENT = "original_content_intent"
        const val EXTRA_REQUEST_CODE = "request_code"
        const val EXTRA_NOTICE_ID = "notice_id"

        fun notifyReminder(
            context: Context,
            title: String,
            text: String,
            appName: String,
            packageName: String,
            originalContentIntent: PendingIntent?,
            requestCode: Int,
        ): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }

            val manager = context.getSystemService(NotificationManager::class.java)
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
            val clickIntent = originalContentIntent ?: fallbackLaunchIntent?.let {
                PendingIntent.getActivity(
                    context,
                    requestCode,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo_light)
                .setContentTitle(displayTitle)
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setContentIntent(clickIntent)
                .setAutoCancel(true)
                .build()

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
    }
}
