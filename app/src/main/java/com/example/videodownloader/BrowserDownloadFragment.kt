package com.example.videodownloader

import android.Manifest
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class BrowserDownloadFragment : Fragment(R.layout.fragment_browser_download) {
    private lateinit var webView: WebView
    private lateinit var downloadDetectedButton: Button
    private lateinit var openExternallyButton: Button
    private lateinit var downloadManager: DownloadManager

    private var pendingDownloadUrl: String? = null
    private var detectedVideoUrl: String? = null
    private var currentPageUrl: String? = null

    private val okHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pendingDownloadUrl?.let { enqueueDownload(it) }
            } else {
                Toast.makeText(requireContext(), R.string.storage_permission_required, Toast.LENGTH_SHORT).show()
            }
            pendingDownloadUrl = null
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        webView = view.findViewById(R.id.in_app_webview)
        downloadDetectedButton = view.findViewById(R.id.download_detected_button)
        openExternallyButton = view.findViewById(R.id.open_external_button)

        setupWebView()

        val initialUrl = requireArguments().getString(ARG_URL).orEmpty()
        resolveAndLoadUrl(initialUrl)

        openExternallyButton.setOnClickListener {
            val target = currentPageUrl ?: initialUrl
            openInExternalBrowser(target)
        }

        downloadDetectedButton.setOnClickListener {
            val url = detectedVideoUrl
            if (url.isNullOrBlank()) {
                Toast.makeText(requireContext(), R.string.no_video_detected, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (needsStoragePermission() && !hasStoragePermission()) {
                pendingDownloadUrl = url
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return@setOnClickListener
            }

            enqueueDownload(url)
        }
    }

    private fun resolveAndLoadUrl(initialUrl: String) {
        Thread {
            val resolved = tryResolveFinalUrl(initialUrl)
            requireActivity().runOnUiThread {
                currentPageUrl = resolved
                webView.loadUrl(resolved)
            }
        }.start()
    }

    private fun tryResolveFinalUrl(initialUrl: String): String {
        return try {
            val request = Request.Builder()
                .url(initialUrl)
                .header("User-Agent", MOBILE_UA)
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        } catch (_: Exception) {
            initialUrl
        }
    }

    private fun setupWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.userAgentString = MOBILE_UA
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString().orEmpty()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    currentPageUrl = url
                    return false
                }
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    true
                } catch (_: Exception) {
                    true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!url.isNullOrBlank()) currentPageUrl = url
                detectVideoViaJavascript()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    Toast.makeText(requireContext(), R.string.webview_load_failed_try_external, Toast.LENGTH_SHORT).show()
                }
            }
        }

        webView.setDownloadListener { url, _, _, _, _ ->
            detectedVideoUrl = url
            downloadDetectedButton.isEnabled = true
            downloadDetectedButton.text = getString(R.string.download_detected_button_text)
        }
    }

    private fun detectVideoViaJavascript() {
        val js = """
            (function() {
              var v = document.querySelector('video');
              if (v && v.currentSrc) return v.currentSrc;
              if (v && v.src) return v.src;
              var source = document.querySelector('video source');
              if (source && source.src) return source.src;
              return '';
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { raw ->
            val value = raw?.trim()?.trim('"') ?: ""
            if (value.isNotBlank() && value.startsWith("http")) {
                detectedVideoUrl = value
                downloadDetectedButton.isEnabled = true
                downloadDetectedButton.text = getString(R.string.download_detected_button_text)
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
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
            downloadManager.enqueue(request)
            Toast.makeText(requireContext(), R.string.download_started, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.download_failed_to_start, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openInExternalBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.no_browser_found, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroyView()
    }

    companion object {
        private const val ARG_URL = "arg_url"
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"

        fun newInstance(url: String): BrowserDownloadFragment {
            return BrowserDownloadFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url)
                }
            }
        }
    }
}
