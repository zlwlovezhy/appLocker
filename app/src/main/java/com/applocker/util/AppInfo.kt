package com.applocker.util

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isLocked: Boolean = false,
    val remainingMillis: Long = 0
)
