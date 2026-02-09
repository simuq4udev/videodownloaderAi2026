package com.example.videodownloader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val wifiOnlySwitch = view.findViewById<SwitchMaterial>(R.id.switch_wifi_only)
        val policyNote = view.findViewById<TextView>(R.id.settings_policy_note)
        val preferences = DownloadPreferences(requireContext())

        wifiOnlySwitch.isChecked = preferences.wifiOnly
        wifiOnlySwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.wifiOnly = isChecked
        }

        policyNote.text = getString(R.string.settings_policy_note)
    }
}
