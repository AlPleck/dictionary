package com.example.myapplication

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class StudyStatsStorage(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("vocabulary", Context.MODE_PRIVATE)

    fun load(): List<StudyEvent> {
        val raw = preferences.getString(KEY_EVENTS, "[]") ?: "[]"
        val entries = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until entries.length()).mapNotNull { index ->
            runCatching {
                val event = entries.getJSONObject(index)
                StudyEvent(event.getLong("timestampMillis"), event.getBoolean("correct"))
            }.getOrNull()
        }
    }

    fun save(events: List<StudyEvent>) {
        val entries = JSONArray()
        events.forEach { event ->
            entries.put(
                JSONObject()
                    .put("timestampMillis", event.timestampMillis)
                    .put("correct", event.correct)
            )
        }
        preferences.edit().putString(KEY_EVENTS, entries.toString()).apply()
    }

    private companion object {
        const val KEY_EVENTS = "study_events"
    }
}
