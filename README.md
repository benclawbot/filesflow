# FilesFlow - fast and elegant android file manager

![FilesFlow banner](docs/assets/filesflow-banner.png)

FilesFlow is a native Android file manager built with Kotlin and Jetpack Compose. It gives users a warm portrait dashboard for checking device storage, browsing common file categories, searching files, reviewing recent files, opening files with Android apps, printing images/PDF documents through Android's print UI, managing files with copy, move, rename, and delete actions, and sending selected files directly to another device over the local network.

FilesFlow is a native Android app and is not deployed as a hosted web service.

## Screenshots

The screenshot below was captured from the debug APK running on a connected Android device.

<img src="docs/assets/filesflow-home-screenshot.png" alt="FilesFlow Android home dashboard screenshot" width="360">

## Functionality

FilesFlow currently includes a status-bar-safe portrait app bar, a real internal-storage usage overview, live file-category summaries for Images, Videos, Docs, Downloads, Music, and Apps, a recent-files feed backed by MediaStore or granted shared storage, search-by-name after Enter, category browsing with swipeable folder filters, SAF folder browsing, Android file opening on tap, long-press multi-select with batch move/delete/share actions, and per-file copy, move, rename, delete, and print actions from the more menu. Tapping the Internal Storage overview opens the phone folder root. Categories, search results, and browsing open as dedicated views with back navigation to the home dashboard, and the Images category uses a fast cached 3 x 6 thumbnail gallery with selection controls in grid view. Copy and move use FilesFlow's own Browse Files view to choose a destination, with favorite folders shown first as one-tap destination proposals and a bottom-right validation button for the current folder.

Favorite folders can be starred or unstarred directly from folder rows or the file action sheet. Starred folders appear on the home dashboard and are prioritized as move/copy destinations. Images print through Android's image printing helper, and PDFs are passed into Android's system print UI so users can choose available printers or Save as PDF.

Files and complete folder trees can be copied or moved between direct shared storage and SAF locations. All transfer paths generate extension-aware collision-safe names, remove incomplete destinations when copying fails, and delete the original only after the destination has been written completely. Recursive transfers additionally reject a source folder as its own destination and reject descendant destinations. If Android refuses source deletion after a successful copy, FilesFlow reports `Copied only` and preserves the original. Mixed multi-selection batches can contain both files and folders and report complete, copied-only, partial, or failed outcomes accurately.

Selected files can also be sent directly to another device on the same Wi-Fi network. FilesFlow opens a dedicated transfer screen, starts a dependency-free local HTTP sender, and presents one temporary session link plus a locally generated QR code. The receiving browser opens a responsive landing page listing every selected file. Sessions use a cryptographically random URL token, expose only explicitly selected files, reject traversal and unsupported methods, disable caching, expire automatically after ten minutes, and stop immediately when the transfer screen closes. File endpoints support standard single-byte HTTP ranges, including bounded, open-ended, and suffix ranges, so browsers can resume interrupted downloads and media clients can request partial content. Invalid or unsatisfiable ranges return HTTP 416 without leaking file contents. No cloud account, website, relay, external QR service, or receiving app is required.

With broad file access, FilesFlow builds a complete private SQLite index of readable shared-storage files instead of relying on capped scans. Each refresh generation is committed atomically, so an interrupted scan leaves the previous complete index available. Dashboard category counts and sizes are computed over the full index; category and search screens use bounded result windows for responsive rendering while querying the complete dataset. Copy, move, rename, and delete operations invalidate the index so the next repository query rebuilds it.

The interface keeps the original FilesFlow design language: warm `#fff8f2` surfaces, serif headline typography, compact portrait spacing, rounded 8-12dp controls, and raised or recessed neumorphic panels.

## Architecture

