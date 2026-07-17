#!/usr/bin/env bash
set -euo pipefail

# ===== Settings =====
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
APK_PATH_RELEASE="$PROJECT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
GRADLE="$PROJECT_DIR/gradlew"
ADB="adb"
PACKAGE="com.kascorp.webhooknotesender"

# ===== Colors =====
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ===== Dependency checks =====
check_deps() {
    info "Checking dependencies..."

    if [ ! -f "$GRADLE" ]; then
        error "gradlew not found: $GRADLE"
        exit 1
    fi

    if ! command -v "$ADB" &>/dev/null; then
        error "adb not found. Install Android SDK platform-tools."
        exit 1
    fi

    ok "All basic dependencies are in place."
}

check_curl() {
    if ! command -v curl &>/dev/null; then
        error "curl not found. Install curl."
        exit 1
    fi
}

# ===== Build debug =====
build() {
    info "Building debug APK..."
    cd "$PROJECT_DIR"
    chmod +x "$GRADLE"
    "$GRADLE" assembleDebug --no-daemon 2>&1
    ok "Build complete."
}

# ===== Build release =====
build_release() {
    info "Building release APK..."

    local keystore="$PROJECT_DIR/webhooknotesender-release.jks"
    local sign_with_debug=false

    if [ -f "$keystore" ] && [ -n "${KEYSTORE_PASSWORD:-}" ]; then
        info "Signing with production keystore: $keystore"
    elif [ -f "$keystore" ] && [ -z "${KEYSTORE_PASSWORD:-}" ]; then
        warn "KEYSTORE_PASSWORD not set. Will sign with Android SDK debug keystore."
        sign_with_debug=true
    else
        warn "Keystore not found. Will sign with Android SDK debug keystore."
        sign_with_debug=true
    fi

    cd "$PROJECT_DIR"
    chmod +x "$GRADLE"

    if $sign_with_debug; then
        if [ -f "$keystore" ]; then
            mv "$keystore" "${keystore}.bak"
            trap 'mv "${keystore}.bak" "$keystore"' EXIT
        fi

        "$GRADLE" assembleRelease --no-daemon 2>&1

        local debug_ks="$HOME/.android/debug.keystore"
        if [ ! -f "$debug_ks" ]; then
            error "Debug keystore not found: $debug_ks"
            return 1
        fi

        local build_tools
        build_tools=$(ls -d "$HOME/Android/Sdk/build-tools/"* 2>/dev/null | sort -V | tail -1)
        if [ -z "$build_tools" ]; then
            error "build-tools not found in Android SDK."
            return 1
        fi

        local unsigned="$APK_PATH_RELEASE"
        local aligned="${unsigned%.*}-aligned.apk"

        "$build_tools/zipalign" -v -p 4 "$unsigned" "$aligned" > /dev/null 2>&1
        "$build_tools/apksigner" sign \
            --ks "$debug_ks" \
            --ks-pass pass:android \
            --ks-key-alias androiddebugkey \
            --key-pass pass:android \
            "$aligned" 2>&1

        ok "Release APK signed with debug keystore: $aligned"
    else
        "$GRADLE" assembleRelease --no-daemon 2>&1
        ok "Release APK signed with production keystore: $APK_PATH_RELEASE"
    fi
}

# ===== Check APK =====
check_apk() {
    local apk="${1:-$APK_PATH}"
    if [ ! -f "$apk" ]; then
        error "APK not found: $apk"
        exit 1
    fi
    local size
    size=$(du -h "$apk" | cut -f1)
    ok "APK found: $apk ($size)"
}

# ===== Check devices =====
check_device() {
    info "Looking for connected devices..."
    local devices
    devices=$("$ADB" devices 2>/dev/null | grep -v "^List" | grep -v "^$" || true)

    if [ -z "$devices" ]; then
        error "No connected devices found."
        echo ""
        echo "  Connect a device via USB and enable USB debugging,"
        echo "  or start an emulator."
        echo ""
        echo "  To connect via Wi-Fi:"
        echo "    adb tcpip 5555"
        echo "    adb connect <DEVICE_IP>:5555"
        exit 1
    fi

    local count
    count=$(echo "$devices" | wc -l)
    ok "Devices found: $count"
    echo "$devices" | while read -r line; do
        echo "  → $line"
    done
}

# ===== Install APK =====
install_apk() {
    local apk="${1:-$APK_PATH}"
    info "Installing APK on device..."
    "$ADB" install -r "$apk" 2>&1
    ok "APK installed."
}

# ===== Launch app =====
launch_app() {
    info "Launching app..."
    "$ADB" shell am start -n "$PACKAGE/.MainActivity" 2>&1
    ok "App launched."
}

# ===== Clear app data =====
clear_data() {
    info "Clearing app data..."
    "$ADB" shell pm clear "$PACKAGE" 2>&1
    ok "App data cleared."
}

# ===== Show logs (logcat) =====
show_logs() {
    info "WebhookNoteSender logs (logcat). Press Ctrl+C to exit."
    echo ""
    "$ADB" logcat -v time \
        | grep -E "(ShortcutReceiverActivity|AudioRecorderService|QueueWorker|WebhookApi|MainActivity|AndroidRuntime|$PACKAGE|ProfileDao|QueueDao)" \
        --line-buffered
}

