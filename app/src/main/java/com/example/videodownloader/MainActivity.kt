package com.example.videodownloader

import android.Manifest
import android.app.DownloadManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Patterns
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
    private lateinit var settingsButton: Button
    private lateinit var downloadsRecyclerView: RecyclerView
    private val adapter = DownloadListAdapter()
    private lateinit var downloadManager: DownloadManager

    private var pendingUrl: String? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pendingUrl?.let { enqueueDownload(it) }
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
        settingsButton = findViewById(R.id.settings_button)
        downloadsRecyclerView = findViewById(R.id.downloads_recycler_view)

        downloadsRecyclerView.layoutManager = LinearLayoutManager(this)
        downloadsRecyclerView.adapter = adapter

        settingsButton.setOnClickListener {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.main_root, SettingsFragment())
                .addToBackStack("settings")
                .commit()
        }

        downloadButton.setOnClickListener {
            val normalizedUrl = normalizeUrl(urlInput.text.toString().trim())
            if (!isValidWebUrl(normalizedUrl)) {
                Toast.makeText(this, R.string.invalid_video_page_url, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (needsStoragePermission() && !hasStoragePermission()) {
                pendingUrl = normalizedUrl
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return@setOnClickListener
            }

            enqueueDownload(normalizedUrl)
        }
    }

    private fun normalizeUrl(url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        return "https://$url"
    }

    private fun isValidWebUrl(url: String): Boolean {
        return Patterns.WEB_URL.matcher(url).matches()
    }

    private fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun needsStoragePermission(): Boolean = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P

    private fun enqueueDownload(url: String) {
        val fileName = URLUtil.guessFileName(url, null, null)
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription(getString(R.string.download_in_progress))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_MOVIES,
                "VideoDownloader/$fileName"
            )

        try {
            val id = downloadManager.enqueue(request)
            adapter.addItem(
                DownloadItemUi(
                    id = id,
                    fileName = fileName,
                    statusText = getString(R.string.download_started),
                    progress = 100,
                    isDone = true
                )
            )
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.download_failed_to_start, Toast.LENGTH_SHORT).show()
        }
    }
}
