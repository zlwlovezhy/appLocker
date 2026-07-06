package com.applocker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.applocker.MainActivity
import com.applocker.R
import com.applocker.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LockMonitorService : LifecycleService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val db by lazy { AppDatabase.getInstance(this) }
    private val dao by lazy { db.lockedAppDao() }

    private var lastForegroundPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_text))
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        startMonitoring()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive) {
                try {
                    checkForegroundApp()
                    cleanupExpiredLocks()
                } catch (e: Exception) {
                    Log.e(TAG, "Monitor error", e)
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun checkForegroundApp() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return

        val currentTime = System.currentTimeMillis()
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - 10000,
            currentTime
        )

        val foregroundPackage = usageStats
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName
            ?: return

        if (foregroundPackage == packageName) return

        if (foregroundPackage != lastForegroundPackage) {
            lastForegroundPackage = foregroundPackage
            handleForegroundChange(foregroundPackage)
        }
    }

    private suspend fun handleForegroundChange(packageName: String) {
        val lockedApp = dao.getByPackageName(packageName)

        if (lockedApp != null && lockedApp.isLocked) {
            startLockOverlay(packageName, lockedApp.remainingMillis)
        } else if (lockedApp != null && !lockedApp.isLocked) {
            dao.deleteByPackageName(packageName)
            stopLockOverlay()
        } else {
            stopLockOverlay()
        }
    }

    private fun startLockOverlay(packageName: String, remainingMillis: Long) {
        val intent = Intent(this, LockOverlayService::class.java).apply {
            action = ACTION_SHOW_OVERLAY
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_REMAINING_MILLIS, remainingMillis)
        }
        startService(intent)
    }

    private fun stopLockOverlay() {
        val intent = Intent(this, LockOverlayService::class.java).apply {
            action = ACTION_HIDE_OVERLAY
        }
        startService(intent)
    }

    private suspend fun cleanupExpiredLocks() {
        dao.deleteExpiredLocks()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.monitor_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.monitor_channel_desc)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "LockMonitorService"
        const val CHANNEL_ID = "lock_monitor_channel"
        const val NOTIFICATION_ID = 1001
        private const val CHECK_INTERVAL_MS = 800L

        const val ACTION_SHOW_OVERLAY = "com.applocker.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.applocker.action.HIDE_OVERLAY"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_REMAINING_MILLIS = "extra_remaining_millis"
    }
}
