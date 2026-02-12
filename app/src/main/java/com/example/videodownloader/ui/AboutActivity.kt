package com.example.videodownloader.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.videodownloader.BuildConfig
import com.example.videodownloader.R

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val text = findViewById<TextView>(R.id.about_text)
        text.text = getString(
            R.string.about_template,
            getString(R.string.app_name),
            BuildConfig.VERSION_NAME
        )
    }
}
