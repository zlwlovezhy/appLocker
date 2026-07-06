package com.applocker

import android.app.Application
import android.content.Intent
import com.applocker.data.AppDatabase
import com.applocker.service.LockMonitorService

class AppLockerApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
    }

    fun startMonitoringService() {
        val intent = Intent(this, LockMonitorService::class.java)
        startForegroundService(intent)
    }

    fun stopMonitoringService() {
        val intent = Intent(this, LockMonitorService::class.java)
        stopService(intent)
    }
}
