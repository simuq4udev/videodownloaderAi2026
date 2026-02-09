package com.example.videodownloader

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
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

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
        val downloadIcon = view.findViewById<ImageView>(R.id.download_ready_icon)
        val historyStore = DownloadHistoryStore(requireContext())

        downloadButton.setOnClickListener {
            val urlText = urlInput.text.toString().trim()
            downloadIcon.visibility = View.GONE
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
            statusText.text = getString(R.string.download_in_progress)

            Thread {
                val result = downloadToCache(requireContext(), urlText, fileName)
                requireActivity().runOnUiThread {
                    if (result != null) {
                        historyStore.add(
                            DownloadHistoryItem(
                                downloadId = System.currentTimeMillis(),
                                url = urlText,
                                fileName = fileName,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        statusText.text = getString(R.string.download_saved_cache, result.name)
                        downloadIcon.visibility = View.VISIBLE
                    } else {
                        statusText.text = getString(R.string.download_failed)
                        downloadIcon.visibility = View.GONE
                    }
                }
            }.start()
        }
    }

    private fun downloadToCache(context: Context, urlText: String, fileName: String): File? {
        return try {
            val url = URL(urlText)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                instanceFollowRedirects = true
            }
            connection.connect()
            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                return null
            }
            val target = File(context.cacheDir, fileName)
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            connection.disconnect()
            target
        } catch (_: Exception) {
            null
        }
    }
}
