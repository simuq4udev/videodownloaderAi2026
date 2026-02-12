package com.example.videodownloader

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DownloadHistoryActivity : AppCompatActivity() {

    private lateinit var downloadManager: DownloadManager
    private lateinit var emptyHistoryText: TextView
    private lateinit var historyList: RecyclerView

    private val adapter = DownloadListAdapter { historyItem ->
        openDownloadedFile(historyItem.downloadId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_history)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.history)

        downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        emptyHistoryText = findViewById(R.id.empty_history_text)
        historyList = findViewById(R.id.history_list)

        historyList.layoutManager = LinearLayoutManager(this)
        historyList.adapter = adapter

        refreshHistory()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        refreshHistory()
    }

    private fun refreshHistory() {
        val records = savedHistoryRecords()
        val historyItems = records.entries
            .sortedByDescending { it.key }
            .map { (downloadId, fileName) ->
                val detail = readDownloadDetail(downloadId, fileName)
                DownloadHistoryItem(downloadId = downloadId, title = fileName, detail = detail)
            }

        adapter.submitItems(historyItems)
        val showEmptyState = historyItems.isEmpty()
        emptyHistoryText.visibility = if (showEmptyState) View.VISIBLE else View.GONE
        historyList.visibility = if (showEmptyState) View.GONE else View.VISIBLE
    }

    private fun readDownloadDetail(downloadId: Long, fallbackFileName: String): String {
        queryDownload(downloadId).use { cursor ->
            if (!cursor.moveToFirst()) return getString(R.string.history_missing, fallbackFileName)

            val statusLabel = when (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_PENDING -> getString(R.string.status_pending)
                DownloadManager.STATUS_RUNNING -> getString(R.string.status_running)
                DownloadManager.STATUS_PAUSED -> getString(R.string.status_paused)
                DownloadManager.STATUS_SUCCESSFUL -> getString(R.string.status_completed)
                DownloadManager.STATUS_FAILED -> getString(R.string.status_failed)
                else -> getString(R.string.status_unknown)
            }

            val localUri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
            return if (localUri.isNullOrBlank()) {
                getString(R.string.history_status_only, statusLabel)
            } else {
                getString(R.string.history_status_and_location, statusLabel, localUri)
            }
        }
    }

    private fun openDownloadedFile(downloadId: Long) {
        queryDownload(downloadId).use { cursor ->
            if (!cursor.moveToFirst()) {
                Toast.makeText(this, getString(R.string.file_not_ready), Toast.LENGTH_SHORT).show()
                return
            }

            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                Toast.makeText(this, getString(R.string.file_not_ready), Toast.LENGTH_SHORT).show()
                return
            }

            val localUri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
            if (localUri.isNullOrBlank()) {
                Toast.makeText(this, getString(R.string.cannot_open_file), Toast.LENGTH_SHORT).show()
                return
            }

            val openIntent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(localUri), "video/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            try {
                startActivity(openIntent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, getString(R.string.cannot_open_file), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun queryDownload(downloadId: Long): Cursor {
        val query = DownloadManager.Query().setFilterById(downloadId)
        return downloadManager.query(query)
    }

    private fun savedHistoryRecords(): Map<Long, String> {
        val serialized = getSharedPreferences(MainActivity.HISTORY_PREFS, MODE_PRIVATE)
            .getString(MainActivity.HISTORY_KEY, "")
            .orEmpty()

        if (serialized.isBlank()) return emptyMap()

        return serialized
            .lineSequence()
            .mapNotNull { line ->
                val sep = line.indexOf('|')
                if (sep <= 0) return@mapNotNull null
                val id = line.substring(0, sep).toLongOrNull() ?: return@mapNotNull null
                val fileName = line.substring(sep + 1)
                id to fileName
            }
            .toMap()
    }
}
