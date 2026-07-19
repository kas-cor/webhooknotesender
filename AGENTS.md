# WebhookNoteSender — Project Documentation

## Overview

**WebhookNoteSender** — Android app written in Kotlin for capturing media (photo, video, audio) and sending it to an AI agent webhook. Full localization: English and Russian. DI via Hilt. Architecture: MVVM + Clean Architecture.

### Core scenario

1. User creates a **profile** with webhook URL, prompt, bearer token, media type, and compression settings
2. Taps the capture button on the profile → camera/recorder opens
3. Media is compressed (JPEG for images, GZIP for audio/video), encoded to Base64, temporary file is deleted
4. JSON with Base64 string is added to the **queue** (Room), large payloads stored as files via PayloadFileHelper
5. **WorkManager** immediately attempts to send
6. On success — the record is deleted from the queue
7. On failure — retry with exponential backoff when network becomes available

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin 2.1.0 |
| UI | Jetpack Compose + Material 3 (BOM 2024.12) |
| Navigation | Navigation Compose |
| DI | Dagger Hilt 2.53 (KSP) |
| Database | Room 2.6.1 (KSP) |
| HTTP | OkHttp 4.12 |
| Background | WorkManager + CoroutineWorker |
| Serialization | kotlinx.serialization 1.7.3 |
| Preferences | DataStore Preferences |
| Camera | CameraX 1.4.1 + ActivityResultContracts |
| Audio | MediaRecorder (AAC, 44100Hz) |
| Image compression | BitmapFactory + JPEG re-encode |
| Video compression | GZIP (file bytes) — same as audio; broken MediaCodec transcode removed |
| Video transcode (removed) | `transcodeVideo` + `compressVideoFile` — decode→H.264→MP4 mux; caused infinite loop hang |
| Build | AGP 8.7.3 / Gradle 8.9 |
| minSdk / targetSdk / compileSdk | 26 / 35 / 36 |
| Testing | JUnit 4.13.2, Robolectric 4.13 |

---

## Architecture

```
WebhookNoteSenderApp (Hilt Application, implements Configuration.Provider)
└─ MainActivity (ComponentActivity)
   └─ AppNavigation (NavHost + BottomNavBar)
      ├─ ProfilesScreen        @AndroidEntryPoint
      │   └─ ProfilesViewModel (AndroidViewModel)
      │       ├─ ProfileRepository
      │       ├─ QueueRepository
      │       ├─ Base64Encoder
      │       ├─ MediaCompressor
      │       ├─ ShortcutHelper
      │       └─ Json (kotlinx.serialization)
      ├─ ProfileEditScreen     (composable with form validation)
      │   └─ ProfilesViewModel (reused)
      ├─ QueueScreen
      │   └─ QueueViewModel
      └─ SettingsScreen
          └─ SettingsViewModel
└─ ShortcutReceiverActivity    @AndroidEntryPoint, transparent theme
   ├─ TakePicture / CaptureVideo launchers
   ├─ Base64Encoder
   ├─ MediaCompressor
   └─ AudioRecorderService     @AndroidEntryPoint, foreground service
```

### Key architectural decisions

- **Hilt DI**: All dependencies (Room, OkHttp, DataStore, utilities) provided via `@HiltAndroidApp` / `@AndroidEntryPoint` / `@HiltViewModel`
- **Room + Flow**: DAOs return `Flow<List<T>>` for reactive UI updates via `collectAsState()`
- **WorkManager**: QueueWorker processes all `PENDING` items on each run. Uses `ExistingWorkPolicy.REPLACE` to avoid duplicate workers. Exponential backoff 30s initial, max 5min
- **Large payloads**: Items with Base64 data exceeding ~2 MB use `PayloadFileHelper` to store JSON in cache files instead of Room, avoiding `SQLiteBlobTooBigException`
- **Media compression**: Images are re-encoded as JPEG (quality 0-100). Audio/video are gzip-compressed. Video transcoding via MediaCodec was removed (broken pipeline caused infinite loop hang) — video now uses gzip-only, same as audio.
- **Transparent activity**: `ShortcutReceiverActivity` uses `Theme.Transparent` — no visible UI, handles camera/audio and finishes immediately
- **Foreground service**: Audio recording uses `AudioRecorderService` with `foregroundServiceType="microphone"`

