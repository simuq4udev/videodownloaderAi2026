package com.example.videodownloader

import android.content.Context

object DownloadHistoryStore {
    private const val HISTORY_PREFS = "download_history_prefs"
    private const val HISTORY_KEY = "download_history_records"

    fun saveRecord(context: Context, downloadId: Long, fileName: String) {
        val updated = loadRecords(context).toMutableMap()
        updated[downloadId] = fileName
        val serialized = updated.entries.joinToString("\n") {
            "${it.key}|${it.value.replace("\n", " ").replace("|", "∣")}" // avoid parse breaks
        }

        context.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(HISTORY_KEY, serialized)
            .apply()
    }

    fun loadRecords(context: Context): Map<Long, String> {
        val serialized = context.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)
            .getString(HISTORY_KEY, "")
            .orEmpty()

        if (serialized.isBlank()) return emptyMap()

        return serialized
            .lineSequence()
            .mapNotNull { line ->
                val sep = line.indexOf('|')
                if (sep <= 0) return@mapNotNull null
                val id = line.substring(0, sep).toLongOrNull() ?: return@mapNotNull null
                val fileName = line.substring(sep + 1).replace("∣", "|")
                id to fileName
            }
            .toMap()
    }
}
