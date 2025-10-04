package com.cj.tapblok

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cj.tapblok.ui.theme.TapBlokTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TapBlokTheme {
                MainScreen()
            }
        }
    }
}

// A helper function to check for Usage Stats permission
private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

@Composable
fun MainScreen() {
    val context = LocalContext.current

    // State to track if the service is running. rememberUpdatedState ensures it re-checks.
    val isServiceRunning by rememberUpdatedState(
        isServiceRunning(context, AppMonitoringService::class.java)
    )

    // Check for permissions
    val hasUsagePermission = hasUsageStatsPermission(context)
    val canDrawOverlays = Settings.canDrawOverlays(context)
    val allPermissionsGranted = hasUsagePermission && canDrawOverlays

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "TapBlok",
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Show status and action buttons based on permission state
            if (allPermissionsGranted) {
                // Permissions are granted, show the Start/Stop and Manage buttons
                Text(
                    text = if (isServiceRunning) "Monitoring is Active" else "Monitoring is Inactive",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isServiceRunning) Color(0xFF4CAF50) else Color.Gray // A greener color
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    val serviceIntent = Intent(context, AppMonitoringService::class.java)
                    if (isServiceRunning) {
                        context.stopService(serviceIntent)
                    } else {
                        context.startForegroundService(serviceIntent)
                    }
                }) {
                    Text(if (isServiceRunning) "Stop Monitoring" else "Start Monitoring")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        context.startActivity(Intent(context, AppSelectionActivity::class.java))
                    },
                    enabled = !isServiceRunning // Disable the button if the service is running
                ) {
                    Text("Manage Blocked Apps")
                }

                // New button to launch the NFC writing activity
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    context.startActivity(Intent(context, NfcWriteActivity::class.java))
                }) {
                    Text("Write to NFC Tag")
                }

            } else {
                // Permissions are missing, guide the user to grant them
                Text(
                    text = "Please grant the required permissions to use TapBlok.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (!hasUsagePermission) {
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }) {
                        Text("Grant Usage Access")
                    }
                }
                if (!canDrawOverlays) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    }) {
                        Text("Grant Overlay Permission")
                    }
                }
            }
        }
    }
}