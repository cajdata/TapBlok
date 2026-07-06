package com.cj.tapblok

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.cj.tapblok.ui.theme.TapBlokTheme
import java.util.Locale

object AppSettings {
    const val PREFS_NAME = "app_prefs"

    const val KEY_OVERRIDE_ENABLED = "override_enabled"
    const val KEY_OVERRIDE_SECONDS = "override_seconds"
    const val KEY_BREAKS_ALLOWED = "breaks_allowed"
    const val KEY_STRICT_MODE = "strict_mode_enabled"
    const val KEY_UNLOCK_MINUTES = "strict_unlock_minutes"
    const val KEY_QR_TOKEN = "qr_token"
    const val KEY_EXTERNAL_AUTOMATION = "external_automation_enabled"
    const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
    const val KEY_SCHEDULE_START_MINUTES = "schedule_start_minutes"
    const val KEY_SCHEDULE_STOP_ENABLED = "schedule_stop_enabled"
    const val KEY_SCHEDULE_STOP_MINUTES = "schedule_stop_minutes"
    // Bit 0 = Monday … bit 6 = Sunday
    const val KEY_SCHEDULE_DAYS = "schedule_days"

    const val DEFAULT_OVERRIDE_SECONDS = 90
    const val DEFAULT_BREAKS_ALLOWED = 3
    const val DEFAULT_UNLOCK_MINUTES = 5
    const val DEFAULT_START_MINUTES = 22 * 60
    const val DEFAULT_STOP_MINUTES = 7 * 60
    const val DEFAULT_DAYS_MASK = 0b1111111

    fun prefs(context: Context): android.content.SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun formatMinutesOfDay(minutesOfDay: Int): String =
        String.format(Locale.US, "%02d:%02d", minutesOfDay / 60, minutesOfDay % 60)
}

class SettingsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TapBlokTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Settings") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { padding ->
                    SettingsScreen(modifier = Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { AppSettings.prefs(context) }

    var isServiceActive by remember { mutableStateOf(isServiceRunning(context, AppMonitoringService::class.java)) }

    var strictMode by remember { mutableStateOf(prefs.getBoolean(AppSettings.KEY_STRICT_MODE, false)) }
    var unlockMinutes by remember { mutableStateOf(prefs.getInt(AppSettings.KEY_UNLOCK_MINUTES, AppSettings.DEFAULT_UNLOCK_MINUTES)) }
    var overrideEnabled by remember { mutableStateOf(prefs.getBoolean(AppSettings.KEY_OVERRIDE_ENABLED, true)) }
    var overrideSeconds by remember { mutableStateOf(prefs.getInt(AppSettings.KEY_OVERRIDE_SECONDS, AppSettings.DEFAULT_OVERRIDE_SECONDS)) }
    var breaksAllowed by remember { mutableStateOf(prefs.getInt(AppSettings.KEY_BREAKS_ALLOWED, AppSettings.DEFAULT_BREAKS_ALLOWED)) }
    var externalAutomation by remember { mutableStateOf(prefs.getBoolean(AppSettings.KEY_EXTERNAL_AUTOMATION, false)) }
    var scheduleEnabled by remember { mutableStateOf(prefs.getBoolean(AppSettings.KEY_SCHEDULE_ENABLED, false)) }
    var startMinutes by remember { mutableStateOf(prefs.getInt(AppSettings.KEY_SCHEDULE_START_MINUTES, AppSettings.DEFAULT_START_MINUTES)) }
    var stopEnabled by remember { mutableStateOf(prefs.getBoolean(AppSettings.KEY_SCHEDULE_STOP_ENABLED, true)) }
    var stopMinutes by remember { mutableStateOf(prefs.getInt(AppSettings.KEY_SCHEDULE_STOP_MINUTES, AppSettings.DEFAULT_STOP_MINUTES)) }
    var daysMask by remember { mutableStateOf(prefs.getInt(AppSettings.KEY_SCHEDULE_DAYS, AppSettings.DEFAULT_DAYS_MASK)) }

    // All inputs lock while a session runs so settings can't be loosened mid-session
    val editable = !isServiceActive

    fun rescheduleAlarms() = ScheduleManager.reschedule(context, startIfInWindow = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isServiceActive) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Settings are locked while a session is active.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        SettingsSection(title = "Strict Mode") {
            SettingsSwitchRow(
                label = "Strict mode",
                caption = "A session can only be stopped by scanning with TapBlok open. " +
                        "Scanning on a block screen unlocks just that app, temporarily.",
                checked = strictMode,
                enabled = editable,
                onCheckedChange = {
                    strictMode = it
                    prefs.edit { putBoolean(AppSettings.KEY_STRICT_MODE, it) }
                }
            )
            if (strictMode) {
                Text(
                    text = "Temporary unlock duration",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "1m", 5 to "5m", 10 to "10m", 30 to "30m").forEach { (minutes, label) ->
                        FilterChip(
                            selected = unlockMinutes == minutes,
                            enabled = editable,
                            onClick = {
                                unlockMinutes = minutes
                                prefs.edit { putInt(AppSettings.KEY_UNLOCK_MINUTES, minutes) }
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Text(
                    text = "For maximum friction, combine with zero breaks and a disabled emergency override.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SettingsSection(title = "Emergency Override") {
            SettingsSwitchRow(
                label = "Enable emergency override",
                caption = "Hold-to-stop button shown during a session",
                checked = overrideEnabled,
                enabled = editable,
                onCheckedChange = {
                    overrideEnabled = it
                    prefs.edit { putBoolean(AppSettings.KEY_OVERRIDE_ENABLED, it) }
                }
            )
            if (overrideEnabled) {
                Text(
                    text = "Hold duration",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30 to "30s", 90 to "90s", 300 to "5m", 900 to "15m").forEach { (seconds, label) ->
                        FilterChip(
                            selected = overrideSeconds == seconds,
                            enabled = editable,
                            onClick = {
                                overrideSeconds = seconds
                                prefs.edit { putInt(AppSettings.KEY_OVERRIDE_SECONDS, seconds) }
                            },
                            label = { Text(label) }
                        )
                    }
                }
            } else {
                Text(
                    text = "With the override disabled, only your NFC tag or QR code can stop a session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        SettingsSection(title = "Breaks") {
            Text(
                text = "Breaks allowed per session",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "None", 1 to "1", 3 to "3", 5 to "5").forEach { (count, label) ->
                    FilterChip(
                        selected = breaksAllowed == count,
                        enabled = editable,
                        onClick = {
                            breaksAllowed = count
                            prefs.edit { putInt(AppSettings.KEY_BREAKS_ALLOWED, count) }
                        },
                        label = { Text(label) }
                    )
                }
            }
        }

        SettingsSection(title = "Scheduled Blocking") {
            SettingsSwitchRow(
                label = "Start sessions on a schedule",
                caption = "Blocking begins automatically, no tag needed",
                checked = scheduleEnabled,
                enabled = editable,
                onCheckedChange = {
                    scheduleEnabled = it
                    prefs.edit { putBoolean(AppSettings.KEY_SCHEDULE_ENABLED, it) }
                    rescheduleAlarms()
                }
            )
            if (scheduleEnabled) {
                TimeRow(
                    label = "Start time",
                    minutesOfDay = startMinutes,
                    enabled = editable,
                    onTimePicked = {
                        startMinutes = it
                        prefs.edit { putInt(AppSettings.KEY_SCHEDULE_START_MINUTES, it) }
                        rescheduleAlarms()
                    }
                )
                SettingsSwitchRow(
                    label = "Auto-stop",
                    caption = "End the session automatically",
                    checked = stopEnabled,
                    enabled = editable,
                    onCheckedChange = {
                        stopEnabled = it
                        prefs.edit { putBoolean(AppSettings.KEY_SCHEDULE_STOP_ENABLED, it) }
                        rescheduleAlarms()
                    }
                )
                if (stopEnabled) {
                    TimeRow(
                        label = "Stop time",
                        minutesOfDay = stopMinutes,
                        enabled = editable,
                        onTimePicked = {
                            stopMinutes = it
                            prefs.edit { putInt(AppSettings.KEY_SCHEDULE_STOP_MINUTES, it) }
                            rescheduleAlarms()
                        }
                    )
                }
                Text(
                    text = "Days",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
                    dayLabels.forEachIndexed { index, label ->
                        val bit = 1 shl index
                        FilterChip(
                            selected = daysMask and bit != 0,
                            enabled = editable,
                            onClick = {
                                daysMask = daysMask xor bit
                                prefs.edit { putInt(AppSettings.KEY_SCHEDULE_DAYS, daysMask) }
                                rescheduleAlarms()
                            },
                            label = { Text(label) }
                        )
                    }
                }
                if (daysMask == 0) {
                    Text(
                        text = "Select at least one day for the schedule to run.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        SettingsSection(title = "Automation") {
            SettingsSwitchRow(
                label = "Allow automation apps",
                caption = "Tasker, MacroDroid, Samsung Routines and similar apps can start or stop " +
                        "sessions by broadcasting com.cj.tapblok.SCHEDULE_START or SCHEDULE_STOP",
                checked = externalAutomation,
                enabled = editable,
                onCheckedChange = {
                    externalAutomation = it
                    prefs.edit { putBoolean(AppSettings.KEY_EXTERNAL_AUTOMATION, it) }
                }
            )
        }
    }

    // Re-check the lock when returning to the screen (e.g. a scheduled session started)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isServiceActive = isServiceRunning(context, AppMonitoringService::class.java)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp)
        )
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    caption: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun TimeRow(
    label: String,
    minutesOfDay: Int,
    enabled: Boolean,
    onTimePicked: (Int) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onTimePicked(hour * 60 + minute) },
                    minutesOfDay / 60,
                    minutesOfDay % 60,
                    true
                ).show()
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = AppSettings.formatMinutesOfDay(minutesOfDay),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}
