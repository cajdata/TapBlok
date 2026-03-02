package com.cj.tapblok

import android.app.Activity
import android.content.Context
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
        if (NfcAdapter.ACTION_NDEF_DISCOVERED != intent.action) {
            Log.w("NfcHandlerActivity", "Unexpected intent action: ${intent.action}")
            finish()
            return
        }

        val messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        if (messages.isNullOrEmpty()) {
            Log.w("NfcHandlerActivity", "No NDEF messages found in intent.")
            Toast.makeText(this, "Could not read NFC tag.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val ndefMessage = messages[0] as? NdefMessage
        val record = ndefMessage?.records?.firstOrNull()
        if (record == null) {
            Log.w("NfcHandlerActivity", "NDEF message had no records.")
            Toast.makeText(this, "NFC tag appears to be empty.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Correctly decode MIME payload, trimming any stray control-char prefixes
        val payload = String(record.payload, Charsets.UTF_8).trimStart { it < ' ' }
        Log.d("NfcHandlerActivity", "NFC Tag Payload: $payload")

        val serviceIntent = Intent(this, AppMonitoringService::class.java)

        // Use SharedPreferences flag instead of deprecated getRunningServices()
        val prefs = getSharedPreferences(AppMonitoringService.PREFS_NAME, Context.MODE_PRIVATE)
        val isRunning = prefs.getBoolean("service_running", false)

        if (isRunning) {
            stopService(serviceIntent)
            prefs.edit().putBoolean("service_running", false).apply()
            Toast.makeText(this, "Monitoring stopped.", Toast.LENGTH_SHORT).show()
        } else {
            startForegroundService(serviceIntent)
            prefs.edit().putBoolean("service_running", true).apply()
            Toast.makeText(this, "Monitoring started.", Toast.LENGTH_SHORT).show()
        }

        finish()
    }
}
