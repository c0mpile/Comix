# Comix

Comix is a Material Design 3 comic book reader for Android built with Kotlin and Jetpack Compose.

## Features (Phase 1)

- **Library Management:** Add one or more comic folders via Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`) with persisted URI permissions.
- **Fast Folder Scanning:** Off-main-thread directory traversal using `DocumentsContract` queries, incremental metadata indexing in Room, and content-based SHA-256 identity hashing (`comicKey`).
- **Cover Grid:** Responsive Material 3 cover grid with automatic Page 1 cover extraction, downsampled WebP disk caching, and Coil 3 integration.
- **CBZ Archive Reader:** Random-access ZIP/CBZ parser (`RandomAccessSource`) supporting standard and ZIP64 archives, natural alphanumeric page sorting, and bounded LRU page bitmap caching (`ReaderPageCache`).
- **Immersive Reader Experience:** Full-screen horizontal pager (`HorizontalPager`) with pinch-to-zoom (`1x` to `4x`), double-tap zoom (`1x` / `2.5x`), edge-clamped panning, left/right tap zones for page navigation, center tap for the HUD overlay + page scrubber, and automatic per-comic reading progress persistence.
- **Resilient Error Handling:** Explicit UI states for corrupt archives, empty archives, missing files, revoked folder permissions, and out-of-memory conditions.

## Tech Stack

- **Language:** Kotlin (`2.3.21`)
- **UI:** Jetpack Compose (`BOM 2026.09.00`) with Material 3 (`dynamicColor` support, light/dark themes, edge-to-edge layout)
- **Architecture:** Single-activity MVVM (`ui`, `domain`, `data` layers) with unidirectional `StateFlow` state
- **Dependency Injection:** Hilt (`2.60.1`)
- **Persistence:** Room (`2.8.5`) with exported schemas under `app/schemas/`
- **Navigation:** Navigation Compose (`2.10.2`) with type-safe Kotlin Serialization routes
- **Image Loading:** Coil 3 (`3.6.3`) with custom `ComicCoverFetcher`
- **Build System:** Gradle (`9.8.1`) with Android Gradle Plugin (`9.4.1`) and Version Catalog (`gradle/libs.versions.toml`)
- **SDK Levels:** `minSdk = 26`, `compileSdk = 36`, `targetSdk = 36`

## Prerequisites

- **JDK:** Java 17 or newer (`ANDROID_HOME` configured to a local Android SDK installation with API 36 platform and build tools)
- **Device:** A physically connected Android device (API 26+) with USB debugging enabled. All instrumented and manual testing runs on a connected physical device.

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

Run JVM unit tests (archive parser, content hasher, repositories, and ViewModels):

```bash
./gradlew testDebugUnitTest
```

Run instrumented Compose UI tests on the connected physical device:

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
  MainActivity.kt        Single activity hosting Compose with edge-to-edge layout
  di/                    Hilt modules (Dispatchers, Database, Source, Archive, Repository)
  data/
    local/               Room database, entities, projections, converters, and DAOs
    source/              ComicSource, RandomAccessSource, LocalSource, Sha256ComicKeyHasher
    archive/             ComicArchiveReader, CbzArchiveReader, CoverExtractor, ComicCoverFetcher
    repository/          LibraryRepositoryImpl and ReaderRepositoryImpl
  domain/
    model/               Domain models (Comic, ReadingProgress, LibraryFolder, ComicPage, ComicError)
    repository/          LibraryRepository and ReaderRepository interfaces
    usecase/             Library and Reader use cases
  ui/
    theme/               Color, Type, Shape, ComixDimens, and ComixTheme
    components/          EmptyStateView, ErrorStateView, and LoadingStateView
    navigation/          Type-safe ComixRoutes and ComixNavHost
    library/             LibraryScreen, ComicGridItem, LibraryViewModel, and LibraryUiState
    reader/              ReaderScreen, ZoomablePageImage, ReaderOverlayHud, ReaderPageCache, ReaderViewModel
```
