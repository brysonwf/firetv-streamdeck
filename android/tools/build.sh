#!/bin/bash
# Builds the Fire TV app without Gradle: aapt2 (resources) -> javac -> d8 (dex) -> zipalign -> apksigner.
# Run as root in WSL after wsl-setup-sdk.sh:  bash build.sh   -> android/build/deckremote.apk
# The signing key lives in WSL (/root/.android/deckremote.jks), outside the repo; keep it for updates.
set -euo pipefail
SDK=/root/android-sdk
BT=$SDK/build-tools/34.0.0
JAR=$SDK/platforms/android-28/android.jar
HERE=$(cd "$(dirname "$0")/.." && pwd)
OUT=$HERE/build
rm -rf "$OUT" && mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes"
"$BT/aapt2" compile --dir "$HERE/res" -o "$OUT/res/res.zip"
"$BT/aapt2" link -o "$OUT/unsigned.apk" -I "$JAR" --manifest "$HERE/AndroidManifest.xml" -A "$HERE/assets" \
  --java "$OUT/gen" --min-sdk-version 25 --target-sdk-version 28 "$OUT/res/res.zip"
javac -Xlint:-options --release 8 -classpath "$JAR" -d "$OUT/classes" $(find "$HERE/src" "$OUT/gen" -name '*.java')
"$BT/d8" --min-api 25 --lib "$JAR" --output "$OUT" $(find "$OUT/classes" -name '*.class')
(cd "$OUT" && zip -q -j unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
KS=/root/.android/deckremote.jks
mkdir -p /root/.android
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass deckremote -keypass deckremote -alias deckremote \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Deck Remote" >/dev/null 2>&1
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:deckremote --key-pass pass:deckremote --out "$OUT/deckremote.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/deckremote.apk" && ls -la "$OUT/deckremote.apk"
