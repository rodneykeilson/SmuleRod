# Copilot Instructions for SmuleRod

You are an expert Android developer maintaining the SmuleRod project.

## Commit and Versioning Rules

1.  **Conventional Commits**: Always use conventional commit messages for every commit.
    *   `feat:` for new features.
    *   `fix:` for bug fixes.
    *   `chore:` for maintenance tasks.
    *   `docs:` for documentation changes.
    *   `refactor:` for code refactoring.
    *   `style:` for formatting changes.
2.  **Versioning**: When a task or update is finished:
    *   Increment the `versionCode` and `versionName` in `app/build.gradle.kts`.
    *   Commit the changes.
    *   Create a new git tag matching the `versionName` (e.g., `v1.2.1`).
    *   Push the commit and the new tag to the remote repository.

## Project Context

*   **Purpose**: Smule media downloader (video and audio).
*   **Tech Stack**: Kotlin, Jetpack Compose, OkHttp, Jsoup, Media3/ExoPlayer.
*   **Extraction Logic**: Uses regex to find encrypted URLs in Smule's `window.DataStore` and resolves them via the `/redir` endpoint.
*   **Permissions**: Requires `INTERNET` and `WRITE_EXTERNAL_STORAGE` (for older Android versions). Uses MediaStore for Android 10+.
