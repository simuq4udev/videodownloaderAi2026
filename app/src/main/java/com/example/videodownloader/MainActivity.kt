package com.example.videodownloader

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.DownloadListener
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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
    private lateinit var webContainer: LinearLayout
    private lateinit var previewWebView: WebView
    private lateinit var webDownloadButton: Button

    private var currentPreviewUrl: String = ""
    private var detectedVideoUrl: String? = null

    private val adapter = DownloadListAdapter { historyItem ->
        openDownloadedFile(historyItem.downloadId)
    }

    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (completedId == -1L || !savedHistoryRecords().containsKey(completedId)) return

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
        webContainer = findViewById(R.id.web_container)
        previewWebView = findViewById(R.id.preview_web_view)

        val downloadCurrentButton: Button = findViewById(R.id.download_current_button)
        val historyButton: Button = findViewById(R.id.history_button)
        webDownloadButton = findViewById(R.id.web_download_button)
        val webCloseButton: Button = findViewById(R.id.web_close_button)

        historyList.layoutManager = LinearLayoutManager(this)
        historyList.adapter = adapter

        setupWebView()
        webDownloadButton.isEnabled = false
        webDownloadButton.text = getString(R.string.searching_video)

        downloadCurrentButton.setOnClickListener {
            val rawUrl = urlInput.text.toString().trim()
            val normalizedUrl = normalizeInputUrl(rawUrl)
            if (!isValidForDownload(normalizedUrl)) return@setOnClickListener

            urlInput.setText(normalizedUrl)
            detectedVideoUrl = null
            currentPreviewUrl = normalizedUrl
            webDownloadButton.isEnabled = false
            webDownloadButton.text = getString(R.string.searching_video)
            webContainer.visibility = View.VISIBLE
            previewWebView.loadUrl(normalizedUrl)
            Toast.makeText(this, getString(R.string.webview_opened), Toast.LENGTH_SHORT).show()
        }

        webDownloadButton.setOnClickListener {
            startDownloadFromWebContainer()
        }

        webCloseButton.setOnClickListener {
            webContainer.visibility = View.GONE
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
        previewWebView.destroy()
    }

    private fun setupWebView() {
        previewWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                currentPreviewUrl = url.orEmpty()
                detectVideoUrlFromPage { foundUrl ->
                    if (!foundUrl.isNullOrBlank()) {
                        setDetectedVideoUrl(foundUrl)
                    }
                }
            }
        }
        previewWebView.settings.javaScriptEnabled = true
        previewWebView.settings.domStorageEnabled = true
        previewWebView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val looksVideo = !url.isNullOrBlank() && (
                isLikelyVideoUrl(url) ||
                    mimeType.orEmpty().startsWith("video/") ||
                    contentDisposition.orEmpty().lowercase(Locale.ROOT).contains(".mp4") ||
                    contentDisposition.orEmpty().lowercase(Locale.ROOT).contains(".m3u8")
                )

            if (looksVideo) {
                setDetectedVideoUrl(url!!)
            }
        })
    }

    private fun startDownloadFromWebContainer() {
        if (currentPreviewUrl.isBlank()) {
            Toast.makeText(this, getString(R.string.invalid_url), Toast.LENGTH_SHORT).show()
            return
        }

        val knownUrl = detectedVideoUrl
        if (!knownUrl.isNullOrBlank() && validateUrl(knownUrl) == UrlValidation.VALID) {
            enqueueDownload(knownUrl)
            return
        }

        detectVideoUrlFromPage { fromPage ->
            if (!fromPage.isNullOrBlank() && validateUrl(fromPage) == UrlValidation.VALID) {
                setDetectedVideoUrl(fromPage)
                enqueueDownload(fromPage)
            } else {
                Toast.makeText(this, getString(R.string.video_not_found), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setDetectedVideoUrl(url: String) {
        detectedVideoUrl = url
        webDownloadButton.isEnabled = true
        webDownloadButton.text = getString(R.string.download_from_webview)
        Toast.makeText(this, getString(R.string.video_link_detected), Toast.LENGTH_SHORT).show()
    }

    private fun detectVideoUrlFromPage(onDetected: (String?) -> Unit) {
        val js = """
            (function() {
              var v = document.querySelector('video');
              if (v && v.currentSrc) return v.currentSrc;
              if (v && v.src) return v.src;
              var s = document.querySelector('video source');
              if (s && s.src) return s.src;
              var candidates = document.querySelectorAll('source, a[href]');
              for (var i = 0; i < candidates.length; i++) {
                var u = candidates[i].src || candidates[i].href || '';
                if (/\.(mp4|webm|mkv|mov|m3u8)(\?|$)/i.test(u)) return u;
              }
              return '';
            })();
        """.trimIndent()

        previewWebView.evaluateJavascript(js) { rawResult ->
            onDetected(cleanJsString(rawResult))
        }
    }

    private fun cleanJsString(value: String?): String? {
        if (value == null || value == "null") return null
        val trimmed = value.trim().removePrefix("\"").removeSuffix("\"")
        val unescaped = trimmed.replace("\\\\/", "/").replace("\\\"", "\"")
        return if (unescaped.isBlank()) null else unescaped
    }

    private fun isLikelyVideoUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.contains(".mp4") ||
            lower.contains(".m3u8") ||
            lower.contains(".webm") ||
            lower.contains(".mkv") ||
            lower.contains(".mov")
    }

    private fun normalizeInputUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return rawUrl
        return if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            "https://$rawUrl"
        }
    }

    private fun isValidForDownload(urlText: String): Boolean {
        return when (validateUrl(urlText)) {
            UrlValidation.INVALID -> {
                Toast.makeText(this, getString(R.string.invalid_url), Toast.LENGTH_SHORT).show()
                false
            }

            UrlValidation.YOUTUBE -> {
                Toast.makeText(this, getString(R.string.youtube_blocked), Toast.LENGTH_SHORT).show()
                false
            }

            UrlValidation.VALID -> true
        }
    }

    private fun enqueueDownload(urlText: String) {
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
                DownloadHistoryItem(downloadId = downloadId, title = fileName, detail = detail)
            }

        adapter.submitItems(historyItems)
        emptyHistoryText.visibility = if (historyItems.isEmpty()) View.VISIBLE else View.GONE
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

    private fun validateUrl(url: String): UrlValidation {
        if (url.isBlank()) return UrlValidation.INVALID

        val uri = Uri.parse(url)
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()

        if (uri.scheme != "http" && uri.scheme != "https") return UrlValidation.INVALID

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
