package com.cj.tapblok

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cj.tapblok.ui.theme.TapBlokTheme
import java.io.IOException

class NfcWriteActivity : ComponentActivity() {

    companion object {
        const val NFC_MIME_TYPE = "application/vnd.com.cj.tapblok"
    }

    private var nfcAdapter: NfcAdapter? = null
    private var currentPayload = "TAPBLOK_TOGGLE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            TapBlokTheme {
                NfcWriteScreen(onPayloadChange = { currentPayload = it })
            }
        }
    }

    @Composable
    fun NfcWriteScreen(onPayloadChange: (String) -> Unit) {
        val prefs = remember { getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
        var unlockDuration by remember { mutableStateOf(prefs.getInt("default_unlock_duration", 5)) }
        var isToggleType by remember { mutableStateOf(true) }

        LaunchedEffect(isToggleType, unlockDuration) {
            onPayloadChange(if (isToggleType) "TAPBLOK_TOGGLE" else "TAPBLOK_UNLOCK:$unlockDuration")
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Nfc,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Write NFC Tag",
                    style = MaterialTheme.typography.headlineMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isToggleType, onClick = { isToggleType = true })
                    Text("Toggle Monitoring", modifier = Modifier.padding(start = 8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !isToggleType, onClick = { isToggleType = false })
                    Text("Temporary Unlock", modifier = Modifier.padding(start = 8.dp))
                }

                if (!isToggleType) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Unlock duration: $unlockDuration min")
                    Slider(
                        value = unlockDuration.toFloat(),
                        onValueChange = { unlockDuration = it.toInt() },
                        valueRange = 1f..60f,
                        steps = 59
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Hold your NFC tag against the back of your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
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
        val tag: Tag? = intent.getParcelableExtraCompat<Tag>(NfcAdapter.EXTRA_TAG)
        if (tag != null) {
            val message = createNdefMessage(currentPayload)
            writeNdefMessageToTag(message, tag)
            finish()
        }
    }

    private fun createNdefMessage(payload: String): NdefMessage {
        val mimeRecord = NdefRecord.createMime(NFC_MIME_TYPE, payload.toByteArray(Charsets.UTF_8))
        return NdefMessage(arrayOf(mimeRecord))
    }

    private fun writeNdefMessageToTag(message: NdefMessage, tag: Tag) {
        val ndef = Ndef.get(tag)
        ndef?.use {
            try {
                it.connect()
                it.writeNdefMessage(message)
                Toast.makeText(this, "Tag written successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Log.e("NfcWriteActivity", "Error writing NFC tag", e)
                Toast.makeText(this, "Failed to write tag.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
