package com.cj.tapblok

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast

class ShortcutHandlerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == "com.cj.tapblok.START_MONITORING") {
            val serviceIntent = Intent(this, AppMonitoringService::class.java)

            // --- START OF CHANGES ---
            // Add a version check to safely call the correct service-starting method.
            // This resolves the "Call requires API level 26" error.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            // --- END OF CHANGES ---

            Toast.makeText(this, "Monitoring started.", Toast.LENGTH_SHORT).show()
        }

        // Finish the activity immediately as it has no UI
        finish()
    }
}