---

## Project structure

```
app/src/main/java/com/kascorp/webhooknotesender/
├── WebhookNoteSenderApp.kt              # Application class, Hilt + WorkManager config + app shortcuts init
├── MainActivity.kt                       # Single Activity, Compose host (attachBaseContext locale)
├── ShortcutReceiverActivity.kt           # Transparent activity for shortcuts + useCount tracking
├── di/
│   ├── AppModule.kt                      # DataStore, OkHttp, Json, Base64, NetworkMonitor
│   ├── DatabaseModule.kt                 # Room database + DAOs (MIGRATION_4_5) 
│   └── RepositoryModule.kt              # Empty (repos via @Inject constructors)
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt                # Room database (v5), 4 migrations
│   │   ├── PayloadFileHelper.kt          # Large payload file I/O (save/load/delete/cleanup)
│   │   ├── dao/
│   │   │   ├── ProfileDao.kt             # CRUD + unique name check + getTopProfiles + incrementUseCount
│   │   │   └── QueueDao.kt               # CRUD + status updates + pending count + orphan cleanup
│   │   └── entity/
│   │       ├── ProfileEntity.kt          # id, name (UNIQUE), type, prompt, url,
│   │       │                                bearerToken, compressEnabled, compressionQuality, useCount, useCount
│   │       └── QueueItemEntity.kt        # id, profileName, url, bearerToken, jsonPayload,
│   │                                        payloadFilePath, mediaType, status, attempts,
│   │                                        lastError, createdAt
│   ├── repository/
│   │   ├── ProfileRepository.kt          # Delegates to ProfileDao
│   │   └── QueueRepository.kt            # Delegates to QueueDao + PayloadFileHelper
│   ├── remote/
│   │   └── WebhookApi.kt                 # OkHttp POST with 30s/120s/120s timeouts
│   │                                        Returns Result with WebhookException(code, shouldRetry)
│   └── model/
│       ├── MediaType.kt                  # IMAGE, AUDIO, VIDEO
│       ├── QueueStatus.kt                # PENDING, SENDING, SENT, FAILED
│       └── ThemeMode.kt                  # LIGHT, DARK, SYSTEM
├── work/
│   └── QueueWorker.kt                    # HiltWorker, processes pending items, updates status
├── ui/
│   ├── theme/
│   │   ├── Color.kt                      # Light/Dark palettes + status colors
│   │   ├── Type.kt                       # Material 3 typography
│   │   └── Theme.kt                      # dynamicColor + themeMode support
│   ├── navigation/
│   │   └── AppNavigation.kt              # NavHost + bottom nav (Profiles, Queue, Settings)
│   ├── profiles/
│   │   ├── ProfilesScreen.kt             # Card list + capture via ActivityResultLauncher
│   │   ├── ProfilesViewModel.kt          # CRUD + capture + compression + encoding + test webhook
│   │   └── ProfileEditScreen.kt          # Form with name/type/prompt/url/bearer/compression
│   ├── queue/
│   │   ├── QueueScreen.kt                # Swipeable item list + status colors + retry
│   │   └── QueueViewModel.kt             # List + pending count + retry all failed
│   ├── settings/
│   │   ├── SettingsScreen.kt             # Theme + language selection + about
│   │   └── SettingsViewModel.kt          # Theme/language state
│   ├── audio/
│   │   └── AudioRecordingScreen.kt       # Composable audio recording UI
│   └── components/
│       ├── CaptureButton.kt              # FAB with type-specific icon
│       ├── AudioRecorder.kt              # Composable with pulsing mic + timer + record/stop
│       ├── AudioRecorderService.kt       # Foreground service (MediaRecorder AAC)
│       └── StatusBadge.kt                # Colored badge: PENDING/SENDING/SENT/FAILED
└── util/
    ├── Base64Encoder.kt                  # Streaming Base64 (8KB chunks, NO_WRAP)
    ├── DateTimeUtils.kt                  # ISO 8601 UTC formatter
    ├── LocaleHelper.kt                   # Locale persistence + wrapContext for attachBaseContext
    ├── MediaCompressor.kt                # JPEG image compression, gzip, video transcode
    ├── NetworkMonitor.kt                 # ConnectivityManager callback → Flow<Boolean>
    └── ShortcutHelper.kt                 # Pinned + dynamic shortcuts via ShortcutManagerCompat
```

