#!/usr/bin/env bash
set -euo pipefail

app_apk="$(find app/build/outputs -type f -name '*.apk' ! -path '*/androidTest/*' -print -quit)"
test_apk="$(find app/build/outputs -type f -name '*.apk' -path '*/androidTest/*' -print -quit)"
test -n "$app_apk"
test -n "$test_apk"
printf 'App APK: %s\nTest APK: %s\n' "$app_apk" "$test_apk"

adb install -r "$app_apk"
adb install -r "$test_apk"

instrumentation="$(
    adb shell pm list instrumentation \
        | tr -d '\r' \
        | sed -n 's/^instrumentation:\([^ ]*NetworkSecurityInstrumentation\).*/\1/p' \
        | head -n 1
)"
test -n "$instrumentation"

output="$(adb shell am instrument -w -e mode schema-migration "$instrumentation" 2>&1 || true)"
printf '%s\n' "$output"
if ! grep -q 'INSTRUMENTATION_CODE: -1' <<< "$output"; then
    echo "Schema migration instrumentation failed." >&2
    exit 1
fi
