package com.cj.tapblok

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import com.cj.tapblok.database.AppDatabase

class App : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        trackForegroundForStrictMode()
    }

    // Strict mode allows stopping a session only while the user is inside TapBlok.
    // Any of our screens counts, except the invisible handlers and the block screen
    // (which has its own tracking so tag scans there unlock a single app instead)
    private fun trackForegroundForStrictMode() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private fun isMainUi(activity: Activity): Boolean = when (activity) {
                is BlockingActivity, is NfcHandlerActivity, is ShortcutHandlerActivity -> false
                else -> true
            }

            override fun onActivityResumed(activity: Activity) {
                if (isMainUi(activity)) AppForeground.onMainResumed()
            }

            override fun onActivityPaused(activity: Activity) {
                if (isMainUi(activity)) AppForeground.onMainPaused()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "App Monitoring"
            val descriptionText = "Channel for the app monitoring service"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(AppMonitoringService.CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}