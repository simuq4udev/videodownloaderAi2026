# Video Downloader (Kotlin, Android)

A complete Android starter project for downloading **direct video file URLs** (for example `.mp4`) with:
- Home screen URL input + download button
- Download queue with progress and pause/resume controls
- Download history screen
- Settings screen (WiFi-only, default directory, max simultaneous downloads)
- About screen (app info/version)
- MVVM + Coroutines + DownloadManager + OkHttp/Retrofit dependencies

## Important policy note
This template **does not bypass platform protections** and does not implement scraping/extraction for social platforms (Instagram/Facebook/TikTok/Twitter).
For those platforms, use official provider APIs and authorized content flows.

## Build in Android Studio
1. Open project in Android Studio.
2. Let Gradle sync dependencies.
3. Run `app` on an emulator/device (Android 7.0+).

## Main project structure
- `MainActivity` + bottom navigation host
- `HomeFragment` + `HomeViewModel` + queue adapter
- `HistoryFragment` and history adapter/store
- `SettingsFragment` and shared preferences (`DownloadPreferences`)
- `AboutFragment`
- `network/VideoResolverRepository` for direct-media URL checks

## Runtime permissions
- Internet permission in manifest
- Legacy storage permissions for older Android versions

