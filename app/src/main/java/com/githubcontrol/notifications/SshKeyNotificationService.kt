package com.githubcontrol.notifications

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.githubcontrol.MainActivity
import com.githubcontrol.ui.navigation.Routes

class SshKeyNotificationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NID_SSH, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): android.app.Notification {
        val ctx = this

        val openIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ROUTE, Routes.SSH_KEYS)
        }
        val openPi = PendingIntent.getActivity(
            ctx, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val turnOffIntent = Intent(ctx, SshKeyNotificationReceiver::class.java).apply {
            action = ACTION_TURN_OFF
        }
        val turnOffPi = PendingIntent.getBroadcast(
            ctx, 1, turnOffIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val addIntent = Intent(ctx, com.githubcontrol.ui.screens.keys.SshKeyQuickAddActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val addPi = PendingIntent.getActivity(
            ctx, 2, addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(ctx, NotificationChannels.SSH_KEYS)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("SSH Keys")
            .setContentText("Manage your GitHub SSH keys")
            .setContentIntent(openPi)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Open SSH Keys", openPi)
            .addAction(0, "Add Key", addPi)
            .addAction(0, "Turn Off", turnOffPi)
            .build()
    }

    companion object {
        const val NID_SSH      = 2001
        const val ACTION_TURN_OFF = "com.githubcontrol.SSH_NOTIF_TURN_OFF"
        const val EXTRA_ROUTE  = "navigate_to"
        const val PREFS_NAME   = "ssh_notif_prefs"
        const val PREF_ENABLED = "enabled"

        fun start(ctx: Context) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_ENABLED, true).apply()
            ctx.startForegroundService(Intent(ctx, SshKeyNotificationService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_ENABLED, false).apply()
            ctx.stopService(Intent(ctx, SshKeyNotificationService::class.java))
        }

        fun isEnabled(ctx: Context): Boolean =
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_ENABLED, false)
    }
}
