package com.cj.tapblok

import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Only restart if the user had an active session before reboot
        val prefs = context.getSharedPreferences(AppMonitoringService.PREFS_NAME, Context.MODE_PRIVATE)
        val wasRunning = prefs.getBoolean("service_running", false)

        if (!wasRunning) {
            Log.d("BootCompletedReceiver", "No active session before reboot. Not starting service.")
            return
        }

        // Check required permissions before starting the service to avoid boot crash
        if (!hasUsageStatsPermission(context) || !Settings.canDrawOverlays(context)) {
            Log.w("BootCompletedReceiver", "Required permissions not granted. Cannot restart service on boot.")
            // Clear the stale running flag since we can't actually start
            prefs.edit().putBoolean("service_running", false).apply()
            return
        }

        Log.d("BootCompletedReceiver", "Boot completed, restarting monitoring service.")

        // Set the flag before starting so NFC toggle state is correct immediately
        prefs.edit().putBoolean("service_running", true).apply()

        val serviceIntent = Intent(context, AppMonitoringService::class.java)
        context.startForegroundService(serviceIntent)
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
