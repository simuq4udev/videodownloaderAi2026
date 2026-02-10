package com.example.videodownloader.starter.data.repository

import com.example.videodownloader.starter.data.model.DownloadItem
import com.example.videodownloader.starter.data.model.DownloadStatus
import com.example.videodownloader.starter.data.remote.ParserApi
import com.example.videodownloader.starter.domain.repository.VideoRepository
import java.util.UUID

class VideoRepositoryImpl(
    private val parserApi: ParserApi
) : VideoRepository {
    override suspend fun parseVideoUrl(url: String) = parserApi.parseUrl(url)

    override suspend fun enqueueDownload(url: String, formatId: String): DownloadItem {
        return DownloadItem(
            id = UUID.randomUUID().toString(),
            sourceUrl = url,
            fileName = "video_$formatId.mp4",
            status = DownloadStatus.QUEUED,
            progressPercent = 0,
            destinationUri = null
        )
    }

    override suspend fun listDownloads(): List<DownloadItem> = emptyList()
}
