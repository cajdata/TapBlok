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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
// --- START OF CHANGES ---
// Use the new LocalLifecycleOwner from the lifecycle-runtime-compose library
// to resolve the deprecation warning.
import androidx.lifecycle.compose.LocalLifecycleOwner
// --- END OF CHANGES ---
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

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

    var hasUsagePermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isServiceRunning by remember { mutableStateOf(isServiceRunning(context, AppMonitoringService::class.java)) }
    var blockedAppAttempts by remember { mutableStateOf(0) }

    var holdProgress by remember { mutableStateOf(0f) }
    var isHolding by remember { mutableStateOf(false) }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasUsagePermission = hasUsageStatsPermission(context)
        canDrawOverlays = Settings.canDrawOverlays(context)
        isServiceRunning = isServiceRunning(context, AppMonitoringService::class.java)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceRunning = isServiceRunning(context, AppMonitoringService::class.java)
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                blockedAppAttempts = prefs.getInt("blocked_app_attempts", 0)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            val startTime = System.currentTimeMillis()
            val duration = 90000L // 90 seconds
            while (isHolding && System.currentTimeMillis() - startTime < duration) {
                holdProgress = (System.currentTimeMillis() - startTime) / duration.toFloat()
                delay(50)
            }
            if (isHolding) {
                holdProgress = 1f
                val serviceIntent = Intent(context, AppMonitoringService::class.java)
                context.stopService(serviceIntent)
                isServiceRunning = false
            }
        } else {
            holdProgress = 0f
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
                if (isServiceRunning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Blocked App Attempts: $blockedAppAttempts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val serviceIntent = Intent(context, AppMonitoringService::class.java)
                        if (isServiceRunning) {
                            context.stopService(serviceIntent)
                            isServiceRunning = false
                        } else {
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

                if (isServiceRunning) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "Press and hold for 90 seconds to force stop",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(50.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isHolding = true
                                        tryAwaitRelease()
                                        isHolding = false
                                    }
                                )
                            }
                    ) {
                        LinearProgressIndicator(
                            progress = holdProgress,
                            modifier = Modifier.fillMaxSize()
                        )
                        Text(
                            text = "EMERGENCY STOP",
                            modifier = Modifier.align(Alignment.Center),
                            color = if (holdProgress > 0.5f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        )
                    }
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

