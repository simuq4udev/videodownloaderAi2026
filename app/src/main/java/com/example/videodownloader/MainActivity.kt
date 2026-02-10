package com.example.videodownloader

import android.Manifest
import android.app.DownloadManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.webkit.URLUtil
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var urlInput: EditText
    private lateinit var downloadButton: Button
    private lateinit var downloadsRecyclerView: RecyclerView
    private val adapter = DownloadListAdapter()
    private lateinit var downloadManager: DownloadManager

    private var pendingUrl: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val progressPoller = object : Runnable {
        override fun run() {
            updateDownloadProgress()
            handler.postDelayed(this, 1000)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pendingUrl?.let { startDownload(it) }
            } else {
                Toast.makeText(this, R.string.storage_permission_required, Toast.LENGTH_SHORT).show()
            }
            pendingUrl = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        urlInput = findViewById(R.id.url_input)
        downloadButton = findViewById(R.id.download_button)
        downloadsRecyclerView = findViewById(R.id.downloads_recycler_view)

        downloadsRecyclerView.layoutManager = LinearLayoutManager(this)
        downloadsRecyclerView.adapter = adapter

        downloadButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (!isValidMp4Url(url)) {
                Toast.makeText(this, R.string.invalid_mp4_url, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (needsStoragePermission() && !hasStoragePermission()) {
                pendingUrl = url
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return@setOnClickListener
            }

            startDownload(url)
        }
    }

    override fun onStart() {
        super.onStart()
        handler.post(progressPoller)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(progressPoller)
    }

    private fun isValidMp4Url(url: String): Boolean {
        return URLUtil.isNetworkUrl(url) && url.lowercase().substringBefore('?').endsWith(".mp4")
    }

    private fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun needsStoragePermission(): Boolean = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P

    private fun startDownload(url: String) {
        val fileName = URLUtil.guessFileName(url, null, "video/mp4")
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription(getString(R.string.download_in_progress))
            .setMimeType("video/mp4")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_MOVIES,
                "VideoDownloader/$fileName"
            )

        val id = downloadManager.enqueue(request)
        adapter.addItem(
            DownloadItemUi(
                id = id,
                fileName = fileName,
                url = url,
                statusText = getString(R.string.download_pending),
                progress = 0,
                isDone = false
            )
        )
    }

    private fun updateDownloadProgress() {
        val ids = adapter.activeDownloadIds()
        if (ids.isEmpty()) return

        val query = DownloadManager.Query().setFilterById(*ids.toLongArray())
        val cursor = downloadManager.query(query)
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val statusIdx = it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val downloadedIdx = it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalIdx = it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val titleIdx = it.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
            val uriIdx = it.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)
            while (it.moveToNext()) {
                val id = it.getLong(idIdx)
                val status = it.getInt(statusIdx)
                val downloaded = it.getLong(downloadedIdx)
                val total = it.getLong(totalIdx)
                val progress = if (total > 0L) ((downloaded * 100L) / total).toInt() else 0
                val fileName = it.getString(titleIdx) ?: getString(R.string.unknown_file)
                val sourceUrl = it.getString(uriIdx) ?: ""

                val (statusText, isDone) = when (status) {
                    DownloadManager.STATUS_PENDING -> getString(R.string.download_pending) to false
                    DownloadManager.STATUS_RUNNING -> getString(R.string.download_progress, progress) to false
                    DownloadManager.STATUS_PAUSED -> getString(R.string.download_paused) to false
                    DownloadManager.STATUS_SUCCESSFUL -> getString(R.string.download_complete) to true
                    DownloadManager.STATUS_FAILED -> getString(R.string.download_failed) to true
                    else -> getString(R.string.download_unknown) to false
                }

                adapter.updateItem(
                    DownloadItemUi(
                        id = id,
                        fileName = fileName,
                        url = sourceUrl,
                        statusText = statusText,
                        progress = progress,
                        isDone = isDone
                    )
                )
            }
        }
    }
}
