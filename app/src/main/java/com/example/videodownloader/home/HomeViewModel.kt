package com.example.videodownloader.home

import android.app.Application
import android.app.DownloadManager
import android.net.Uri
import android.webkit.URLUtil
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videodownloader.DownloadHistoryItem
import com.example.videodownloader.DownloadHistoryStore
import com.example.videodownloader.DownloadPreferences
import com.example.videodownloader.R
import com.example.videodownloader.network.VideoResolverRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val items: List<DownloadQueueItem> = emptyList(),
    val statusMessage: String = ""
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val manager = app.getSystemService(DownloadManager::class.java)
    private val preferences = DownloadPreferences(app)
    private val historyStore = DownloadHistoryStore(app)
    private val resolverRepository = VideoResolverRepository()
    private val mutableState = MutableStateFlow(HomeUiState(statusMessage = app.getString(R.string.status_placeholder)))
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    private val queue = linkedMapOf<Long, DownloadQueueItem>()
    private var pollJob: Job? = null

    fun enqueueDownload(sourceUrl: String) {
        viewModelScope.launch {
            runCatching {
                resolverRepository.resolveDirectMediaUrl(sourceUrl)
            }.onFailure {
                updateStatus(it.message ?: app.getString(R.string.error_invalid_url))
                return@launch
            }.onSuccess { directUrl ->
                val activeDownloads = queue.values.count { it.status == QueueStatus.DOWNLOADING || it.status == QueueStatus.QUEUED }
                if (activeDownloads >= preferences.maxSimultaneousDownloads) {
                    updateStatus(app.getString(R.string.error_max_simultaneous, preferences.maxSimultaneousDownloads))
                    return@onSuccess
                }

                val fileName = URLUtil.guessFileName(directUrl, null, "video/mp4")
                val request = DownloadManager.Request(Uri.parse(directUrl))
                    .setTitle(fileName)
                    .setDescription(app.getString(R.string.download_description))
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverMetered(!preferences.wifiOnly)
                    .setAllowedOverRoaming(false)
                    .setDestinationInExternalPublicDir(preferences.defaultDirectory, fileName)

                val downloadId = manager.enqueue(request)
                queue[downloadId] = DownloadQueueItem(
                    id = downloadId,
                    url = directUrl,
                    fileName = fileName,
                    progress = 0,
                    status = QueueStatus.QUEUED
                )
                publishQueue(app.getString(R.string.download_started, fileName))
                startPolling()
            }
        }
    }

    fun pauseDownload(id: Long) {
        val item = queue[id] ?: return
        manager.remove(id)
        queue[id] = item.copy(status = QueueStatus.PAUSED)
        publishQueue(app.getString(R.string.download_paused, item.fileName))
    }

    fun resumeDownload(id: Long) {
        val item = queue[id] ?: return
        queue.remove(id)
        enqueueDownload(item.url)
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                refreshProgress()
                delay(1000)
            }
        }
    }

    private suspend fun refreshProgress() = withContext(Dispatchers.IO) {
        if (queue.isEmpty()) return@withContext
        val updated = queue.toMutableMap()

        queue.values.forEach { item ->
            if (item.status == QueueStatus.PAUSED) return@forEach
            val query = DownloadManager.Query().setFilterById(item.id)
            manager.query(query).use { cursor ->
                if (!cursor.moveToFirst()) {
                    updated[item.id] = item.copy(status = QueueStatus.FAILED, reason = app.getString(R.string.error_download_failed))
                    return@use
                }

                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val bytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                val status = cursor.getInt(statusIndex)
                val bytes = cursor.getLong(bytesIndex)
                val total = cursor.getLong(totalIndex)
                val progress = if (total > 0) ((bytes * 100) / total).toInt() else 0

                val queueStatus = when (status) {
                    DownloadManager.STATUS_RUNNING -> QueueStatus.DOWNLOADING
                    DownloadManager.STATUS_SUCCESSFUL -> QueueStatus.COMPLETED
                    DownloadManager.STATUS_FAILED -> QueueStatus.FAILED
                    DownloadManager.STATUS_PAUSED -> QueueStatus.PAUSED
                    else -> QueueStatus.QUEUED
                }

                updated[item.id] = item.copy(progress = progress.coerceIn(0, 100), status = queueStatus)
                if (queueStatus == QueueStatus.COMPLETED) {
                    historyStore.add(
                        DownloadHistoryItem(
                            downloadId = item.id,
                            url = item.url,
                            fileName = item.fileName,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        queue.clear()
        queue.putAll(updated)
        publishQueue(mutableState.value.statusMessage)
    }

    private fun updateStatus(message: String) {
        mutableState.value = mutableState.value.copy(statusMessage = message)
    }

    private fun publishQueue(statusMessage: String) {
        mutableState.value = HomeUiState(
            items = queue.values.toList().sortedByDescending { it.id },
            statusMessage = statusMessage
        )
    }
}
