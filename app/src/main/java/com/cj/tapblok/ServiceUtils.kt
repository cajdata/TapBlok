package com.cj.tapblok

import android.content.Context
import android.app.Service

fun isServiceRunning(@Suppress("UNUSED_PARAMETER") context: Context, serviceClass: Class<out Service>): Boolean {
    return serviceClass == AppMonitoringService::class.java && AppMonitoringService.isRunning
}
