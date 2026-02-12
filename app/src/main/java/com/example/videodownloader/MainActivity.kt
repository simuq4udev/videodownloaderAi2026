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
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import java.net.URI
import java.net.URLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var downloadManager: DownloadManager
    private lateinit var topControls: LinearLayout
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

    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (completedId == -1L || !DownloadHistoryStore.loadRecords(this@MainActivity).containsKey(completedId)) return

            Toast.makeText(this@MainActivity, getString(R.string.download_completed), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager

        topControls = findViewById(R.id.top_controls)
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

            openUrlInWebView(normalizedUrl)
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

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webContainer.visibility == View.VISIBLE) {
                    closeWebView()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun openUrlInWebView(url: String) {
        urlInput.setText(url)
        detectedVideoUrl = null
        detectedUserAgent = null
        detectedMimeType = null
        detectedContentDisposition = null
        currentPreviewUrl = url
        hasRetriedWithHttp = false
        webDownloadButton.isEnabled = true
        webDownloadButton.text = getString(R.string.download_from_webview)

        if (isLikelyVideoUrl(url)) {
            setDetectedVideoUrl(url)
        }

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
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                detectedVideoUrl = null
                webDownloadButton.isEnabled = true
                webDownloadButton.text = getString(R.string.download_from_webview)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                currentPreviewUrl = url.orEmpty()
                hasRetriedWithHttp = false
                detectVideoUrlFromPage { foundUrl ->
                    if (!foundUrl.isNullOrBlank()) {
                        setDetectedVideoUrl(foundUrl)
                    } else {
                        webDownloadButton.isEnabled = true
                        webDownloadButton.text = getString(R.string.download_from_webview)
                    }
                }
                injectInlineVideoButtons()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val target = request?.url?.toString().orEmpty()

                if (target.startsWith("http://") || target.startsWith("https://")) {
                    return false
                }

                if (target.startsWith("intent://")) {
                    val fallback = request?.url?.getQueryParameter("browser_fallback_url")
                    if (!fallback.isNullOrBlank()) {
                        view?.loadUrl(fallback)
                        return true
                    }
                }

                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.link_open_inside_only),
                    Toast.LENGTH_SHORT
                ).show()
                return true
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
        previewWebView.settings.setSupportMultipleWindows(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            previewWebView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            CookieManager.getInstance().setAcceptThirdPartyCookies(previewWebView, true)
        }

        previewWebView.addJavascriptInterface(VideoDownloadBridge(), "AndroidDownloader")

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

        previewWebView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                detectVideoUrlFromPage { foundUrl ->
                    if (!foundUrl.isNullOrBlank()) {
                        setDetectedVideoUrl(foundUrl)
                    }
                }
            }
            false
        }
    }

    private inner class VideoDownloadBridge {
        @JavascriptInterface
        fun downloadVideo(url: String?) {
            runOnUiThread {
                val resolvedUrl = resolveDownloadableUrl(url)
                if (resolvedUrl.isNullOrBlank()) {
                    Toast.makeText(this@MainActivity, getString(R.string.video_not_found), Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }

                setDetectedVideoUrl(resolvedUrl)
                enqueueDownload(
                    urlText = resolvedUrl,
                    userAgentHeader = previewWebView.settings.userAgentString,
                    refererHeader = currentPreviewUrl,
                    mimeTypeHint = detectedMimeType,
                    contentDispositionHint = detectedContentDisposition
                )
            }
        }
    }

    private fun injectInlineVideoButtons() {
        val buttonLabel = getString(R.string.inline_download_video)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val js = """
            (function() {
              var buttonText = "$buttonLabel";

              function getVideoId(video, idx) {
                if (!video.dataset.androidDlId) {
                  video.dataset.androidDlId = 'android_video_' + idx + '_' + Math.random().toString(36).slice(2, 8);
                }
                return video.dataset.androidDlId;
              }

              function getOrCreateLayer() {
                var layer = document.getElementById('android-dl-layer');
                if (!layer) {
                  layer = document.createElement('div');
                  layer.id = 'android-dl-layer';
                  layer.style.position = 'fixed';
                  layer.style.left = '0';
                  layer.style.top = '0';
                  layer.style.width = '100%';
                  layer.style.height = '100%';
                  layer.style.pointerEvents = 'none';
                  layer.style.zIndex = '2147483647';
                  document.documentElement.appendChild(layer);
                }
                return layer;
              }

              function getVideoSource(video) {
                if (!video) return '';
                var src = video.currentSrc || video.src || '';
                if (!src) {
                  var source = video.querySelector('source');
                  if (source) src = source.src || source.getAttribute('src') || '';
                }
                if (!src) return '';

                try {
                  return new URL(src, window.location.href).href;
                } catch (e) {
                  return src;
                }
              }

              function getOrCreateButton(layer, id) {
                var btn = layer.querySelector('button[data-video-id="' + id + '"]');
                if (!btn) {
                  btn = document.createElement('button');
                  btn.type = 'button';
                  btn.dataset.videoId = id;
                  btn.innerText = buttonText;
                  btn.style.position = 'fixed';
                  btn.style.padding = '6px 10px';
                  btn.style.border = 'none';
                  btn.style.borderRadius = '8px';
                  btn.style.background = '#0B57D0';
                  btn.style.color = '#fff';
                  btn.style.fontSize = '12px';
                  btn.style.cursor = 'pointer';
                  btn.style.opacity = '0.92';
                  btn.style.pointerEvents = 'auto';
                  btn.style.boxShadow = '0 2px 8px rgba(0,0,0,0.3)';

                  btn.addEventListener('click', function(ev) {
                    ev.preventDefault();
                    ev.stopPropagation();

                    var targetVideo = document.querySelector('video[data-android-dl-id="' + btn.dataset.videoId + '"]');
                    var absolute = getVideoSource(targetVideo);
                    if (!absolute) return;

                    if (window.AndroidDownloader && window.AndroidDownloader.downloadVideo) {
                      window.AndroidDownloader.downloadVideo(absolute);
                    }
                  });

                  layer.appendChild(btn);
                }
                return btn;
              }

              function refreshButtons() {
                var layer = getOrCreateLayer();
                var videos = document.querySelectorAll('video');
                var seen = {};

                for (var i = 0; i < videos.length; i++) {
                  var video = videos[i];
                  var rect = video.getBoundingClientRect();
                  if (rect.width < 80 || rect.height < 60) continue;

                  var id = getVideoId(video, i);
                  video.setAttribute('data-android-dl-id', id);
                  seen[id] = true;

                  var btn = getOrCreateButton(layer, id);
                  var top = Math.max(8, rect.bottom - 42);
                  var left = Math.max(8, rect.right - 120);
                  btn.style.top = top + 'px';
                  btn.style.left = left + 'px';
                  btn.style.display = (rect.bottom < 0 || rect.top > window.innerHeight) ? 'none' : 'block';
                }

                var allButtons = layer.querySelectorAll('button[data-video-id]');
                for (var b = 0; b < allButtons.length; b++) {
                  var button = allButtons[b];
                  if (!seen[button.dataset.videoId]) {
                    button.remove();
                  }
                }
              }

              refreshButtons();

              if (!window.__androidDlObserver) {
                var observer = new MutationObserver(function() { refreshButtons(); });
                observer.observe(document.documentElement || document.body, { childList: true, subtree: true });
                window.__androidDlObserver = observer;
              }

              if (!window.__androidDlRefreshBound) {
                window.addEventListener('scroll', refreshButtons, { passive: true });
                window.addEventListener('resize', refreshButtons);
                window.__androidDlRefreshBound = true;
              }

              if (!window.__androidDlTimer) {
                window.__androidDlTimer = setInterval(refreshButtons, 1200);
              }
            })();
        """.trimIndent()

        previewWebView.evaluateJavascript(js, null)
    }

    private fun startDownloadFromWebContainer() {
        if (currentPreviewUrl.isBlank()) {
            Toast.makeText(this, getString(R.string.invalid_url), Toast.LENGTH_SHORT).show()
            return
        }

        // Always re-detect on click to pick the currently playing/selected video
        detectVideoUrlFromPage { fromPage ->
            val resolvedUrl = resolveDownloadableUrl(fromPage)
            val targetUrl = resolvedUrl ?: detectedVideoUrl

            if (!targetUrl.isNullOrBlank()) {
                setDetectedVideoUrl(targetUrl)
                enqueueDownload(
                    urlText = targetUrl,
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
        val resolvedUrl = resolveDownloadableUrl(url) ?: return
        val firstDetection = detectedVideoUrl.isNullOrBlank()
        detectedVideoUrl = resolvedUrl
        webDownloadButton.isEnabled = true
        webDownloadButton.text = getString(R.string.download_from_webview)
        if (firstDetection) {
            Toast.makeText(this, getString(R.string.video_link_detected), Toast.LENGTH_SHORT).show()
        }
    }

    private fun detectVideoUrlFromPage(onDetected: (String?) -> Unit) {
        val js = """
            (function() {
              function pick(arr) {
                for (var i = 0; i < arr.length; i++) {
                  var u = arr[i];
                  if (!u) continue;
                  if (/\.(mp4|webm|mkv|mov|m3u8)(\?|$)/i.test(u)) return u;
                  if (/browser_native_(hd|sd)_url|playable_url|sd_src|hd_src/i.test(u)) return u;
                  if (/video|stream|playlist|manifest/i.test(u)) return u;
                }
                return '';
              }

              var videos = document.querySelectorAll('video');
              for (var vi = 0; vi < videos.length; vi++) {
                var vv = videos[vi];
                var isActive = !vv.paused || vv.currentTime > 0 || vv.autoplay;
                if (isActive && vv.currentSrc) return vv.currentSrc;
                if (isActive && vv.src) return vv.src;
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

              // Important for Facebook: parse embedded script data directly
              var scripts = document.querySelectorAll('script');
              var keyPatterns = [
                /"browser_native_hd_url"\s*:\s*"(https?:[^"]+)"/i,
                /"browser_native_sd_url"\s*:\s*"(https?:[^"]+)"/i,
                /"playable_url_quality_hd"\s*:\s*"(https?:[^"]+)"/i,
                /"playable_url"\s*:\s*"(https?:[^"]+)"/i,
                /"sd_src"\s*:\s*"(https?:[^"]+)"/i,
                /"hd_src"\s*:\s*"(https?:[^"]+)"/i
              ];

              for (var s = 0; s < scripts.length; s++) {
                var txt = scripts[s].textContent || '';
                if (!txt) continue;
                for (var p = 0; p < keyPatterns.length; p++) {
                  var match = txt.match(keyPatterns[p]);
                  if (match && match[1]) return match[1].replace(/\\/g, '');
                }
              }

              var links = document.querySelectorAll('a[href], *[data-href], *[data-src]');
              var candidateUrls = [];
              for (var k = 0; k < links.length; k++) {
                candidateUrls.push(links[k].href || links[k].getAttribute('data-href') || links[k].getAttribute('data-src') || '');
              }
              return pick(candidateUrls);
            })();
        """.trimIndent()

        previewWebView.evaluateJavascript(js) { rawResult ->
            val direct = cleanJsString(rawResult)
            if (!direct.isNullOrBlank()) {
                onDetected(direct)
            } else {
                detectVideoUrlFromPageSource(onDetected)
            }
        }
    }

    private fun detectVideoUrlFromPageSource(onDetected: (String?) -> Unit) {
        val js = "(function(){return document.documentElement ? document.documentElement.outerHTML : '';})();"
        previewWebView.evaluateJavascript(js) { rawHtml ->
            val html = cleanJsString(rawHtml).orEmpty()
            onDetected(extractVideoUrlFromRawHtml(html))
        }
    }

    private fun extractVideoUrlFromRawHtml(rawHtml: String): String? {
        if (rawHtml.isBlank()) return null

        val normalized = rawHtml
            .replace("\\u0025", "%")
            .replace("\\u002F", "/")
            .replace("\\/", "/")

        val priorityPatterns = listOf(
            Regex("""\"browser_native_hd_url\"\s*:\s*\"(https?:[^\"]+)\"""", RegexOption.IGNORE_CASE),
            Regex("""\"browser_native_sd_url\"\s*:\s*\"(https?:[^\"]+)\"""", RegexOption.IGNORE_CASE),
            Regex("""\"playable_url_quality_hd\"\s*:\s*\"(https?:[^\"]+)\"""", RegexOption.IGNORE_CASE),
            Regex("""\"playable_url\"\s*:\s*\"(https?:[^\"]+)\"""", RegexOption.IGNORE_CASE),
            Regex("""\"sd_src\"\s*:\s*\"(https?:[^\"]+)\"""", RegexOption.IGNORE_CASE),
            Regex("""\"hd_src\"\s*:\s*\"(https?:[^\"]+)\"""", RegexOption.IGNORE_CASE)
        )

        for (pattern in priorityPatterns) {
            val match = pattern.find(normalized)?.groupValues?.getOrNull(1)
            if (!match.isNullOrBlank()) return match.replace("\\", "")
        }

        val generic = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
            .findAll(normalized)
            .map { it.value }
            .firstOrNull { isLikelyVideoUrl(it) || it.contains(".mp4") || it.contains(".m3u8") }

        return generic
    }

    private fun cleanJsString(value: String?): String? {
        if (value == null || value == "null") return null
        val trimmed = value.trim().removePrefix("\"").removeSuffix("\"")
        val unescaped = trimmed.replace("\\\\/", "/").replace("\\\"", "\"")
        return if (unescaped.isBlank()) null else unescaped
    }


    private fun resolveDownloadableUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null

        val trimmed = rawUrl.trim()
        val normalized = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> Uri.parse(currentPreviewUrl).buildUpon().path(trimmed).build().toString()
            else -> {
                try {
                    URI(currentPreviewUrl).resolve(trimmed).toString()
                } catch (_: Exception) {
                    null
                }
            }
        } ?: return null

        return if (validateUrl(normalized) == UrlValidation.VALID) normalized else null
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
        DownloadHistoryStore.saveRecord(this, downloadId, fileName)

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

    private fun validateUrl(url: String): UrlValidation {
        if (url.isBlank()) return UrlValidation.INVALID

        val uri = Uri.parse(url)
        if (uri.scheme != "http" && uri.scheme != "https") return UrlValidation.INVALID

        return UrlValidation.VALID
    }

}

enum class UrlValidation {
    VALID,
    INVALID
}
