package com.example.videodownloader.starter.data.model

data class VideoFormat(
    val id: String,
    val qualityLabel: String,
    val mimeType: String,
    val estimatedSizeBytes: Long
)
