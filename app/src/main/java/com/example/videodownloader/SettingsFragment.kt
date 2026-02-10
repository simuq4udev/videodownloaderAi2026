package com.example.videodownloader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val wifiOnlySwitch = view.findViewById<SwitchMaterial>(R.id.switch_wifi_only)
        val policyNote = view.findViewById<TextView>(R.id.settings_policy_note)
        val saveButton = view.findViewById<Button>(R.id.settings_save)
        val directoryInput = view.findViewById<EditText>(R.id.default_directory_input)
        val maxDownloadsInput = view.findViewById<EditText>(R.id.max_downloads_input)
        val preferences = DownloadPreferences(requireContext())

        wifiOnlySwitch.isChecked = preferences.wifiOnly
        directoryInput.setText(preferences.defaultDirectory)
        maxDownloadsInput.setText(preferences.maxSimultaneousDownloads.toString())

        saveButton.setOnClickListener {
            preferences.wifiOnly = wifiOnlySwitch.isChecked
            preferences.defaultDirectory = directoryInput.text.toString()
            preferences.maxSimultaneousDownloads = maxDownloadsInput.text.toString().toIntOrNull() ?: 2
            policyNote.text = getString(R.string.settings_saved)
        }

        policyNote.text = getString(R.string.settings_policy_note)
    }
}
