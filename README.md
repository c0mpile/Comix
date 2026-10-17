# Comix

Comix is a Material Design 3 comic book reader for Android built with Kotlin and Jetpack Compose.

## Features

- **Multiple Libraries & Collapsible Side Menu:** Manage separate named libraries (pre-seeded with **Comics** in `Left to right` mode and **Manga** in `Right to left` mode, plus custom user-created libraries) from a collapsible Material 3 `ModalNavigationDrawer` side menu.
- **Supported Comic Formats (CBZ & PDF):** Read both `.cbz` (standard and ZIP64 archives via random-access `CbzArchiveReader`) and `.pdf` (rendered via Android's `PdfRenderer` in `PdfArchiveReader`) files from local SAF folders and Google Drive folders.
- **Embedded Comic Metadata (`ComicInfo.xml`):** Automatically extract and persist embedded `ComicInfo.xml` metadata (`Title`, `Series`, `Number`, `Volume`, `Summary`, `Writer`, `Penciller`, `Publisher`, `Genre`, and release date) during library scans, searchable from the library filter bar and viewable in a dedicated `ComicDetailsDialog`.
- **Filtering Section (Search + Status Tabs) & Grid / List View Toggle:** Filter comics within the active library by real-time search query (`title`, `fileName`, `series`, `writer`, `publisher`, `genre`) and reading status tabs (`All`, `In Progress`, `Finished`), and switch between the responsive Cover Grid and compact List View (`LibraryLayoutMode`).
- **Google Drive Integration (Auth, Browsing, Streamed Reading, and Progress Sync):** Sign in with Google via Credential Manager and Play Services Identity (`drive.readonly` and `drive.appdata`), browse folders and folder shortcuts in `My Drive`, stream remote `.cbz` and `.pdf` comics on demand via HTTP `Range` chunk requests (`DriveStreamingRandomAccessSource`) with automatic cover extraction and a 128 MB disk chunk cache, and synchronize reading progress across devices via `appDataFolder/comix_reading_progress.json`.
- **Immersive Reader Experience:** Full-screen horizontal pager (`HorizontalPager`) with per-library default reading direction (`LTR` / `RTL`), `Fit Screen` and `Fit Width` scale modes, pinch-to-zoom (`1x` to `4x`), double-tap zoom (`1x` / `2.5x`), edge-clamped panning, directional left/right tap zones, HUD overlay with quick toggles and page scrubber, `Keep screen on` support, and automatic per-comic reading progress persistence.
- **Settings Screen (DataStore Preferences & Google Drive):** Configure application theme (`System default`, `Light`, `Dark`), default reading direction, page scale mode, `Keep screen on`, local library folders, and Google Drive account/folders.
- **Resilient Error Handling:** Explicit UI states for corrupt archives, empty archives, missing files, revoked folder permissions, expired or revoked Google Drive tokens, rate limits, network unavailability, and out-of-memory conditions.

## Tech Stack

- **Language:** Kotlin (`2.3.21`)
- **UI:** Jetpack Compose (`BOM 2026.09.00`) with Material 3 (`dynamicColor` support, light/dark themes, edge-to-edge layout)
- **Architecture:** Single-activity MVVM (`ui`, `domain`, `data` layers) with unidirectional `StateFlow` state
- **Dependency Injection:** Hilt (`2.60.1`)
- **Persistence:** Room (`2.8.5`, schema v4 with `MIGRATION_1_2`, `MIGRATION_2_3`, and `MIGRATION_3_4`) with exported schemas under `app/schemas/`, and Jetpack DataStore Preferences (`1.2.1`) for user settings
- **Networking & Auth:** OkHttp (`5.5.0`), AndroidX Credentials (`1.6.0`), Google Identity (`1.2.1`), and Play Services Auth (`22.0.0`)
- **Navigation:** Navigation Compose (`2.10.2`) with type-safe Kotlin Serialization routes (`LibraryRoute`, `ReaderRoute`, `SettingsRoute`, `DriveFolderPickerRoute`)
- **Image Loading:** Coil 3 (`3.6.3`) with custom `ComicCoverFetcher`
- **Build System:** Gradle (`9.8.1`) with Android Gradle Plugin (`9.4.1`) and Version Catalog (`gradle/libs.versions.toml`)
- **SDK Levels:** `minSdk = 26`, `compileSdk = 36`, `targetSdk = 36`

## Prerequisites

- **JDK:** Java 17 or newer (`ANDROID_HOME` configured to a local Android SDK installation with API 36 platform and build tools)
- **Device:** A physically connected Android device (API 26+) with USB debugging enabled. All instrumented and manual testing runs on a connected physical device.
- **Google Drive Configuration (Optional for local builds, required for Drive sign-in):**
  1. Enable the Google Drive API in a Google Cloud project and configure the OAuth consent screen.
  2. Create an **Android** OAuth 2.0 Client ID for package `com.c0mpile.comix` with your debug keystore SHA-1 fingerprint.
  3. Create a **Web application** OAuth 2.0 Client ID and add it to `local.properties` (never committed to git):
     ```properties
     DRIVE_WEB_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
     ```
     If `DRIVE_WEB_CLIENT_ID` is omitted, the app builds and runs normally with local folders and shows a friendly message if Google Drive sign-in is tapped.

## Build and Run

Verify that your physical Android device is connected and authorized:

```bash
adb devices -l
```

Assemble the debug APK:

```bash
./gradlew assembleDebug
```

Install and launch the debug build on the connected device:

```bash
./gradlew installDebug
adb shell am start -n com.c0mpile.comix/.MainActivity
```

If multiple devices are connected, specify the target serial with `ANDROID_SERIAL=<serial>` and `adb -s <serial>`.

## Testing and Lint

Run JVM unit tests (archive parser, content hasher, Drive REST client and retry policy, Room and DataStore repositories, and ViewModels):

```bash
./gradlew testDebugUnitTest
```

Run instrumented Room migration and Compose UI tests on the connected physical device:

```bash
./gradlew connectedDebugAndroidTest
```

Run Android Lint checks:

```bash
./gradlew lintDebug
```

## Project Structure

```text
app/src/main/java/com/c0mpile/comix/
  ComixApp.kt            Application class (Hilt and Coil ImageLoader factory)
  MainActivity.kt        Single activity hosting Compose with dynamic theme and edge-to-edge layout
  MainViewModel.kt       ViewModel observing user theme preferences for MainActivity
  di/                    Hilt modules (Dispatchers, Database, DataStore, Network, Drive, Source, Archive, Repository)
  data/
    local/               Room database (v4), migrations, entities, projections, converters, and DAOs
    remote/drive/        Drive REST v3 client (OkHttpDriveApiClient), DriveAuthorizerImpl, RetryPolicy, and DTOs
    source/              ComicSource, RandomAccessSource, LocalRandomAccessSource, DriveStreamingRandomAccessSource, LocalSource, DriveSource, Sha256ComicKeyHasher
    archive/             ComicArchiveReader, CbzArchiveReader, PdfArchiveReader, CompositeComicArchiveReaderFactory, ComicInfoXmlParser, CoverExtractor, ComicCoverFetcher
    repository/          LibraryRepositoryImpl, ReaderRepositoryImpl, SettingsRepositoryImpl, and DriveRepositoryImpl
  domain/
    model/               Domain models (Comic, ComicLibrary, ComicMetadata, LibraryFilterTab, LibraryLayoutMode, ReadingProgress, LibraryFolder, DriveAccount, DriveAuthState, DriveFolder, UserSettings)
    repository/          LibraryRepository, ReaderRepository, SettingsRepository, and DriveRepository interfaces
    usecase/             Library, Reader, Settings, and Drive use cases
  ui/
    theme/               Color, Type, Shape, ComixDimens, and ComixTheme
    components/          EmptyStateView, ErrorStateView, and LoadingStateView
    navigation/          Type-safe ComixRoutes and ComixNavHost
    library/             LibraryScreen, ComicGridItem, ComicListItem, ComicDetailsDialog, LibraryViewModel, and LibraryUiState
    reader/              ReaderScreen, ZoomablePageImage, ReaderOverlayHud, ReaderPageCache, ReaderViewModel
    settings/            SettingsScreen, SettingsViewModel, and SettingsUiState
    drive/               DriveFolderPickerScreen, DriveFolderPickerViewModel, and DriveFolderPickerUiState
```
