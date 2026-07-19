# Changelog 📋

All notable changes to **WebhookNoteSender** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- `renameApk` Gradle task — APK output renamed to `WebhookNoteSender-v{version}-{variant}.apk`
- Update check fix: handles both relative and absolute GitHub `Location` redirect headers
- GitHub link added to About card in Settings

### Fixed
- Update check URL malformed when GitHub returns absolute Location redirect

---

## [0.3.0] — 2026-07-19

### Added
- **App Shortcuts (long-press app icon)**: Top 5 most-used profiles now appear on long-press of the app icon. Usage frequency (`use_count`) tracked per profile and incremented on each capture. App shortcuts update on startup, after capture, save, and delete.
- **`use_count` column** in `profiles` table (migration 4→5) for tracking profile usage frequency
- **`ShortcutHelper.updateAppShortcuts()`**: Sets dynamic shortcuts with distinct `"app_shortcut_"` ID prefix to avoid collision with pinned shortcuts
- UI improvements: gradient cards by media type, animated navigation icons with badge, pulsing status indicators, progress bar for SENDING, section headers with icons in Settings
- CI: `checkHardcodedStrings` Gradle task in build-apk.yml workflow
- About card with GitHub repository link
- Update check via GitHub releases API

### Changed
- **Video compression**: Removed broken MediaCodec transcode pipeline (`transcodeVideo` / `compressVideoFile`). Video via shortcuts now uses simple gzip compression (same as audio and in-app path). The decoder/encoder/surface pipeline caused infinite loop hang.
- **Shortcut creation**: `requestPinShortcut()` now cleans up disabled shortcuts (Xiaomi) before creating, using `enableShortcuts()` + platform API `removeLongLivedShortcuts()` to avoid crash on re-create
- **Shortcut state tracking**: `isShortcutCreated()` now uses SharedPreferences as source of truth for pinned shortcuts (Xiaomi doesn't report pinned via platform API)
- **ProfilesViewModel**: `enqueueCapturedMedia()` increments `useCount` and updates app shortcuts; `saveProfile()` (update) and `deleteProfile()` also update app shortcuts
- **ShortcutReceiverActivity / AudioRecorderService**: Both increment `useCount` after successful capture
- Navigation icons: `CameraAlt` → `Person` for Profiles tab
- Status badges: removed redundant icon, text-only display
- About Card: removed `Surface` wrapper, plain text display
- All hardcoded strings migrated to `stringResource()` — English + Russian
- `SettingsScreenTest` updated to use `str()` helpers with string resources
- `AGENTS.md` updated with localization conventions

### Fixed
- **Shortcut re-create crash**: `requestPinShortcut()` now cleans up disabled shortcuts before creating, preventing `IllegalArgumentException: Shortcut ID already exists but disabled`
- **Video capture hang**: Removed broken `transcodeVideo()` which caused infinite loop when encoder never received frames via surface
- **Capture tap not working**: Fixed `onCaptureClick` handler — was using stale `profileId` reference, changed to `Long` parameter
- **Audio shortcut recording**: Audio shortcut now opens `AudioRecordingScreen` instead of starting service directly; locale change no longer triggers premature recording
- **Remove shortcut from home screen**: Proper lifecycle management for shortcut removal (Xiaomi compatibility — shortcut becomes grey then removable)
- **Shortcut state on app resume**: `DisposableEffect` with `LifecycleEventObserver` refreshes shortcut status on `ON_RESUME`
- **Shortcut state on menu open**: `onOpenMenu` callback re-checks `isShortcutCreated()` before showing menu
- **Locale switching**: Fixed `attachBaseContext` to apply saved locale on app startup
- **Update check URL**: Fixed malformed URL when GitHub returns absolute `Location` redirect

### Removed
- `compressVideoFile()` and `transcodeVideo()` methods (120+ lines of broken MediaCodec pipeline)
- MediaCodec-related imports (MediaCodec, MediaCodecInfo, MediaExtractor, MediaFormat, MediaMuxer)
- Redundant emoji/icons in queue status badges

---

## [0.2.0] — 2026-07-18

### Initial Feature Release 🚀

Full-featured MVP as specified in the technical documentation.

#### Added

**Profiles**
- CRUD for named webhook profiles with fields: name (unique), type (image/audio/video), prompt, URL, bearer token
- Form validation in real-time (name length, URL scheme, prompt required)
- Card-based profile list with type icons and quick-capture button
- Long-press context menu: edit, delete, create/remove shortcut
- FAB for creating new profiles

**Media Capture**
- Image capture via `ActivityResultContracts.TakePicture` with temp file → Base64 → queue
- Video capture via `ActivityResultContracts.CaptureVideo` with same pipeline
- Audio recording via `MediaRecorder` (AAC, 44100Hz) with foreground service and persistent notification
- Start/stop recording with timer display in UI
- Temp file deleted immediately after Base64 encoding

**Queue System**
- Room-based queue (`queue_items` table) with status: PENDING, SENDING, SENT, FAILED
- WorkManager `CoroutineWorker` with exponential backoff (30s initial, 5min max)
- Network-aware: retries when connectivity becomes available
- Smart error handling: HTTP 4xx (except 408/429) → FAILED, 5xx/network → PENDING + retry
- Immediate deletion on successful send
- Queue screen with color-coded status indicators
- Swipe-to-delete and manual retry buttons
- Pending count badge

**Home Screen Shortcuts**
- Pinned shortcuts via `ShortcutManagerCompat.requestPinShortcut()`
- One-tap capture from shortcut without opening app UI
- Transparent `ShortcutReceiverActivity` for image/video/audio capture
- Shortcut removal on profile delete
- Shortcut status indicator on profile cards

**UI & Theme**
- Material 3 with dynamic color support (Android 12+)
- Three theme modes: Light, Dark, System
- Navigation Compose with bottom navigation bar (Profiles, Queue, Settings)
- Responsive card-based layouts with rounded corners and elevation
- Audio recording UI with pulsing microphone icon and elapsed timer
- Queue items with swipe-to-delete and status dot indicators

**Localization**
- English (`values/strings.xml`) — 72 strings
- Russian (`values-ru/strings.xml`) — 72 strings
- All user-facing strings in `stringResource()` — no hardcoded strings
- CI validation of string key parity between locales

**Architecture & Infrastructure**
- Clean Architecture with MVVM pattern
- Hilt DI (Application, ViewModel, EntryPoint)
- Room database with KSP for DAOs
- OkHttp 4.x with 30s/120s/120s timeouts
- DataStore Preferences for settings
- kotlinx.serialization for JSON payloads
- `NetworkMonitor` utility with `ConnectivityManager.NetworkCallback` → `Flow<Boolean>`
- Streaming `Base64Encoder` (8KB chunks, NO_WRAP flag)
- `ShortcutHelper` for pinned and dynamic shortcut management
- Media compression: JPEG re-encode for images, GZIP for audio/video, GZIP+gzip for video
- Large payload file storage via `PayloadFileHelper` (avoids SQLiteBlobTooBigException)

**CI/CD & Developer Experience**
- `build.sh` — universal build script with flags: `--run`, `--release`, `--install`, `--launch`, `--logs`, `--test <url> [token]`, `--clear`
- GitHub Actions workflow (`.github/workflows/build-apk.yml`):
  - Parallel jobs: lint, unit tests, locale validation, debug APK
  - Release build with keystore signing on tag push `v*`
  - Automatic GitHub Release with changelog and APK artifacts
- Dependabot config for weekly dependency updates
- `SETUP.md` with GitHub Secrets instructions
- `AGENTS.md` — comprehensive AI agent documentation
- 117+ unit tests with MockK
- Gradle version catalog (`libs.versions.toml`)
- `.gitignore` configured for secrets and build artifacts

#### Technical Details

| Component | Version |
|---|---|
| Kotlin | 2.1.0 |
| Compose BOM | 2024.12.01 |
| Material 3 | 1.3.1 |
| Hilt | 2.53.1 |
| Room | 2.6.1 |
| OkHttp | 4.12.0 |
| WorkManager | 2.10.0 |
| CameraX | 1.4.1 |
| AGP | 8.7.3 |
| Gradle | 8.9 |
| minSdk / targetSdk / compileSdk | 26 / 35 / 36 |

---

## [0.1.0] — 2026-07-18

### Pre-release

- Project scaffolding with Gradle KTS, version catalog
- Package structure following Clean Architecture guidelines

---

## How to release

```bash
# 1. Update CHANGELOG.md with the new version
# 2. Commit changes
git add CHANGELOG.md
git commit -m "chore: update changelog for v0.4"

# 3. Create and push tag
git tag v0.4
git push origin v0.4

# 4. CI/CD will:
#    - Bump versionCode + versionName
#    - Build signed release APK
#    - Create GitHub Release with changelog
```

---

[Unreleased]: https://github.com/kas-cor/webhooknotesender/compare/v0.3...HEAD
[0.3.0]: https://github.com/kas-cor/webhooknotesender/releases/tag/v0.3
[0.2.0]: https://github.com/kas-cor/webhooknotesender/releases/tag/v0.2
[0.1.0]: https://github.com/kas-cor/webhooknotesender/releases/tag/v0.1
