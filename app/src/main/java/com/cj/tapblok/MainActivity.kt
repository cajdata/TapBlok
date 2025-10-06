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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cj.tapblok.ui.theme.TapBlokTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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


    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasUsagePermission = hasUsageStatsPermission(context)
        canDrawOverlays = Settings.canDrawOverlays(context)
        isServiceRunning = isServiceRunning(context, AppMonitoringService::class.java)
    }

    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceRunning = isServiceRunning(context, AppMonitoringService::class.java)
                if (isServiceRunning) {
                    blockedAppAttempts = prefs.getInt("blocked_app_attempts", 0)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // This effect will periodically update the counter while the screen is active
    LaunchedEffect(isServiceRunning) {
        if (isServiceRunning) {
            while (isActive) {
                blockedAppAttempts = prefs.getInt("blocked_app_attempts", 0)
                delay(1000) // Update every second
            }
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
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
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
                    Text("Start Monitoring")
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

                    var holdProgress by remember { mutableStateOf(0f) }
                    var isHolding by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    var job by remember { mutableStateOf<Job?>(null) }

                    Text(
                        text = "Emergency Stop:",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isHolding = true
                                        job = scope.launch {
                                            val startTime = System.currentTimeMillis()
                                            while (isActive && System.currentTimeMillis() - startTime < 90000L) {
                                                holdProgress = (System.currentTimeMillis() - startTime) / 90000f
                                                delay(50)
                                            }
                                            if (isActive) {
                                                holdProgress = 1f
                                                val serviceIntent = Intent(context, AppMonitoringService::class.java)
                                                context.stopService(serviceIntent)
                                                isServiceRunning = false
                                                holdProgress = 0f
                                            }
                                        }
                                        try {
                                            awaitRelease()
                                        } finally {
                                            isHolding = false
                                            job?.cancel()
                                            holdProgress = 0f
                                        }
                                    }
                                )
                            }
                    ) {
                        LinearProgressIndicator(
                            progress = { holdProgress },
                            modifier = Modifier.fillMaxSize()
                        )
                        Text(
                            text = if (isHolding) "Keep Holding..." else "Hold for 90s to Stop",
                            color = if (isHolding) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
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

