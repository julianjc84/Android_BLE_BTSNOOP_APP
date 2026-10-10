#!/usr/bin/env bash
# Pull new btsnoop capture files from an Android phone over ADB.
#
# Phone layout (as written by the BLE Snoop app):
#   $BTSNOOP_PHONE_DIR/               <- new .log files land here
#   $BTSNOOP_PHONE_DIR/processed/     <- archived after a successful pull
#   $BTSNOOP_PHONE_DIR/CSV/           <- new .csv exports land here
#   $BTSNOOP_PHONE_DIR/CSV/processed/
#
# Each new file is pulled into $BTSNOOP_LOCAL_DIR, size-verified, then moved
# to processed/ on the phone. Files already present locally with the same
# name and size are not re-pulled but are still archived on the phone.
#
# Environment:
#   BTSNOOP_LOCAL_DIR   local destination (default: <repo root>/BTSnoopLogs)
#   BTSNOOP_PHONE_DIR   source folder on the phone (default: /sdcard/Download/BTSnoopLog)
#   ANDROID_SERIAL      standard adb device selector when several are attached
set -euo pipefail

PHONE_DIR="${BTSNOOP_PHONE_DIR:-/sdcard/Download/BTSnoopLog}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_DIR="${BTSNOOP_LOCAL_DIR:-$SCRIPT_DIR/../../../BTSnoopLogs}"
mkdir -p "$LOCAL_DIR"
LOCAL_DIR="$(cd "$LOCAL_DIR" && pwd)"

if ! command -v adb >/dev/null 2>&1; then
    echo "ERROR: adb not found on PATH. Install Android platform-tools." >&2
    exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
    echo "ERROR: no ADB device connected, or more than one without ANDROID_SERIAL set." >&2
    echo "       Run 'adb devices -l' to see what is attached." >&2
    exit 1
fi

if ! adb shell "test -d '$PHONE_DIR'" 2>/dev/null; then
    echo "ERROR: $PHONE_DIR does not exist on the phone." >&2
    echo "       Has the app saved a capture yet? Override with BTSNOOP_PHONE_DIR if needed." >&2
    exit 1
fi

sync_dir() {
    local remote_dir="$1" pattern="$2"
    local found=0

    adb shell "test -d '$remote_dir'" 2>/dev/null || { echo "  (folder not present on phone)"; return 0; }
    adb shell "mkdir -p '$remote_dir/processed'"

    while IFS= read -r remote; do
        [ -z "$remote" ] && continue
        found=1
        local name local_path rsize
        name="$(basename "$remote")"
        local_path="$LOCAL_DIR/$name"
        rsize="$(adb shell "stat -c %s '$remote'" | tr -d '\r')"

        if [ -f "$local_path" ] && [ "$(stat -c %s "$local_path")" = "$rsize" ]; then
            echo "SKIP (already local): $name"
        else
            adb pull "$remote" "$local_path" >/dev/null
            local lsize
            lsize="$(stat -c %s "$local_path")"
            if [ "$lsize" != "$rsize" ]; then
                echo "ERROR: size mismatch for $name (remote $rsize, local $lsize) - leaving on phone" >&2
                continue
            fi
            echo "PULLED: $name ($rsize bytes)"
        fi

        adb shell "mv '$remote' '$remote_dir/processed/'"
        echo "  archived to $remote_dir/processed/"
    done < <(adb shell "find '$remote_dir' -maxdepth 1 -type f -name '$pattern'" | tr -d '\r')

    [ "$found" = 0 ] && echo "  (no new files)"
    return 0
}

echo "== btsnoop .log files =="
sync_dir "$PHONE_DIR" '*.log'

echo "== CSV exports =="
sync_dir "$PHONE_DIR/CSV" '*.csv'

echo ""
echo "Done. Local dir: $LOCAL_DIR"
