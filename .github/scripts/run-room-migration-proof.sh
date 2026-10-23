#!/usr/bin/env bash
set -euo pipefail

app_apk="$(find app/build/outputs -type f -name '*.apk' ! -path '*/androidTest/*' -print -quit)"
test_apk="$(find app/build/outputs -type f -name '*.apk' -path '*/androidTest/*' -print -quit)"
test -n "$app_apk"
test -n "$test_apk"
printf 'App APK: %s\nTest APK: %s\n' "$app_apk" "$test_apk"

adb install -r "$app_apk"
adb install -r "$test_apk"

instrumentation_list="$(adb shell pm list instrumentation 2>&1 | tr -d '\r')"
printf 'Registered instrumentations:\n%s\n' "$instrumentation_list"
instrumentation_components="$(printf '%s\n' "$instrumentation_list" | sed -n 's/^instrumentation:\([^[:space:]]*\).*/\1/p')"
junit_runner="$(printf '%s\n' "$instrumentation_components" | grep -F 'RoomMigrationTestRunner' | head -n 1 || true)"
adb logcat -c

if [[ -z "$junit_runner" ]]; then
    echo "RoomMigrationTestRunner was not registered; refusing to run the proof with a different application startup path." >&2
    exit 1
fi

test_classes='com.convx.music.db.RoomSchemaMigrationTest,com.convx.music.db.RoomSchemaMigrationMatrixTest,com.convx.music.db.BackupArchiveStagedSettingsValidatorTest'
output="$(adb shell am instrument -w -e class "$test_classes" "$junit_runner" 2>&1 || true)"

printf '%s\n' "$output"
if ! grep -Eq 'OK \([0-9]+ tests?\)' <<< "$output" || ! grep -Fq 'INSTRUMENTATION_CODE: -1' <<< "$output"; then
    echo "Recent Android logcat output:"
    adb logcat -d -v time | tail -n 300
    echo "Room migration or staged-backup instrumentation failed." >&2
    exit 1
fi
