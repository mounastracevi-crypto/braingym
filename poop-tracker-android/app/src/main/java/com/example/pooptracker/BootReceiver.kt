package com.example.pooptracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        if (ReminderScheduler.isEnabled(context)) {
            ReminderScheduler.scheduleDaily(context)
        }
    }
}