```mermaid
flowchart TD
    A["MainActivity"] --> B["FilesFlowApp"]
    B --> C["FilesFlowTheme"]
    B --> D["Activity Result Launchers"]
    D --> E["Media Permissions"]
    D --> F["SAF Folder Picker"]
    D --> G["All Files Settings"]
    B --> H["FilesFlowViewModel"]
    H --> I["IndexedFileManagerRepository"]
    I --> J["AndroidFileManagerRepository"]
    I --> K["DirectStorageIndex"]
    I --> L["SingleFileTransfer"]
    I --> M["RecursiveFolderTransfer"]
    K --> N["Atomic SQLite Generations"]
    L --> O["Direct and SAF Files"]
    M --> P["Direct and SAF Trees"]
    J --> Q["StatFs Storage Usage"]
    J --> R["MediaStore Queries"]
    J --> S["DocumentFile SAF Trees"]
    J --> T["Direct File Access"]
    J --> U["Favorite Folder Persistence"]
    B --> V["Android Print UI"]
    B --> W["LanTransferActivity"]
    W --> X["LanTransferServer"]
    X --> Y["Expiring Tokenized Session Page"]
    X --> Z["Resumable Byte-Range Downloads"]
    H --> AA["FilesFlowUiState"]
    AA --> AB["HomeDashboardScreen"]
    AB --> AC["StorageOverviewCard"]
    AB --> AD["SearchAndBrowseCard"]
    AB --> AE["FavoriteFoldersList"]
    AB --> AF["FileBrowserSection"]
```

`FilesFlowApp` owns Android permission launchers, saved stable routes, print actions, file open actions, and handoff into the private LAN transfer activity. `FilesFlowViewModel` owns dashboard, browser, selection, favorite-folder, and in-app destination-picking state. `IndexedFileManagerRepository` routes broad-access category summaries, category listings, and searches through the complete durable index, uses `SingleFileTransfer` for safe collision-aware file operations, and uses `RecursiveFolderTransfer` for safe cross-storage folder trees. `LanTransferActivity` owns the visible send session, QR handoff, and lifecycle cleanup; `LanTransferServer` serves the tokenized landing page and selected files with bounded request parsing and resumable single-range responses. The `features/home/components` package renders the portrait-only Compose UI.

## Installation

### Install the debug APK from a local build

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

FilesFlow asks for Android system access only when the user opens a category, search, or browser that needs it. Tap a file to open it, or long-press files and folders to manage them. Use the file action sheet or the multi-select folder action to open Browse Files, navigate to a destination folder, and validate it with the bottom-right button. Star folders in Browse Files to make them appear on Home and as first-choice copy/move destinations. Use Print / Save as PDF on printable images and PDFs to open Android's native print picker. To send files to another device, select one or more files, tap Share, keep the transfer screen open, and scan the QR code or open the displayed link on a browser connected to the same Wi-Fi network.

### Run from Android Studio

Open this repository in Android Studio, let Gradle sync, select the `app` configuration, connect an Android device or emulator in portrait orientation, and run the app.

### Verify locally

```powershell
.\gradlew.bat testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease
```

The unsigned optimized release APK is generated at `app/build/outputs/apk/release/app-release-unsigned.apk` when no signing environment is configured.

## Release process

Every push and pull request runs JVM unit tests, Android lint for debug and release builds, debug APK assembly, optimized release APK assembly, and an API 35 hardware-accelerated emulator smoke test covering launch, Browse Files navigation, process recreation, return navigation, LAN transfer QR rendering, and raw HTTP range behavior. GitHub Actions retains raw unit-test diagnostics, verification reports, instrumentation reports when available, the R8 mapping, the debug APK, and the unsigned release APK.

A tag matching `v*` starts the signed-release job only after both the standard verification job and the emulator smoke job pass. Configure all four repository secrets before creating the tag:

- `FILESFLOW_KEYSTORE_BASE64`: the release keystore encoded as one Base64 string;
- `FILESFLOW_KEYSTORE_PASSWORD`;
- `FILESFLOW_KEY_ALIAS`;
- `FILESFLOW_KEY_PASSWORD`.

Partial signing configuration is rejected. The tagged job decodes the keystore into the runner's temporary directory, builds the signed and optimized release APK, verifies its signature with `apksigner`, and attaches it to a GitHub release. The keystore and passwords are never written to the repository or uploaded as artifacts.
