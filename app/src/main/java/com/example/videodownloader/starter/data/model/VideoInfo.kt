package com.example.videodownloader.starter.data.model

data class VideoInfo(
    val sourceUrl: String,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val formats: List<VideoFormat>
)
