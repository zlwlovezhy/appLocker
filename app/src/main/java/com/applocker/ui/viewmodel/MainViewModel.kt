package com.applocker.ui.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.applocker.AppLockerApp
import com.applocker.data.entity.LockedApp
import com.applocker.util.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AppLockerApp
    private val dao = app.database.lockedAppDao()
    private val pm = application.packageManager

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _hasAnyLocked = MutableStateFlow(false)
    val hasAnyLocked: StateFlow<Boolean> = _hasAnyLocked.asStateFlow()

    private var lockedAppsMap: Map<String, LockedApp> = emptyMap()

    init {
        loadInstalledApps()
        observeLockedApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val installedApps = withContext(Dispatchers.IO) {
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                packages
                    .filter { it.packageName != app.packageName }
                    .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                    .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
                    .map { appInfo ->
                        AppInfo(
                            packageName = appInfo.packageName,
                            appName = pm.getApplicationLabel(appInfo).toString(),
                            icon = pm.getApplicationIcon(appInfo)
                        )
                    }
            }
            _apps.value = mergeWithLockStatus(installedApps)
        }
    }

    private fun observeLockedApps() {
        viewModelScope.launch {
            dao.getAllLockedApps().collect { lockedApps ->
                lockedAppsMap = lockedApps.associateBy { it.packageName }
                _apps.value = mergeWithLockStatus(_apps.value)
                _hasAnyLocked.value = lockedApps.any { it.isLocked }
            }
        }
    }

    private fun mergeWithLockStatus(apps: List<AppInfo>): List<AppInfo> {
        return apps.map { app ->
            val lockedApp = lockedAppsMap[app.packageName]
            if (lockedApp != null && lockedApp.isLocked) {
                app.copy(isLocked = true, remainingMillis = lockedApp.remainingMillis)
            } else {
                app.copy(isLocked = false, remainingMillis = 0)
            }
        }
    }

    fun setAppLock(packageName: String, appName: String, durationMinutes: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val lockEndTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
                val lockedApp = LockedApp(
                    packageName = packageName,
                    appName = appName,
                    lockEndTimeMillis = lockEndTime
                )
                dao.upsert(lockedApp)
            }
        }
    }

    fun unlockApp(packageName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.deleteByPackageName(packageName)
            }
        }
    }

    fun startMonitoring() {
        app.startMonitoringService()
    }

    fun refreshApps() {
        loadInstalledApps()
    }
}
