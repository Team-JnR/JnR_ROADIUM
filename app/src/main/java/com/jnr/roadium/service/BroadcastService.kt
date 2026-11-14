package com.jnr.roadium.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.jnr.roadium.MainActivity
import com.jnr.roadium.R
import com.jnr.roadium.RoadiumApp

/**
 * 중계 예약·중계 중에만 도는 포그라운드 서비스. 앱 화면을 닫거나 내비 등 다른 앱을 보고 있어도
 * 효과음·음성 중계가 이어지게 프로세스를 유지한다.
 *
 * Pleos '미사용 앱 BG→CCH 전환 가이드'에 따라
 * - 부팅 때 자동으로 시작하지 않는다 (사용자가 앱에서 예약하거나 중계를 시작해야 시작).
 * - 예약·중계가 모두 끝나면 즉시 종료한다.
 */
class BroadcastService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    private val controller get() = (application as RoadiumApp).controller

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            controller.stopRelay()
            stopSelf()
            return START_NOT_STICKY
        }
        val ui = controller.ui.value
        if (ui.reservedGameId == null && ui.liveGameId == null && intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        val type = when {
            Build.VERSION.SDK_INT >= 34 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            Build.VERSION.SDK_INT >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            else -> 0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(ui.liveGameId != null), type)
        controller.start()
        return START_NOT_STICKY
    }

    private fun buildNotification(live: Boolean): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, BroadcastService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(if (live) R.string.notif_text_live else R.string.notif_text_reserved))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_launcher_foreground),
                    getString(R.string.notif_stop),
                    stop,
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "relay"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "com.jnr.roadium.action.START"
        private const val ACTION_UPDATE = "com.jnr.roadium.action.UPDATE"
        private const val ACTION_STOP = "com.jnr.roadium.action.STOP"

        private var lastLive: Boolean? = null

        fun start(context: Context) {
            lastLive = null
            send(context, ACTION_START)
        }

        /** 예약 ↔ 중계 상태가 바뀌었을 때만 알림 문구를 갱신한다 */
        fun update(context: Context) {
            val live = (context.applicationContext as RoadiumApp).controller.ui.value.liveGameId != null
            if (live == lastLive) return
            lastLive = live
            send(context, ACTION_UPDATE)
        }

        private fun send(context: Context, action: String) {
            try {
                context.startForegroundService(Intent(context, BroadcastService::class.java).setAction(action))
            } catch (e: IllegalStateException) {
                Log.w("BroadcastService", "foreground service start not allowed", e)
            }
        }

        fun stop(context: Context) {
            lastLive = null
            context.stopService(Intent(context, BroadcastService::class.java))
        }
    }
}
