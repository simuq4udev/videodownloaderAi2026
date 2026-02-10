package com.example.videodownloader.starter.data.remote

import com.example.videodownloader.starter.data.model.VideoInfo

/**
 * Remote contract for URL parsing / metadata lookup.
 * Can later be implemented by Retrofit/Ktor.
 */
interface ParserApi {
    suspend fun parseUrl(url: String): VideoInfo
}
