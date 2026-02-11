package com.example.videodownloader

import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var urlInput: EditText
    private lateinit var downloadButton: Button
    private lateinit var settingsButton: Button
    private lateinit var historyButton: Button
    private lateinit var downloadsRecyclerView: RecyclerView
    private lateinit var fragmentContainer: View
    private val adapter = DownloadListAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.url_input)
        downloadButton = findViewById(R.id.download_button)
        settingsButton = findViewById(R.id.settings_button)
        historyButton = findViewById(R.id.history_button)
        downloadsRecyclerView = findViewById(R.id.downloads_recycler_view)
        fragmentContainer = findViewById(R.id.fragment_container)

        downloadsRecyclerView.layoutManager = LinearLayoutManager(this)
        downloadsRecyclerView.adapter = adapter

        supportFragmentManager.addOnBackStackChangedListener {
            fragmentContainer.visibility =
                if (supportFragmentManager.backStackEntryCount > 0) View.VISIBLE else View.GONE
        }


        historyButton.setOnClickListener {
            fragmentContainer.visibility = View.VISIBLE
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, DownloadHistoryFragment())
                .addToBackStack("history")
                .commit()
        }

        settingsButton.setOnClickListener {
            fragmentContainer.visibility = View.VISIBLE
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment())
                .addToBackStack("settings")
                .commit()
        }

        downloadButton.setOnClickListener {
            val normalizedUrl = normalizeUrl(urlInput.text.toString().trim())
            if (!isValidWebUrl(normalizedUrl)) {
                Toast.makeText(this, R.string.invalid_video_page_url, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            fragmentContainer.visibility = View.VISIBLE
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, BrowserDownloadFragment.newInstance(normalizedUrl))
                .addToBackStack("browser_download")
                .commit()
        }
    }

    private fun normalizeUrl(url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        return "https://$url"
    }

    private fun isValidWebUrl(url: String): Boolean {
        return Patterns.WEB_URL.matcher(url).matches()
    }
}
