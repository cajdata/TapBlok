package com.cj.tapblok

import android.content.Context
import android.content.Intent
import android.app.Service
import android.os.Build

fun isServiceRunning(@Suppress("UNUSED_PARAMETER") context: Context, serviceClass: Class<out Service>): Boolean {
    return serviceClass == AppMonitoringService::class.java && AppMonitoringService.isRunning
}

fun startMonitoringService(context: Context) {
    val intent = Intent(context, AppMonitoringService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}
