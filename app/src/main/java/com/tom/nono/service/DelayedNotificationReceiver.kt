package com.tom.nono.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty()
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
        val noticeId = intent.getStringExtra(EXTRA_NOTICE_ID).orEmpty()
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

        val contentText = listOf(text, appName.takeIf { it.isNotBlank() }?.let { "来自 $it" })
            .filterNotNull()
            .joinToString(" · ")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_light)
            .setContentTitle(title.ifBlank { "延后通知" })
            .setContentText(contentText.ifBlank { "你有一条延后提醒" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText.ifBlank { "你有一条延后提醒" }))
            .setAutoCancel(true)
            .build()

        manager.notify(requestCode, notification)
        if (noticeId.isNotBlank()) {
            DelayedNoticeStore(context).removeNotice(noticeId)
        }
    }

    companion object {
        const val CHANNEL_ID = "nono_delayed_notifications"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_REQUEST_CODE = "request_code"
        const val EXTRA_NOTICE_ID = "notice_id"
    }
}
