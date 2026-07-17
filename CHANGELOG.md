# Changelog 📋

All notable changes to **WebhookNoteSender** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Planned
- DataStore persistence for theme mode and language selection
- Additional unit tests (ViewModel, Base64Encoder, DAOs)
- JaCoCo coverage report with verification gates
- AutoMirrored icons for deprecated Shortcut references
- Profile-based audio recording from shortcut (polish)

---

## [1.0.0] — 2026-07-18

### Initial Release 🚀

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
- Dynamic shortcuts (long-press app icon menu, max 5)
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

**CI/CD & Developer Experience**
- `build.sh` — universal build script with flags: `--run`, `--release`, `--install`, `--launch`, `--logs`, `--test <url> [token]`, `--clear`
- GitHub Actions workflow (`.github/workflows/build-apk.yml`):
  - Parallel jobs: lint, unit tests, locale validation, debug APK
  - Release build with keystore signing on tag push `v*`
  - Automatic GitHub Release with changelog and APK artifacts
- Dependabot config for weekly dependency updates
- `SETUP.md` with GitHub Secrets instructions
- `AGENTS.md` — comprehensive AI agent documentation
- 19 unit tests (ProfileRepository: 10, QueueWorker: 9) with MockK
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
git commit -m "chore: update changelog for v1.1"

# 3. Create and push tag
git tag v1.1
git push origin v1.1

# 4. CI/CD will:
#    - Bump versionCode + versionName
#    - Build signed release APK
#    - Create GitHub Release with changelog
```

---

[Unreleased]: https://github.com/kas-cor/webhooknotesender/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/kas-cor/webhooknotesender/releases/tag/v1.0.0
[0.1.0]: https://github.com/kas-cor/webhooknotesender/commits/516807a
