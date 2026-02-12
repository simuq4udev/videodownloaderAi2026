package com.example.videodownloader

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.net.URLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var downloadManager: DownloadManager
    private lateinit var emptyHistoryText: TextView
    private lateinit var historyList: RecyclerView
    private lateinit var urlInput: EditText
    private lateinit var webContainer: LinearLayout
    private lateinit var previewWebView: WebView
    private lateinit var webDownloadButton: Button

    private var currentPreviewUrl: String = ""
    private var hasRetriedWithHttp: Boolean = false
    private var detectedVideoUrl: String? = null
    private var detectedUserAgent: String? = null
    private var detectedMimeType: String? = null
    private var detectedContentDisposition: String? = null

    private val adapter = DownloadListAdapter { historyItem ->
        openDownloadedFile(historyItem.downloadId)
    }

    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (completedId == -1L || !savedHistoryRecords().containsKey(completedId)) return

            Toast.makeText(this@MainActivity, getString(R.string.download_completed), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager

        historyList = findViewById(R.id.history_list)
        emptyHistoryText = findViewById(R.id.empty_history_text)
        urlInput = findViewById(R.id.video_url_input)
        webContainer = findViewById(R.id.web_container)
        previewWebView = findViewById(R.id.preview_web_view)

        val downloadCurrentButton: Button = findViewById(R.id.download_current_button)
        val historyButton: Button = findViewById(R.id.history_button)
        webDownloadButton = findViewById(R.id.web_download_button)
        val webCloseButton: Button = findViewById(R.id.web_close_button)

        setupWebView()
        setupBackPressHandler()
        webDownloadButton.isEnabled = false
        webDownloadButton.text = getString(R.string.searching_video)

        downloadCurrentButton.setOnClickListener {
            val rawUrl = urlInput.text.toString().trim()
            val normalizedUrl = normalizeInputUrl(rawUrl)
            if (!isValidForDownload(normalizedUrl)) return@setOnClickListener

            urlInput.setText(normalizedUrl)
            detectedVideoUrl = null
            detectedUserAgent = null
            detectedMimeType = null
            detectedContentDisposition = null
            currentPreviewUrl = normalizedUrl
            hasRetriedWithHttp = false
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
            closeWebView()
        }

        historyButton.setOnClickListener {
            startActivity(Intent(this, DownloadHistoryActivity::class.java))
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(downloadCompleteReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(downloadCompleteReceiver)
        previewWebView.destroy()
    }

    override fun onBackPressed() {
        if (webContainer.visibility == View.VISIBLE) {
            closeWebView()
            return
        }
        super.onBackPressed()
    }

    private fun openUrlInWebView(url: String) {
        urlInput.setText(url)
        detectedVideoUrl = null
        detectedUserAgent = null
        detectedMimeType = null
        detectedContentDisposition = null
        currentPreviewUrl = url
        hasRetriedWithHttp = false
        webDownloadButton.isEnabled = false
        webDownloadButton.text = getString(R.string.searching_video)

        topControls.visibility = View.GONE
        webContainer.visibility = View.VISIBLE
        previewWebView.loadUrl(url)
        Toast.makeText(this, getString(R.string.webview_opened), Toast.LENGTH_SHORT).show()
    }

    private fun closeWebView() {
        webContainer.visibility = View.GONE
        topControls.visibility = View.VISIBLE
    }

    private fun setupWebView() {
        previewWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                currentPreviewUrl = url.orEmpty()
                hasRetriedWithHttp = false
                detectVideoUrlFromPage { foundUrl ->
                    if (!foundUrl.isNullOrBlank()) {
                        setDetectedVideoUrl(foundUrl)
                    }
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val target = request?.url?.toString().orEmpty()
                if (target.startsWith("http://") || target.startsWith("https://")) {
                    return false
                }

                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                    true
                } catch (_: ActivityNotFoundException) {
                    false
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame != true) return

                val failingUrl = request.url?.toString().orEmpty()
                if (!hasRetriedWithHttp && failingUrl.startsWith("https://")) {
                    hasRetriedWithHttp = true
                    val fallbackUrl = "http://" + failingUrl.removePrefix("https://")
                    view?.loadUrl(fallbackUrl)
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.retrying_with_http),
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.webview_failed_to_load),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        previewWebView.settings.javaScriptEnabled = true
        previewWebView.settings.domStorageEnabled = true
        previewWebView.settings.loadsImagesAutomatically = true
        previewWebView.settings.useWideViewPort = true
        previewWebView.settings.loadWithOverviewMode = true
        previewWebView.settings.javaScriptCanOpenWindowsAutomatically = true
        previewWebView.settings.mediaPlaybackRequiresUserGesture = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            previewWebView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            CookieManager.getInstance().setAcceptThirdPartyCookies(previewWebView, true)
        }

        previewWebView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val looksVideo = !url.isNullOrBlank() && (
                isLikelyVideoUrl(url) ||
                    mimeType.orEmpty().startsWith("video/") ||
                    contentDisposition.orEmpty().lowercase(Locale.ROOT).contains(".mp4") ||
                    contentDisposition.orEmpty().lowercase(Locale.ROOT).contains(".m3u8")
                )

            if (looksVideo) {
                detectedUserAgent = userAgent
                detectedMimeType = mimeType
                detectedContentDisposition = contentDisposition
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
            enqueueDownload(
                urlText = knownUrl,
                userAgentHeader = detectedUserAgent,
                refererHeader = currentPreviewUrl,
                mimeTypeHint = detectedMimeType,
                contentDispositionHint = detectedContentDisposition
            )
            return
        }

        detectVideoUrlFromPage { fromPage ->
            if (!fromPage.isNullOrBlank() && validateUrl(fromPage) == UrlValidation.VALID) {
                setDetectedVideoUrl(fromPage)
                enqueueDownload(
                    urlText = fromPage,
                    userAgentHeader = detectedUserAgent,
                    refererHeader = currentPreviewUrl,
                    mimeTypeHint = detectedMimeType,
                    contentDispositionHint = detectedContentDisposition
                )
            } else {
                Toast.makeText(this, getString(R.string.video_not_found), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setDetectedVideoUrl(url: String) {
        detectedVideoUrl = url
        webDownloadButton.isEnabled = true
        webDownloadButton.text = getString(R.string.download_from_webview)
    }

    private fun detectVideoUrlFromPage(onDetected: (String?) -> Unit) {
        val js = """
            (function() {
              function pick(arr) {
                for (var i = 0; i < arr.length; i++) {
                  var u = arr[i];
                  if (!u) continue;
                  if (/\.(mp4|webm|mkv|mov|m3u8)(\?|$)/i.test(u)) return u;
                  if (/video|stream|playlist|manifest/i.test(u)) return u;
                }
                return '';
              }

              var v = document.querySelector('video');
              if (v && v.currentSrc) return v.currentSrc;
              if (v && v.src) return v.src;

              var sourceEls = document.querySelectorAll('video source, source[src], source[data-src]');
              var sourceUrls = [];
              for (var i = 0; i < sourceEls.length; i++) {
                sourceUrls.push(sourceEls[i].src || sourceEls[i].getAttribute('data-src') || '');
              }
              var sourcePick = pick(sourceUrls);
              if (sourcePick) return sourcePick;

              var metaCandidates = [
                'meta[property="og:video"]',
                'meta[property="og:video:url"]',
                'meta[property="og:video:secure_url"]',
                'meta[name="twitter:player:stream"]',
                'meta[itemprop="contentUrl"]'
              ];
              var metaUrls = [];
              for (var j = 0; j < metaCandidates.length; j++) {
                var m = document.querySelector(metaCandidates[j]);
                if (m) metaUrls.push(m.getAttribute('content') || '');
              }
              var metaPick = pick(metaUrls);
              if (metaPick) return metaPick;

              var links = document.querySelectorAll('a[href], *[data-href], *[data-src]');
              var candidateUrls = [];
              for (var k = 0; k < links.length; k++) {
                candidateUrls.push(links[k].href || links[k].getAttribute('data-href') || links[k].getAttribute('data-src') || '');
              }
              return pick(candidateUrls);
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
            lower.contains(".mov") ||
            lower.contains("video") ||
            lower.contains("playlist")
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

            UrlValidation.VALID -> true
        }
    }

    private fun enqueueDownload(
        urlText: String,
        userAgentHeader: String? = null,
        refererHeader: String? = null,
        mimeTypeHint: String? = null,
        contentDispositionHint: String? = null
    ) {
        val guessedMimeType = mimeTypeHint
            ?.takeIf { it.isNotBlank() }
            ?: URLConnection.guessContentTypeFromName(urlText)
        val fileName = buildDownloadFileName(urlText, guessedMimeType, contentDispositionHint)

        val request = DownloadManager.Request(Uri.parse(urlText))
            .setTitle(fileName)
            .setDescription(getString(R.string.download_started))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        guessedMimeType?.let { request.setMimeType(it) }

        val cookieHeader = CookieManager.getInstance().getCookie(urlText)
        if (!cookieHeader.isNullOrBlank()) {
            request.addRequestHeader("Cookie", cookieHeader)
        }

        val effectiveUserAgent = userAgentHeader
            ?: previewWebView.settings.userAgentString
        if (!effectiveUserAgent.isNullOrBlank()) {
            request.addRequestHeader("User-Agent", effectiveUserAgent)
        }

        val effectiveReferer = refererHeader
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: currentPreviewUrl.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        if (!effectiveReferer.isNullOrBlank()) {
            request.addRequestHeader("Referer", effectiveReferer)
        }

        val downloadId = downloadManager.enqueue(request)
        saveHistoryRecord(downloadId, fileName)

        Toast.makeText(this, getString(R.string.download_started), Toast.LENGTH_SHORT).show()
        refreshHistory()
    }

    private fun buildDownloadFileName(
        urlText: String,
        mimeType: String?,
        contentDisposition: String?
    ): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val guessed = URLUtil.guessFileName(urlText, contentDisposition, mimeType)
            .ifBlank { "video_$timestamp" }

        val hasExtension = guessed.substringAfterLast('.', "") != guessed
        if (hasExtension) return guessed

        val extension = mimeType
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?.takeIf { it.isNotBlank() }
            ?: "mp4"

        return "$guessed.$extension"
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

        val effectiveUserAgent = userAgentHeader
            ?: previewWebView.settings.userAgentString
        if (!effectiveUserAgent.isNullOrBlank()) {
            request.addRequestHeader("User-Agent", effectiveUserAgent)
        }

        val effectiveReferer = refererHeader
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: currentPreviewUrl.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        if (!effectiveReferer.isNullOrBlank()) {
            request.addRequestHeader("Referer", effectiveReferer)
        }

        val downloadId = downloadManager.enqueue(request)
        saveHistoryRecord(downloadId, fileName)

        Toast.makeText(this, getString(R.string.download_started), Toast.LENGTH_SHORT).show()
    }

    private fun buildDownloadFileName(
        urlText: String,
        mimeType: String?,
        contentDisposition: String?
    ): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val guessed = URLUtil.guessFileName(urlText, contentDisposition, mimeType)
            .ifBlank { "video_$timestamp" }

        val hasExtension = guessed.substringAfterLast('.', "") != guessed
        if (hasExtension) return guessed

        val extension = mimeType
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?.takeIf { it.isNotBlank() }
            ?: "mp4"

        return "$guessed.$extension"
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
        if (uri.scheme != "http" && uri.scheme != "https") return UrlValidation.INVALID

        return UrlValidation.VALID
    }

    companion object {
        const val HISTORY_PREFS = "download_history_prefs"
        const val HISTORY_KEY = "download_history_records"
    }
}

enum class UrlValidation {
    VALID,
    INVALID
}
