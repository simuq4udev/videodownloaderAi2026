package com.example.videodownloader.viewmodel

import android.app.Application
import android.media.ThumbnailUtils
import android.os.Build
import android.util.Size
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.videodownloader.data.DownloadItem
import com.example.videodownloader.data.DownloadStatus
import com.example.videodownloader.data.SettingsRepository
import com.example.videodownloader.download.DownloadCoordinator
import com.example.videodownloader.network.NetworkModule
import com.example.videodownloader.network.VideoUrlExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val extractor = VideoUrlExtractor(NetworkModule.pageService)
    private val settings = SettingsRepository(application)
    private val coordinator = DownloadCoordinator(settings.maxSimultaneousDownloads())

    private val _items = MutableLiveData<List<DownloadItem>>(emptyList())
    val items: LiveData<List<DownloadItem>> = _items

    init {
        viewModelScope.launch {
            coordinator.events.collectLatest { updated ->
                replaceItem(updated)
                if (updated.status == DownloadStatus.COMPLETED) {
                    attachThumbnail(updated)
                }
            }
        }
    }

    fun enqueue(url: String) {
        val fileName = "video_${System.currentTimeMillis()}.mp4"
        val outputDir = File(settings.defaultDirectory(getApplication()))
        outputDir.mkdirs()
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            sourceUrl = url,
            title = "Queued video",
            fileName = fileName,
            outputFile = File(outputDir, fileName),
            status = DownloadStatus.EXTRACTING
        )
        addItem(item)

        viewModelScope.launch {
            try {
                val directUrl = withContext(Dispatchers.IO) { extractor.resolveDirectVideoUrl(url) }
                if (directUrl.isNullOrBlank()) {
                    replaceItem(item.copy(status = DownloadStatus.ERROR, errorMessage = "Could not find direct video URL."))
                    return@launch
                }
                val title = runCatching { url.substringAfter("//").substringBefore("/") + " video" }.getOrDefault("Video")
                val enriched = item.copy(title = title, directVideoUrl = directUrl, status = DownloadStatus.QUEUED)
                replaceItem(enriched)
                coordinator.queue(enriched, directUrl)
            } catch (e: Exception) {
                replaceItem(item.copy(status = DownloadStatus.ERROR, errorMessage = e.message ?: "Unknown error"))
            }
        }
    }

    fun pause(item: DownloadItem) = coordinator.pause(item)

    fun resume(item: DownloadItem) = coordinator.resume(item)

    fun refreshSettings() {
        coordinator.updateMaxConcurrent(settings.maxSimultaneousDownloads())
    }

    private fun addItem(item: DownloadItem) {
        _items.value = listOf(item) + (_items.value ?: emptyList())
    }

    private fun replaceItem(item: DownloadItem) {
        _items.value = (_items.value ?: emptyList()).map { current ->
            if (current.id == item.id) item else current
        }
    }

    private fun attachThumbnail(item: DownloadItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createVideoThumbnail(item.outputFile, Size(320, 180), null)
            } else {
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(item.outputFile.path, android.provider.MediaStore.Video.Thumbnails.MINI_KIND)
            }
            if (bitmap != null) {
                val thumbFile = File(item.outputFile.parentFile, "thumb_${item.id}.jpg")
                thumbFile.outputStream().use { out -> bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out) }
                replaceItem(item.copy(thumbnailPath = thumbFile.absolutePath))
            }
        }
    }
}
