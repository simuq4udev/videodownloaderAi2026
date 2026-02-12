package com.example.videodownloader.network

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoUrlExtractor(private val pageService: PageService) {

    suspend fun resolveDirectVideoUrl(pageUrl: String): String? = withContext(Dispatchers.IO) {
        val body = pageService.fetchPage(pageUrl).string()

        val ogVideo = Regex("<meta[^>]+property=\\\"og:video(?::url)?\\\"[^>]+content=\\\"([^\\\"]+)\\\"")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)

        if (!ogVideo.isNullOrBlank()) return@withContext Uri.decode(ogVideo)

        val jsonVideoUrl = Regex("https?:\\\\?/\\\\?/[^\"'\\s>]+\\.(mp4|m4v)(\\?[^\"'\\s>]*)?")
            .find(body)
            ?.value
            ?.replace("\\/", "/")

        if (!jsonVideoUrl.isNullOrBlank()) return@withContext jsonVideoUrl

        val directLink = Regex("https?://[^\"'\\s>]+\\.(mp4|m4v)(\\?[^\"'\\s>]*)?")
            .find(body)
            ?.value

        return@withContext directLink
    }
}
