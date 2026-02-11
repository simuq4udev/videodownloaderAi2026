package com.example.videodownloader

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class DownloadHistoryItem(
    val id: Long,
    val title: String,
    val localUri: String,
    val mimeType: String?
)

class DownloadHistoryFragment : Fragment(R.layout.fragment_download_history) {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DownloadHistoryAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.history_recycler_view)
        adapter = DownloadHistoryAdapter(::openVideo)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL)
        val cursor = dm.query(query)

        val items = mutableListOf<DownloadHistoryItem>()
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val titleIdx = it.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
            val uriIdx = it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
            val mimeIdx = it.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE)

            while (it.moveToNext()) {
                val localUri = it.getString(uriIdx) ?: continue
                if (!localUri.contains("VideoDownloader")) continue

                items.add(
                    DownloadHistoryItem(
                        id = it.getLong(idIdx),
                        title = it.getString(titleIdx) ?: getString(R.string.unknown_file),
                        localUri = localUri,
                        mimeType = it.getString(mimeIdx)
                    )
                )
            }
        }
        adapter.submit(items)
    }

    private fun openVideo(item: DownloadHistoryItem) {
        val uri = Uri.parse(item.localUri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType ?: "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.no_video_player_found, Toast.LENGTH_SHORT).show()
        }
    }
}
