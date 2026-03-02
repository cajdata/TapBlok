package com.cj.tapblok

import android.app.Activity
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.widget.Toast

class NfcHandlerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("NfcHandlerActivity", "Activity launched by NFC intent.")
        handleNfcIntent()
    }

    private fun handleNfcIntent() {
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            val messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            if (messages != null) {
                val ndefMessage = messages[0] as NdefMessage
                val record = ndefMessage.records[0]

                if (String(record.type) != NfcWriteActivity.NFC_MIME_TYPE) {
                    Log.w("NfcHandlerActivity", "Ignoring NFC tag with unexpected MIME type.")
                    finish()
                    return
                }

                Log.d("NfcHandlerActivity", "Valid TapBlok NFC tag detected.")

                val serviceIntent = Intent(this, AppMonitoringService::class.java)

                if (isServiceRunning(this, AppMonitoringService::class.java)) {
                    // If the service is running, stop it.
                    stopService(serviceIntent)
                    Toast.makeText(this, "Monitoring stopped.", Toast.LENGTH_SHORT).show()
                } else {
                    // If the service is not running, start it.
                    startMonitoringService(this)
                    Toast.makeText(this, "Monitoring started.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        // Finish the activity immediately since it has no UI
        finish()
    }
}