package com.example.videodownloader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HistoryFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.history_list)
        val emptyState = view.findViewById<TextView>(R.id.history_empty)
        val clearButton = view.findViewById<Button>(R.id.history_clear)
        val store = DownloadHistoryStore(requireContext())
        val adapter = HistoryAdapter(emptyList())

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        fun refresh() {
            val items = store.load()
            adapter.submitList(items)
            val showEmpty = items.isEmpty()
            emptyState.visibility = if (showEmpty) View.VISIBLE else View.GONE
            recyclerView.visibility = if (showEmpty) View.GONE else View.VISIBLE
        }

        clearButton.setOnClickListener {
            store.clear()
            refresh()
        }

        refresh()
    }
}
