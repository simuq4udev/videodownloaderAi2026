package com.example.videodownloader

import android.net.Uri
import android.webkit.URLUtil

sealed interface UrlValidationResult {
    data object Valid : UrlValidationResult
    data object InvalidHttps : UrlValidationResult
    data object BlockedSocialHost : UrlValidationResult
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
        if (blockedHosts.any { host == it || host.endsWith(".$it") }) {
            return UrlValidationResult.BlockedSocialHost
        }

        return UrlValidationResult.Valid
    }
}
