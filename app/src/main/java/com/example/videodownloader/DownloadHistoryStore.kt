package com.example.videodownloader

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DownloadHistoryItem(
    val downloadId: Long,
    val url: String,
    val fileName: String,
    val timestamp: Long
) {
    val formattedTime: String
        get() {
            val formatter = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
            return formatter.format(Date(timestamp))
        }
}

class DownloadHistoryStore(private val context: Context) {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun add(item: DownloadHistoryItem) {
        val entries = preferences.getStringSet(KEY_HISTORY, emptySet())?.toMutableSet() ?: mutableSetOf()
        entries.add(serialize(item))
        preferences.edit().putStringSet(KEY_HISTORY, entries).apply()
    }

    fun load(): List<DownloadHistoryItem> {
        val entries = preferences.getStringSet(KEY_HISTORY, emptySet()) ?: emptySet()
        return entries.mapNotNull { deserialize(it) }
            .sortedByDescending { it.timestamp }
    }

    fun clear() {
        preferences.edit().remove(KEY_HISTORY).apply()
    }

    private fun serialize(item: DownloadHistoryItem): String {
        return listOf(item.downloadId, item.timestamp, item.fileName, item.url).joinToString("|")
    }

    private fun deserialize(raw: String): DownloadHistoryItem? {
        val parts = raw.split("|")
        if (parts.size < 4) return null
        val downloadId = parts[0].toLongOrNull() ?: return null
        val timestamp = parts[1].toLongOrNull() ?: return null
        val fileName = parts[2]
        val url = parts.subList(3, parts.size).joinToString("|")
        return DownloadHistoryItem(downloadId, url, fileName, timestamp)
    }

    companion object {
        private const val PREFS_NAME = "download_history"
        private const val KEY_HISTORY = "history_entries"
    }
}
