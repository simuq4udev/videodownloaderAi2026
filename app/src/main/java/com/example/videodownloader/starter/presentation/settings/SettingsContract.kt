package com.example.videodownloader.starter.presentation.settings

data class SettingsUiState(
    val allowMobileData: Boolean = true,
    val saveToPublicGallery: Boolean = true,
    val maxConcurrentDownloads: Int = 2
)
