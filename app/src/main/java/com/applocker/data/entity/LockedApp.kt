package com.applocker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locked_apps")
data class LockedApp(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val lockEndTimeMillis: Long
) {
    val isLocked: Boolean
        get() = System.currentTimeMillis() < lockEndTimeMillis

    val remainingMillis: Long
        get() = (lockEndTimeMillis - System.currentTimeMillis()).coerceAtLeast(0)
}
