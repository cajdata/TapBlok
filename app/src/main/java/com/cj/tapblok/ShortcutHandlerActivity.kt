package com.cj.tapblok

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

class ShortcutHandlerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == "com.cj.tapblok.START_MONITORING") {
            if (!isServiceRunning(this, AppMonitoringService::class.java)) {
                startMonitoringService(this)
                Toast.makeText(this, "Monitoring started.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Monitoring is already running.", Toast.LENGTH_SHORT).show()
            }
        }

        // Finish the activity immediately as it has no UI
        finish()
    }
}

