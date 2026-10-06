#!/usr/bin/env bash
# Install and keep updating the debug build on a real phone (USB or wireless debugging).
#   tools/phone.sh              install (or update in place) and launch
#   tools/phone.sh watch        rebuild + reinstall whenever source files change
#   tools/phone.sh logs         live log of the app, crashes highlighted
#   tools/phone.sh pair IP:PORT CODE    one-time wireless pairing (Android 11+)
#   tools/phone.sh connect IP:PORT      connect over Wi-Fi after pairing
# Updates keep your data because every debug build uses the same signing key. Never mix this with the
# release APK (different key): Android would require an uninstall.
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
APP=app.cove.companion

help_connect() {
  cat <<'H'
No phone found. One-time setup on the phone:
  1. Settings > About phone > tap "Build number" 7 times (turns on Developer options).
  2. Settings > System > Developer options > turn on "USB debugging" (and "Wireless debugging" for Wi-Fi).
  3a. USB: plug it in, accept the "Allow USB debugging?" prompt, then run this script again.
  3b. Wi-Fi: Developer options > Wireless debugging > "Pair device with pairing code", then run
        tools/phone.sh pair <IP:PORT shown> <6-digit code>
      and then tools/phone.sh connect <IP:PORT shown on the main Wireless debugging screen>
H
}

phone_serial() {
  # First attached device that is not an emulator.
  "$ADB" devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ {print $1; exit}'
}

case "${1:-install}" in
  pair)    "$ADB" pair "${2:?IP:PORT}" "${3:?pairing code}"; exit ;;
  connect) "$ADB" connect "${2:?IP:PORT}"; exit ;;
esac

SERIAL="${PHONE_SERIAL:-$(phone_serial)}"   # PHONE_SERIAL overrides detection (also handy for an emulator)
if [ -z "$SERIAL" ]; then help_connect; exit 1; fi
export ANDROID_SERIAL="$SERIAL"
echo "Using phone: $SERIAL ($("$ADB" shell getprop ro.product.model | tr -d '\r'))"

install_and_launch() {
  ./gradlew :app:installDebug -q
  "$ADB" shell am start -n "$APP/.MainActivity" >/dev/null
  echo "Installed and launched at $(date +%H:%M:%S)"
}

case "${1:-install}" in
  install) install_and_launch ;;
  logs)    "$ADB" logcat -c; "$ADB" logcat -v time --pid="$("$ADB" shell pidof "$APP" | tr -d '\r')" '*:W' ;;
  watch)
    install_and_launch
    stamp="$(mktemp)"
    echo "Watching app/src and design tokens; save a file to update the phone (Ctrl-C to stop)."
    while true; do
      sleep 2
      if [ -n "$(find app/src gradle -type f \( -name '*.kt' -o -name '*.xml' -o -name '*.kts' -o -name '*.toml' \) -newer "$stamp" 2>/dev/null | head -1)" ]; then
        touch "$stamp"
        install_and_launch || echo "Build failed: fix the error and save again."
      fi
    done ;;
  *) echo "Unknown command: $1"; exit 2 ;;
esac
