package com.example.videodownloader

import android.net.Uri
import android.webkit.URLUtil

sealed interface UrlValidationResult {
    data object Valid : UrlValidationResult
    data object InvalidHttps : UrlValidationResult
    data class BlockedSocialHost(val host: String) : UrlValidationResult
}

object UrlPolicyValidator {
    private val blockedHosts = setOf(
        "facebook.com",
        "fb.watch",
        "instagram.com",
        "tiktok.com",
        "twitter.com",
        "x.com",
        "youtube.com",
        "youtu.be",
        "vimeo.com",
        "snapchat.com",
        "pinterest.com"
    )

    fun validate(url: String): UrlValidationResult {
        if (!URLUtil.isHttpsUrl(url)) {
            return UrlValidationResult.InvalidHttps
        }

        val host = Uri.parse(url).host?.lowercase().orEmpty()
        val blockedMatch = blockedHosts.firstOrNull { host == it || host.endsWith(".$it") }
        if (blockedMatch != null) {
            return UrlValidationResult.BlockedSocialHost(blockedMatch)
        }

        return UrlValidationResult.Valid
    }
}
