package com.example.videodownloader.starter.presentation.home

import com.example.videodownloader.starter.data.model.VideoInfo

data class HomeUiState(
    val inputUrl: String = "",
    val loading: Boolean = false,
    val parsedVideo: VideoInfo? = null,
    val errorMessage: String? = null
)

sealed interface HomeAction {
    data class UrlChanged(val value: String) : HomeAction
    data object ParseClicked : HomeAction
    data class DownloadClicked(val formatId: String) : HomeAction
}
