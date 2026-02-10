# VideoDownloader (Android, Kotlin)

Market-standard starter project for a **policy-compliant** video downloader. The app only allows downloads from **direct HTTPS URLs** and **blocks common social media domains** to align with platform policies and content rights.

## ✅ Compliance Principles
- **User rights confirmation** required before download.
- **Social media domains blocked** (YouTube, Instagram, TikTok, etc.).
- **No circumvention** of DRM or platform restrictions.
- **Direct URLs only** (e.g., your own CDN or storage bucket).

## ✅ Features
- Clean single-activity UI (Material Components).
- DownloadManager integration.
- Status messaging for compliance and errors.
- Starter clean-architecture package layout for scaling to production.

## ✅ Complete Android Starter Project Structure

```text
app/src/main/java/com/example/videodownloader/
├── MainActivity.kt
├── HomeFragment.kt
├── HistoryFragment.kt
├── SettingsFragment.kt
├── HistoryAdapter.kt
├── DownloadHistoryStore.kt
├── DownloadPreferences.kt
└── starter/
    ├── AppStructure.kt
    ├── data/
    │   ├── local/
    │   │   └── DownloadEntity.kt
    │   ├── model/
    │   │   ├── DownloadItem.kt
    │   │   ├── VideoFormat.kt
    │   │   └── VideoInfo.kt
    │   ├── remote/
    │   │   └── ParserApi.kt
    │   └── repository/
    │       └── VideoRepositoryImpl.kt
    ├── domain/
    │   ├── repository/
    │   │   └── VideoRepository.kt
    │   └── usecase/
    │       ├── EnqueueDownloadUseCase.kt
    │       └── ParseVideoUrlUseCase.kt
    ├── presentation/
    │   ├── common/
    │   │   └── UiEvent.kt
    │   ├── history/
    │   │   └── HistoryContract.kt
    │   ├── home/
    │   │   └── HomeContract.kt
    │   └── settings/
    │       └── SettingsContract.kt
    ├── worker/
    │   └── DownloadWorkerPlan.kt
    └── di/
        └── ServiceLocator.kt
```

## ✅ How to expand this starter
1. Replace `ParserApi` with a Retrofit/Ktor implementation.
2. Add Room entities/DAO for persistent download history.
3. Add WorkManager worker implementation based on `DownloadWorkerPlan`.
4. Wire `HomeFragment` to use `ParseVideoUrlUseCase` + `EnqueueDownloadUseCase`.
5. Replace `ServiceLocator` with Hilt when ready.


## Why Facebook/Instagram links show "domain is blocked"
This template intentionally blocks social media domains in `UrlPolicyValidator` to keep the app aligned with Google Play and platform terms. Facebook/Instagram post URLs are usually webpage links, not direct media-file URLs.

If you need authorized support for those sources, use official provider APIs for content you own/manage and download only when your app has explicit rights.

## Facebook link support (authorized method)
Facebook share links are webpage URLs and are blocked in the direct downloader flow.
Use the compliant API-based method described in `docs/FACEBOOK_AUTHORIZED_INTEGRATION.md`.

## ✅ Build & Run
1. Open the project in **Android Studio**.
2. Sync Gradle.
3. Run on a device/emulator (Android 7.0+).

## ✅ Policy Note
This template is designed to comply with Google Play and platform policies by preventing downloads from social media sites and requiring user permission confirmation. It is intended for **content you own or have explicit permission to download**.
