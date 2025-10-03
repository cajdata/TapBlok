package com.cj.tapblok

import android.app.PendingIntent
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cj.tapblok.ui.theme.TapBlokTheme
import java.io.IOException

class NfcWriteActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var ndefMessage: NdefMessage? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // For now, we'll hardcode the "work" profile. Later, this can be dynamic.
        val profilePayload = "work"
        ndefMessage = createNdefMessage(profilePayload)

        setContent {
            TapBlokTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Ready to Write\nHold tag to phone",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Enable foreground dispatch to give this activity priority for NFC intents
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        // Disable foreground dispatch when the activity is not in the foreground
        nfcAdapter?.disableForegroundDispatch(this)
    }

    // This method is called when an NFC tag is detected while the activity is in the foreground
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        if (tag != null && ndefMessage != null) {
            writeNdefMessageToTag(ndefMessage!!, tag)
            finish()
        }
    }

    private fun createNdefMessage(payload: String): NdefMessage {
        val mimeType = "application/vnd.com.cj.tapblok"
        val mimeRecord = NdefRecord.createMime(mimeType, payload.toByteArray(Charsets.UTF_8))
        return NdefMessage(arrayOf(mimeRecord))
    }

    private fun writeNdefMessageToTag(message: NdefMessage, tag: Tag) {
        val ndef = Ndef.get(tag)
        ndef?.use {
            try {
                it.connect()
                if (it.maxSize < message.toByteArray().size) {
                    Toast.makeText(this, "Tag is too small!", Toast.LENGTH_SHORT).show()
                    return
                }
                if (!it.isWritable) {
                    Toast.makeText(this, "Tag is read-only!", Toast.LENGTH_SHORT).show()
                    return
                }
                it.writeNdefMessage(message)
                Toast.makeText(this, "Tag written successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Log.e("NfcWriteActivity", "Error writing NFC tag", e)
                Toast.makeText(this, "Failed to write tag.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}