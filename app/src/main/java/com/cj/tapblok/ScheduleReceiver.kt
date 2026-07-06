package com.cj.tapblok

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Handles scheduled-blocking alarms, and doubles as the entry point for external
 * automation apps (Tasker, MacroDroid, Samsung Routines): they can broadcast
 * SCHEDULE_START / SCHEDULE_STOP at this component when the user has enabled
 * "Allow automation apps" in Settings.
 *
 * Note: EXTRA_FROM_ALARM is a routing flag, not a security boundary — this is an
 * open-source self-control app, and anyone determined enough to fake the extra
 * could also just uninstall the app. The automation toggle exists so blocking
 * can't be toggled by other apps unless the user opted in.
 */
class ScheduleReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SCHEDULE_START = "com.cj.tapblok.SCHEDULE_START"
        const val ACTION_SCHEDULE_STOP = "com.cj.tapblok.SCHEDULE_STOP"
        const val EXTRA_FROM_ALARM = "com.cj.tapblok.extra.FROM_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val fromAlarm = intent.getBooleanExtra(EXTRA_FROM_ALARM, false)
        val prefs = AppSettings.prefs(context)

        if (!fromAlarm && !prefs.getBoolean(AppSettings.KEY_EXTERNAL_AUTOMATION, false)) {
            Log.w("ScheduleReceiver", "External trigger ignored — automation is disabled in Settings.")
            return
        }

        when (intent.action) {
            ACTION_SCHEDULE_START -> {
                if (!isServiceRunning(context, AppMonitoringService::class.java)) {
                    Log.d("ScheduleReceiver", "Starting session (${if (fromAlarm) "schedule" else "automation"}).")
                    startMonitoringService(context)
                }
            }
            ACTION_SCHEDULE_STOP -> {
                Log.d("ScheduleReceiver", "Stopping session (${if (fromAlarm) "schedule" else "automation"}).")
                context.stopService(Intent(context, AppMonitoringService::class.java))
            }
            else -> return
        }

        if (fromAlarm) {
            // Chain the next occurrence; alarm fires are one-shot
            ScheduleManager.reschedule(context)
        }
    }
}
