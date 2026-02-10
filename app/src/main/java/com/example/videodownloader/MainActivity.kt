package com.example.videodownloader

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var urlInput: EditText
    private lateinit var downloadButton: Button
    private lateinit var settingsButton: Button
    private lateinit var downloadsRecyclerView: RecyclerView
    private val adapter = DownloadListAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.url_input)
        downloadButton = findViewById(R.id.download_button)
        settingsButton = findViewById(R.id.settings_button)
        downloadsRecyclerView = findViewById(R.id.downloads_recycler_view)

        downloadsRecyclerView.layoutManager = LinearLayoutManager(this)
        downloadsRecyclerView.adapter = adapter

        settingsButton.setOnClickListener {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.main_root, SettingsFragment())
                .addToBackStack("settings")
                .commit()
        }

        downloadButton.setOnClickListener {
            val rawUrl = urlInput.text.toString().trim()
            val finalUrl = normalizeUrl(rawUrl)
            if (!isValidWebUrl(finalUrl)) {
                Toast.makeText(this, R.string.invalid_video_page_url, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            openInBrowser(finalUrl)
        }
    }

    private fun normalizeUrl(url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        return "https://$url"
    }

    private fun isValidWebUrl(url: String): Boolean {
        return Patterns.WEB_URL.matcher(url).matches()
    }

    private fun openInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        try {
            startActivity(intent)
            adapter.addItem(
                DownloadItemUi(
                    id = System.currentTimeMillis(),
                    fileName = url,
                    statusText = getString(R.string.opened_in_browser_status),
                    progress = 100,
                    isDone = true
                )
            )
            Toast.makeText(this, R.string.browser_download_instruction, Toast.LENGTH_LONG).show()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_browser_found, Toast.LENGTH_SHORT).show()
        }
    }
}
