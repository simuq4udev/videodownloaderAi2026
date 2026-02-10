package com.example.videodownloader.starter.domain.repository

import com.example.videodownloader.starter.data.model.DownloadItem
import com.example.videodownloader.starter.data.model.VideoInfo

interface VideoRepository {
    suspend fun parseVideoUrl(url: String): VideoInfo
    suspend fun enqueueDownload(url: String, formatId: String): DownloadItem
    suspend fun listDownloads(): List<DownloadItem>
}
