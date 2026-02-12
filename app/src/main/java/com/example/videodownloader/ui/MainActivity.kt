package com.example.videodownloader.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.videodownloader.R
import com.example.videodownloader.data.DownloadStatus
import com.example.videodownloader.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op: user can retry */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ensureRuntimePermissions()

        val urlInput = findViewById<EditText>(R.id.video_url_input)
        val downloadButton = findViewById<Button>(R.id.download_button)
        val settingsButton = findViewById<Button>(R.id.settings_button)
        val aboutButton = findViewById<Button>(R.id.about_button)
        val recycler = findViewById<RecyclerView>(R.id.download_list)

        val adapter = DownloadAdapter { item ->
            when (item.status) {
                DownloadStatus.DOWNLOADING -> viewModel.pause(item)
                DownloadStatus.PAUSED, DownloadStatus.ERROR -> viewModel.resume(item)
                else -> Unit
            }
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        downloadButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (!isValidUrl(url)) {
                Toast.makeText(this, getString(R.string.invalid_url), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (url.contains("youtube.com") || url.contains("youtu.be")) {
                Toast.makeText(this, getString(R.string.youtube_not_supported), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            viewModel.enqueue(url)
            urlInput.setText("")
        }

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        aboutButton.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        viewModel.items.observe(this) { adapter.submitList(it) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSettings()
    }

    private fun ensureRuntimePermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.READ_MEDIA_VIDEO)
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return android.util.Patterns.WEB_URL.matcher(url).matches()
    }
}