# ===== Send test POST to webhook =====
send_test() {
    info "Sending test POST to webhook..."

    if [ $# -lt 1 ]; then
        error "Specify webhook URL: $0 --test https://example.com/webhook [bearer_token]"
        exit 1
    fi

    check_curl

    local url="$1"
    local token="${2:-}"
    local ts_iso
    ts_iso=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    # Build payload per WebhookNoteSender spec
    local payload
    payload=$(printf '{
  "messages": [
    {
      "name": "test_profile",
      "prompt": "Test request from build.sh",
      "datetime": "%s",
      "type": "image",
      "data": ""
    }
  ]
}' "$ts_iso")

    local http_code
    if [ -n "$token" ]; then
        http_code=$(curl -s -o /dev/null -w "%{http_code}" \
            -X POST "$url" \
            -H "Content-Type: application/json" \
            -H "Accept: application/json" \
            -H "Authorization: Bearer $token" \
            -d "$payload")
    else
        http_code=$(curl -s -o /dev/null -w "%{http_code}" \
            -X POST "$url" \
            -H "Content-Type: application/json" \
            -H "Accept: application/json" \
            -d "$payload")
    fi

    if [ "$http_code" -eq 200 ] || [ "$http_code" -eq 201 ] || [ "$http_code" -eq 204 ]; then
        ok "Test POST sent. HTTP $http_code"
    else
        warn "Test POST responded with HTTP $http_code"
    fi
}

# ===== Full cycle: build → install → launch =====
run_full() {
    echo ""
    echo -e "${GREEN}╔══════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║  WebhookNoteSender — Build & Run         ║${NC}"
    echo -e "${GREEN}╚══════════════════════════════════════════╝${NC}"
    echo ""
    check_deps
    echo ""
    build
    echo ""
    check_apk
    echo ""
    check_device
    echo ""
    install_apk
    echo ""
    launch_app
    echo ""
    ok "Done! 🎉"
}

# ===== Usage =====
usage() {
    echo ""
    echo -e "${GREEN}╔══════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║  WebhookNoteSender — Build Tool          ║${NC}"
    echo -e "${GREEN}╚══════════════════════════════════════════╝${NC}"
    echo ""
    echo "Usage: $0 [flags]"
    echo ""
    echo "Flags:"
    echo "  --run               Build debug APK, install, and launch"
    echo "  --release           Build release APK (signed)"
    echo "  --install [apk]     Install APK on device (default: debug)"
    echo "  --launch            Launch app on device"
    echo "  --clear             Clear app data on device"
    echo "  --logs              Show logcat filtered by app package"
    echo "  --test <url> [token] Send test POST to webhook"
    echo "  --help, -h          Show this help"
    echo ""
    echo "Without flags — build debug APK only."
    echo ""
}

# ===== Main =====
main() {
    local do_run=false
    local do_release=false
    local do_install=""
    local do_launch=false
    local do_clear=false
    local do_logs=false
    local do_test=""
    local do_test_token=""

    if [ $# -eq 0 ]; then
        build
        exit 0
    fi

    while [ $# -gt 0 ]; do
        case "$1" in
            --run)
                do_run=true
                shift
                ;;
            --release)
                do_release=true
                shift
                ;;
            --install)
                shift
                if [ $# -gt 0 ] && [[ "$1" != --* ]]; then
                    do_install="$1"
                    shift
                else
                    do_install="$APK_PATH"
                fi
                ;;
            --launch)
                do_launch=true
                shift
                ;;
            --clear)
                do_clear=true
                shift
                ;;
            --logs)
                do_logs=true
                shift
                ;;
            --test)
                shift
                if [ $# -gt 0 ] && [[ "$1" != --* ]]; then
                    do_test="$1"
                    shift
                else
                    error "--test requires a URL"
                    exit 1
                fi
                if [ $# -gt 0 ] && [[ "$1" != --* ]]; then
                    do_test_token="$1"
                    shift
                fi
                ;;
            --help|-h)
                usage
                exit 0
                ;;
            *)
                error "Unknown flag: $1"
                usage
                exit 1
                ;;
        esac
    done

    # --run
    if $do_run; then
        run_full
    fi

    # --release
    if $do_release; then
        echo ""
        echo -e "${GREEN}╔══════════════════════════════════════════╗${NC}"
        echo -e "${GREEN}║  WebhookNoteSender — Release Build       ║${NC}"
        echo -e "${GREEN}╚══════════════════════════════════════════╝${NC}"
        echo ""
        check_deps
        echo ""
        build_release
        echo ""
        ok "Done! 🎉"
    fi

    # --install
    if [ -n "$do_install" ]; then
        echo ""
        echo -e "${GREEN}╔══════════════════════════════════════════╗${NC}"
        echo -e "${GREEN}║  WebhookNoteSender — Install APK        ║${NC}"
        echo -e "${GREEN}╚══════════════════════════════════════════╝${NC}"
        echo ""
        check_apk "$do_install"
        echo ""
        check_device
        echo ""
        install_apk "$do_install"
        echo ""
        ok "Done! 🎉"
    fi

    # --launch
    if $do_launch; then
        check_device
        launch_app
    fi

    # --clear
    if $do_clear; then
        check_device
        clear_data
    fi

    # --logs
    if $do_logs; then
        show_logs
    fi

    # --test
    if [ -n "$do_test" ]; then
        echo ""
        echo -e "${GREEN}╔══════════════════════════════════════════╗${NC}"
        echo -e "${GREEN}║  WebhookNoteSender — Test POST          ║${NC}"
        echo -e "${GREEN}╚══════════════════════════════════════════╝${NC}"
        echo ""
        send_test "$do_test" "$do_test_token"
        echo ""
    fi
}

main "$@"
