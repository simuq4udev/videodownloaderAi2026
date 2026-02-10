package com.example.videodownloader.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.videodownloader.R

class DownloadQueueAdapter(
    private val onPauseClicked: (Long) -> Unit,
    private val onResumeClicked: (Long) -> Unit
) : RecyclerView.Adapter<DownloadQueueAdapter.QueueViewHolder>() {

    private var items: List<DownloadQueueItem> = emptyList()

    fun submitList(newItems: List<DownloadQueueItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_download_queue, parent, false)
        return QueueViewHolder(view, onPauseClicked, onResumeClicked)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class QueueViewHolder(
        itemView: View,
        private val onPauseClicked: (Long) -> Unit,
        private val onResumeClicked: (Long) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.queue_file_name)
        private val progressText: TextView = itemView.findViewById(R.id.queue_progress_text)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.queue_progress_bar)
        private val pauseButton: Button = itemView.findViewById(R.id.queue_pause_button)
        private val resumeButton: Button = itemView.findViewById(R.id.queue_resume_button)

        fun bind(item: DownloadQueueItem) {
            title.text = item.fileName
            progressText.text = itemView.context.getString(R.string.queue_progress_format, item.progress, item.status.name)
            progressBar.progress = item.progress

            pauseButton.isEnabled = item.status == QueueStatus.DOWNLOADING || item.status == QueueStatus.QUEUED
            resumeButton.isEnabled = item.status == QueueStatus.PAUSED || item.status == QueueStatus.FAILED

            pauseButton.setOnClickListener { onPauseClicked(item.id) }
            resumeButton.setOnClickListener { onResumeClicked(item.id) }
        }
    }
}
