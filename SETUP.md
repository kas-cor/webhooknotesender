# WebhookNoteSender — Setup Guide 🚀

## Setting up CI/CD and GitHub Secrets

### 1. Create a keystore for release APK signing

If you don't have a keystore for signing release builds yet, create one:

```bash
# Create keystore (remember the passwords you set!)
keytool -genkey -v -keystore webhooknotesender-release.jks \
        -alias webhooknotesender \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storetype PKCS12
```

> **Important:** Never commit `.jks`, `.keystore`, or password files to git.  
> The `.gitignore` is already configured to exclude `*.jks` and `*.keystore`.

### 2. Encode keystore to Base64 for GitHub Secrets

```bash
# Encode keystore to Base64
base64 -w0 webhooknotesender-release.jks > webhooknotesender-release.jks.base64

# Copy the contents
cat webhooknotesender-release.jks.base64 | wl-copy   # Linux (Wayland)
# or:
cat webhooknotesender-release.jks.base64 | xclip     # Linux (X11)
# or just open the file and copy manually
```

> **After copying, delete the base64 file:**
> ```bash
> rm webhooknotesender-release.jks.base64
> ```

### 3. Configure GitHub Secrets

Navigate to your repository on GitHub:
`https://github.com/kas-cor/webhooknotesender/settings/secrets/actions`

Click **«New repository secret»** and add the following secrets:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | Contents of `webhooknotesender-release.jks.base64` (full base64 string) |
| `KEYSTORE_PASSWORD` | The password you set when creating the keystore |
| `KEY_ALIAS` | Key alias (default: `webhooknotesender`) |
| `KEY_PASSWORD` | Key password (if not set, `KEYSTORE_PASSWORD` is used) |

![GitHub Secrets](https://docs.github.com/assets/cb-25923/images/help/settings/actions-secrets-setting.png)

### 4. Verify CI/CD Pipeline

After setting up secrets, CI/CD will work automatically:

1. **Push to `main` or `develop`** — triggers:
   - `lint` — code linting (non-blocking)
   - `test` — run unit tests (117+ tests)
   - `locales` — validate string key parity between EN and RU
   - `build-debug` — build debug APK

2. **Push tag `v*`** (e.g., `v0.3`) — additionally:
   - `build-release` — signed release build
   - `release` — create GitHub Release with APK

### 5. How to create a release

```bash
# 1. Make sure all changes are committed
git status

# 2. Create a new version tag
git tag v0.3

# 3. Push the tag
git push origin v0.3

# 4. CI/CD will automatically:
#    - Increment versionCode
#    - Build a signed APK
#    - Create a GitHub Release with changelog and APK
```

---

## Running CI checks locally

Before pushing, it's recommended to run the same checks as CI:

```bash
# Unit tests
./gradlew testDebugUnitTest

# Hardcoded strings check
./gradlew checkHardcodedStrings

# Linter
./gradlew lintDebug

# Build debug APK
./build.sh

# Build signed release APK
export KEYSTORE_PASSWORD=your-password
export KEYSTORE_PATH="$(pwd)/webhooknotesender-release.jks"
./build.sh --release
```

---

## Quick start for new developers

```bash
# Clone
git clone git@github.com:kas-cor/webhooknotesender.git
cd webhooknotesender

# Configure Android SDK
echo "sdk.dir=/home/your-user/Android/Sdk" > local.properties

# Build
chmod +x gradlew build.sh
./build.sh

# Run tests
./gradlew testDebugUnitTest
```

---

## Troubleshooting

### CI fails with «No key store» error

Make sure:
1. A keystore is created (`webhooknotesender-release.jks`)
2. `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD` secrets are set in GitHub
3. The base64 string was copied entirely (no extra spaces or line breaks)

### CI fails at the `test` stage

```bash
# Run tests locally
./gradlew testDebugUnitTest

# Make sure all tests pass
# If not — fix the errors and push again
```

### Release APK is not signed

`build.sh` has a fallback: if the keystore is not found or `KEYSTORE_PASSWORD` is not set, the APK is signed with the Android SDK debug keystore.  
For production signing, always provide `KEYSTORE_PASSWORD`.

---

## CI/CD file structure

```
.github/
├── dependabot.yml             # Automatic dependency updates
└── workflows/
    └── build-apk.yml           # Main CI/CD pipeline

build.sh                        # Local build script
SETUP.md                        # This guide
.gitignore                      # Secrets and artifact exclusions
```

---

## Links

- [WebhookNoteSender on GitHub](https://github.com/kas-cor/webhooknotesender)
- [Workflow: Build APK](.github/workflows/build-apk.yml)
- [README.md](README.md) — English documentation
- [README_ru.md](README_ru.md) — Russian documentation
- [AGENTS.md](AGENTS.md) — AI agent documentation