---

## Data flow

### Compression flow

```
MediaCompressor.compress(bytes, type, quality)  /  .compressFile(file, type, quality)
  │
  ├─ "image" → BitmapFactory.decode → Bitmap.compress(JPEG, quality) → CompressResult("jpeg")
  │
  ├─ "video" → gzipCompress(raw bytes) → CompressResult("gzip")   ← gzip only (transcode removed)
  │
  └─ "audio" / else → gzipCompress(raw bytes) → CompressResult("gzip")
```

### Media capture flow

```
User taps capture button on profile card
  │
  ├─ image/video → ActivityResultContracts.TakePicture/CaptureVideo
  │                  → FileProvider temp file (cache dir)
  │                  → Camera app captures media
  │                  → On result: read bytes → compress (if enabled) → Base64 → delete temp file
  │                  → ViewModel.enqueueCapturedMedia() → QueueItemEntity → Room
  │                  → QueueWorker.enqueue() → WorkManager starts
  │
  └─ audio → AudioRecorderService (foreground service with notification)
              → MediaRecorder records AAC to cache file
              → On stop: read file → compress (if enabled) → Base64 → delete temp file
              → QueueItemEntity → Room → QueueWorker.enqueue()
```

### Queue processing flow

```
QueueWorker.doWork()
  │
  ├─ cleanupOrphanedPayloads() — removes stale files from cache
  │
  ├─ queueRepository.getPendingItems() → List<QueueItemEntity>
  │
  ├─ For each item:
  │    ├─ Update status → SENDING
  │    ├─ Load payload from file (if payloadFilePath is set) or from jsonPayload
  │    ├─ webhookApi.send(url, jsonPayload, bearerToken)
  │    │
  │    ├─ Success (HTTP 200/201/204):
  │    │    └─ deleteById()  ← deletes DB record + payload file
  │    │
  │    ├─ Client error (4xx except 408, 429):
  │    │    └─ Update status → FAILED, delete payload file (will never succeed)
  │    │
  │    └─ Server error / network error / timeout:
  │         └─ Update status → PENDING (retry with backoff)
  │
  └─ If any failures: Result.retry() → WorkManager re-enqueues with backoff
```

### Shortcut flow (without opening app)

#### Pinned shortcuts (home screen)
```
User taps home screen shortcut
  │
  └─ ShortcutReceiverActivity (transparent theme)
       ├─ Read profile_id from Intent
       ├─ Load profile from Room (coroutine)
       │
       ├─ image → request CAMERA permission → TakePicture → compress (gzip) → encode → queue → finish()
       ├─ video → request CAMERA permission → CaptureVideo → compress (gzip) → encode → queue → finish()
       └─ audio → request RECORD_AUDIO → start AudioRecorderService → finish()
```

#### Dynamic shortcuts (long-press app icon)
```
User long-presses app icon (launcher)
  │
  ├─ ShortcutManager displays up to 5 dynamic shortcuts
  │  (set via ShortcutHelper.updateAppShortcuts() on startup + after captures)
  │
  └─ User taps a profile shortcut
       └─ Same flow as pinned shortcuts → ShortcutReceiverActivity
```

---

## Database schema

### Table `profiles`

