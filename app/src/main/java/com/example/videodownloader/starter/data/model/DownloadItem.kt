package com.example.videodownloader.starter.data.model

enum class DownloadStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED
}

data class DownloadItem(
    val id: String,
    val sourceUrl: String,
    val fileName: String,
    val status: DownloadStatus,
    val progressPercent: Int,
    val destinationUri: String?
)
