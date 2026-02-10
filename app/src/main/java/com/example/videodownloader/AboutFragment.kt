package com.example.videodownloader

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment

class AboutFragment : Fragment(R.layout.fragment_about) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appNameText = view.findViewById<TextView>(R.id.about_app_name)
        val versionText = view.findViewById<TextView>(R.id.about_version)
        val copyrightText = view.findViewById<TextView>(R.id.about_copyright)

        appNameText.text = getString(R.string.app_name)
        versionText.text = getString(R.string.about_version_value, BuildConfig.VERSION_NAME)
        copyrightText.text = getString(R.string.about_copyright_text)
    }
}
