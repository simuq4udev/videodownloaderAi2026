package com.example.videodownloader.download

import com.example.videodownloader.data.DownloadItem
import com.example.videodownloader.data.DownloadStatus
import com.example.videodownloader.network.NetworkModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import java.io.RandomAccessFile
import java.net.URL
import java.util.concurrent.Semaphore

class DownloadCoordinator(maxConcurrent: Int = 2) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val semaphore = Semaphore(maxConcurrent)

    private val _events = MutableSharedFlow<DownloadItem>(extraBufferCapacity = 128)
    val events: SharedFlow<DownloadItem> = _events

    fun updateMaxConcurrent(maxConcurrent: Int) {
        if (jobs.isEmpty()) {
            while (semaphore.availablePermits() < maxConcurrent) {
                semaphore.release()
            }
        }
    }

    fun queue(item: DownloadItem, directUrl: String) {
        val job = scope.launch {
            semaphore.acquire()
            try {
                performDownload(item, directUrl)
            } finally {
                semaphore.release()
            }
        }
        jobs[item.id] = job
    }

    fun pause(item: DownloadItem) {
        jobs[item.id]?.cancel(CancellationException("Paused by user"))
        jobs.remove(item.id)
        _events.tryEmit(item.copy(status = DownloadStatus.PAUSED))
    }

    fun resume(item: DownloadItem) {
        val direct = item.directVideoUrl ?: return
        queue(item.copy(status = DownloadStatus.QUEUED), direct)
    }

    private suspend fun performDownload(item: DownloadItem, directUrl: String) {
        val output = item.outputFile
        output.parentFile?.mkdirs()

        _events.emit(item.copy(status = DownloadStatus.DOWNLOADING, directVideoUrl = directUrl))

        try {
            var lastError = "Unknown download error"

            repeat(MAX_RETRIES) { attemptIndex ->
                val attempt = attemptIndex + 1
                val result = runDownloadAttempt(item, directUrl)
                if (result.isSuccess) {
                    return
                }

                lastError = result.errorMessage ?: "Download failed"
                if (!result.isRetryable || attempt == MAX_RETRIES) {
                    return@repeat
                }

                delay(RETRY_DELAYS_MS[attemptIndex])
            }

            _events.emit(item.copy(status = DownloadStatus.ERROR, errorMessage = lastError, directVideoUrl = directUrl))
        } catch (cancelled: CancellationException) {
            _events.emit(item.copy(status = DownloadStatus.PAUSED, directVideoUrl = directUrl, downloadedBytes = output.length()))
        } catch (e: Exception) {
            _events.emit(item.copy(status = DownloadStatus.ERROR, errorMessage = e.message, directVideoUrl = directUrl))
        } finally {
            jobs.remove(item.id)
        }
    }

    private suspend fun runDownloadAttempt(item: DownloadItem, directUrl: String): AttemptResult {
        val output = item.outputFile
        var existingBytes = if (output.exists()) output.length() else 0L

        var response = executeDownloadRequest(item, directUrl, existingBytes)

        // Range request fallback: restart from 0 if CDN rejects resume request.
        if ((response.code == 400 || response.code == 403 || response.code == 416) && existingBytes > 0L) {
            response.close()
            output.delete()
            existingBytes = 0L
            response = executeDownloadRequest(item, directUrl, existingBytes)
        }

        if (!response.isSuccessful) {
            val retryable = response.code in listOf(408, 425, 429, 500, 502, 503, 504)
            val message = "HTTP ${response.code}. ${if (response.code == 429) "Rate limited, retrying..." else "Try public post URL or reopen link in browser."}"
            response.close()
            return AttemptResult(false, retryable, message)
        }

        // If resume requested but server ignored range and returned 200, restart file to avoid corruption.
        if (existingBytes > 0L && response.code == 200) {
            output.delete()
            existingBytes = 0L
        }

        val body = response.body ?: run {
            response.close()
            return AttemptResult(false, true, "Empty response body")
        }

        val contentType = body.contentType()?.toString().orEmpty().lowercase()
        if (contentType.contains("text/html") || contentType.contains("application/json")) {
            response.close()
            return AttemptResult(false, false, "Received webpage instead of video. Open post in browser then retry.")
        }

        val totalBytes = body.contentLength().let {
            if (it > 0 && existingBytes > 0) it + existingBytes else it
        }

        RandomAccessFile(output, "rw").use { raf ->
            if (existingBytes > 0L) raf.seek(existingBytes)
            body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER)
                var downloaded = existingBytes
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    raf.write(buffer, 0, read)
                    downloaded += read

                    val progress = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt() else 0
                    _events.tryEmit(
                        item.copy(
                            progress = progress,
                            downloadedBytes = downloaded,
                            totalBytes = totalBytes,
                            status = DownloadStatus.DOWNLOADING,
                            directVideoUrl = directUrl
                        )
                    )
                }
            }
        }

        response.close()

        _events.emit(
            item.copy(
                progress = 100,
                downloadedBytes = output.length(),
                totalBytes = output.length(),
                status = DownloadStatus.COMPLETED,
                directVideoUrl = directUrl
            )
        )

        return AttemptResult(true, false, null)
    }

    private fun executeDownloadRequest(item: DownloadItem, directUrl: String, existingBytes: Long): Response {
        val requestBuilder = Request.Builder()
            .url(directUrl)
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept", "video/*,*/*;q=0.8")
            .header("Referer", item.sourceUrl)
            .header("Origin", originOf(item.sourceUrl))
            .header("Connection", "keep-alive")

        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        return NetworkModule.okHttpClient.newCall(requestBuilder.build()).execute()
    }

    private fun originOf(url: String): String {
        return runCatching {
            val parsed = URL(url)
            "${parsed.protocol}://${parsed.host}"
        }.getOrDefault(url)
    }

    private data class AttemptResult(
        val isSuccess: Boolean,
        val isRetryable: Boolean,
        val errorMessage: String?
    )

    companion object {
        private const val DEFAULT_BUFFER = 8 * 1024
        private const val MAX_RETRIES = 3
        private val RETRY_DELAYS_MS = listOf(1000L, 2000L, 4000L)
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}
