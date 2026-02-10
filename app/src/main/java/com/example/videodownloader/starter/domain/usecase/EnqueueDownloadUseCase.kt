package com.example.videodownloader.starter.domain.usecase

import com.example.videodownloader.starter.domain.repository.VideoRepository

class EnqueueDownloadUseCase(
    private val repository: VideoRepository
) {
    suspend operator fun invoke(url: String, formatId: String) =
        repository.enqueueDownload(url = url, formatId = formatId)
}
