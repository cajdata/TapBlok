package com.cj.tapblok

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
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
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Your QR Code") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { padding ->
                    QrCodeScreen(
                        modifier = Modifier.padding(padding),
                        content = QR_CODE_CONTENT
                    )
                }
            }
        }
    }
}

@Composable
fun QrCodeScreen(modifier: Modifier = Modifier, content: String) {
    val qrBitmap by produceState<Bitmap?>(initialValue = null, producer = {
        value = withContext(Dispatchers.Default) {
            generateQrCode(content)
        }
    })

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap!!.asImageBitmap(),
                contentDescription = "QR Code",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Fit
            )
        } else {
            CircularProgressIndicator()
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Scan this code to start or stop a monitoring session.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
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
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp[x, y] = if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
        bmp
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

