package com.githubcontrol.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SshKeyNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            SshKeyNotificationService.ACTION_TURN_OFF -> {
                SshKeyNotificationService.stop(ctx)
            }
        }
    }
}
