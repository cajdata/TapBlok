package com.cj.tapblok

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    val lifecycleOwner = LocalLifecycleOwner.current

    // State variables to track the status of permissions and the service
    var hasUsagePermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isServiceRunning by remember { mutableStateOf(isServiceRunning(context, AppMonitoringService::class.java)) }

    // This launcher handles returning from the settings screen
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // When the user returns, re-check the permissions and service status
        hasUsagePermission = hasUsageStatsPermission(context)
        canDrawOverlays = Settings.canDrawOverlays(context)
        isServiceRunning = isServiceRunning(context, AppMonitoringService::class.java)
    }

    // This effect handles resuming the app from the background (e.g., after an NFC scan)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceRunning = isServiceRunning(context, AppMonitoringService::class.java)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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

            if (allPermissionsGranted) {
                Text(
                    text = if (isServiceRunning) "Monitoring is Active" else "Monitoring is Inactive",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isServiceRunning) Color(0xFF4CAF50) else Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val serviceIntent = Intent(context, AppMonitoringService::class.java)
                        if (!isServiceRunning) {
                            context.startForegroundService(serviceIntent)
                            isServiceRunning = true
                        }
                    },
                    enabled = !isServiceRunning
                ) {
                    Text(if (isServiceRunning) "Stop Monitoring" else "Start Monitoring")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        context.startActivity(Intent(context, AppSelectionActivity::class.java))
                    },
                    enabled = !isServiceRunning
                ) {
                    Text("Manage Blocked Apps")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    context.startActivity(Intent(context, NfcWriteActivity::class.java))
                }) {
                    Text("Write to NFC Tag")
                }
            } else {
                Text(
                    text = "Please grant the required permissions to use TapBlok.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (!hasUsagePermission) {
                    Button(onClick = {
                        settingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }) {
                        Text("Grant Usage Access")
                    }
                }
                if (!canDrawOverlays) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    }) {
                        Text("Grant Overlay Permission")
                    }
                }
            }
        }
    }
}