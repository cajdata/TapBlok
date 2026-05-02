package com.cj.tapblok

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat

class NfcHandlerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("NfcHandlerActivity", "Activity launched by NFC intent.")
        handleNfcIntent()
    }

    private fun handleNfcIntent() {
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            val messages = intent.getParcelableArrayExtraCompat<NdefMessage>(NfcAdapter.EXTRA_NDEF_MESSAGES)
            if (!messages.isNullOrEmpty()) {
                val ndefMessage = messages[0] as NdefMessage
                if (ndefMessage.records.isEmpty()) {
                    Log.w("NfcHandlerActivity", "NFC message has no records.")
                    finish()
                    return
                }
                val record = ndefMessage.records[0]

                if (String(record.type, Charsets.UTF_8) != NfcWriteActivity.NFC_MIME_TYPE) {
                    Log.w("NfcHandlerActivity", "Ignoring NFC tag with unexpected MIME type.")
                    finish()
                    return
                }

                val payload = String(record.payload, Charsets.UTF_8)
                val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val isStrictMode = prefs.getBoolean("is_strict_mode", false)
                val isRunning = isServiceRunning(this, AppMonitoringService::class.java)

                Log.d("NfcHandlerActivity", "Valid TapBlok NFC tag detected: $payload")

                val isToggle = payload == "TAPBLOK_TOGGLE"
                val isUnlock = payload.startsWith("TAPBLOK_UNLOCK:")

                if (isToggle || isUnlock) {
                    if (!isStrictMode) {
                        // Chill Mode: Always toggle
                        val serviceIntent = Intent(this, AppMonitoringService::class.java)
                        if (isRunning) {
                            stopService(serviceIntent)
                            Toast.makeText(this, "Monitoring stopped.", Toast.LENGTH_SHORT).show()
                        } else {
                            startMonitoringService(this)
                            Toast.makeText(this, "Monitoring started.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Strict Mode
                        if (isRunning) {
                            // Service is running, can't stop from background in Strict Mode
                            showStrictWarningNotification()
                            Toast.makeText(this, "Strict Mode: Open TapBlok to scan and stop.", Toast.LENGTH_LONG).show()
                        } else {
                            // Starting monitoring is always allowed
                            startMonitoringService(this)
                            Toast.makeText(this, "Monitoring started.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        finish()
    }

    private fun showStrictWarningNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "tapblok_alerts"
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Strict Mode Active")
            .setContentText("Tap to open TapBlok and scan your tag to stop monitoring.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}
