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
custom_instrumentation="$(printf '%s\n' "$instrumentation_components" | grep -F 'NetworkSecurityInstrumentation' | head -n 1 || true)"
junit_runner="$(printf '%s\n' "$instrumentation_components" | grep -F 'AndroidJUnitRunner' | head -n 1 || true)"
adb logcat -c

if [[ -n "$custom_instrumentation" ]]; then
    output="$(adb shell am instrument -w -e mode schema-migration "$custom_instrumentation" 2>&1 || true)"
    success_pattern='INSTRUMENTATION_CODE: -1'
elif [[ -n "$junit_runner" ]]; then
    output="$(adb shell am instrument -w -e class com.convx.music.db.RoomSchemaMigrationTest "$junit_runner" 2>&1 || true)"
    success_pattern='OK (1 test)'
else
    echo "No supported Android instrumentation was registered." >&2
    exit 1
fi

printf '%s\n' "$output"
if ! grep -Fq "$success_pattern" <<< "$output"; then
    echo "Recent Android logcat output:"
    adb logcat -d -v time | tail -n 300
    echo "Schema migration instrumentation failed." >&2
    exit 1
fi
