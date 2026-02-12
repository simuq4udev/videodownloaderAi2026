# Video Downloader (Android, Kotlin)

A complete MVVM Android app for downloading publicly accessible video files from social-page URLs (Instagram/Facebook/TikTok/Twitter pages where a direct video link can be resolved).

> ⚠️ This project intentionally **does not support YouTube downloads** to align with Play Store policy requirements.

## Features

- Paste URL + queue download from main screen
- Direct video URL extraction from page HTML (`og:video`, `.mp4/.m4v` link matching)
- Download queue with progress updates in RecyclerView
- Pause/resume support
- Downloaded item title + thumbnail display
- Runtime permission flow for storage/media video access
- Settings screen:
  - Default directory
  - Max simultaneous downloads
- About screen with app name/version/copyright
- Coroutines + MVVM architecture
- Retrofit + OkHttp networking
- Error handling (invalid URL, unresolved stream URL, HTTP errors)

## Open in Android Studio

1. Open Android Studio (Giraffe or newer recommended).
2. Choose **Open** and select this repository root.
3. Let Gradle sync finish.
4. Run the `app` configuration on an emulator/device (API 24+).

## Package Structure

- `data/` – models + settings repository
- `network/` – Retrofit service + direct URL extractor
- `download/` – coroutine-based downloader with pause/resume
- `viewmodel/` – Main/Settings view models
- `ui/` – activities and RecyclerView adapter

## Notes

- Some websites require login/cookies or anti-bot checks; extraction is best-effort for public pages.
- On newer Android versions, app-specific directories are used by default.