| Column | Type | Description |
|---|---|---|
| `id` | Long (PK, autoGenerate) | Identifier |
| `name` | String | **UNIQUE** — profile name |
| `type` | String | `image`, `audio`, `video` |
| `prompt` | String | AI prompt |
| `url` | String | Webhook URL |
| `bearer_token` | String? | Bearer token, nullable |
| `compress_enabled` | Boolean | Enable media compression (default `true`) |
| `compression_quality` | Int | JPEG quality 0–100 / (default `70`); for audio/video — ignored (gzip only) |
| `use_count` | Int | Usage frequency counter for app shortcuts ranking (default `0`) |

### Table `queue_items`

| Column | Type | Description |
|---|---|---|
| `id` | Long (PK, autoGenerate) | Identifier |
| `profile_name` | String | Profile name (snapshot) |
| `url` | String | Webhook URL (snapshot) |
| `bearer_token` | String? | Token (snapshot) |
| `json_payload` | String | Complete JSON with Base64 data (empty if file-backed) |
| `payload_file_path` | String? | Filename in cache dir for large payloads |
| `media_type` | String | `image`, `audio`, `video` |
| `status` | String | `PENDING`, `SENDING`, `SENT`, `FAILED` |
| `attempts` | Int | Retry count (default 0) |
| `last_error` | String? | Last error message |
| `created_at` | Long | Unix timestamp (ms) |

### Migrations

| From | To | Changes |
|---|---|---|
| 1 | 2 | Add `payload_file_path TEXT` to `queue_items` |
| 2 | 3 | Clear oversized json_payload (>100KB), delete SENT items |
| 3 | 4 | Add `compress_enabled INTEGER` and `compression_quality INTEGER` to `profiles` |
| 4 | 5 | Add `use_count INTEGER NOT NULL DEFAULT 0` to `profiles` |

---

## Webhook API

### Request

```
POST {url}
Content-Type: application/json
Authorization: Bearer {token}  (optional)
Accept: application/json
```

### Payload

```json
{
  "messages": [
    {
      "name": "string",
      "prompt": "string",
      "datetime": "2026-07-18T12:00:00Z",
      "type": "image|audio|video",
      "data": "Base64-encoded content (NO_WRAP)",
      "encoding": "jpeg|gzip"
    }
  ]
}
```

`encoding` is present when compression is enabled:
- `"jpeg"` — image re-encoded as JPEG (self-contained, no decoding needed)
- `"gzip"` — audio/video gzip-compressed (server must gzip-decompress)
- absent — raw Base64 data (compression disabled)

### Error handling

| Condition | Status | shouldRetry | Queue action |
|---|---|---|---|
| HTTP 200, 201, 204 | Success | — | Delete from queue |
| HTTP 4xx (except 408, 429) | Failure | `false` | `FAILED` |
| HTTP 408, 429 | Failure | `true` | `PENDING`, retry |
| HTTP 5xx | Failure | `true` | `PENDING`, retry |
| SocketTimeout | Failure | `true` | `PENDING`, retry |
| UnknownHost | Failure | `true` | `PENDING`, retry |
| Other Exception | Failure | `true` | `PENDING`, retry |

---

## Key classes and responsibilities

### `ProfilesViewModel`
- Manages list of `ProfileEntity` via `StateFlow`
- Form state for create/edit with validation
- `saveProfile()` — insert or update in Room, handles UNIQUE constraint; updates app shortcuts on rename
- `deleteProfile()` — removes shortcut if exists, deletes from Room; updates app shortcuts
- `createShortcut()` — `ShortcutHelper.requestPinShortcut()`
- `isShortcutCreated()` / `removeShortcut()` — delegates to `ShortcutHelper`
- `compressAndEncode()` — compresses via `MediaCompressor`, encodes via `Base64Encoder`
- `buildJsonPayload()` — constructs JSON with name/prompt/datetime/type/data/encoding
- `enqueueCapturedMedia()` — builds JSON payload (file-backed for large payloads), inserts to queue, triggers worker; increments profile `useCount` and updates app shortcuts
- `testWebhook()` — sends test payload with empty Base64 to verify connectivity

