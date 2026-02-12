package com.example.videodownloader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.videodownloader.data.SettingsRepository

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)

    fun directory(): String = repository.defaultDirectory(getApplication())

    fun maxDownloads(): Int = repository.maxSimultaneousDownloads()

    fun save(directory: String, maxDownloads: Int) {
        repository.saveSettings(directory, maxDownloads)
    }
}
