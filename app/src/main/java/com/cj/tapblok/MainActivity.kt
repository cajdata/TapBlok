package com.cj.tapblok

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cj.tapblok.ui.theme.TapBlokTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasUsagePermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isServiceRunning by remember { mutableStateOf(isServiceRunning(context, AppMonitoringService::class.java)) }
    var blockedAppAttempts by remember { mutableStateOf(0) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var holdProgress by remember { mutableStateOf(0f) }
    var isHolding by remember { mutableStateOf(false) }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasUsagePermission = hasUsageStatsPermission(context)
        canDrawOverlays = Settings.canDrawOverlays(context)
        isServiceRunning = isServiceRunning(context, AppMonitoringService::class.java)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasCameraPermission = isGranted }

    val qrCodeScannerLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        if (result.contents == QrCodeActivity.QR_CODE_CONTENT) {
            if (isServiceRunning) {
                context.stopService(Intent(context, AppMonitoringService::class.java))
                Toast.makeText(context, "Monitoring stopped.", Toast.LENGTH_SHORT).show()
                isServiceRunning = false
            } else {
                startMonitoringService(context)
                Toast.makeText(context, "Monitoring started.", Toast.LENGTH_SHORT).show()
                isServiceRunning = true
            }
        } else if (result.contents != null) {
            Toast.makeText(context, "Incorrect QR Code", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsagePermission = hasUsageStatsPermission(context)
                canDrawOverlays = Settings.canDrawOverlays(context)
                isServiceRunning = isServiceRunning(context, AppMonitoringService::class.java)
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                blockedAppAttempts = prefs.getInt("blocked_app_attempts", 0)
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            val startTime = System.currentTimeMillis()
            val duration = 90000L
            while (isHolding && System.currentTimeMillis() - startTime < duration) {
                holdProgress = (System.currentTimeMillis() - startTime) / duration.toFloat()
                delay(50)
            }
            if (isHolding) {
                holdProgress = 1f
                context.stopService(Intent(context, AppMonitoringService::class.java))
                isServiceRunning = false
            }
        } else {
            holdProgress = 0f
        }
    }

    val allPermissionsGranted = hasUsagePermission && canDrawOverlays

    Surface(modifier = Modifier.fillMaxSize()) {
        if (allPermissionsGranted) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 56.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "TapBlok",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = "App blocking with real-world friction",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status card
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = if (isServiceRunning) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = if (isServiceRunning) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (isServiceRunning) "Monitoring Active" else "Monitoring Inactive",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = when {
                                    isServiceRunning && blockedAppAttempts > 0 ->
                                        "$blockedAppAttempts blocked attempt${if (blockedAppAttempts != 1) "s" else ""} this session"
                                    isServiceRunning -> "Session in progress"
                                    else -> "Tap below to start a session"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Primary action button — only shown when not monitoring
                if (!isServiceRunning) {
                    Button(
                        onClick = {
                            startMonitoringService(context)
                            isServiceRunning = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Monitoring",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // Secondary actions card
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ActionRow(
                            icon = Icons.Default.Apps,
                            label = "Manage Blocked Apps",
                            enabled = !isServiceRunning,
                            onClick = { context.startActivity(Intent(context, AppSelectionActivity::class.java)) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ActionRow(
                            icon = Icons.Default.Nfc,
                            label = "Write NFC Tag",
                            onClick = { context.startActivity(Intent(context, NfcWriteActivity::class.java)) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ActionRow(
                            icon = Icons.Default.QrCode2,
                            label = "Show QR Code",
                            onClick = { context.startActivity(Intent(context, QrCodeActivity::class.java)) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ActionRow(
                            icon = Icons.Default.QrCodeScanner,
                            label = "Scan QR Code",
                            onClick = {
                                if (hasCameraPermission) {
                                    qrCodeScannerLauncher.launch(ScanOptions().setOrientationLocked(true))
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        )
                    }
                }

                // Emergency stop
                if (isServiceRunning) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Emergency Override",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(MaterialTheme.shapes.medium)
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
                                progress = { holdProgress },
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.error,
                                trackColor = MaterialTheme.colorScheme.errorContainer
                            )
                            Text(
                                text = "HOLD 90s TO FORCE STOP",
                                modifier = Modifier.align(Alignment.Center),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (holdProgress > 0.5f) MaterialTheme.colorScheme.onError
                                        else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        } else {
            // Permissions screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Permissions Required",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "TapBlok needs a few permissions to monitor and block apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                if (!hasUsagePermission) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { settingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    ) {
                        Text("Grant Usage Access")
                    }
                }
                if (!canDrawOverlays) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
                    ) {
                        Text("Grant Overlay Permission")
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) Color.Unspecified
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = if (enabled) 1f else 0.38f
            )
        )
    }
}
