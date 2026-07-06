package com.cj.tapblok

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
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
    private var lastEventTimestamp = 0L
    private var currentForegroundApp: String? = null
    // Strict mode: apps granted a timed unlock, package -> expiry epoch millis
    private val temporarilyUnlockedApps = java.util.concurrent.ConcurrentHashMap<String, Long>()

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "app_monitoring_channel"
        const val ACTION_START_BREAK = "com.cj.tapblok.ACTION_START_BREAK"
        const val ACTION_UNLOCK_APP = "com.cj.tapblok.ACTION_UNLOCK_APP"
        const val EXTRA_UNLOCK_PACKAGE = "com.cj.tapblok.extra.UNLOCK_PACKAGE"
        private const val INITIAL_EVENT_LOOKBACK_MS = 60 * 60 * 1000L
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
            // The last onStartCommand return value governs restart-after-kill behaviour,
            // so a break must not downgrade the service to non-sticky
            return START_STICKY
        }

        if (intent?.action == ACTION_UNLOCK_APP) {
            intent.getStringExtra(EXTRA_UNLOCK_PACKAGE)?.let { unlockPackage ->
                val minutes = prefs.getInt(AppSettings.KEY_UNLOCK_MINUTES, AppSettings.DEFAULT_UNLOCK_MINUTES)
                temporarilyUnlockedApps[unlockPackage] = System.currentTimeMillis() + minutes * 60_000L
                Log.d("AppMonitoringService", "Temporarily unlocked $unlockPackage for $minutes minutes.")
            }
            return START_STICKY
        }

        Log.d("AppMonitoringService", "Service has started.")

        prefs.edit {
            putInt("breaks_remaining", prefs.getInt(AppSettings.KEY_BREAKS_ALLOWED, AppSettings.DEFAULT_BREAKS_ALLOWED))
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

                    if (foregroundApp != null && foregroundApp in blockedApps &&
                        foregroundApp != packageName && !isTemporarilyUnlocked(foregroundApp)
                    ) {
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

    private fun isTemporarilyUnlocked(packageName: String): Boolean {
        val expiry = temporarilyUnlockedApps[packageName] ?: return false
        if (expiry <= System.currentTimeMillis()) {
            temporarilyUnlockedApps.remove(packageName)
            return false
        }
        return true
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

    // Some launchers kill the process when the app's task is swiped away; schedule a
    // restart so an active session survives "clear all apps"
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (prefs.getBoolean("monitoring_active", false)) {
            val restartIntent = Intent(applicationContext, AppMonitoringService::class.java)
            val flags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(this, 1, restartIntent, flags)
            } else {
                PendingIntent.getService(this, 1, restartIntent, flags)
            }
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000, pendingIntent)
            Log.d("AppMonitoringService", "Task removed — scheduled service restart.")
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    // Usage events are the reliable way to track the foreground app. queryUsageStats over a
    // short window misses apps that were already in the foreground before the session began
    // (no new stats update = invisible), which let blocked apps slip through.
    @Suppress("DEPRECATION") // MOVE_TO_FOREGROUND == ACTIVITY_RESUMED; the old name also covers pre-API-29 devices
    private fun getForegroundApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val begin = if (lastEventTimestamp == 0L) now - INITIAL_EVENT_LOOKBACK_MS else lastEventTimestamp + 1
        val events = usageStatsManager.queryEvents(begin, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                currentForegroundApp = event.packageName
            }
            if (event.timeStamp > lastEventTimestamp) {
                lastEventTimestamp = event.timeStamp
            }
        }
        return currentForegroundApp
    }
}

