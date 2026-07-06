package com.applocker

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.applocker.databinding.ActivityMainBinding
import com.applocker.ui.AppListAdapter
import com.applocker.ui.LockSettingsDialog
import com.applocker.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: AppListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = MainViewModel(this)

        setupRecyclerView()
        observeViewModel()

        checkAndRequestPermissions()
    }

    private fun setupRecyclerView() {
        adapter = AppListAdapter(
            onLockClick = { app ->
                showLockSettingsDialog(app)
            },
            onUnlockClick = { app ->
                showUnlockConfirmDialog(app)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshApps()
        }
    }

    private fun observeViewModel() {
        viewModel.apps.observe(this) { apps ->
            adapter.submitList(apps)
            binding.swipeRefresh.isRefreshing = false

            val lockedCount = apps.count { it.isLocked }
            binding.lockedCountText.text = if (lockedCount > 0) {
                getString(R.string.locked_count, lockedCount)
            } else {
                getString(R.string.no_locked_apps)
            }
        }

        viewModel.hasAnyLocked.observe(this) { hasLocked ->
            binding.btnResetAll.isEnabled = hasLocked
            binding.btnResetAll.alpha = if (hasLocked) 1.0f else 0.5f
        }

        binding.btnResetAll.setOnClickListener {
            showResetAllConfirmDialog()
        }

        lifecycleScope.launch {
            viewModel.apps.collect { /* trigger initial load */ }
        }
    }

    private fun showLockSettingsDialog(app: com.applocker.util.AppInfo) {
        LockSettingsDialog(
            context = this,
            appName = app.appName,
            onConfirm = { durationMinutes ->
                if (durationMinutes <= 0) {
                    Toast.makeText(this, R.string.invalid_duration, Toast.LENGTH_SHORT).show()
                    return@LockSettingsDialog
                }
                viewModel.setAppLock(app.packageName, app.appName, durationMinutes)
                viewModel.startMonitoring()
                Toast.makeText(
                    this,
                    getString(R.string.lock_set_success, app.appName, durationMinutes),
                    Toast.LENGTH_SHORT
                ).show()
            }
        ).show()
    }

    private fun showUnlockConfirmDialog(app: com.applocker.util.AppInfo) {
        AlertDialog.Builder(this)
            .setTitle(R.string.unlock_confirm_title)
            .setMessage(getString(R.string.unlock_confirm_message, app.appName))
            .setPositiveButton(R.string.btn_confirm) { _, _ ->
                viewModel.unlockApp(app.packageName)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showResetAllConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_all_title)
            .setMessage(R.string.reset_all_message)
            .setPositiveButton(R.string.btn_confirm) { _, _ ->
                lifecycleScope.launch {
                    viewModel.apps.value.filter { it.isLocked }.forEach { app ->
                        viewModel.unlockApp(app.packageName)
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun checkAndRequestPermissions() {
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
        } else if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        } else {
            viewModel.startMonitoring()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (appOps == null) return false

        val currentTime = System.currentTimeMillis()
        val stats = try {
            appOps.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                currentTime - 1000 * 60,
                currentTime
            )
        } catch (e: Exception) {
            return false
        }
        return stats.isNotEmpty()
    }

    private fun requestUsageStatsPermission() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_usage_title)
            .setMessage(R.string.permission_usage_message)
            .setPositiveButton(R.string.btn_go_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                checkAndRequestPermissions()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestOverlayPermission() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_overlay_title)
            .setMessage(R.string.permission_overlay_message)
            .setPositiveButton(R.string.btn_go_settings) { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                checkAndRequestPermissions()
            }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (hasUsageStatsPermission() && Settings.canDrawOverlays(this)) {
            viewModel.startMonitoring()
        } else {
            checkAndRequestPermissions()
        }
    }
}
