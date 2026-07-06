package com.cj.tapblok

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NfcHandlerActivity : ComponentActivity() {

    companion object {
        private const val STRICT_NOTIFICATION_ID = 2
    }

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

                Log.d("NfcHandlerActivity", "Valid TapBlok NFC tag detected.")
                handleValidTag()
            }
        }
        // Finish the activity immediately since it has no UI
        finish()
    }

    private fun handleValidTag() {
        if (!isServiceRunning(this, AppMonitoringService::class.java)) {
            startMonitoringService(this)
            Toast.makeText(this, "Monitoring started.", Toast.LENGTH_SHORT).show()
            return
        }

        val strictMode = AppSettings.prefs(this).getBoolean(AppSettings.KEY_STRICT_MODE, false)
        val blockedPackage = AppForeground.blockedPackage
        when {
            !strictMode -> stopSession()
            // Scanning on a block screen grants a timed unlock for that app only
            AppForeground.isBlockingVisible() && blockedPackage != null ->
                grantTemporaryUnlock(blockedPackage)
            // Stopping the whole session requires TapBlok itself to be open
            AppForeground.isMainVisible() -> stopSession()
            else -> {
                Toast.makeText(this, "Strict mode: open TapBlok, then scan again to stop.", Toast.LENGTH_LONG).show()
                showStrictModeNotification()
            }
        }
    }

    private fun stopSession() {
        stopService(Intent(this, AppMonitoringService::class.java))
        Toast.makeText(this, "Monitoring stopped.", Toast.LENGTH_SHORT).show()
    }

    private fun grantTemporaryUnlock(blockedPackage: String) {
        val minutes = AppSettings.prefs(this)
            .getInt(AppSettings.KEY_UNLOCK_MINUTES, AppSettings.DEFAULT_UNLOCK_MINUTES)
        val unlockIntent = Intent(this, AppMonitoringService::class.java).apply {
            action = AppMonitoringService.ACTION_UNLOCK_APP
            putExtra(AppMonitoringService.EXTRA_UNLOCK_PACKAGE, blockedPackage)
        }
        startService(unlockIntent)
        Toast.makeText(this, "Unlocked for $minutes minute${if (minutes != 1) "s" else ""}.", Toast.LENGTH_SHORT).show()
        // Bring the unblocked app forward; the block screen closes itself once hidden
        packageManager.getLaunchIntentForPackage(blockedPackage)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        }
    }

    // NotificationManagerCompat.notify silently drops the notification when
    // POST_NOTIFICATIONS isn't granted; the toast above still explains what to do
    @SuppressLint("MissingPermission")
    private fun showStrictModeNotification() {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, AppMonitoringService.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Strict mode is on")
            .setContentText("Open TapBlok, then scan your tag again to stop the session.")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) {
            manager.notify(STRICT_NOTIFICATION_ID, notification)
        }
    }
}
