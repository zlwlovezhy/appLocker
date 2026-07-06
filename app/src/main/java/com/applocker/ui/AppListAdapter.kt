package com.applocker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.applocker.R
import com.applocker.databinding.ItemAppBinding
import com.applocker.util.AppInfo

class AppListAdapter(
    private val onLockClick: (AppInfo) -> Unit,
    private val onUnlockClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppListAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = getItem(position)
        holder.binding.apply {
            appIcon.setImageDrawable(app.icon)
            appName.text = app.appName

            if (app.isLocked) {
                lockStatus.text = formatRemaining(app.remainingMillis)
                lockStatus.setTextColor(
                    root.context.getColor(R.color.locked_text)
                )
                lockActionButton.text = root.context.getString(R.string.btn_unlock)
                lockActionButton.setBackgroundColor(
                    root.context.getColor(R.color.unlock_button)
                )
                lockActionButton.setOnClickListener {
                    onUnlockClick(app)
                }
                root.alpha = 0.6f
            } else {
                lockStatus.text = root.context.getString(R.string.status_unlocked)
                lockStatus.setTextColor(
                    root.context.getColor(R.color.unlocked_text)
                )
                lockActionButton.text = root.context.getString(R.string.btn_set_lock)
                lockActionButton.setBackgroundColor(
                    root.context.getColor(R.color.lock_button)
                )
                lockActionButton.setOnClickListener {
                    onLockClick(app)
                }
                root.alpha = 1.0f
            }
        }
    }

    private fun formatRemaining(millis: Long): String {
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

    class DiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem == newItem
        }
    }
}
