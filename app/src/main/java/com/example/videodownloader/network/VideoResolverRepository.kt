package com.example.videodownloader.network

import okhttp3.OkHttpClient
import okhttp3.Request

class VideoResolverRepository(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun resolveDirectMediaUrl(inputUrl: String): String {
        if (inputUrl.endsWith(".mp4", ignoreCase = true)) {
            return inputUrl
        }

        val request = Request.Builder().url(inputUrl).head().build()
        client.newCall(request).execute().use { response ->
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            if (response.isSuccessful && contentType.startsWith("video/")) {
                return inputUrl
            }
        }

        throw IllegalArgumentException("Could not resolve direct video file URL. Paste a direct video file link (.mp4) in this template.")
    }
}
