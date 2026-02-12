package com.example.videodownloader.data

import android.content.Context
import java.io.File

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("video_downloader_settings", Context.MODE_PRIVATE)

    fun defaultDirectory(context: Context): String {
        return prefs.getString(KEY_DIRECTORY, null)
            ?: File(context.getExternalFilesDir(null), "downloads").absolutePath
    }

    fun maxSimultaneousDownloads(): Int {
        return prefs.getInt(KEY_MAX_DOWNLOADS, 2)
    }

    fun saveSettings(directory: String, maxDownloads: Int) {
        prefs.edit()
            .putString(KEY_DIRECTORY, directory)
            .putInt(KEY_MAX_DOWNLOADS, maxDownloads.coerceIn(1, 5))
            .apply()
    }

    companion object {
        private const val KEY_DIRECTORY = "key_directory"
        private const val KEY_MAX_DOWNLOADS = "key_max_downloads"
    }
}
