package com.example.videodownloader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.webkit.URLUtil
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val urlInput = findViewById<EditText>(R.id.url_input)
        val rightsCheck = findViewById<CheckBox>(R.id.rights_check)
        val downloadButton = findViewById<Button>(R.id.download_button)
        val statusText = findViewById<TextView>(R.id.status_text)

        downloadButton.setOnClickListener {
            val urlText = urlInput.text.toString().trim()
            if (!URLUtil.isHttpsUrl(urlText)) {
                statusText.text = getString(R.string.error_https_required)
                return@setOnClickListener
            }

            val host = Uri.parse(urlText).host?.lowercase().orEmpty()
            if (blockedHosts.any { host == it || host.endsWith(".$it") }) {
                statusText.text = getString(R.string.error_blocked_host)
                return@setOnClickListener
            }

            if (!rightsCheck.isChecked) {
                statusText.text = getString(R.string.error_rights_required)
                return@setOnClickListener
            }

            val fileName = URLUtil.guessFileName(urlText, null, null)
            val request = DownloadManager.Request(Uri.parse(urlText))
                .setTitle(fileName)
                .setDescription(getString(R.string.download_description))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir("Download", fileName)

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            statusText.text = getString(R.string.download_started, fileName)
        }
    }
}
