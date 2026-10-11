#!/usr/bin/env bash
# Builds SubcalScriber for the LightOS emulator, installs it on the Light_Phone emulator, and opens it.
# Targets the emulator by serial so a Light Phone plugged in over USB is never touched.
# --testdata replaces the tool's feeds with the made-up ones in tool/testdata.
set -e
cd "$(dirname "$0")/../.."

SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
ADB="$SDK/platform-tools/adb"
SERIAL=emulator-5554
TOOL=com.jmsbrain.subcalscriber
TOML=tool/lighttool.toml

if ! "$ADB" devices | grep -q "^$SERIAL"; then
    echo "Light_Phone emulator isn't running. Start it with tool/scripts/emulator-start.sh"
    exit 1
fi

if [ -z "$JAVA_HOME" ] && [ -d "/c/Program Files/Android/Android Studio/jbr" ]; then
    export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
fi
export ANDROID_HOME="$SDK"

# Point at the LightOS emulator for this build only; lighttool.toml always goes back to com.lightos.
BACKUP="$(mktemp)"
cp "$TOML" "$BACKUP"
trap 'cp "$BACKUP" "$TOML"; rm -f "$BACKUP"' EXIT
sed -i 's/^serverPackage = "com.lightos"$/serverPackage = "com.thelightphone.sdk.emulator"/' "$TOML"

ANDROID_SERIAL=$SERIAL ./gradlew :tool:installDebug -DlightSdk.allowAltServerPackage=true

if [ "$1" = "--testdata" ]; then
    "$ADB" -s "$SERIAL" shell am force-stop "$TOOL"
    # updatedAt = now, so the app doesn't try the fake addresses for an hour.
    NOW="$(date +%s)000"
    sed "s/\"updatedAt\":0/\"updatedAt\":$NOW/g" tool/testdata/feeds.json |
        MSYS_NO_PATHCONV=1 "$ADB" -s "$SERIAL" shell "run-as $TOOL sh -c 'mkdir -p files/feeds && cat > files/feeds.json'"
    for f in tool/testdata/*.ics; do
        MSYS_NO_PATHCONV=1 "$ADB" -s "$SERIAL" shell "run-as $TOOL sh -c 'cat > files/feeds/$(basename "$f")'" < "$f"
    done
    echo "Test feeds copied into SubcalScriber."
fi

"$ADB" -s "$SERIAL" shell monkey -p "$TOOL" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
echo "SubcalScriber installed and opened on the Light_Phone emulator."
