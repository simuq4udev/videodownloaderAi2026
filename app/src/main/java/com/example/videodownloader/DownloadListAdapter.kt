package com.example.videodownloader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class DownloadHistoryItem(
    val downloadId: Long,
    val title: String,
    val detail: String
)

class DownloadListAdapter(
    private val onItemClick: (DownloadHistoryItem) -> Unit
) : RecyclerView.Adapter<DownloadListAdapter.DownloadViewHolder>() {

    private val items = mutableListOf<DownloadHistoryItem>()

    fun submitItems(newItems: List<DownloadHistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download, parent, false)
        return DownloadViewHolder(view)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class DownloadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.download_item_title)
        private val detailText: TextView = itemView.findViewById(R.id.download_item_detail)

        fun bind(item: DownloadHistoryItem) {
            titleText.text = item.title
            detailText.text = item.detail
        }
    }
}
