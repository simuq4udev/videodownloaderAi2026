package com.example.videodownloader.starter.di

import com.example.videodownloader.starter.data.remote.ParserApi
import com.example.videodownloader.starter.data.repository.VideoRepositoryImpl
import com.example.videodownloader.starter.domain.repository.VideoRepository

/**
 * Replace with Hilt/Koin in production.
 */
object ServiceLocator {
    fun provideVideoRepository(parserApi: ParserApi): VideoRepository =
        VideoRepositoryImpl(parserApi)
}
