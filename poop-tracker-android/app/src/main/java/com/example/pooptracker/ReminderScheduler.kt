package com.example.pooptracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object ReminderScheduler {

    private const val PREFS_NAME = "poop_tracker_prefs"
    private const val KEY_REMINDER_ENABLED = "reminder_enabled"
    private const val KEY_REMINDER_HOUR = "reminder_hour"
    private const val KEY_REMINDER_MINUTE = "reminder_minute"

    private const val DEFAULT_REMINDER_HOUR = 20
    private const val DEFAULT_REMINDER_MINUTE = 0
    private const val REMINDER_REQUEST_CODE = 1001

    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_REMINDER_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()
    }

    fun getReminderTime(context: Context): Pair<Int, Int> {
        val prefs = prefs(context)
        val hour = prefs.getInt(KEY_REMINDER_HOUR, DEFAULT_REMINDER_HOUR)
        val minute = prefs.getInt(KEY_REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE)
        return hour to minute
    }

    fun saveReminderTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit()
            .putInt(KEY_REMINDER_HOUR, hour)
            .putInt(KEY_REMINDER_MINUTE, minute)
            .apply()
    }

    fun scheduleDaily(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = reminderPendingIntent(context)
        val (hour, minute) = getReminderTime(context)
        val triggerAtMillis = nextTriggerMillis(hour, minute)

        alarmManager.cancel(pendingIntent)
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent,
        )
    }

    fun cancelDaily(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = reminderPendingIntent(context)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun reminderPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
