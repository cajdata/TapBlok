package com.cj.tapblok

import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.cj.tapblok.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.SortedMap
import java.util.TreeMap

class AppMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private lateinit var db: AppDatabase
    private var blockedApps: List<String> = emptyList()
    private var isBreakActive = false
    private var breakTimer: CountDownTimer? = null
    private var isBlockingActivityVisible = false

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "app_monitoring_channel"
        const val ACTION_START_BREAK = "com.cj.tapblok.ACTION_START_BREAK"
        const val ACTION_BLOCKING_DISMISSED = "com.cj.tapblok.ACTION_BLOCKING_DISMISSED"
        const val PREFS_NAME = "tapblok_prefs"
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_BREAK) {
            startBreak()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_BLOCKING_DISMISSED) {
            isBlockingActivityVisible = false
            return START_NOT_STICKY
        }

        Log.d("AppMonitoringService", "Service has started.")

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit {
            putBoolean("service_running", true)
            putInt("breaks_remaining", 3)
            putInt("blocked_app_attempts", 0)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TapBlok is Active")
            .setContentText("App monitoring and blocking is running.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            val localContext = this@AppMonitoringService
            var refreshCounter = 0
            blockedApps = db.blockedAppDao().getAllBlockedAppsList().map { it.packageName }
            Log.d("AppMonitoringService", "Initial blocked apps: $blockedApps")

            while (isActive) {
                if (!hasUsageStatsPermission() || !Settings.canDrawOverlays(localContext)) {
                    Log.e("AppMonitoringService", "Permissions revoked. Stopping service.")
                    stopSelf()
                    break
                }

                // Refresh blocked apps list every 5 seconds to catch runtime changes
                if (refreshCounter++ >= 5) {
                    blockedApps = db.blockedAppDao().getAllBlockedAppsList().map { it.packageName }
                    refreshCounter = 0
                }

                if (!isBreakActive) {
                    val foregroundApp = getForegroundApp()
                    Log.d("AppMonitoringService", "Current App: $foregroundApp")

                    if (foregroundApp != null
                        && foregroundApp in blockedApps
                        && foregroundApp != packageName
                        && !isBlockingActivityVisible
                    ) {
                        isBlockingActivityVisible = true
                        val blockIntent = Intent(localContext, BlockingActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra("BLOCKED_APP_PACKAGE_NAME", foregroundApp)
                        }
                        startActivity(blockIntent)
                        Log.d("AppMonitoringService", "Blocked app detected: $foregroundApp")

                        val currentPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        val attempts = currentPrefs.getInt("blocked_app_attempts", 0)
                        currentPrefs.edit { putInt("blocked_app_attempts", attempts + 1) }

                    } else if (foregroundApp != null && foregroundApp !in blockedApps) {
                        // Reset flag when user navigates away from the blocked app
                        isBlockingActivityVisible = false
                    }
                }
                delay(1000)
            }
        }

        return START_STICKY
    }

    private fun startBreak() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val breaksRemaining = prefs.getInt("breaks_remaining", 0)

        if (breaksRemaining <= 0) {
            Log.d("AppMonitoringService", "No breaks remaining.")
            return
        }

        prefs.edit { putInt("breaks_remaining", breaksRemaining - 1) }
        isBreakActive = true
        Log.d("AppMonitoringService", "Break started. Breaks remaining: ${breaksRemaining - 1}")

        breakTimer?.cancel()
        breakTimer = object : CountDownTimer(300000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                isBreakActive = false
                Log.d("AppMonitoringService", "Break finished.")
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        breakTimer?.cancel()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putBoolean("service_running", false)
        }
        Log.d("AppMonitoringService", "Service has been destroyed.")
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun getForegroundApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        // Reduced window from 10s to 2s to avoid stale app detection
        val appList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 2000,
            time
        )
        if (appList.isNullOrEmpty()) return null
        val sortedMap: SortedMap<Long, String> = TreeMap()
        for (usageStats in appList) {
            sortedMap[usageStats.lastTimeUsed] = usageStats.packageName
        }
        return if (sortedMap.isNotEmpty()) sortedMap[sortedMap.lastKey()] else null
    }
}
