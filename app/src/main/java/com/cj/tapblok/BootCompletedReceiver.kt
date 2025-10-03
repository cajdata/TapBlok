package com.cj.tapblok

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // We only care about the boot completed event
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootCompletedReceiver", "Boot completed, starting monitoring service.")

            // Create an intent to start our service
            val serviceIntent = Intent(context, AppMonitoringService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}