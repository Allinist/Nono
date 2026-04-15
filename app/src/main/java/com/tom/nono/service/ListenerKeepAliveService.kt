package com.tom.nono.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tom.nono.R

class ListenerKeepAliveService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastRebindAtMillis: Long = 0L

    private val rebindRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (now - lastRebindAtMillis >= REBIND_INTERVAL_MS) {
                runCatching {
                    val component = ComponentName(this@ListenerKeepAliveService, AppNotificationGateService::class.java)
                    NotificationListenerService.requestRebind(component)
                }
                lastRebindAtMillis = now
            }

            val hasPendingNotices = runCatching {
                DelayedNotificationScheduler.hasPendingNotices(this@ListenerKeepAliveService)
            }.getOrDefault(false)

            if (hasPendingNotices) {
                runCatching {
                    DelayedNotificationScheduler.flushDueNotices(this@ListenerKeepAliveService, now)
                }
            }

            val nextDelay = if (hasPendingNotices) {
                ACTIVE_CHECK_INTERVAL_MS
            } else {
                IDLE_CHECK_INTERVAL_MS
            }
            handler.postDelayed(this, nextDelay)
        }
    }

    override fun onCreate() {
        super.onCreate()
        runCatching {
            startForeground(NOTIFICATION_ID, buildForegroundNotification())
            handler.post(rebindRunnable)
        }.onFailure { error ->
            Log.e(TAG, "keep-alive start failed", error)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(rebindRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildForegroundNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nono 监听守护",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "保持 Nono 通知监听服务稳定运行"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_light)
            .setContentTitle("Nono 正在守护通知监听")
            .setContentText("用于提高后台拦截稳定性")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val TAG = "NonoKeepAlive"
        private const val CHANNEL_ID = "nono_listener_keepalive"
        private const val NOTIFICATION_ID = 3001
        private const val REBIND_INTERVAL_MS = 90_000L
        private const val ACTIVE_CHECK_INTERVAL_MS = 60_000L
        private const val IDLE_CHECK_INTERVAL_MS = 180_000L
    }
}
