package com.example.videodownloader

import android.content.Context

class DownloadPreferences(context: Context) {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var wifiOnly: Boolean
        get() = preferences.getBoolean(KEY_WIFI_ONLY, true)
        set(value) {
            preferences.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "download_prefs"
        private const val KEY_WIFI_ONLY = "wifi_only"
    }
}
