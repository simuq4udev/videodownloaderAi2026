package com.example.videodownloader.starter.domain.usecase

import com.example.videodownloader.starter.domain.repository.VideoRepository

class ParseVideoUrlUseCase(
    private val repository: VideoRepository
) {
    suspend operator fun invoke(url: String) = repository.parseVideoUrl(url)
}
