package com.example.videodownloader

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var downloadManager: DownloadManager
    private lateinit var emptyHistoryText: TextView
    private lateinit var urlInput: EditText

    private val adapter = DownloadListAdapter { historyItem ->
        Toast.makeText(this, historyItem.detail, Toast.LENGTH_LONG).show()
        startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
    }

    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                return
            }

            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (completedId == -1L || !savedHistoryRecords().containsKey(completedId)) {
                return
            }

            Toast.makeText(this@MainActivity, getString(R.string.download_completed), Toast.LENGTH_SHORT).show()
            refreshHistory()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager

        val historyList: RecyclerView = findViewById(R.id.history_list)
        emptyHistoryText = findViewById(R.id.empty_history_text)
        urlInput = findViewById(R.id.video_url_input)
        val downloadCurrentButton: Button = findViewById(R.id.download_current_button)
        val historyButton: Button = findViewById(R.id.history_button)

        historyList.layoutManager = LinearLayoutManager(this)
        historyList.adapter = adapter

        downloadCurrentButton.setOnClickListener {
            startCurrentDownload(urlInput.text.toString().trim())
        }

        historyButton.setOnClickListener {
            refreshHistory()
            if (savedHistoryRecords().isEmpty()) {
                Toast.makeText(this, getString(R.string.no_history), Toast.LENGTH_SHORT).show()
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(downloadCompleteReceiver, filter)
        }

        refreshHistory()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(downloadCompleteReceiver)
    }

    private fun startCurrentDownload(urlText: String) {
        when (validateUrl(urlText)) {
            UrlValidation.INVALID -> {
                Toast.makeText(this, getString(R.string.invalid_url), Toast.LENGTH_SHORT).show()
                return
            }

            UrlValidation.YOUTUBE -> {
                Toast.makeText(this, getString(R.string.youtube_blocked), Toast.LENGTH_SHORT).show()
                return
            }

            UrlValidation.VALID -> Unit
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "video_$timestamp.mp4"

        val request = DownloadManager.Request(Uri.parse(urlText))
            .setTitle(fileName)
            .setDescription(getString(R.string.download_started))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadId = downloadManager.enqueue(request)
        saveHistoryRecord(downloadId, fileName)

        Toast.makeText(this, getString(R.string.download_started), Toast.LENGTH_SHORT).show()
        refreshHistory()
    }

    private fun refreshHistory() {
        val records = savedHistoryRecords()
        val historyItems = records.entries
            .sortedByDescending { it.key }
            .map { (downloadId, fileName) ->
                val detail = readDownloadDetail(downloadId, fileName)
                DownloadHistoryItem(
                    downloadId = downloadId,
                    title = fileName,
                    detail = detail
                )
            }

        adapter.submitItems(historyItems)
        emptyHistoryText.visibility = if (historyItems.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun readDownloadDetail(downloadId: Long, fallbackFileName: String): String {
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) {
                return getString(R.string.history_missing, fallbackFileName)
            }

            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

            val statusLabel = when (cursor.getInt(statusIndex)) {
                DownloadManager.STATUS_PENDING -> getString(R.string.status_pending)
                DownloadManager.STATUS_RUNNING -> getString(R.string.status_running)
                DownloadManager.STATUS_PAUSED -> getString(R.string.status_paused)
                DownloadManager.STATUS_SUCCESSFUL -> getString(R.string.status_completed)
                DownloadManager.STATUS_FAILED -> getString(R.string.status_failed)
                else -> getString(R.string.status_unknown)
            }

            val localUri = cursor.getString(localUriIndex)
            return if (localUri.isNullOrBlank()) {
                getString(R.string.history_status_only, statusLabel)
            } else {
                getString(R.string.history_status_and_location, statusLabel, localUri)
            }
        }
    }

    private fun saveHistoryRecord(downloadId: Long, fileName: String) {
        val updated = savedHistoryRecords().toMutableMap()
        updated[downloadId] = fileName
        val serialized = updated.entries.joinToString("\n") { "${it.key}|${it.value}" }
        getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE)
            .edit()
            .putString(HISTORY_KEY, serialized)
            .apply()
    }

    private fun savedHistoryRecords(): Map<Long, String> {
        val serialized = getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE)
            .getString(HISTORY_KEY, "")
            .orEmpty()

        if (serialized.isBlank()) {
            return emptyMap()
        }

        return serialized
            .lineSequence()
            .mapNotNull { line ->
                val sep = line.indexOf('|')
                if (sep <= 0) {
                    return@mapNotNull null
                }
                val id = line.substring(0, sep).toLongOrNull() ?: return@mapNotNull null
                val fileName = line.substring(sep + 1)
                id to fileName
            }
            .toMap()
    }

    private fun validateUrl(url: String): UrlValidation {
        if (url.isBlank()) {
            return UrlValidation.INVALID
        }

        val uri = Uri.parse(url)
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()

        if (uri.scheme != "http" && uri.scheme != "https") {
            return UrlValidation.INVALID
        }

        return if (host.contains("youtube.com") || host.contains("youtu.be")) {
            UrlValidation.YOUTUBE
        } else {
            UrlValidation.VALID
        }
    }

    companion object {
        private const val HISTORY_PREFS = "download_history_prefs"
        private const val HISTORY_KEY = "download_history_records"
    }
}

enum class UrlValidation {
    VALID,
    INVALID,
    YOUTUBE
}
