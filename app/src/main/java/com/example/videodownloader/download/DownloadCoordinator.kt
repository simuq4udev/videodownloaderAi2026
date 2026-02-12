package com.example.videodownloader.download

import com.example.videodownloader.data.DownloadItem
import com.example.videodownloader.data.DownloadStatus
import com.example.videodownloader.network.NetworkModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
            var existingBytes = if (output.exists()) output.length() else 0L
            var response = executeDownloadRequest(item, directUrl, existingBytes)

            // Some CDNs reject range or hotlink requests with 400/403/416.
            if ((response.code == 400 || response.code == 403 || response.code == 416) && existingBytes > 0L) {
                response.close()
                output.delete()
                existingBytes = 0L
                response = executeDownloadRequest(item, directUrl, existingBytes)
            }

            if (!response.isSuccessful) {
                _events.emit(item.copy(status = DownloadStatus.ERROR, errorMessage = "HTTP ${response.code}. Try a public post URL and open it once in browser."))
                response.close()
                return
            }

            val body = response.body ?: run {
                _events.emit(item.copy(status = DownloadStatus.ERROR, errorMessage = "Empty response body"))
                response.close()
                return
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
        } catch (cancelled: CancellationException) {
            _events.emit(item.copy(status = DownloadStatus.PAUSED, directVideoUrl = directUrl, downloadedBytes = output.length()))
        } catch (e: Exception) {
            _events.emit(item.copy(status = DownloadStatus.ERROR, errorMessage = e.message, directVideoUrl = directUrl))
        } finally {
            jobs.remove(item.id)
        }
    }

    private fun executeDownloadRequest(item: DownloadItem, directUrl: String, existingBytes: Long): Response {
        val requestBuilder = Request.Builder()
            .url(directUrl)
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept", "video/*,*/*;q=0.8")
            .header("Referer", item.sourceUrl)
            .header("Origin", originOf(item.sourceUrl))

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

    companion object {
        private const val DEFAULT_BUFFER = 8 * 1024
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}
