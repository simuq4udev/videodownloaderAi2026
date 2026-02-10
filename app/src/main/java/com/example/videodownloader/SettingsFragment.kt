package com.example.videodownloader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private lateinit var settingsStore: SettingsStore

    private lateinit var selectedDirectoryText: TextView
    private lateinit var chooseDirectoryButton: Button
    private lateinit var maxDownloadsInput: EditText
    private lateinit var saveButton: Button

    private var selectedDirectoryUri: String? = null

    private val directoryPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri: Uri = result.data?.data ?: return@registerForActivityResult
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Best effort; app can still use the URI for this session.
            }
            selectedDirectoryUri = uri.toString()
            selectedDirectoryText.text = selectedDirectoryUri
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsStore = SettingsStore(requireContext())

        selectedDirectoryText = view.findViewById(R.id.selected_directory_text)
        chooseDirectoryButton = view.findViewById(R.id.choose_directory_button)
        maxDownloadsInput = view.findViewById(R.id.max_downloads_input)
        saveButton = view.findViewById(R.id.save_settings_button)

        viewLifecycleOwner.lifecycleScope.launch {
            val settings = settingsStore.settingsFlow.first()
            selectedDirectoryUri = settings.downloadDirectoryUri
            selectedDirectoryText.text = selectedDirectoryUri ?: getString(R.string.settings_default_directory)
            maxDownloadsInput.setText(settings.maxSimultaneousDownloads.toString())
        }

        chooseDirectoryButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            directoryPickerLauncher.launch(intent)
        }

        saveButton.setOnClickListener {
            val maxDownloads = maxDownloadsInput.text.toString().toIntOrNull()
            if (maxDownloads == null || maxDownloads < 1 || maxDownloads > 10) {
                Toast.makeText(requireContext(), R.string.settings_invalid_max_downloads, Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                settingsStore.setDownloadDirectoryUri(selectedDirectoryUri)
                settingsStore.setMaxSimultaneousDownloads(maxDownloads)
                Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
        }
    }
}
