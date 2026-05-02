package com.cj.tapblok

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class AppMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private lateinit var db: AppDatabase
    private lateinit var prefs: android.content.SharedPreferences
    @Volatile private var blockedApps: Set<String> = emptySet()
    @Volatile private var isBreakActive = false
    
    // Package Name -> Expiry Timestamp (millis)
    private val temporarilyUnlockedApps = ConcurrentHashMap<String, Long>()
    private val appLabelCache = ConcurrentHashMap<String, String>()
    private var lastNotificationText: String? = null
    
    private var isMonitoring = false
    private var breakTimer: CountDownTimer? = null
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "app_monitoring_channel"
        const val ACTION_START_BREAK = "com.cj.tapblok.ACTION_START_BREAK"
        const val ACTION_UNLOCK_APP = "com.cj.tapblok.ACTION_UNLOCK_APP"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
        
        private val _isRunning = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunning.asStateFlow()
        
        var isRunning: Boolean
            get() = _isRunning.value
            set(value) { _isRunning.value = value }
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        isRunning = true
        Log.d("AppMonitoringService", "Service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BREAK -> {
                startBreak()
                return START_NOT_STICKY
            }
            ACTION_UNLOCK_APP -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                val duration = intent.getIntExtra(EXTRA_DURATION_MINUTES, 0)
                if (pkg != null && duration > 0) {
                    temporarilyUnlockedApps[pkg] = System.currentTimeMillis() + (duration * 60 * 1000L)
                    Log.d("AppMonitoringService", "Unlocked $pkg for $duration minutes")
                    // Trigger immediate update
                    updateNotificationFromMap()
                }
                return START_STICKY
            }
        }

        Log.d("AppMonitoringService", "Service has started.")

        prefs.edit {
            putInt("breaks_remaining", 3)
            putInt("blocked_app_attempts", 0)
            putBoolean("monitoring_active", true)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        // pendingIntent is used inside createNotification()

        // Always start as foreground to ensure reliability, but we'll hide it if no unlocks exist
        startForeground(NOTIFICATION_ID, createNotification())
        updateNotificationFromMap()

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

                updateNotificationFromMap()

                if (!isBreakActive) {
                    val foregroundApp = getForegroundApp()
                    
                    val isTempUnlocked = foregroundApp?.let {
                        val expiry = temporarilyUnlockedApps[it]
                        expiry != null && expiry > System.currentTimeMillis()
                    } ?: false

                    if (foregroundApp != null && foregroundApp in blockedApps && foregroundApp != packageName && !isTempUnlocked) {
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

    private fun createNotification(contentText: String? = null): android.app.Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TapBlok is Active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)

        if (contentText != null) {
            builder.setContentText(contentText)
        }

        return builder.build()
    }

    private fun updateNotificationFromMap() {
        val now = System.currentTimeMillis()
        val activeUnlocks = mutableListOf<String>()
        
        val iterator = temporarilyUnlockedApps.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value <= now) {
                iterator.remove()
            } else {
                val remainingMinutes = ((entry.value - now) / 60000L) + 1
                val appLabel = getAppLabel(entry.key)
                activeUnlocks.add("$appLabel: ${remainingMinutes}m")
            }
        }
        updateNotification(activeUnlocks)
    }

    private fun updateNotification(activeUnlocks: List<String>) {
        val contentText = if (activeUnlocks.isEmpty()) {
            null
        } else {
            "Unlocked: ${activeUnlocks.joinToString(", ")}"
        }
        
        if (contentText == lastNotificationText) return
        lastNotificationText = contentText
        
        if (contentText == null) {
            // No active unlocks, remove foreground notification.
            // Note: The service remains running in the background.
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            val notification = createNotification(contentText)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun getAppLabel(packageName: String): String {
        appLabelCache[packageName]?.let { return it }
        val label = try {
            val pm = packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
        appLabelCache[packageName] = label
        return label
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
