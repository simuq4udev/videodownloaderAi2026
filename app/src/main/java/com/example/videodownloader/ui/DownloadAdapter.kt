package com.example.videodownloader.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.videodownloader.R
import com.example.videodownloader.data.DownloadItem
import com.example.videodownloader.data.DownloadStatus
import java.io.File

class DownloadAdapter(
    private val onPauseResumeClick: (DownloadItem) -> Unit
) : ListAdapter<DownloadItem, DownloadAdapter.DownloadViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_download, parent, false)
        return DownloadViewHolder(view)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position), onPauseResumeClick)
    }

    class DownloadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.download_title)
        private val subtitle: TextView = itemView.findViewById(R.id.download_subtitle)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.download_progress)
        private val action: TextView = itemView.findViewById(R.id.download_action)
        private val thumbnail: ImageView = itemView.findViewById(R.id.download_thumbnail)

        fun bind(item: DownloadItem, onPauseResumeClick: (DownloadItem) -> Unit) {
            title.text = item.title
            subtitle.text = item.errorMessage ?: "${item.progress}% • ${item.status.name}"
            progressBar.progress = item.progress

            val actionText = when (item.status) {
                DownloadStatus.DOWNLOADING -> "Pause"
                DownloadStatus.PAUSED, DownloadStatus.ERROR -> "Resume"
                DownloadStatus.COMPLETED -> "Done"
                else -> "..."
            }
            action.text = actionText
            action.isEnabled = item.status != DownloadStatus.COMPLETED
            action.setOnClickListener { onPauseResumeClick(item) }

            val thumbPath = item.thumbnailPath
            if (!thumbPath.isNullOrBlank() && File(thumbPath).exists()) {
                thumbnail.setImageURI(android.net.Uri.fromFile(File(thumbPath)))
            } else {
                thumbnail.setImageResource(R.drawable.ic_video_placeholder)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<DownloadItem>() {
            override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean = oldItem == newItem
        }
    }
}
