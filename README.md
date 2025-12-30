# Smule Downloader (Minimalist Android App)

A minimalist Android application to download Smule videos and audio from links.

## Features
- Simple, clean UI using Jetpack Compose.
- Extracts direct MP4/M4A links from Smule recording URLs.
- Supports "Share to" functionality (share a link from the Smule app directly to this app).
- Uses Android's native `DownloadManager` for reliable downloads.

## How to Build
1. Open this folder in **Android Studio**.
2. Wait for Gradle to sync.
3. Connect your Android device or start an emulator.
4. Click **Run** (Shift + F10).

## How to Use
1. Copy a Smule recording link (e.g., `https://www.smule.com/recording/...`).
2. Paste it into the app and click **Download**.
3. Alternatively, in the Smule app, click **Share** -> **More** -> **Smule Downloader**.

## Technical Details
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Networking**: OkHttp
- **HTML Parsing**: Jsoup
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

## Example Link for Testing
`https://www.smule.com/recording/noah-menghapus-jejakmu/1411828004_2871525322`
