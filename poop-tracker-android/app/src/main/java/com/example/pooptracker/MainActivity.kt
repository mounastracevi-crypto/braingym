package com.example.pooptracker

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.pooptracker.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private data class PoopEvent(
        val timestamp: Long,
        val bristolType: Int,
    )

    private data class ChartBarItem(
        val label: String,
        val count: Int,
    )

    private lateinit var binding: ActivityMainBinding
    private lateinit var historyAdapter: ArrayAdapter<String>
    private val poopEvents = mutableListOf<PoopEvent>()
    private val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy h:mm a", Locale.getDefault())
    private val dayLabelFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    private val weekLabelFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    private var isUpdatingReminderSwitch = false
    private var pendingReminderEnableAfterPermission = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!pendingReminderEnableAfterPermission) {
                return@registerForActivityResult
            }
            pendingReminderEnableAfterPermission = false
            if (granted) {
                setReminderEnabled(true)
            } else {
                setReminderSwitchState(false)
                Toast.makeText(this, R.string.toast_notifications_denied, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationHelper.ensureReminderChannel(this)

        historyAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        binding.historyList.adapter = historyAdapter

        loadEvents()
        setupReminderControls()
        refreshUi()

        binding.logPoopButton.setOnClickListener {
            showBristolTypePicker { selectedType ->
                logPoop(
                    bristolType = selectedType,
                    timestamp = System.currentTimeMillis(),
                    isPastEntry = false,
                )
            }
        }

        binding.logPastPoopButton.setOnClickListener {
            showBristolTypePicker { selectedType ->
                showPastPoopDatePicker(selectedType)
            }
        }
    }

    private fun setupReminderControls() {
        binding.reminderSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingReminderSwitch) {
                return@setOnCheckedChangeListener
            }
            onReminderToggleRequested(isChecked)
        }
        setReminderSwitchState(ReminderScheduler.isEnabled(this))
        updateReminderTimeLabel()

        binding.setReminderTimeButton.setOnClickListener {
            showReminderTimePicker()
        }
    }

    private fun onReminderToggleRequested(enabled: Boolean) {
        if (enabled && shouldRequestNotificationPermission()) {
            pendingReminderEnableAfterPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        setReminderEnabled(enabled)
    }

    private fun shouldRequestNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
    }

    private fun setReminderEnabled(enabled: Boolean) {
        ReminderScheduler.setEnabled(this, enabled)
        if (enabled) {
            ReminderScheduler.scheduleDaily(this)
            Toast.makeText(this, R.string.toast_reminder_enabled, Toast.LENGTH_SHORT).show()
        } else {
            ReminderScheduler.cancelDaily(this)
            Toast.makeText(this, R.string.toast_reminder_disabled, Toast.LENGTH_SHORT).show()
        }
        setReminderSwitchState(enabled)
    }

    private fun setReminderSwitchState(checked: Boolean) {
        isUpdatingReminderSwitch = true
        binding.reminderSwitch.isChecked = checked
        isUpdatingReminderSwitch = false
    }

    private fun showReminderTimePicker() {
        val (currentHour, currentMinute) = ReminderScheduler.getReminderTime(this)
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                ReminderScheduler.saveReminderTime(this, hourOfDay, minute)
                if (ReminderScheduler.isEnabled(this)) {
                    ReminderScheduler.scheduleDaily(this)
                }
                updateReminderTimeLabel()
                Toast.makeText(this, R.string.toast_reminder_time_updated, Toast.LENGTH_SHORT).show()
            },
            currentHour,
            currentMinute,
            DateFormat.is24HourFormat(this),
        ).show()
    }

    private fun updateReminderTimeLabel() {
        val (hour, minute) = ReminderScheduler.getReminderTime(this)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val formattedTime = DateFormat.getTimeFormat(this).format(calendar.time)
        binding.reminderTimeValue.text = getString(R.string.reminder_time_value, formattedTime)
    }

    private fun showBristolTypePicker(onTypeSelected: (Int) -> Unit) {
        val options = resources.getStringArray(R.array.bristol_type_options)
        var selectedIndex = DEFAULT_BRISTOL_TYPE - 1

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pick_stool_type)
            .setSingleChoiceItems(options, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.log_action) { _, _ ->
                onTypeSelected(selectedIndex + 1)
            }
            .show()
    }

    private fun showPastPoopDatePicker(bristolType: Int) {
        val now = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                showPastPoopTimePicker(
                    year = year,
                    month = month,
                    dayOfMonth = dayOfMonth,
                    bristolType = bristolType,
                )
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH),
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
            setTitle(getString(R.string.pick_past_date))
            show()
        }
    }

    private fun showPastPoopTimePicker(
        year: Int,
        month: Int,
        dayOfMonth: Int,
        bristolType: Int,
    ) {
        val now = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val selectedTimestamp = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                if (selectedTimestamp > System.currentTimeMillis()) {
                    Toast.makeText(this, R.string.toast_future_time_error, Toast.LENGTH_SHORT).show()
                    return@TimePickerDialog
                }

                logPoop(
                    bristolType = bristolType,
                    timestamp = selectedTimestamp,
                    isPastEntry = true,
                )
            },
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            DateFormat.is24HourFormat(this),
        ).apply {
            setTitle(getString(R.string.pick_past_time))
            show()
        }
    }

    private fun logPoop(
        bristolType: Int,
        timestamp: Long,
        isPastEntry: Boolean,
    ) {
        poopEvents.add(
            PoopEvent(
                timestamp = timestamp,
                bristolType = bristolType.coerceIn(1, 7),
            ),
        )
        poopEvents.sortByDescending { it.timestamp }
        saveEvents()
        refreshUi()
        val toastResId = if (isPastEntry) R.string.toast_logged_past else R.string.toast_logged
        Toast.makeText(this, toastResId, Toast.LENGTH_SHORT).show()
    }

    private fun refreshUi() {
        val now = System.currentTimeMillis()

        if (poopEvents.isEmpty()) {
            binding.lastPoopValue.text = getString(R.string.empty_history)
            binding.lastStoolTypeValue.text = getString(R.string.no_data_yet)
            binding.regularityValue.text = getString(R.string.not_enough_data)
            binding.constipationValue.text = getString(R.string.no_data_yet)
            historyAdapter.clear()
            historyAdapter.add(getString(R.string.empty_history))
            historyAdapter.notifyDataSetChanged()
            updateCharts(now)
            return
        }

        val lastPoop = poopEvents.first()
        binding.lastPoopValue.text =
            "${dateFormat.format(Date(lastPoop.timestamp))} (${elapsedSince(lastPoop.timestamp, now)})"
        binding.lastStoolTypeValue.text = getString(R.string.bristol_type_short, lastPoop.bristolType)
        binding.regularityValue.text = buildRegularitySummary()
        binding.constipationValue.text = buildConstipationSummary(lastPoop.timestamp, now)

        val rows = poopEvents.mapIndexed { index, event ->
            getString(
                R.string.history_row,
                index + 1,
                dateFormat.format(Date(event.timestamp)),
                event.bristolType,
                elapsedSince(event.timestamp, now),
            )
        }
        historyAdapter.clear()
        historyAdapter.addAll(rows)
        historyAdapter.notifyDataSetChanged()

        updateCharts(now)
    }

    private fun updateCharts(nowMillis: Long) {
        val zoneId = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val eventDates = poopEvents.map { Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate() }

        val dailyItems = (6 downTo 0).map { dayOffset ->
            val date = today.minusDays(dayOffset.toLong())
            ChartBarItem(
                label = date.format(dayLabelFormatter),
                count = eventDates.count { it == date },
            )
        }
        renderChart(binding.dailyChartContainer, dailyItems)

        val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeklyItems = (7 downTo 0).map { weekOffset ->
            val weekStart = thisWeekStart.minusWeeks(weekOffset.toLong())
            val weekEnd = weekStart.plusDays(6)
            val count = eventDates.count { eventDate ->
                !eventDate.isBefore(weekStart) && !eventDate.isAfter(weekEnd)
            }
            ChartBarItem(
                label = getString(R.string.week_of_label, weekStart.format(weekLabelFormatter)),
                count = count,
            )
        }
        renderChart(binding.weeklyChartContainer, weeklyItems)
    }

    private fun renderChart(container: LinearLayout, items: List<ChartBarItem>) {
        container.removeAllViews()
        val maxCount = items.maxOfOrNull { it.count } ?: 0
        val progressMax = maxCount.coerceAtLeast(1)

        items.forEach { item ->
            val rowView = layoutInflater.inflate(R.layout.item_chart_row, container, false)
            val labelView = rowView.findViewById<TextView>(R.id.chartRowLabel)
            val progressView = rowView.findViewById<ProgressBar>(R.id.chartRowBar)
            val countView = rowView.findViewById<TextView>(R.id.chartRowCount)

            labelView.text = item.label
            progressView.max = progressMax
            progressView.progress = item.count
            countView.text = getString(R.string.count_format, item.count)
            container.addView(rowView)
        }
    }

    private fun buildRegularitySummary(): String {
        if (poopEvents.size < 2) {
            return getString(R.string.not_enough_data)
        }

        val sortedAscending = poopEvents.map { it.timestamp }.sorted()
        val intervalsHours = sortedAscending.zipWithNext { first, second ->
            TimeUnit.MILLISECONDS.toMinutes(second - first).toDouble() / 60.0
        }

        if (intervalsHours.isEmpty()) {
            return getString(R.string.not_enough_data)
        }

        val average = intervalsHours.average()
        val min = intervalsHours.minOrNull() ?: average
        val max = intervalsHours.maxOrNull() ?: average
        val allInHealthyRange = intervalsHours.all { it in HEALTHY_INTERVAL_MIN_HOURS..HEALTHY_INTERVAL_MAX_HOURS }
        val stateLabel = if (allInHealthyRange) {
            getString(R.string.regular_pattern)
        } else {
            getString(R.string.irregular_pattern)
        }

        return getString(
            R.string.regularity_summary,
            stateLabel,
            formatHours(average),
            formatHours(min),
            formatHours(max),
        )
    }

    private fun buildConstipationSummary(lastPoop: Long, now: Long): String {
        val hoursSince = TimeUnit.MILLISECONDS.toMinutes(now - lastPoop).toDouble() / 60.0
        val elapsed = elapsedSince(lastPoop, now)
        return when {
            hoursSince >= LIKELY_CONSTIPATED_HOURS -> getString(R.string.constipation_likely, elapsed)
            hoursSince >= POSSIBLE_CONSTIPATED_HOURS -> getString(R.string.constipation_possible, elapsed)
            else -> getString(R.string.no_warning_signs)
        }
    }

    private fun elapsedSince(timestamp: Long, now: Long): String {
        val deltaMillis = (now - timestamp).coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(deltaMillis)

        return when {
            minutes < 1 -> getString(R.string.just_now)
            minutes < 60 -> getString(R.string.minutes_ago, minutes)
            minutes < 60 * 24 -> {
                val hours = minutes / 60
                val remainderMinutes = minutes % 60
                if (remainderMinutes == 0L) {
                    getString(R.string.hours_ago, hours)
                } else {
                    getString(R.string.hours_minutes_ago, hours, remainderMinutes)
                }
            }
            else -> {
                val days = minutes / (60 * 24)
                val remainderHours = (minutes % (60 * 24)) / 60
                if (remainderHours == 0L) {
                    getString(R.string.days_ago, days)
                } else {
                    getString(R.string.days_hours_ago, days, remainderHours)
                }
            }
        }
    }

    private fun formatHours(hours: Double): String {
        val roundedHours = hours.roundToInt()
        if (roundedHours < 24) {
            return "${roundedHours}h"
        }

        val days = roundedHours / 24
        val remainderHours = roundedHours % 24
        return if (remainderHours == 0) {
            "${days}d"
        } else {
            "${days}d ${remainderHours}h"
        }
    }

    private fun loadEvents() {
        poopEvents.clear()
        val raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_EVENTS, null) ?: return

        try {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                when (val entry = array.get(index)) {
                    is JSONObject -> {
                        val timestamp = entry.optLong(JSON_KEY_TIMESTAMP, INVALID_TIMESTAMP)
                        if (timestamp == INVALID_TIMESTAMP) {
                            continue
                        }
                        val bristolType = entry.optInt(JSON_KEY_BRISTOL_TYPE, DEFAULT_BRISTOL_TYPE).coerceIn(1, 7)
                        poopEvents.add(
                            PoopEvent(
                                timestamp = timestamp,
                                bristolType = bristolType,
                            ),
                        )
                    }
                    is Number -> {
                        // Backward compatibility for old saved format (timestamp only).
                        poopEvents.add(
                            PoopEvent(
                                timestamp = entry.toLong(),
                                bristolType = DEFAULT_BRISTOL_TYPE,
                            ),
                        )
                    }
                }
            }
            poopEvents.sortByDescending { it.timestamp }
        } catch (_: JSONException) {
            // Corrupt data should not crash the app. Reset persisted history.
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(KEY_EVENTS).apply()
        }
    }

    private fun saveEvents() {
        val array = JSONArray()
        poopEvents.sortedByDescending { it.timestamp }.forEach { event ->
            array.put(
                JSONObject().apply {
                    put(JSON_KEY_TIMESTAMP, event.timestamp)
                    put(JSON_KEY_BRISTOL_TYPE, event.bristolType)
                },
            )
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_EVENTS, array.toString())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "poop_tracker_prefs"
        private const val KEY_EVENTS = "events"
        private const val JSON_KEY_TIMESTAMP = "timestamp"
        private const val JSON_KEY_BRISTOL_TYPE = "bristolType"
        private const val INVALID_TIMESTAMP = Long.MIN_VALUE
        private const val DEFAULT_BRISTOL_TYPE = 4

        private const val HEALTHY_INTERVAL_MIN_HOURS = 12.0
        private const val HEALTHY_INTERVAL_MAX_HOURS = 48.0
        private const val POSSIBLE_CONSTIPATED_HOURS = 48.0
        private const val LIKELY_CONSTIPATED_HOURS = 72.0
    }
}
