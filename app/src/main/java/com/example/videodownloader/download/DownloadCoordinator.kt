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
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.Semaphore

class DownloadCoordinator(maxConcurrent: Int = 2) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val semaphore = Semaphore(maxConcurrent)

    private val _events = MutableSharedFlow<DownloadItem>(extraBufferCapacity = 128)
    val events: SharedFlow<DownloadItem> = _events

    fun updateMaxConcurrent(maxConcurrent: Int) {
        // Simplified: recreate only if no active jobs.
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

        val existingBytes = if (output.exists()) output.length() else 0L
        val requestBuilder = Request.Builder().url(directUrl)
        if (existingBytes > 0L) {
            requestBuilder.addHeader("Range", "bytes=$existingBytes-")
        }

        _events.emit(item.copy(status = DownloadStatus.DOWNLOADING, directVideoUrl = directUrl))

        try {
            val response = NetworkModule.okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                _events.emit(item.copy(status = DownloadStatus.ERROR, errorMessage = "Network error: ${response.code}"))
                return
            }

            val body = response.body ?: run {
                _events.emit(item.copy(status = DownloadStatus.ERROR, errorMessage = "Empty response body"))
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

    companion object {
        private const val DEFAULT_BUFFER = 8 * 1024
    }
}
