package com.applocker.ui

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.NumberPicker
import com.applocker.R

class LockSettingsDialog(
    private val context: Context,
    private val appName: String,
    private val onConfirm: (durationMinutes: Int) -> Unit
) {

    fun show() {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_lock_settings, null)

        val hoursPicker = view.findViewById<NumberPicker>(R.id.hours_picker).apply {
            minValue = 0
            maxValue = 23
            value = 0
        }

        val minutesPicker = view.findViewById<NumberPicker>(R.id.minutes_picker).apply {
            minValue = 0
            maxValue = 59
            value = 30
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.lock_settings_title, appName))
            .setView(view)
            .setPositiveButton(R.string.btn_confirm) { _, _ ->
                val totalMinutes = hoursPicker.value * 60 + minutesPicker.value
                onConfirm(totalMinutes)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
