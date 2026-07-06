package com.applocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.applocker.service.LockMonitorService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, LockMonitorService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
