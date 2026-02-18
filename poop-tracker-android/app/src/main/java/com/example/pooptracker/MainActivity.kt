package com.example.pooptracker

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pooptracker.databinding.ActivityMainBinding
import org.json.JSONArray
import org.json.JSONException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var historyAdapter: ArrayAdapter<String>
    private val poopEvents = mutableListOf<Long>()
    private val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        historyAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        binding.historyList.adapter = historyAdapter

        loadEvents()
        refreshUi()

        binding.logPoopButton.setOnClickListener {
            logPoopNow()
        }
    }

    private fun logPoopNow() {
        poopEvents.add(System.currentTimeMillis())
        poopEvents.sortDescending()
        saveEvents()
        refreshUi()
        Toast.makeText(this, R.string.toast_logged, Toast.LENGTH_SHORT).show()
    }

    private fun refreshUi() {
        if (poopEvents.isEmpty()) {
            binding.lastPoopValue.text = getString(R.string.empty_history)
            binding.regularityValue.text = "Not enough data yet. Log at least 2 poops."
            binding.constipationValue.text = "No data yet."
            historyAdapter.clear()
            historyAdapter.add(getString(R.string.empty_history))
            historyAdapter.notifyDataSetChanged()
            return
        }

        val now = System.currentTimeMillis()
        val lastPoop = poopEvents.first()
        binding.lastPoopValue.text = "${dateFormat.format(Date(lastPoop))} (${elapsedSince(lastPoop, now)})"
        binding.regularityValue.text = buildRegularitySummary()
        binding.constipationValue.text = buildConstipationSummary(lastPoop, now)

        val rows = poopEvents.mapIndexed { index, timestamp ->
            "${index + 1}. ${dateFormat.format(Date(timestamp))} (${elapsedSince(timestamp, now)})"
        }
        historyAdapter.clear()
        historyAdapter.addAll(rows)
        historyAdapter.notifyDataSetChanged()
    }

    private fun buildRegularitySummary(): String {
        if (poopEvents.size < 2) {
            return "Not enough data yet. Log at least 2 poops."
        }

        val sortedAscending = poopEvents.sorted()
        val intervalsHours = sortedAscending.zipWithNext { first, second ->
            TimeUnit.MILLISECONDS.toMinutes(second - first).toDouble() / 60.0
        }

        if (intervalsHours.isEmpty()) {
            return "Not enough data yet. Log at least 2 poops."
        }

        val average = intervalsHours.average()
        val min = intervalsHours.minOrNull() ?: average
        val max = intervalsHours.maxOrNull() ?: average
        val allInHealthyRange = intervalsHours.all { it in HEALTHY_INTERVAL_MIN_HOURS..HEALTHY_INTERVAL_MAX_HOURS }
        val stateLabel = if (allInHealthyRange) "Regular pattern." else "Irregular pattern."

        return "$stateLabel Avg gap ${formatHours(average)} (range ${formatHours(min)} to ${formatHours(max)})."
    }

    private fun buildConstipationSummary(lastPoop: Long, now: Long): String {
        val hoursSince = TimeUnit.MILLISECONDS.toMinutes(now - lastPoop).toDouble() / 60.0
        val elapsed = elapsedSince(lastPoop, now)
        return when {
            hoursSince >= LIKELY_CONSTIPATED_HOURS -> "Likely constipated. Last poop was $elapsed."
            hoursSince >= POSSIBLE_CONSTIPATED_HOURS -> "Possible constipation. Last poop was $elapsed."
            else -> "No warning signs right now."
        }
    }

    private fun elapsedSince(timestamp: Long, now: Long): String {
        val deltaMillis = (now - timestamp).coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(deltaMillis)

        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 60 * 24 -> {
                val hours = minutes / 60
                val remainderMinutes = minutes % 60
                if (remainderMinutes == 0L) {
                    "$hours h ago"
                } else {
                    "$hours h $remainderMinutes min ago"
                }
            }
            else -> {
                val days = minutes / (60 * 24)
                val remainderHours = (minutes % (60 * 24)) / 60
                if (remainderHours == 0L) {
                    "$days d ago"
                } else {
                    "$days d $remainderHours h ago"
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
                poopEvents.add(array.getLong(index))
            }
            poopEvents.sortDescending()
        } catch (_: JSONException) {
            // Corrupt data should not crash the app. Reset persisted history.
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(KEY_EVENTS).apply()
        }
    }

    private fun saveEvents() {
        val array = JSONArray()
        poopEvents.sortedDescending().forEach(array::put)
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_EVENTS, array.toString())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "poop_tracker_prefs"
        private const val KEY_EVENTS = "events"
        private const val HEALTHY_INTERVAL_MIN_HOURS = 12.0
        private const val HEALTHY_INTERVAL_MAX_HOURS = 48.0
        private const val POSSIBLE_CONSTIPATED_HOURS = 48.0
        private const val LIKELY_CONSTIPATED_HOURS = 72.0
    }
}
