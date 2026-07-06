package com.applocker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.applocker.MainActivity
import com.applocker.R

class LockOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var countDownTimer: CountDownTimer? = null
    private var remainingText: TextView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            LockMonitorService.ACTION_SHOW_OVERLAY -> {
                val packageName = intent.getStringExtra(LockMonitorService.EXTRA_PACKAGE_NAME) ?: ""
                val remainingMillis = intent.getLongExtra(LockMonitorService.EXTRA_REMAINING_MILLIS, 0)
                showOverlay(packageName, remainingMillis)
            }
            LockMonitorService.ACTION_HIDE_OVERLAY -> {
                hideOverlay()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay(packageName: String, remainingMillis: Long) {
        hideOverlay()

        val pm = packageManager
        val appInfo = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (e: Exception) {
            null
        }
        val appName = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: packageName
        val appIcon = appInfo?.let { pm.getApplicationIcon(packageName) }

        createNotification()

        overlayView = createOverlayView(appName, appIcon, remainingMillis)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun createOverlayView(appName: String, appIcon: android.graphics.drawable.Drawable?, remainingMillis: Long): View {
        val context = this
        val density = resources.displayMetrics.density

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xE6000000.toInt())
        }

        val iconView = ImageView(context).apply {
            if (appIcon != null) {
                setImageDrawable(appIcon)
            } else {
                setImageResource(R.drawable.ic_shield)
            }
            val size = (80 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                bottomMargin = (24 * density).toInt()
            }
        }

        val titleText = TextView(context).apply {
            text = getString(R.string.lock_overlay_title, appName)
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * density).toInt()
            }
        }

        val subtitleText = TextView(context).apply {
            text = getString(R.string.lock_overlay_subtitle)
            textSize = 14f
            setTextColor(0xCCFFFFFF.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (32 * density).toInt()
            }
        }

        remainingText = TextView(context).apply {
            text = formatRemainingTime(remainingMillis)
            textSize = 36f
            setTextColor(0xFFFF6B35.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (48 * density).toInt()
            }
        }

        val backButton = Button(context).apply {
            text = getString(R.string.lock_overlay_back)
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF4A90D9.toInt())
            val paddingH = (32 * density).toInt()
            val paddingV = (12 * density).toInt()
            setPadding(paddingH, paddingV, paddingH, paddingV)
            setOnClickListener {
                hideOverlay()
            }
        }

        rootLayout.addView(iconView)
        rootLayout.addView(titleText)
        rootLayout.addView(subtitleText)
        rootLayout.addView(remainingText)
        rootLayout.addView(backButton)

        startCountdown(remainingMillis)

        return rootLayout
    }

    private fun startCountdown(remainingMillis: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(remainingMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingText?.text = formatRemainingTime(millisUntilFinished)
            }

            override fun onFinish() {
                hideOverlay()
            }
        }.start()
    }

    private fun hideOverlay() {
        countDownTimer?.cancel()
        countDownTimer = null
        try {
            if (overlayView != null && windowManager != null) {
                windowManager?.removeView(overlayView)
            }
        } catch (e: Exception) {
            // view already removed
        }
        overlayView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification() {
        val channel = NotificationChannel(
            OVERLAY_CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, OVERLAY_CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(OVERLAY_NOTIFICATION_ID, notification)
    }

    private fun formatRemainingTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    companion object {
        private const val OVERLAY_CHANNEL_ID = "lock_overlay_channel"
        private const val OVERLAY_NOTIFICATION_ID = 1002
    }
}
