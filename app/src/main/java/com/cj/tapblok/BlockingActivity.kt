package com.cj.tapblok

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.cj.tapblok.ui.theme.TapBlokTheme

class BlockingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val blockedPackage = intent.getStringExtra("BLOCKED_APP_PACKAGE_NAME") ?: "An app"

        val goHome = {
            notifyServiceDismissed()
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })

        setContent {
            TapBlokTheme {
                BlockingScreen(
                    packageName = blockedPackage,
                    onGoHomeClick = goHome,
                    onTakeBreakClick = {
                        notifyServiceDismissed()
                        // Service handles the break decrement — do not decrement here
                        val breakIntent = Intent(this, AppMonitoringService::class.java).apply {
                            action = AppMonitoringService.ACTION_START_BREAK
                        }
                        startService(breakIntent)
                        finish()
                    }
                )
            }
        }
    }

    private fun notifyServiceDismissed() {
        val intent = Intent(this, AppMonitoringService::class.java).apply {
            action = AppMonitoringService.ACTION_BLOCKING_DISMISSED
        }
        startService(intent)
    }

    // onStop() -> finish() removed: caused premature dismissal on system dialogs/notifications
}

@Composable
fun BlockingScreen(
    packageName: String,
    onGoHomeClick: () -> Unit,
    onTakeBreakClick: () -> Unit
) {
    val context = LocalContext.current

    var appName by remember { mutableStateOf(packageName) }
    var appIcon by remember { mutableStateOf<Drawable?>(null) }
    var breaksRemaining by remember { mutableStateOf(0) }

    LaunchedEffect(key1 = Unit) {
        val prefs = context.getSharedPreferences(AppMonitoringService.PREFS_NAME, Context.MODE_PRIVATE)
        breaksRemaining = prefs.getInt("breaks_remaining", 0)

        val pm = context.packageManager
        try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appName = pm.getApplicationLabel(appInfo).toString()
            appIcon = pm.getApplicationIcon(appInfo)
        } catch (_: PackageManager.NameNotFoundException) {
            appName = packageName
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = appIcon),
                contentDescription = "$appName icon",
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "$appName is blocked by TapBlok",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(onClick = onGoHomeClick) {
                Text(text = "Go Home")
            }

            if (breaksRemaining > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                // Decrement handled by AppMonitoringService.startBreak() only
                Button(onClick = onTakeBreakClick) {
                    Text(text = "Take a Break ($breaksRemaining remaining)")
                }
            }
        }
    }
}
