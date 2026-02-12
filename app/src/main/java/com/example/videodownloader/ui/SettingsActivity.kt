package com.example.videodownloader.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.videodownloader.R
import com.example.videodownloader.viewmodel.SettingsViewModel

class SettingsActivity : AppCompatActivity() {
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val dirInput = findViewById<EditText>(R.id.default_dir_input)
        val maxInput = findViewById<EditText>(R.id.max_downloads_input)
        val saveButton = findViewById<Button>(R.id.save_settings_button)

        dirInput.setText(viewModel.directory())
        maxInput.setText(viewModel.maxDownloads().toString())

        saveButton.setOnClickListener {
            val dir = dirInput.text.toString().trim()
            val max = maxInput.text.toString().toIntOrNull() ?: 2
            viewModel.save(dir, max)
            Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
