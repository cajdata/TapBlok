package com.cj.tapblok

import android.app.Application
import com.cj.tapblok.database.AppDatabase

class App : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}