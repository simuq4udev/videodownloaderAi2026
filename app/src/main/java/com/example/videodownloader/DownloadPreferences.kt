package com.example.videodownloader

import android.content.Context

class DownloadPreferences(context: Context) {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var wifiOnly: Boolean
        get() = preferences.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = preferences.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    var defaultDirectory: String
        get() = preferences.getString(KEY_DIRECTORY, "Download") ?: "Download"
        set(value) = preferences.edit().putString(KEY_DIRECTORY, value.ifBlank { "Download" }).apply()

    var maxSimultaneousDownloads: Int
        get() = preferences.getInt(KEY_MAX_SIMULTANEOUS, 2)
        set(value) = preferences.edit().putInt(KEY_MAX_SIMULTANEOUS, value.coerceIn(1, 5)).apply()

    companion object {
        private const val PREFS_NAME = "download_prefs"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_DIRECTORY = "default_directory"
        private const val KEY_MAX_SIMULTANEOUS = "max_simultaneous"
    }
}
