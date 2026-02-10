package com.example.videodownloader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class DownloadItemUi(
    val id: Long,
    val fileName: String,
    val url: String,
    val statusText: String,
    val progress: Int,
    val isDone: Boolean
)

class DownloadListAdapter : RecyclerView.Adapter<DownloadListAdapter.DownloadViewHolder>() {
    private val items = mutableListOf<DownloadItemUi>()

    fun addItem(item: DownloadItemUi) {
        items.add(0, item)
        notifyItemInserted(0)
    }

    fun updateItem(updated: DownloadItemUi) {
        val index = items.indexOfFirst { it.id == updated.id }
        if (index != -1) {
            items[index] = updated
            notifyItemChanged(index)
        }
    }

    fun activeDownloadIds(): List<Long> = items.filter { !it.isDone }.map { it.id }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download, parent, false)
        return DownloadViewHolder(view)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class DownloadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.download_item_title)
        private val statusText: TextView = itemView.findViewById(R.id.download_item_status)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.download_item_progress)

        fun bind(item: DownloadItemUi) {
            titleText.text = item.fileName
            statusText.text = item.statusText
            progressBar.progress = item.progress
            progressBar.visibility = if (item.isDone) View.GONE else View.VISIBLE
        }
    }
}
