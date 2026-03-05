package com.cj.tapblok

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

class AppMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private lateinit var db: AppDatabase
    private lateinit var prefs: android.content.SharedPreferences
    @Volatile private var blockedApps: Set<String> = emptySet()
    @Volatile private var isBreakActive = false
    private var isMonitoring = false
    private var breakTimer: CountDownTimer? = null

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "app_monitoring_channel"
        const val ACTION_START_BREAK = "com.cj.tapblok.ACTION_START_BREAK"
        @Volatile var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_BREAK) {
            startBreak()
            return START_NOT_STICKY
        }

        Log.d("AppMonitoringService", "Service has started.")

        prefs.edit {
            putInt("breaks_remaining", 3)
            putInt("blocked_app_attempts", 0)
            putBoolean("monitoring_active", true)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TapBlok is Active")
            .setContentText("App monitoring and blocking is running.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        if (isMonitoring) return START_STICKY
        isMonitoring = true

        serviceScope.launch {
            db.blockedAppDao().getAllBlockedApps().collect { list ->
                blockedApps = list.map { it.packageName }.toSet()
                if (BuildConfig.DEBUG) Log.d("AppMonitoringService", "Blocked apps updated from DB: $blockedApps")
            }
        }

        serviceScope.launch {
            val localContext = this@AppMonitoringService

            while (isActive) {
                if (!hasUsageStatsPermission(localContext) || !Settings.canDrawOverlays(localContext)) {
                    Log.e("AppMonitoringService", "Permissions revoked. Stopping service.")
                    stopSelf()
                    break
                }

                if (!isBreakActive) {
                    val foregroundApp = getForegroundApp()
                    if (BuildConfig.DEBUG) Log.d("AppMonitoringService", "Current App: $foregroundApp")

                    if (foregroundApp != null && foregroundApp in blockedApps && foregroundApp != packageName) {
                        val blockIntent = Intent(localContext, BlockingActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra("BLOCKED_APP_PACKAGE_NAME", foregroundApp)
                        }
                        startActivity(blockIntent)
                        if (BuildConfig.DEBUG) Log.d("AppMonitoringService", "Blocked app detected: $foregroundApp")

                        val attempts = prefs.getInt("blocked_app_attempts", 0)
                        prefs.edit {
                            putInt("blocked_app_attempts", attempts + 1)
                        }
                    }
                }
                delay(1000)
            }
        }

        return START_STICKY
    }

    private fun startBreak() {
        breakTimer?.cancel()
        isBreakActive = true
        Log.d("AppMonitoringService", "Break started.")

        breakTimer = object : CountDownTimer(300000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
            }

            override fun onFinish() {
                isBreakActive = false
                Log.d("AppMonitoringService", "Break finished.")
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        prefs.edit { putBoolean("monitoring_active", false) }
        serviceScope.cancel()
        breakTimer?.cancel()
        Log.d("AppMonitoringService", "Service has been destroyed.")
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun getForegroundApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val appList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 10,
            time
        )
        return appList?.maxByOrNull { it.lastTimeUsed }?.packageName
    }
}

