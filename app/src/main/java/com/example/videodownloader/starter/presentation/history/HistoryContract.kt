package com.example.videodownloader.starter.presentation.history

import com.example.videodownloader.starter.data.model.DownloadItem

data class HistoryUiState(
    val loading: Boolean = false,
    val items: List<DownloadItem> = emptyList(),
    val emptyMessage: String = "No downloads yet"
)
