package com.cj.tapblok

import android.app.Application
import android.content.Intent
// Unused import 'android.content.pm.PackageManager' has been removed.
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
// --- START OF CHANGES ---
// Use the new AutoMirrored version of ArrowBack to resolve the deprecation warning.
import androidx.compose.material.icons.automirrored.filled.ArrowBack
// --- END OF CHANGES ---
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.compose.rememberAsyncImagePainter
import com.cj.tapblok.database.BlockedApp
import com.cj.tapblok.database.BlockedAppDao
import com.cj.tapblok.ui.theme.TapBlokTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable?,
    var isSelected: Boolean = false
)

class AppSelectionViewModel(private val blockedAppDao: BlockedAppDao, private val application: Application) : ViewModel() {
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = application.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val allApps = pm.queryIntentActivities(intent, 0)

            val excludedPackages = setOf(
                "com.google.android.dialer",
                "com.samsung.android.dialer",
                "com.android.dialer",
                "com.sonyericsson.android.socialphonebook",
                "com.android.systemui",
                "com.google.android.apps.nexuslauncher",
                "com.sec.android.app.launcher",
                "com.android.settings",
                "com.google.android.packageinstaller",
                "com.android.packageinstaller",
                "com.google.android.apps.messaging",
                "com.samsung.android.messaging"
            )

            blockedAppDao.getAllBlockedApps().collect { blockedApps ->
                val blockedAppPackages = blockedApps.map { it.packageName }.toSet()
                val appList = allApps.mapNotNull { app ->
                    val packageName = app.activityInfo.packageName
                    if (packageName == application.packageName || excludedPackages.contains(packageName)) {
                        return@mapNotNull null
                    }
                    AppInfo(
                        appName = app.loadLabel(pm).toString(),
                        packageName = packageName,
                        icon = app.loadIcon(pm),
                        isSelected = blockedAppPackages.contains(packageName)
                    )
                }.sortedBy { it.appName.lowercase() }

                _apps.value = appList
            }
        }
    }

    fun onAppSelectionChanged(app: AppInfo, isSelected: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isSelected) {
                blockedAppDao.insert(BlockedApp(packageName = app.packageName))
            } else {
                blockedAppDao.delete(BlockedApp(packageName = app.packageName))
            }
        }
    }

    fun selectAllApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val allAppPackages = apps.value.map { BlockedApp(it.packageName) }
            blockedAppDao.insertAll(allAppPackages)
        }
    }

    fun unselectAllApps() {
        viewModelScope.launch(Dispatchers.IO) {
            blockedAppDao.deleteAll()
        }
    }
}

class AppSelectionViewModelFactory(private val application: Application, private val blockedAppDao: BlockedAppDao) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppSelectionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppSelectionViewModel(blockedAppDao, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class AppSelectionActivity : ComponentActivity() {
    private val viewModel: AppSelectionViewModel by viewModels {
        AppSelectionViewModelFactory(
            application,
            (application as App).database.blockedAppDao()
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TapBlokTheme {
                val appList by viewModel.apps.collectAsState()
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Select Apps to Block") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    // --- START OF CHANGES ---
                                    // Using the new AutoMirrored icon
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    // --- END OF CHANGES ---
                                }
                            },
                            actions = {
                                TextButton(onClick = { viewModel.selectAllApps() }) {
                                    Text("Select All")
                                }
                                TextButton(onClick = { viewModel.unselectAllApps() }) {
                                    Text("Unselect All")
                                }
                            }
                        )
                    }
                ) { padding ->
                    AppSelectionScreen(
                        apps = appList,
                        onAppCheckedChange = { app, isSelected ->
                            viewModel.onAppSelectionChanged(app, isSelected)
                        },
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }
}

@Composable
fun AppSelectionScreen(
    apps: List<AppInfo>,
    onAppCheckedChange: (AppInfo, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isServiceRunning by rememberUpdatedState(
        isServiceRunning(context, AppMonitoringService::class.java)
    )

    LazyColumn(modifier = modifier.padding(all = 8.dp)) {
        items(apps, key = { it.packageName }) { app ->
            AppListItem(
                app = app,
                onCheckedChange = { isSelected ->
                    onAppCheckedChange(app, isSelected)
                },
                isEnabled = !isServiceRunning
            )
        }
    }
}

@Composable
fun AppListItem(
    app: AppInfo,
    onCheckedChange: (Boolean) -> Unit,
    isEnabled: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isEnabled) { onCheckedChange(!app.isSelected) }
            .padding(vertical = 8.dp, horizontal = 8.dp)
    ) {
        Image(
            painter = rememberAsyncImagePainter(model = app.icon),
            contentDescription = "${app.appName} icon",
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = app.appName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Checkbox(
            checked = app.isSelected,
            onCheckedChange = onCheckedChange,
            enabled = isEnabled
        )
    }
}

