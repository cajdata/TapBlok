package com.cj.tapblok

import android.app.PendingIntent
import android.content.Intent
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
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
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        if (tag != null && ndefMessage != null) {
            // finish() is now called inside writeNdefMessageToTag only on success
            writeNdefMessageToTag(ndefMessage!!, tag)
        }
    }

    private fun createNdefMessage(payload: String): NdefMessage {
        val mimeType = "application/vnd.com.cj.tapblok"
        val mimeRecord = NdefRecord.createMime(mimeType, payload.toByteArray(Charsets.UTF_8))
        return NdefMessage(arrayOf(mimeRecord))
    }

    private fun writeNdefMessageToTag(message: NdefMessage, tag: Tag) {
        val ndef = Ndef.get(tag)

        if (ndef != null) {
            try {
                ndef.connect()
                if (ndef.maxSize < message.toByteArray().size) {
                    Toast.makeText(this, "Tag is too small!", Toast.LENGTH_SHORT).show()
                    return
                }
                if (!ndef.isWritable) {
                    Toast.makeText(this, "Tag is read-only!", Toast.LENGTH_SHORT).show()
                    return
                }
                ndef.writeNdefMessage(message)
                Toast.makeText(this, "Tag written successfully!", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: IOException) {
                Log.e("NfcWriteActivity", "IOException writing NFC tag", e)
                Toast.makeText(this, "Failed to write tag. Ensure tag is held steady.", Toast.LENGTH_SHORT).show()
            } catch (e: FormatException) {
                Log.e("NfcWriteActivity", "FormatException writing NFC tag", e)
                Toast.makeText(this, "Tag format error. Try a different tag.", Toast.LENGTH_SHORT).show()
            } finally {
                try {
                    ndef.close()
                } catch (e: IOException) {
                    Log.e("NfcWriteActivity", "Error closing NDEF tag", e)
                }
            }
        } else {
            // Tag is unformatted — attempt to format and write
            val ndefFormatable = NdefFormatable.get(tag)
            if (ndefFormatable != null) {
                try {
                    ndefFormatable.connect()
                    ndefFormatable.format(message)
                    Toast.makeText(this, "Tag formatted and written successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                } catch (e: IOException) {
                    Log.e("NfcWriteActivity", "IOException formatting NFC tag", e)
                    Toast.makeText(this, "Failed to format tag.", Toast.LENGTH_SHORT).show()
                } catch (e: FormatException) {
                    Log.e("NfcWriteActivity", "FormatException formatting NFC tag", e)
                    Toast.makeText(this, "Tag format error.", Toast.LENGTH_SHORT).show()
                } finally {
                    try {
                        ndefFormatable.close()
                    } catch (e: IOException) {
                        Log.e("NfcWriteActivity", "Error closing formatable tag", e)
                    }
                }
            } else {
                Toast.makeText(this, "Tag is not NDEF compatible.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