### `QueueWorker` (HiltWorker)
- `doWork()` — processes all `PENDING` items
- For each item: updates status to `SENDING` → loads payload (from file or DB) → sends via `WebhookApi` → updates result
- Returns `Result.retry()` if any item needs retry, `Result.success()` otherwise
- Static `enqueue(context)` — creates `OneTimeWorkRequest` with `NetworkType.CONNECTED` constraint

### `ShortcutHelper`
- `@Singleton` class, `@Inject constructor(@ApplicationContext)`
- `requestPinShortcut(profile)` — pinned shortcut on home screen (user confirms via system dialog); cleans up disabled shortcut first to avoid crash
- `removeShortcut(profileId)` — removes pinned/dynamic shortcut via 4 fallback methods (dynamic, compat long-lived, platform API 30+, disable as grey fallback)
- `isShortcutCreated(profileId)` — checks via dynamic shortcuts API and SharedPreferences (source of truth for pinned shortcuts; Xiaomi launchers don't report pinned shortcuts correctly)
- `updateAppShortcuts(profiles)` — sets top 5 profiles as dynamic shortcuts for app icon long-press menu; uses distinct `"app_shortcut_"` ID prefix to avoid collision with pinned `"shortcut_"`
- `createShortcutInfo(profile, idPrefix, longLived)` — creates `ShortcutInfoCompat` with colored circle icon, reused for both pinned and app shortcuts

### `ShortcutReceiverActivity`
- Transparent theme, no visible UI
- Receives `profile_id` from shortcut Intent
- Requests permissions (CAMERA or RECORD_AUDIO) at runtime
- Launches camera or starts audio recording service
- On capture complete: compresses + encodes to Base64, inserts to queue (file-backed), increments profile `useCount`, shows toast, finishes

### `AudioRecorderService`
- Foreground service with `microphone` type
- Records AAC audio via `MediaRecorder` to cache directory
- Notification with stop action
- On stop: compresses + encodes to Base64, deletes temp file, inserts to queue, increments profile `useCount`, triggers worker

### `MediaCompressor`
- `@Singleton` class, `@Inject constructor()`
- `compress(data, mediaType, quality)` — dispatches by type
- `compressFile(file, mediaType, quality)` — file-based dispatch
- `compressImageBytes / compressImageFile` — decode → JPEG re-encode
- `gzipCompress / gzipDecompress` — GZIP for audio/video/fallback
- **Note:** Video transcoding via MediaCodec (`transcodeVideo` / `compressVideoFile`) was **removed** because the decoder/encoder/surface pipeline caused an infinite loop hang. Both `compress()` and `compressFile()` now use the same gzip-only path for audio/video.
- Returns `CompressResult` with `data`, `encoding` (`"jpeg"` / `"gzip"`), `originalSize`, `compressedSize`

### `WebhookApi`
- OkHttp client with 30s connect / 120s read / 120s write timeouts
- `send(url, jsonPayload, bearerToken)` → `Result<String>`
- Returns `WebhookException(shouldRetry)` for proper retry decision

### `PayloadFileHelper`
- `object`, no DI needed
- Saves/loads/deletes large JSON payloads as files in `cacheDir/queue_payloads/`
- `cleanupOrphanedFiles()` — removes files not referenced by any DB record

### `Base64Encoder`
- `@Singleton` class, `@Inject constructor()`
- `encode(bytes)` — `Base64.NO_WRAP` for byte arrays
- `encodeFile(file)` — streaming encode via JDK Base64 `wrap(outputStream)`, 8KB buffer

### `LocaleHelper`
- `object`, no DI needed
- `wrapContext(context)` — applies saved locale in `attachBaseContext()`
- `saveLanguage(context, language)` — persists to SharedPreferences
- Dual persistence: DataStore (for ViewModel reactivity) + SharedPreferences (for sync attachBaseContext)

---

## Conventions

### Code style
- Kotlin official style (`kotlin.code.style=official`)
- Jetpack Compose with Material 3
- MVVM with Clean Architecture
- All user-facing strings in `stringResource()` via `strings.xml`
- DI via Hilt (`@Inject`, `@HiltViewModel`, `@AndroidEntryPoint`)
- Reactive UI via `StateFlow` + `collectAsState()`

### Naming
- ViewModels: `{Feature}ViewModel`
- Screens: `{Feature}Screen`
- Dependencies: `{Feature}Repository`, `{Feature}Dao`
- Database: `{Entity}Entity`
- Packages: lowercase, feature-based (`ui/profiles/`, `ui/queue/`, etc.)

### Error handling
- Repository methods return data classes or throw Room exceptions
- WebhookApi returns `Result<String>` with typed `WebhookException`
- ViewModel catches exceptions and updates form error state
- UI uses `Snackbar` or `Toast` for user-facing errors
- Worker cleanup: orphaned payload files removed on each `doWork()` run

### Resources / Localization (для ИИ-агентов)
- **English**: `res/values/strings.xml`
- **Russian**: `res/values-ru/strings.xml`
- **НИКАКИХ хардкодных строк** в Kotlin-коде. Все пользовательские строки — только через `stringResource()` / `getString()`.

#### Какое API использовать:
| Контекст | API |
|---|---|
| `@Composable` функция | `stringResource(R.string.xxx)` |
| `Activity` / `Service` | `getString(R.string.xxx)` |
| `ViewModel` (есть `Application`) | `application.getString(R.string.xxx)` |
| `Toast.makeText(context, ..., ...)` | `context.getString(R.string.xxx)` |

#### Правила для ИИ-агентов:
1. **Никогда** не пиши `"Some string"` напрямую в Kotlin-коде, если это видит пользователь.
2. **Форматные строки** используй с `%1$s`, `%2$d` и т.д. — оба локализационных файла должны иметь одинаковые плейсхолдеры.
3. **Content descriptions** для иконок — тоже строковые ресурсы, помечай `translatable="false"`.
4. **Новые строки** добавляй сразу в **оба** файла: `values/strings.xml` + `values-ru/strings.xml`.
5. **Изменяя существующую строку** — обновляй оба перевода.
6. **В тестах**, где `application` — mock, обязательно мокай `application.getString(R.string.xxx)` для тех ресурсов, чьё содержимое проверяется (не только `!= null`).

#### Примеры

✅ Правильно:
```kotlin
// В Compose:
Text(stringResource(R.string.your_profiles))
Icon(contentDescription = stringResource(R.string.cd_capture_format, mediaType))

// В Activity:
Toast.makeText(this, getString(R.string.added_to_queue), Toast.LENGTH_SHORT).show()

// В ViewModel:
application.getString(R.string.name_required)

// В тесте:
every { application.getString(R.string.url_http_warning) } returns "Using HTTP is not secure. Consider using HTTPS."
```

❌ Неправильно:
```kotlin
Text("Your Profiles")                 // хардкод
Toast.makeText(this, "Added", ...)     // хардкод
application.getString("Name required") // не через R
contentDescription = "Queue"           // хардкод
```

---

## Testing

```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Run specific test class
./gradlew testDebugUnitTest --tests "*MediaCompressorTest*"

# Run migration tests
./gradlew testDebugUnitTest --tests "*MigrationTest*"
```

### Test files

| File | Scope |
|---|---|
| `MigrationTest.kt` | Room database migrations (1→2, 2→3, 3→4) |
| `ProfileRepositoryTest.kt` | Profile CRUD, unique constraint |
| `QueueRepositoryTest.kt` | Queue item CRUD, status transitions |
| `ProfilesViewModelTest.kt` | ViewModel state, validation, capture flow |
| `QueueViewModelTest.kt` | Queue list, pending count, retry all |
| `SettingsViewModelTest.kt` | Theme/language state |
| `MediaCompressorTest.kt` | Image JPEG compression, gzip roundtrip, quality levels |
| `LocaleHelperTest.kt` | Locale persistence and resolution |
| `QueueWorkerTest.kt` | Worker dispatch, status updates, retry logic |

#### Паттерн для UI-тестов (androidTest)

В `SettingsScreenTest` используется паттерн для получения строк из ресурсов без хардкода:

```kotlin
private var ctx: Context? = null

private fun str(id: Int): String = ctx!!.getString(id)
private fun str(id: Int, vararg args: Any?): String = ctx!!.getString(id, *args)

@Test
fun example() {
    composeTestRule.setContent {
        ctx = LocalContext.current  // сохраняем контекст для str()
        SettingsScreen(viewModel = vm)
    }
    composeTestRule.onNodeWithText(str(R.string.nav_settings)).assertIsDisplayed()
}
```

Правила:
- `ctx` присваивается внутри `setContent { }` через `LocalContext.current`
- `str()` использует `ctx!!.getString()` — безопасно, т.к. `setContent` выполняется синхронно
- Все `onNodeWithText("...")` заменяются на `onNodeWithText(str(R.string.xxx))`
- Для тестов дропдаунов: стартуй с другого языка, чтобы избежать дублирования текста

---

## CI/CD (GitHub Actions)

Workflow: `.github/workflows/build-apk.yml`

### Pipeline stages

1. **lint** — `lintDebug` (non-blocking)
2. **test** — `testDebugUnitTest` → upload `test-results` (7 days)
3. **locales** — validates string keys match between EN and RU
4. **build-debug** — `assembleDebug` → upload debug APK (7 days)
5. **build-release** (tag push `v*` only) — version bump → decode keystore → `assembleRelease` → upload (30 days)
6. **release** (tag push only) — download APK → generate changelog → create GitHub Release

### GitHub Secrets

| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | `webhooknotesender-release.jks` in base64 |
| `KEYSTORE_PASSWORD` | Keystore password |

### Build script

`build.sh` — see [README.md](README.md#build-script-buildsh-) for full documentation.

---

## Common tasks

### Adding a new feature
1. Create DAO method in `ProfileDao` or `QueueDao`
2. Add repository method in `ProfileRepository` or `QueueRepository`
3. Create use case in `domain/usecase/` (if complex logic)
4. Add ViewModel state + logic
5. Create/update Compose screen
6. Add string resources to both `values/strings.xml` and `values-ru/strings.xml`
7. Add navigation route in `AppNavigation.kt`

### Adding a new language
1. `mkdir -p app/src/main/res/values-{locale}`
2. `cp app/src/main/res/values/strings.xml app/src/main/res/values-{locale}/strings.xml`
3. Translate all `<string>` values (keep `name` attributes unchanged)
4. Update `SettingsScreen.kt` to include the new locale option
5. Verify CI locale validation passes

### Releasing
```bash
# Bump versionName and versionCode in app/build.gradle.kts
git tag v1.1
git push origin v1.1
# CI handles: build → release
```

---

## Permissions

| Permission | Required for |
|---|---|
| `INTERNET` | Webhook HTTP requests |
| `ACCESS_NETWORK_STATE` | Network monitoring via `ConnectivityManager` |
| `CAMERA` | Photo/video capture |
| `RECORD_AUDIO` | MediaRecorder audio recording |
| `POST_NOTIFICATIONS` | Foreground service notification (API 33+) |
| `FOREGROUND_SERVICE` | AudioRecorderService |
| `FOREGROUND_SERVICE_MICROPHONE` | Microphone foreground service type |

---

## Key links

- [README.md](README.md) — English documentation
- [README_ru.md](README_ru.md) — Russian documentation
- [SETUP.md](SETUP.md) — CI/CD setup and GitHub Secrets guide
- [build.sh](build.sh) — Build script
- [.github/workflows/build-apk.yml](.github/workflows/build-apk.yml) — CI/CD pipeline
- [app/build.gradle.kts](app/build.gradle.kts) — Dependencies and build config
