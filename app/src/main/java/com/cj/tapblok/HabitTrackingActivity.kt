package com.cj.tapblok

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cj.tapblok.database.AppDatabase
import com.cj.tapblok.database.SessionHistory
import com.cj.tapblok.ui.theme.TapBlokTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HabitTrackingViewModel(application: Application) : ViewModel() {
    private val sessionHistoryDao = AppDatabase.getDatabase(application).sessionHistoryDao()

    val sessions: StateFlow<List<SessionHistory>> = sessionHistoryDao.getAllSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}

class HabitTrackingViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HabitTrackingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HabitTrackingViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class HabitTrackingActivity : ComponentActivity() {
    private val viewModel: HabitTrackingViewModel by viewModels {
        HabitTrackingViewModelFactory(application)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TapBlokTheme {
                val sessions by viewModel.sessions.collectAsState()
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Session History") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { padding ->
                    HabitTrackingScreen(
                        sessions = sessions,
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }
}

@Composable
fun HabitTrackingScreen(sessions: List<SessionHistory>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (sessions.isEmpty()) {
            item {
                Text(
                    text = "No session history yet. Start a monitoring session to begin tracking your habits!",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(sessions) { session ->
                SessionHistoryItem(session = session)
            }
        }
    }
}

@Composable
fun SessionHistoryItem(session: SessionHistory) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val formattedDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(session.startTime))
            val formattedTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(session.startTime))

            Text(
                text = "$formattedDate at $formattedTime",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val durationString = session.endTime?.let {
                val durationMillis = it - session.startTime
                val hours = TimeUnit.MILLISECONDS.toHours(durationMillis)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60
                when {
                    hours > 0 -> String.format(Locale.getDefault(), "%d hr, %d min", hours, minutes)
                    minutes > 0 -> String.format(Locale.getDefault(), "%d min, %d sec", minutes, seconds)
                    else -> String.format(Locale.getDefault(), "%d sec", seconds)
                }
            } ?: "In Progress"

            Row {
                Text("Duration: ", fontWeight = FontWeight.SemiBold)
                Text(durationString)
            }
            Row {
                Text("Blocked App Attempts: ", fontWeight = FontWeight.SemiBold)
                Text(session.blockedAppAttempts.toString())
            }
        }
    }
}