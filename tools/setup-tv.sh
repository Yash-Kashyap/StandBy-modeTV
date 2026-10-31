#!/usr/bin/env bash
# StandBy TV - one-step TV setup (macOS / Linux)
#
# Installs the app over ADB, allows the Now Playing music screen, and makes StandBy TV
# the screensaver that starts automatically when the TV is idle.
#
# Usage:   ./tools/setup-tv.sh <TV-IP> [idle-minutes=5] [sleep-hours=4] [apk-path]
# Example: ./tools/setup-tv.sh 192.168.1.9
#          ./tools/setup-tv.sh 192.168.1.9 6 0      (6 min idle, never turn the TV off)
#          SKIP_INSTALL=1 ./tools/setup-tv.sh 192.168.1.9   (settings only)
set -u

TV_IP="${1:?Usage: $0 <TV-IP> [idle-minutes] [sleep-hours] [apk-path]}"
IDLE_MIN="${2:-5}"
SLEEP_H="${3:-4}"
HERE="$(cd "$(dirname "$0")" && pwd)"
APK="${4:-$HERE/../apk/StandBy-TV.apk}"

PKG="com.yash.standbytv"
LISTENER="$PKG/$PKG.MediaListener"
DREAM="$PKG/.StandbyDream"

step() { printf '\n\033[36m==> %s\033[0m\n' "$1"; }
ok()   { printf '    \033[32mOK\033[0m  %s\n' "$1"; }
warn() { printf '    \033[33m!!\033[0m  %s\n' "$1"; }

command -v adb >/dev/null || { echo "adb not found. Install platform-tools (macOS: brew install android-platform-tools, Ubuntu: sudo apt install adb)."; exit 1; }

case "$TV_IP" in *:*) SERIAL="$TV_IP" ;; *) SERIAL="$TV_IP:5555" ;; esac
tv() { adb -s "$SERIAL" "$@"; }
get() { tv shell settings get "$1" "$2" | tr -d '\r\n'; }

step "Connecting to the TV at $SERIAL"
adb connect "$SERIAL"
sleep 2
if [ "$(adb -s "$SERIAL" get-state 2>/dev/null | tr -d '\r\n')" != "device" ]; then
  warn "The TV did not accept the connection yet."
  echo "    On the TV: accept 'Allow USB debugging?' (tick 'Always allow'), then run this again."
  echo "    No pop-up? Turn on Developer options > USB debugging (see README)."
  exit 1
fi
ok "Connected"

if [ "${SKIP_INSTALL:-0}" != "1" ]; then
  [ -f "$APK" ] || { echo "APK not found: $APK"; exit 1; }
  step "Installing $(basename "$APK")"
  if tv install -r "$APK" | grep -q Success; then ok "Installed"; else warn "Install failed"; exit 1; fi
fi

step "Allowing the Now Playing screen to see Spotify / YouTube Music / YouTube"
tv shell cmd notification allow_listener "$LISTENER" >/dev/null 2>&1
CUR="$(get secure enabled_notification_listeners)"
case "$CUR" in
  *"$LISTENER"*) ;;
  ""|null) tv shell settings put secure enabled_notification_listeners "$LISTENER" ;;
  *) tv shell settings put secure enabled_notification_listeners "$CUR:$LISTENER" ;;
esac
case "$(get secure enabled_notification_listeners)" in
  *"$LISTENER"*) ok "Music access allowed" ;;
  *) warn "Could not allow music access on this TV (other screens still work)" ;;
esac

step "Making StandBy TV the screensaver (starts after $IDLE_MIN min idle)"
IDLE_MS=$((IDLE_MIN * 60000))
if [ "$SLEEP_H" -le 0 ]; then SLEEP_MS=0; else SLEEP_MS=$((SLEEP_H * 3600000)); fi
tv shell settings put secure screensaver_enabled 1
tv shell settings put secure screensaver_activate_on_sleep 1
tv shell settings put secure screensaver_components "$DREAM"
tv shell settings put system screen_off_timeout "$IDLE_MS"
# The TV's own "turn off" timer must be LONGER than the screensaver delay, or the TV just sleeps.
tv shell settings put secure sleep_timeout "$SLEEP_MS"

step "Checking"
ALL_OK=1
check() {
  v="$(get "$1" "$2")"
  if [ "$v" = "$3" ]; then ok "$2 = $v"; else warn "$2 = $v (expected $3)"; ALL_OK=0; fi
}
check secure screensaver_enabled 1
check secure screensaver_activate_on_sleep 1
check secure screensaver_components "$DREAM"
check system screen_off_timeout "$IDLE_MS"
check secure sleep_timeout "$SLEEP_MS"
[ "$ALL_OK" = 1 ] || warn "Some settings did not stick - see Troubleshooting in the README."

step "Showing the screensaver now as a test (press any remote button to close it)"
tv shell am start -n com.android.systemui/.Somnambulator >/dev/null 2>&1
printf '\n\033[32mDone. Leave the TV idle on the home screen for %s minutes and StandBy TV starts by itself.\033[0m\n' "$IDLE_MIN"
