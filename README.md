# FilesFlow Android

FilesFlow is a native Android Jetpack Compose implementation of the FilesFlow home dashboard.

This repository contains the dashboard specified in `FilesFlow_Codex_Implementation_Spec.md`: a warm light file-manager home screen with a fixed top app bar, storage overview card, six-item category grid, and four-row recent files list. The app name is `FilesFlow` in the UI and launcher label.

## Build

Use the included Gradle wrapper:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Notes

The dashboard is intentionally static for this phase. Real storage usage, file categories, recent files, search, and file operations are later-phase work and are not included here.
