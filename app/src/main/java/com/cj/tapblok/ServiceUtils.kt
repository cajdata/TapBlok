package com.cj.tapblok

import android.app.ActivityManager
import android.content.Context
import android.app.Service

// A helper function to check if a service is currently running
fun isServiceRunning(context: Context, serviceClass: Class<out Service>): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    @Suppress("DEPRECATION") // Necessary for older API levels
    for (service in manager.getRunningServices(Int.MAX_VALUE)) {
        if (serviceClass.name == service.service.className) {
            return true
        }
    }
    return false
}