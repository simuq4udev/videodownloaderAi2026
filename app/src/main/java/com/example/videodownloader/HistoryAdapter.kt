package com.example.videodownloader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter(
    private var items: List<DownloadHistoryItem>
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<DownloadHistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnailView: ImageView = itemView.findViewById(R.id.history_thumbnail)
        private val fileNameView: TextView = itemView.findViewById(R.id.history_file_name)
        private val urlView: TextView = itemView.findViewById(R.id.history_url)
        private val timeView: TextView = itemView.findViewById(R.id.history_time)

        fun bind(item: DownloadHistoryItem) {
            thumbnailView.setImageResource(android.R.drawable.ic_media_play)
            fileNameView.text = item.fileName
            urlView.text = item.url
            timeView.text = item.formattedTime
        }
    }
}
