package com.cj.tapblok

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("monitoring_active", false)) {
                Log.d("BootCompletedReceiver", "Boot completed, resuming monitoring service.")
                startMonitoringService(context)
                // Alarms don't survive reboot; recompute them
                ScheduleManager.reschedule(context)
            } else {
                Log.d("BootCompletedReceiver", "Boot completed, monitoring was not active — skipping auto-start.")
                // Recompute alarms, and start blocking if we're inside a scheduled window
                // whose start alarm was missed while the phone was off
                ScheduleManager.reschedule(context, startIfInWindow = true)
            }
        }
    }
}