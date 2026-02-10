package com.example.videodownloader

import android.app.DownloadManager
import android.content.Context
import android.os.Bundle
import android.net.Uri
import android.webkit.URLUtil
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {


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
            when (UrlPolicyValidator.validate(urlText)) {
                UrlValidationResult.InvalidHttps -> {
                    statusText.text = getString(R.string.error_https_required)
                    return@setOnClickListener
                }

                UrlValidationResult.BlockedSocialHost -> {
                    statusText.text = getString(R.string.error_blocked_host_with_reason)
                    return@setOnClickListener
                }

                UrlValidationResult.Valid -> Unit
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
