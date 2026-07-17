# WebhookNoteSender 🚀

[![CI](https://github.com/kas-cor/webhooknotesender/actions/workflows/build-apk.yml/badge.svg)](https://github.com/kas-cor/webhooknotesender/actions/workflows/build-apk.yml)
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.0-purple)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-BOM%202024.12.01-green)](https://developer.android.com/jetpack/compose)
[![Material3](https://img.shields.io/badge/Material%203-dynamic-blue)](https://m3.material.io/)
[![API](https://img.shields.io/badge/minSdk-26%20%7C%20target-35-orange)](app/build.gradle.kts)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![Русский](https://img.shields.io/badge/README-%D0%A0%D1%83%D1%81%D1%81%D0%BA%D0%B8%D0%B9-blue)](README_ru.md)

> Android app for capturing media (photo, video, audio) and sending it to an AI webhook endpoint.  
> **Dual language support**: English and Russian.

---

## Preview 📱

| Profiles | Queue | Audio Recording | Shortcuts |
|---|---|---|---|
| Create & manage profiles | Track pending/sent messages | Record audio with timer | Home screen shortcuts |
| Card-based UI with capture | Swipe to delete, retry failed | Foreground service | One-tap capture from launcher |

---

## Features ✨

- **Profile management** — create, edit, delete named profiles with webhook URL, prompt, bearer token, and media type
- **Media capture** — take photo/video with system camera or record audio with MediaRecorder
- **Base64 encoding** — streaming encode, temp file deleted immediately after encoding
- **Queue system** — Room database queue with WorkManager background processing
- **Automatic retry** — exponential backoff (30s → 5min max), retries when network becomes available
- **Smart error handling** — HTTP 4xx (except 408/429) → `FAILED`, HTTP 5xx / network error → `PENDING` + retry
- **Home screen shortcuts** — pinned shortcuts for one-tap capture without opening the app
- **Dynamic shortcuts** — long-press app icon menu for quick capture
- **Foreground audio recording** — persistent notification with stop action
- **Dark / Light / System theme** — Material 3 dynamic color with manual override
- **Bilingual UI** — English and Russian, all strings in resources
- **JSON payload** — proper `{"messages": [...]}` format as per spec

---

## Architecture 🏗️

```
WebhookNoteSenderApp (Hilt Application)
└─ MainActivity (ComponentActivity + Compose)
   └─ AppNavigation (Navigation Compose, Bottom Nav)
      ├─ ProfilesScreen        — list of profiles as cards
      ├─ ProfileEditScreen     — create/edit profile form
      ├─ QueueScreen           — queue items with status indicators
      └─ SettingsScreen        — theme & language selection
   └─ ShortcutReceiverActivity — transparent activity for shortcuts
   └─ AudioRecorderService     — foreground service for audio recording
```

### Clean Architecture layers

```
┌──────────────────────────────────────────────┐
│  UI Layer (Compose + ViewModels)             │
│  ProfilesScreen · QueueScreen · Settings     │
├──────────────────────────────────────────────┤
│  Domain Layer                                │
│  MediaType · QueueStatus · ThemeMode         │
├──────────────────────────────────────────────┤
│  Data Layer                                  │
│  Room DB · Repositories · WebhookApi (OkHttp)│
├──────────────────────────────────────────────┤
│  Infrastructure                              │
│  WorkManager · Hilt DI · DataStore · CameraX │
└──────────────────────────────────────────────┘
```

### Project structure

```
webhooknotesender/
├── app/src/main/java/com/kascorp/webhooknotesender/
│   ├── WebhookNoteSenderApp.kt              # Hilt Application
│   ├── MainActivity.kt                       # Single Activity
│   ├── ShortcutReceiverActivity.kt           # Transparent shortcut activity
│   ├── di/                                   # Hilt modules
│   │   ├── AppModule.kt                      # DataStore, OkHttp, JSON
│   │   ├── DatabaseModule.kt                 # Room database
│   │   └── RepositoryModule.kt               # Repository bindings
│   ├── data/
│   │   ├── local/                            # Room DB, DAOs, Entities
│   │   ├── repository/                       # ProfileRepository, QueueRepository
│   │   ├── remote/                           # WebhookApi (OkHttp)
│   │   └── model/                            # MediaType, QueueStatus, ThemeMode
│   ├── work/                                 # QueueWorker (CoroutineWorker)
│   ├── ui/
│   │   ├── theme/                            # Material 3 dynamic color
│   │   ├── navigation/                       # NavHost + bottom nav
│   │   ├── profiles/                         # Profile list + edit
│   │   ├── queue/                            # Queue list
│   │   ├── settings/                         # Settings
│   │   └── components/                       # CaptureButton, AudioRecorder, StatusBadge
│   └── util/                                 # Base64Encoder, NetworkMonitor, ShortcutHelper
├── app/src/main/res/
│   ├── values/strings.xml                    # English (72 strings)
│   └── values-ru/strings.xml                 # Russian (72 strings)
├── build.sh                                  # Universal build script
├── .github/workflows/build-apk.yml           # CI/CD pipeline
└── AGENTS.md                                 # AI agent documentation
```

---

## Tech Stack ⚡

| Component | Technology |
|---|---|
| **Language** | Kotlin 2.1.0 |
| **UI** | Jetpack Compose + Material 3 (BOM 2024.12) |
| **Navigation** | Navigation Compose (Bottom Nav) |
| **DI** | Dagger Hilt 2.53 |
| **Database** | Room 2.6.1 (KSP) |
| **HTTP Client** | OkHttp 4.12 |
| **Background** | WorkManager + CoroutineWorker |
| **Serialization** | kotlinx.serialization 1.7.3 |
| **Preferences** | DataStore Preferences |
| **Camera** | CameraX 1.4.1 + ActivityResultContracts |
| **Audio** | MediaRecorder + Foreground Service |
| **minSdk / targetSdk / compileSdk** | 26 / 35 / 36 |
| **Testing** | JUnit 4.13.2 |

---

## Quick Start 🚀

### Prerequisites

- Android Studio Hedgehog (2023.1.1+) or IntelliJ IDEA
- JDK 17+
- Android SDK API 35

### Build & Install

```bash
# Make the build script executable
chmod +x build.sh gradlew

# Debug build
./build.sh

# Build, install, and launch on device
./build.sh --run

# Install APK on device
./build.sh --install

# Launch app on device
./build.sh --launch

# Clear app data
./build.sh --clear

# Show filtered logcat
./build.sh --logs

# Send test POST to webhook
./build.sh --test https://your-webhook.com/endpoint
./build.sh --test https://your-webhook.com/endpoint your-bearer-token

# Open in Android Studio
studio .   # or: android-studio . (Linux) / open -a "Android Studio" . (macOS)
```

### Direct Gradle

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release Build

```bash
# Release with debug keystore (no KEYSTORE_PASSWORD env var)
./build.sh --release

# Release with production keystore
export KEYSTORE_PASSWORD=your-password
./build.sh --release
```

> **CI/CD & GitHub Secrets setup:** Detailed guide on keystore creation, Base64 encoding, GitHub Secrets configuration, and the release process — see [SETUP.md](SETUP.md).

---

## Build Script (`build.sh`) 🔧

Comprehensive bash script for debugging, building, and testing:

| Flag | Description |
|---|---|
| *(no flags)* | Build debug APK |
| `--run` | Build debug APK, install, and launch |
| `--release` | Build release APK (signed with production or debug keystore) |
| `--install [apk]` | Install APK on device (default: debug) |
| `--launch` | Launch app on connected device |
| `--clear` | Clear app data on device |
| `--logs` | Show logcat filtered by app package |
| `--test <url> [token]` | Send test POST to webhook |
| `--help` | Show help |

---

## Webhook Payload 📡

### Request format

| Attribute | Value |
|---|---|
| **Method** | `POST` |
| **Content-Type** | `application/json` |
| **Authorization** | `Bearer <token>` (optional) |
| **Accept** | `application/json` |
| **Timeout** | connect: 30s, read: 120s, write: 120s |

### JSON Payload

```json
{
  "messages": [
    {
      "name": "profile_name",
      "prompt": "Describe what the AI should do with this media...",
      "datetime": "2026-07-18T12:00:00Z",
      "type": "image",
      "data": "/9j/4AAQSkZJRg...Base64-encoded-content..."
    }
  ]
}
```

| Field | Description |
|---|---|
| `name` | Profile name (snapshot at capture time) |
| `prompt` | AI prompt from profile configuration |
| `datetime` | ISO 8601 UTC timestamp of capture (`yyyy-MM-dd'T'HH:mm:ss'Z'`) |
| `type` | Media type: `image`, `audio`, or `video` |
| `data` | Base64-encoded file content (NO_WRAP flag) |

### Queue retry strategy

| HTTP Status | Action |
|---|---|
| 200, 201, 204 | ✅ Mark as `SENT`, delete from queue |
| 408, 429 | 🔄 Keep `PENDING`, retry with exponential backoff |
| 4xx (except 408, 429) | ❌ Mark as `FAILED`, no retry |
| 5xx | 🔄 Keep `PENDING`, retry |
| Network error / timeout | 🔄 Keep `PENDING`, retry when network available |

---

## Home Screen Shortcuts 📌

Each profile can be pinned to the device home screen for one-tap capture:

| Type | Shortcut action |
|---|---|
| **Image** | Opens camera directly, no app UI |
| **Video** | Opens video camera directly, no app UI |
| **Audio** | Starts foreground recording service, no app UI |

**How to create:**
1. Long-press a profile card → **Create Shortcut**
2. System will prompt to confirm pinning to home screen

**Dynamic shortcuts** (app icon long-press menu):
- First 5 profiles are available in the long-press menu
- Updated automatically when profiles change

---

## CI/CD 🚀

Workflow: [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)

### Triggers

- **Push** to `main`, `develop`
- **Pull Request** to `main`
- **Push tag** `v*` (e.g., `v1.0`)
- **Manual** via `workflow_dispatch`

### Pipeline

```
Push
 ├─ lint       — lintDebug (continue-on-error: true)
 ├─ test       — testDebugUnitTest → upload test-results artifact
 ├─ locales    — validate string keys match between EN and RU
 └─ build-debug — assembleDebug → upload debug APK (7 days)

Tag push (after parallel jobs):
 └─ build-release
     ├─ bump versionName from tag, increment versionCode
     ├─ commit + push version bump to main
     ├─ decode keystore from KEYSTORE_BASE64 secret
     ├─ assembleRelease (signed)
     └─ upload release APK (30 days)

Tag push (after build-release):
 └─ release
     ├─ download release APK
     ├─ generate changelog from git log
     └─ create GitHub Release with APK
```

### GitHub Secrets

| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | `webhooknotesender-release.jks` in base64 |
| `KEYSTORE_PASSWORD` | Keystore password |

### Release Process

```bash
git tag v1.0
git push origin v1.0
# CI: bump version → build → create GitHub Release
```

---

## Localization 🌐

- **English** — `app/src/main/res/values/strings.xml` (72 strings)
- **Russian** — `app/src/main/res/values-ru/strings.xml` (72 strings)

### How to add a new language

```bash
# 1. Create locale directory
mkdir -p app/src/main/res/values-de

# 2. Copy English strings
cp app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml

# 3. Translate all <string> values (keep name attributes!)
# 4. Update SettingsScreen to include the new locale

# 5. Add badge link in README.md:
# [![Deutsch](https://img.shields.io/badge/README-Deutsch-blue)](README.de.md)
```

> **Pro tip:** All format placeholders (`%1$s`, `%2$d`) must match between locales — they are filled programmatically.

---

## Permissions 🔐

| Permission | Purpose |
|---|---|
| `INTERNET` | Webhook HTTP requests |
| `ACCESS_NETWORK_STATE` | Network connectivity monitoring |
| `CAMERA` | Photo and video capture |
| `RECORD_AUDIO` | Audio recording |
| `POST_NOTIFICATIONS` | Foreground service notification (API 33+) |
| `FOREGROUND_SERVICE` | Audio recording service |
| `FOREGROUND_SERVICE_MICROPHONE` | Microphone foreground service type |

---

## Changelog 📋

| Version | Date | Highlights |
|---|---|---|
| v1.0.0 | 2026-07-18 | Initial release: profile CRUD, media capture, queue with WorkManager, shortcuts, bilingual UI |

---

## License 📄

MIT

---

[![English](https://img.shields.io/badge/README-English-blue)](README.md)
[![Русский](https://img.shields.io/badge/README-%D0%A0%D1%83%D1%81%D1%81%D0%BA%D0%B8%D0%B9-blue)](README_ru.md)
