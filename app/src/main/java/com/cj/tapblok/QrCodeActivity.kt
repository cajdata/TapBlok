package com.cj.tapblok

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.cj.tapblok.ui.theme.TapBlokTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QrCodeActivity : ComponentActivity() {

    companion object {
        const val QR_CODE_CONTENT = "TAPBLOK_TOGGLE"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TapBlokTheme {
                QrCodeScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var unlockDuration by remember { mutableStateOf(prefs.getInt("default_unlock_duration", 5)) }
    var isToggleType by remember { mutableStateOf(true) }

    val content = if (isToggleType) {
        QrCodeActivity.QR_CODE_CONTENT
    } else {
        "TAPBLOK_UNLOCK:$unlockDuration"
    }

    val qrResult by produceState<Result<Bitmap>?>(initialValue = null, key1 = content) {
        value = withContext(Dispatchers.Default) {
            runCatching { generateQrCode(content) ?: error("Failed to generate QR code") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your QR Code") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val result = qrResult) {
                null -> Box(modifier = Modifier.size(256.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> {
                    val bitmap = result.getOrNull()
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier
                                .size(256.dp)
                                .aspectRatio(1f),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = "Failed to generate QR code.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Code Type", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isToggleType, onClick = { isToggleType = true })
                        Text("Toggle Monitoring", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !isToggleType, onClick = { isToggleType = false })
                        Text("Temporary Unlock", modifier = Modifier.padding(start = 8.dp))
                    }

                    if (!isToggleType) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Unlock duration: $unlockDuration min")
                        Slider(
                            value = unlockDuration.toFloat(),
                            onValueChange = { unlockDuration = it.toInt() },
                            valueRange = 1f..60f,
                            steps = 59
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (isToggleType)
                    "Scan this code to start or stop a monitoring session."
                else
                    "Scan this code from a blocked app to unlock it for $unlockDuration minutes.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun generateQrCode(content: String): Bitmap? {
    val writer = QRCodeWriter()
    return try {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = createBitmap(width, height, Bitmap.Config.RGB_565)
        val pixels = IntArray(width * height) { i ->
            if (bitMatrix[i % width, i / width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        bmp
    } catch (e: Exception) {
        Log.e("QrCodeActivity", "Failed to generate QR code", e)
        null
    }
}

