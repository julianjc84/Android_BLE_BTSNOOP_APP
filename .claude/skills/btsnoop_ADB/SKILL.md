---
name: btsnoop_ADB
description: Pull new btsnoop HCI logs and CSV exports saved by the BLE Snoop app from an Android phone over ADB into a local folder, archiving them to processed/ on the phone. Use when the user asks to check, fetch, pull, or sync btsnoop logs or CSV captures from the phone.
---

# btsnoop_ADB — sync capture files from the phone

The BLE Snoop app saves capture files to `Download/BTSnoopLog/` on the phone's
shared storage. Raw `.log` btsnoop files sit at the top level and `.csv`
exports under `CSV/`. Files that have already been pulled are moved into a
`processed/` subfolder next to where they landed, so the top level only ever
holds new captures.

Works with any Android device reachable by `adb` (USB or wireless debugging).
Root is not needed; the folder is ordinary media storage.

## Steps

1. Run the sync script. It checks that exactly one ADB device is reachable,
   then pulls new files:

   ```bash
   bash .claude/skills/btsnoop_ADB/sync_logs.sh
   ```

   For each new file it pulls the file into the local folder, verifies the
   byte size matches the phone copy, then moves the phone copy into
   `processed/`. Files already present locally with the same size are not
   re-pulled but are still archived on the phone. On a size mismatch it
   leaves the file on the phone and reports an error.

2. Report which files were pulled (name and size), which were skipped, and
   any errors. If nothing new was found, say so.

3. If a new `.log` file was pulled and the user is interested in the
   contents, offer a short summary: record count, connected device
   addresses, ATT traffic.

## Configuration

All settings are environment variables with sensible defaults:

| Variable            | Default                          | Meaning                                  |
|---------------------|----------------------------------|------------------------------------------|
| `BTSNOOP_LOCAL_DIR` | `<repo root>/BTSnoopLogs`        | Where pulled files land (gitignored)     |
| `BTSNOOP_PHONE_DIR` | `/sdcard/Download/BTSnoopLog`    | Where the app saves captures on the phone|
| `ANDROID_SERIAL`    | unset                            | Pick a device when more than one is attached (standard adb variable) |

## Troubleshooting

- **No device**: run `adb devices -l`. For wireless debugging, make sure the
  phone and PC are on the same network and the pairing is still valid.
- **More than one device**: set `ANDROID_SERIAL` to the serial shown by
  `adb devices`.
- **Permission denied on phone paths**: should not happen for `Download/`.
  If it does, the app may have saved somewhere else. Check the Settings
  screen in the app for the current save path and set `BTSNOOP_PHONE_DIR`.
