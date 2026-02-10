package com.example.videodownloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.videodownloader.home.DownloadQueueAdapter
import com.example.videodownloader.home.HomeViewModel
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var rightsCheck: CheckBox
    private lateinit var statusText: TextView
    private lateinit var urlInput: EditText

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                statusText.text = getString(R.string.permission_storage_denied)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        urlInput = view.findViewById(R.id.url_input)
        rightsCheck = view.findViewById(R.id.rights_check)
        val downloadButton = view.findViewById<Button>(R.id.download_button)
        statusText = view.findViewById(R.id.status_text)
        val queueList = view.findViewById<RecyclerView>(R.id.queue_list)

        val queueAdapter = DownloadQueueAdapter(
            onPauseClicked = { viewModel.pauseDownload(it) },
            onResumeClicked = { viewModel.resumeDownload(it) }
        )

        queueList.layoutManager = LinearLayoutManager(requireContext())
        queueList.adapter = queueAdapter

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                statusText.text = state.statusMessage
                queueAdapter.submitList(state.items)
            }
        }

        downloadButton.setOnClickListener {
            val urlText = urlInput.text.toString().trim()
            val validationResult = UrlPolicyValidator.validate(urlText)
            when (validationResult) {
                UrlValidationResult.InvalidHttps -> {
                    statusText.text = getString(R.string.error_https_required)
                    return@setOnClickListener
                }

                is UrlValidationResult.BlockedSocialHost -> {
                    if (validationResult.host == "facebook.com" || validationResult.host == "fb.watch") {
                        statusText.text = getString(R.string.error_facebook_requires_api)
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlText)))
                    } else {
                        statusText.text = getString(R.string.error_blocked_host_with_reason)
                    }
                    return@setOnClickListener
                }

                UrlValidationResult.Valid -> Unit
            }

            if (!rightsCheck.isChecked) {
                statusText.text = getString(R.string.error_rights_required)
                return@setOnClickListener
            }

            ensureStoragePermissionIfRequired()
            viewModel.enqueueDownload(urlText)
        }
    }

    private fun ensureStoragePermissionIfRequired() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return

        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permission)
        }
    }
}
