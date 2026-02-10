package com.example.videodownloader.home

enum class QueueStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }

data class DownloadQueueItem(
    val id: Long,
    val url: String,
    val fileName: String,
    val progress: Int,
    val status: QueueStatus,
    val reason: String? = null
)
