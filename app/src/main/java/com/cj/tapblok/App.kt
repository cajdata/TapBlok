package com.cj.tapblok

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.cj.tapblok.database.AppDatabase

class App : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Monitoring channel (silent)
            val monitoringChannel = NotificationChannel(
                AppMonitoringService.CHANNEL_ID,
                "App Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for the app monitoring service"
            }
            notificationManager.createNotificationChannel(monitoringChannel)

            // Alerts channel (high priority)
            val alertChannel = NotificationChannel(
                "tapblok_alerts",
                "Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for important TapBlok alerts"
            }
            notificationManager.createNotificationChannel(alertChannel)
        }
    }
}