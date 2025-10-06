package com.cj.tapblok

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class ShortcutHandlerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == "com.cj.tapblok.ACTION_START_FROM_SHORTCUT") {
            // Check if the service is already running
            if (!isServiceRunning(this, AppMonitoringService::class.java)) {
                val serviceIntent = Intent(this, AppMonitoringService::class.java)
                startForegroundService(serviceIntent)
                Toast.makeText(this, "Monitoring started.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Monitoring is already active.", Toast.LENGTH_SHORT).show()
            }
        }
        // Finish immediately
        finish()
    }
}

