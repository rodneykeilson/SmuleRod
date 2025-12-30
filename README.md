<p align="center">
  <img src="app/src/main/res/drawable/app_icon.jpeg" width="128" height="128" style="border-radius: 20%">
</p>

<h1 align="center">SmuleRod</h1>

<p align="center">
  <a href="https://github.com/rodneykeilson/SmuleRod/actions/workflows/android.yml">
    <img src="https://github.com/rodneykeilson/SmuleRod/actions/workflows/android.yml/badge.svg" alt="Android CI/CD">
  </a>
</p>

<p align="center">
  A minimalist, high-accessibility Android application to download Smule videos and audio with a built-in player.
</p>

---

## 🚀 Features
- **Minimalist UI**: Clean Jetpack Compose interface.
- **WCAG AAA Compliant**: High contrast and accessible touch targets.
- **Built-in Player**: Watch videos directly inside the app.
- **Video Previews**: See thumbnails of your downloads.
- **Multi-select**: Bulk delete or share your recordings.
- **Theme Support**: Quick toggle between Light and Dark modes.
- **Share Integration**: Share a link from the Smule app directly to SmuleRod.

## 📖 How to Use
1. **Download**: Copy a Smule link or share it directly to SmuleRod.
2. **Manage**: Go to the **Files** tab to view, play, or share your downloads.
3. **Multi-select**: Tap the checklist icon in the Files tab to select multiple files for bulk actions.

## 🛠️ How to Build
1. Clone the repository.
2. Open in **Android Studio**.
3. Build and run on your device.

## 📦 CI/CD & Releases
- **Automated Builds**: Every push to `main` triggers a build check.
- **Releases**: To create a formal release with an APK, simply push a tag:
  ```bash
  git tag v1.0.2
  git push --tags
  ```
  The APK will be automatically attached to the GitHub Release.

## ⚙️ Technical Details
- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Media**: Media3 ExoPlayer & Coil Video Decoding
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)


