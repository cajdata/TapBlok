package com.cj.tapblok

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

        val packageName = intent.getStringExtra("BLOCKED_APP_PACKAGE_NAME") ?: "An app"

        // --- START OF CHANGES ---
        // Override the back button behavior
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Instead of closing the blocking screen, go to the home screen.
                // This prevents the user from returning to the blocked app.
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
        // --- END OF CHANGES ---

        setContent {
            TapBlokTheme {
                BlockingScreen(
                    packageName = packageName,
                    onGoHomeClick = {
                        val intent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                    },
                    onTakeBreakClick = {
                        val breakIntent = Intent(this, AppMonitoringService::class.java).apply {
                            action = AppMonitoringService.ACTION_START_BREAK
                        }
                        startService(breakIntent)
                        finish() // Close the blocking screen
                    }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        finish()
    }
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

    // Use a LaunchedEffect that re-runs when the activity is resumed
    LaunchedEffect(key1 = Unit) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        breaksRemaining = prefs.getInt("breaks_remaining", 0)

        val pm = context.packageManager
        try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appName = pm.getApplicationLabel(appInfo).toString()
            appIcon = pm.getApplicationIcon(appInfo)
        } catch (e: PackageManager.NameNotFoundException) {
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

            // Only show the "Take a Break" button if there are breaks remaining
            if (breaksRemaining > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    // Decrement the break counter in SharedPreferences
                    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putInt("breaks_remaining", breaksRemaining - 1).apply()
                    onTakeBreakClick()
                }) {
                    Text(text = "Take a Break ($breaksRemaining remaining)")
                }
            }
        }
    }
}
