package com.example.videodownloader

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val urlInput = findViewById<EditText>(R.id.url_input)
        val downloadButton = findViewById<Button>(R.id.download_button)
        val downloadsRecyclerView = findViewById<RecyclerView>(R.id.downloads_recycler_view)

        downloadsRecyclerView.layoutManager = LinearLayoutManager(this)
        downloadsRecyclerView.adapter = DownloadListAdapter(
            listOf(
                "Sample Video 1",
                "Sample Video 2",
                "Sample Video 3"
            )
        )

        downloadButton.setOnClickListener {
            urlInput.clearFocus()
        }
    }
}
