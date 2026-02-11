package com.example.videodownloader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DownloadHistoryAdapter(
    private val onClick: (DownloadHistoryItem) -> Unit
) : RecyclerView.Adapter<DownloadHistoryAdapter.HistoryViewHolder>() {
    private val items = mutableListOf<DownloadHistoryItem>()

    fun submit(newItems: List<DownloadHistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount(): Int = items.size

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.history_item_title)
        private val subtitle: TextView = itemView.findViewById(R.id.history_item_subtitle)

        fun bind(item: DownloadHistoryItem, onClick: (DownloadHistoryItem) -> Unit) {
            title.text = item.title
            subtitle.text = item.localUri
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
