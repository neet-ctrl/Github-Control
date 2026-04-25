package com.githubcontrol

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.githubcontrol.notifications.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        com.githubcontrol.utils.CrashHandler.install(this)
        createNotificationChannels()
        com.githubcontrol.utils.Logger.i("App", "GitHub Control starting v${runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"}")
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        listOf(
            NotificationChannel(NotificationChannels.UPLOAD, "Uploads", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(NotificationChannels.SYNC, "Sync", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(NotificationChannels.ALERTS, "Alerts", NotificationManager.IMPORTANCE_DEFAULT),
        ).forEach(nm::createNotificationChannel)
    }
}
