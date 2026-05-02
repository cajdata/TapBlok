package com.cj.tapblok

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import coil.compose.rememberAsyncImagePainter
import com.cj.tapblok.ui.theme.TapBlokTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class BlockingActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        val packageName = intent.getStringExtra("BLOCKED_APP_PACKAGE_NAME") ?: "An app"
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isStrictMode = prefs.getBoolean("is_strict_mode", false)

        val goHome = {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goHome() }
        })

        setContent {
            TapBlokTheme {
                BlockingScreen(
                    packageName = packageName,
                    isStrictMode = isStrictMode,
                    onGoHomeClick = goHome,
                    onTakeBreakClick = {
                        val breakIntent = Intent(this, AppMonitoringService::class.java).apply {
                            action = AppMonitoringService.ACTION_START_BREAK
                        }
                        startService(breakIntent)
                        finish()
                    },
                    onAppUnlocked = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            val messages = intent.getParcelableArrayExtraCompat<NdefMessage>(NfcAdapter.EXTRA_NDEF_MESSAGES)
            if (!messages.isNullOrEmpty()) {
                val ndefMessage = messages[0] as NdefMessage
                val record = ndefMessage.records[0]
                val payload = String(record.payload, Charsets.UTF_8)
                val packageName = this.intent.getStringExtra("BLOCKED_APP_PACKAGE_NAME")

                if (packageName != null) {
                    val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    val isStrictMode = prefs.getBoolean("is_strict_mode", false)

                    if (!isStrictMode) {
                        // Chill Mode: Scanning any valid tag (Toggle or Unlock) on block screen stops monitoring
                        if (payload == "TAPBLOK_TOGGLE" || payload.startsWith("TAPBLOK_UNLOCK:")) {
                            stopService(Intent(this, AppMonitoringService::class.java))
                            Toast.makeText(this, "Monitoring stopped.", Toast.LENGTH_SHORT).show()
                            finish()
                            return
                        }
                    } else {
                        // Strict Mode: Pause monitoring (unlock) for specific durations
                        var duration = prefs.getInt("default_unlock_duration", 5)
                        if (payload.startsWith("TAPBLOK_UNLOCK:")) {
                            duration = payload.removePrefix("TAPBLOK_UNLOCK:").toIntOrNull() ?: duration
                        } else if (payload != "TAPBLOK_TOGGLE") {
                            return
                        }

                        val unlockIntent = Intent(this, AppMonitoringService::class.java).apply {
                            action = AppMonitoringService.ACTION_UNLOCK_APP
                            putExtra(AppMonitoringService.EXTRA_PACKAGE_NAME, packageName)
                            putExtra(AppMonitoringService.EXTRA_DURATION_MINUTES, duration)
                        }
                        startService(unlockIntent)
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
fun BlockingScreen(
    packageName: String,
    isStrictMode: Boolean,
    onGoHomeClick: () -> Unit,
    onTakeBreakClick: () -> Unit,
    onAppUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    var appName by remember { mutableStateOf(packageName) }
    var appIcon by remember { mutableStateOf<Drawable?>(null) }
    var breaksRemaining by rememberSaveable { mutableIntStateOf(0) }

    val qrCodeScannerLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        if (result.contents != null) {
            val content = result.contents
            
            if (!isStrictMode) {
                // Chill Mode: Scanning any valid tag on block screen stops monitoring
                if (content == QrCodeActivity.QR_CODE_CONTENT || content.startsWith("TAPBLOK_UNLOCK:")) {
                    context.stopService(Intent(context, AppMonitoringService::class.java))
                    Toast.makeText(context, "Monitoring stopped.", Toast.LENGTH_SHORT).show()
                    onAppUnlocked()
                    return@rememberLauncherForActivityResult
                }
            } else {
                // Strict Mode: Unlocks
                var duration = prefs.getInt("default_unlock_duration", 5)
                if (content.startsWith("TAPBLOK_UNLOCK:")) {
                    duration = content.removePrefix("TAPBLOK_UNLOCK:").toIntOrNull() ?: duration
                } else if (content != QrCodeActivity.QR_CODE_CONTENT) {
                    Toast.makeText(context, "Invalid QR Code", Toast.LENGTH_SHORT).show()
                    return@rememberLauncherForActivityResult
                }

                val unlockIntent = Intent(context, AppMonitoringService::class.java).apply {
                    action = AppMonitoringService.ACTION_UNLOCK_APP
                    putExtra(AppMonitoringService.EXTRA_PACKAGE_NAME, packageName)
                    putExtra(AppMonitoringService.EXTRA_DURATION_MINUTES, duration)
                }
                context.startService(unlockIntent)
                onAppUnlocked()
            }
        }
    }

    LaunchedEffect(key1 = Unit) {
        breaksRemaining = prefs.getInt("breaks_remaining", 0)

        val pm = context.packageManager
        try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            appName = pm.getApplicationLabel(appInfo).toString()
            appIcon = pm.getApplicationIcon(appInfo)
        } catch (_: PackageManager.NameNotFoundException) {
            appName = packageName
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App icon with lock badge
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = appIcon),
                        contentDescription = "$appName icon",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "BLOCKED",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = appName,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isStrictMode) "Strict mode is active. Scan to unlock this app temporarily."
                       else "Tap your NFC tag or scan your QR code to unlock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onGoHomeClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Go Home")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    qrCodeScannerLauncher.launch(ScanOptions().setOrientationLocked(true))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan to Unlock")
            }

            if (!isStrictMode && breaksRemaining > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        prefs.edit { putInt("breaks_remaining", breaksRemaining - 1) }
                        onTakeBreakClick()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Take a Break ($breaksRemaining remaining)")
                }
            }
        }
    }
}
