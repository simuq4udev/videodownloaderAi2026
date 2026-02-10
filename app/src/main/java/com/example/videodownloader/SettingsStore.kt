package com.example.videodownloader

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val downloadDirectoryUri: String? = null,
    val maxSimultaneousDownloads: Int = 2
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val downloadDirectoryUri = stringPreferencesKey("download_directory_uri")
        val maxSimultaneousDownloads = intPreferencesKey("max_simultaneous_downloads")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            downloadDirectoryUri = prefs[Keys.downloadDirectoryUri],
            maxSimultaneousDownloads = prefs[Keys.maxSimultaneousDownloads] ?: 2
        )
    }

    suspend fun setDownloadDirectoryUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri.isNullOrBlank()) {
                prefs.remove(Keys.downloadDirectoryUri)
            } else {
                prefs[Keys.downloadDirectoryUri] = uri
            }
        }
    }

    suspend fun setMaxSimultaneousDownloads(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.maxSimultaneousDownloads] = value.coerceIn(1, 10)
        }
    }
}
