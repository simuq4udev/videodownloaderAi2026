package com.example.videodownloader.data

import java.io.File

enum class DownloadStatus {
    QUEUED,
    EXTRACTING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    ERROR
}

data class DownloadItem(
    val id: String,
    val sourceUrl: String,
    val title: String,
    val fileName: String,
    val outputFile: File,
    val thumbnailPath: String? = null,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val directVideoUrl: String? = null,
    val errorMessage: String? = null
)
