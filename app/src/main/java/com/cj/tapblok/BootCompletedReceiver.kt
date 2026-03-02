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
            } else {
                Log.d("BootCompletedReceiver", "Boot completed, monitoring was not active — skipping auto-start.")
            }
        }
    }
}