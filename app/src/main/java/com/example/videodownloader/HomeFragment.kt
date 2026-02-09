package com.example.videodownloader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val urlInput = view.findViewById<EditText>(R.id.url_input)
        val rightsCheck = view.findViewById<CheckBox>(R.id.rights_check)
        val downloadButton = view.findViewById<Button>(R.id.download_button)
        val statusText = view.findViewById<TextView>(R.id.status_text)
        val preferences = DownloadPreferences(requireContext())
        val historyStore = DownloadHistoryStore(requireContext())

        downloadButton.setOnClickListener {
            val urlText = urlInput.text.toString().trim()
            if (urlText.isEmpty()) {
                statusText.text = getString(R.string.error_url_required)
                return@setOnClickListener
            }

            val uri = Uri.parse(urlText)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase().orEmpty()
            if (scheme != "https" || host.isBlank()) {
                statusText.text = getString(R.string.error_invalid_url)
                return@setOnClickListener
            }

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
                .setAllowedOverMetered(!preferences.wifiOnly)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir("Download", fileName)

            val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)
            historyStore.add(
                DownloadHistoryItem(
                    downloadId = downloadId,
                    url = urlText,
                    fileName = fileName,
                    timestamp = System.currentTimeMillis()
                )
            )
            statusText.text = getString(R.string.download_started, fileName)
        }
    }
}
