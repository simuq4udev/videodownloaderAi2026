package com.example.videodownloader.network

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class VideoUrlExtractor(private val pageService: PageService) {

    suspend fun resolveDirectVideoUrl(pageUrl: String): String? = withContext(Dispatchers.IO) {
        val body = pageService.fetchPage(pageUrl).string()

        val candidates = listOfNotNull(
            extractMeta(body),
            extractJsonField(body, "video_url"),
            extractJsonField(body, "contentUrl"),
            extractEscapedMp4(body),
            extractDirectMp4(body)
        )

        return@withContext candidates
            .map { decodeCandidate(it) }
            .firstOrNull { it.startsWith("http") && (it.contains(".mp4") || it.contains(".m4v")) }
    }

    private fun extractMeta(body: String): String? {
        val patterns = listOf(
            "<meta[^>]+property=[\"']og:video(?::url)?[\"'][^>]+content=[\"']([^\"']+)[\"']",
            "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:video(?::url)?[\"']"
        )

        for (pattern in patterns) {
            val value = Regex(pattern, RegexOption.IGNORE_CASE)
                .find(body)
                ?.groupValues
                ?.getOrNull(1)
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun extractJsonField(body: String, key: String): String? {
        val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        return regex.find(body)?.groupValues?.getOrNull(1)
    }

    private fun extractEscapedMp4(body: String): String? {
        return Regex("https?:\\\\?/\\\\?/[^\"'\\s>]+\\.(mp4|m4v)(\\?[^\"'\\s>]*)?", RegexOption.IGNORE_CASE)
            .find(body)
            ?.value
    }

    private fun extractDirectMp4(body: String): String? {
        return Regex("https?://[^\"'\\s>]+\\.(mp4|m4v)(\\?[^\"'\\s>]*)?", RegexOption.IGNORE_CASE)
            .find(body)
            ?.value
    }

    private fun decodeCandidate(raw: String): String {
        return runCatching {
            val unescaped = raw
                .replace("\\/", "/")
                .replace("\\u0026", "&")
            Uri.decode(URLDecoder.decode(unescaped, StandardCharsets.UTF_8.name()))
        }.getOrDefault(raw)
    }
}
