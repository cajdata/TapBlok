package com.cj.tapblok

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import coil.compose.rememberAsyncImagePainter
import com.cj.tapblok.ui.theme.TapBlokTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class BlockingActivity : ComponentActivity() {

    private var blockedPackage: String? = null
    private var awaitingScanResult = false

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        awaitingScanResult = false
        when (result.contents) {
            null -> Unit // scan cancelled
            QrCodeActivity.getOrCreateToken(this) -> unlockAndReturn()
            QrCodeActivity.LEGACY_QR_CONTENT ->
                Toast.makeText(
                    this,
                    "That QR code is from an older TapBlok version. Print a new one from \"Show QR Code\".",
                    Toast.LENGTH_LONG
                ).show()
            else -> Toast.makeText(this, "Incorrect QR Code", Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchScanner()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        blockedPackage = intent.getStringExtra("BLOCKED_APP_PACKAGE_NAME")
        val packageName = blockedPackage ?: "An app"
        val prefs = AppSettings.prefs(this)
        val strictMode = prefs.getBoolean(AppSettings.KEY_STRICT_MODE, false)
        val unlockMinutes = prefs.getInt(AppSettings.KEY_UNLOCK_MINUTES, AppSettings.DEFAULT_UNLOCK_MINUTES)

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
                    strictMode = strictMode,
                    unlockMinutes = unlockMinutes,
                    onGoHomeClick = goHome,
                    onTakeBreakClick = {
                        val breakIntent = Intent(this, AppMonitoringService::class.java).apply {
                            action = AppMonitoringService.ACTION_START_BREAK
                        }
                        startService(breakIntent)
                        finish()
                    },
                    onScanToUnlockClick = {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            launchScanner()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppForeground.onBlockingResumed(blockedPackage)
    }

    override fun onPause() {
        super.onPause()
        AppForeground.onBlockingPaused()
    }

    // The block screen is a gate, not a destination — close it whenever it leaves the
    // screen so it doesn't linger in the back stack after an unlock or app switch.
    // Skipped while the QR scanner is up, since that covers this activity too.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isFinishing && !awaitingScanResult) {
            finish()
        }
    }

    private fun launchScanner() {
        awaitingScanResult = true
        qrScanLauncher.launch(ScanOptions().setOrientationLocked(true))
    }

    private fun unlockAndReturn() {
        val pkg = blockedPackage ?: return
        val minutes = AppSettings.prefs(this)
            .getInt(AppSettings.KEY_UNLOCK_MINUTES, AppSettings.DEFAULT_UNLOCK_MINUTES)
        val unlockIntent = Intent(this, AppMonitoringService::class.java).apply {
            action = AppMonitoringService.ACTION_UNLOCK_APP
            putExtra(AppMonitoringService.EXTRA_UNLOCK_PACKAGE, pkg)
        }
        startService(unlockIntent)
        Toast.makeText(this, "Unlocked for $minutes minute${if (minutes != 1) "s" else ""}.", Toast.LENGTH_SHORT).show()
        packageManager.getLaunchIntentForPackage(pkg)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        }
        finish()
    }
}

@Composable
fun BlockingScreen(
    packageName: String,
    strictMode: Boolean,
    unlockMinutes: Int,
    onGoHomeClick: () -> Unit,
    onTakeBreakClick: () -> Unit,
    onScanToUnlockClick: () -> Unit
) {
    val context = LocalContext.current

    var appName by remember { mutableStateOf(packageName) }
    var appIcon by remember { mutableStateOf<Drawable?>(null) }
    var breaksRemaining by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(key1 = Unit) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
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
                text = if (strictMode) {
                    "Tap your NFC tag or scan your QR code to unlock $appName for " +
                            "$unlockMinutes minute${if (unlockMinutes != 1) "s" else ""}."
                } else {
                    "Tap your NFC tag or scan your QR code to unlock."
                },
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

            if (strictMode) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onScanToUnlockClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Scan QR to Unlock")
                }
            }

            if (breaksRemaining > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
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
