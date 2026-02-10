package com.example.videodownloader.starter.presentation.common

sealed interface UiEvent {
    data class ShowMessage(val value: String) : UiEvent
    data object NavigateBack : UiEvent
}
