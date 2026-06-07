package com.cj.tapblok

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

class ShortcutHandlerActivity : ComponentActivity() {

    companion object {
        const val ACTION_START_MONITORING = "com.cj.tapblok.START_MONITORING"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == ACTION_START_MONITORING) {
            if (!isServiceRunning(this, AppMonitoringService::class.java)) {
                if (hasUsageStatsPermission(this) && android.provider.Settings.canDrawOverlays(this) && hasNotificationPermission(this)) {
                    startMonitoringService(this)
                    Toast.makeText(this, "Monitoring started.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Please open TapBlok to grant missing permissions.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Monitoring is already running.", Toast.LENGTH_SHORT).show()
            }
        }

        // Finish the activity immediately as it has no UI
        finish()
    }
}

