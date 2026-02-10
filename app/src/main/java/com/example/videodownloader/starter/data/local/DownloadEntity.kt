package com.example.videodownloader.starter.data.local

import com.example.videodownloader.starter.data.model.DownloadStatus

data class DownloadEntity(
    val id: String,
    val sourceUrl: String,
    val fileName: String,
    val status: DownloadStatus,
    val progressPercent: Int,
    val createdAtEpochMs: Long
)
