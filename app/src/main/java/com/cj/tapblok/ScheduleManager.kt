package com.cj.tapblok

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar

/**
 * Schedules the start/stop alarms for scheduled blocking sessions. All state lives in
 * SharedPreferences (see AppSettings); reschedule() is idempotent and recomputes both
 * alarms from scratch, so it is safe to call from settings changes, boot, and alarm fires.
 */
object ScheduleManager {

    private const val TAG = "ScheduleManager"
    private const val REQUEST_START = 100
    private const val REQUEST_STOP = 101
    private const val MINUTES_PER_DAY = 24 * 60

    /**
     * @param startIfInWindow when true and "now" falls inside an active schedule window,
     * the session starts immediately (used on settings save and boot so a missed start
     * alarm still results in blocking). Alarm-fire paths pass false so a manual stop
     * during a window isn't immediately re-started.
     */
    fun reschedule(context: Context, startIfInWindow: Boolean = false) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val startPi = pendingIntent(context, REQUEST_START, ScheduleReceiver.ACTION_SCHEDULE_START)
        val stopPi = pendingIntent(context, REQUEST_STOP, ScheduleReceiver.ACTION_SCHEDULE_STOP)
        alarmManager.cancel(startPi)
        alarmManager.cancel(stopPi)

        val prefs = AppSettings.prefs(context)
        if (!prefs.getBoolean(AppSettings.KEY_SCHEDULE_ENABLED, false)) return
        val daysMask = prefs.getInt(AppSettings.KEY_SCHEDULE_DAYS, AppSettings.DEFAULT_DAYS_MASK)
        if (daysMask == 0) return

        val startMinutes = prefs.getInt(AppSettings.KEY_SCHEDULE_START_MINUTES, AppSettings.DEFAULT_START_MINUTES)
        val stopEnabled = prefs.getBoolean(AppSettings.KEY_SCHEDULE_STOP_ENABLED, true)
        val stopMinutes = prefs.getInt(AppSettings.KEY_SCHEDULE_STOP_MINUTES, AppSettings.DEFAULT_STOP_MINUTES)
        // A stop time equal to the start time would make a zero-length window; treat it
        // as no auto-stop rather than guessing 24h
        val windowMinutes = if (stopEnabled) ((stopMinutes - startMinutes) + MINUTES_PER_DAY) % MINUTES_PER_DAY else 0

        val now = System.currentTimeMillis()

        val currentWindowStart = currentWindowStartMillis(now, daysMask, startMinutes, windowMinutes)
        if (currentWindowStart != null) {
            if (startIfInWindow && !isServiceRunning(context, AppMonitoringService::class.java)) {
                Log.d(TAG, "Inside a scheduled window — starting session now.")
                startMonitoringService(context)
            }
            setAlarm(context, alarmManager, stopPi, currentWindowStart + windowMinutes * 60_000L)
        }

        val nextStart = nextStartMillis(now, daysMask, startMinutes)
        if (nextStart != null) {
            setAlarm(context, alarmManager, startPi, nextStart)
            Log.d(TAG, "Next scheduled start: ${java.util.Date(nextStart)}")
        }
    }

    private fun pendingIntent(context: Context, requestCode: Int, action: String): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java)
            .setAction(action)
            .putExtra(ScheduleReceiver.EXTRA_FROM_ALARM, true)
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setAlarm(context: Context, alarmManager: AlarmManager, operation: PendingIntent, triggerAt: Long) {
        try {
            // setAlarmClock fires exactly even in Doze/battery saver, and puts the app on
            // the temporary allowlist so the receiver may start the foreground service
            val showIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), operation)
        } catch (e: SecurityException) {
            // Exact-alarm permission revoked by the user; an inexact alarm beats none
            Log.w(TAG, "Exact alarm not permitted, falling back to inexact.", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    // Bit 0 = Monday … bit 6 = Sunday; Calendar.DAY_OF_WEEK has Sunday=1 … Saturday=7
    private fun dayBit(calendar: Calendar): Int = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7

    private fun dayEnabled(calendar: Calendar, daysMask: Int): Boolean =
        daysMask and (1 shl dayBit(calendar)) != 0

    private fun atMinutesOfDay(base: Calendar, minutesOfDay: Int): Calendar =
        (base.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
            set(Calendar.MINUTE, minutesOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun nextStartMillis(now: Long, daysMask: Int, startMinutes: Int): Long? {
        val base = Calendar.getInstance().apply { timeInMillis = now }
        for (dayOffset in 0..7) {
            val candidate = atMinutesOfDay(base, startMinutes).apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
            if (candidate.timeInMillis > now && dayEnabled(candidate, daysMask)) {
                return candidate.timeInMillis
            }
        }
        return null
    }

    // Windows are anchored to their start day and never exceed 24h, so only today's and
    // yesterday's start times can contain "now" (yesterday's covers overnight windows)
    private fun currentWindowStartMillis(now: Long, daysMask: Int, startMinutes: Int, windowMinutes: Int): Long? {
        if (windowMinutes <= 0) return null
        val base = Calendar.getInstance().apply { timeInMillis = now }
        for (dayOffset in 0 downTo -1) {
            val candidate = atMinutesOfDay(base, startMinutes).apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
            val start = candidate.timeInMillis
            if (dayEnabled(candidate, daysMask) && now >= start && now < start + windowMinutes * 60_000L) {
                return start
            }
        }
        return null
    }
}
