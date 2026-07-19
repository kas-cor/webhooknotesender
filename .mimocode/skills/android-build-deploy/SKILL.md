---
name: android-build-deploy
description: Build Android APK, install on connected device, launch app, and monitor logs for crashes. Automatically retries on failure.
---

# Android Build-Deploy-Debug

Automated cycle for building, installing, and testing Android apps on physical devices.

## Prerequisites

- Connected Android device via USB (`adb devices` shows device)
- Android SDK with build-tools installed
- Project uses Gradle wrapper (`./gradlew`)

## Workflow

### 1. Build Debug APK

```bash
./gradlew assembleDebug 2>&1 | tail -30
```

- Timeout: 300 seconds
- Check for `BUILD SUCCESSFUL` in output
- If build fails, report error and stop

### 2. Install on Device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk 2>&1
```

- Timeout: 60 seconds
- `-r` flag replaces existing installation
- Verify `Success` in output

### 3. Launch App and Capture Logs

```bash
adb shell am force-stop <package_name>
adb logcat -c
adb shell am start -n <package_name>/<activity>
sleep 5
adb logcat -d -v time --pid=$(adb shell pidof <package_name>) 2>&1 | head -200
```

- Package name: `com.kascorp.webhooknotesender`
- Main activity: `.MainActivity`
- Wait 5 seconds for app to initialize
- Capture first 200 log lines

### 4. Check for Crashes

```bash
adb logcat -d -v time -s AndroidRuntime:E ActivityManager:W 2>&1 | tail -80
```

Look for:
- `FATAL EXCEPTION` in logs
- `AndroidRuntime: E` error tags
- Crash stack traces

### 5. Handle Results

**If crashes detected:**
1. Analyze crash log
2. Apply fix to code
3. Return to step 1 (rebuild)

**If no crashes:**
- Report successful deployment
- Log clean startup confirmation

## Error Handling

| Issue | Action |
|-------|--------|
| No device connected | Prompt user to connect device |
| Build failure | Show error, suggest `./gradlew clean` |
| Install failure | Check device storage, suggest uninstall first |
| App crash | Capture stack trace, analyze, suggest fix |
| Logcat timeout | Increase timeout or check device connection |

## Example Usage

User: "Собери проект, установи на устройство, проверь логи"

Agent executes:
1. `adb devices` → verify device
2. `./gradlew assembleDebug` → build
3. `adb install -r` → install
4. Launch + capture logs → monitor
5. Check crash logs → report status

## Customization

To adapt for different projects, modify:
- Package name in launch command
- APK output path if non-standard
- Log capture duration (sleep value)
- Specific log tags to monitor
