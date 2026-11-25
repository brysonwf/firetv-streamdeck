#!/bin/bash
# Presses Stream Deck keys through the app's test hook and prints where the highlight is after each press
# (from a uiautomator dump). Usage: nav-test.sh <adb> <serial> key1 key2 ...
ADB=$1; SER=$2; shift 2
where() {
  MSYS_NO_PATHCONV=1 "$ADB" -s "$SER" shell 'uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; cat /sdcard/ui.xml' \
    | grep -o '<node [^>]*focused="true"[^>]*>' | grep -v 'bounds="\[0,0\]\[1920,1080\]"' | tail -1 \
    | sed -E 's/.*content-desc="([^"]{0,40})[^"]*".*bounds="([^"]*)".*/\2 \1/'
}
echo "start: $(where)"
for k in "$@"; do
  MSYS_NO_PATHCONV=1 "$ADB" -s "$SER" shell am broadcast -a com.altbryson.deckremote.PRESS --es key "$k" -n com.altbryson.deckremote/.TestReceiver >/dev/null
  sleep 1.2
  echo "$k -> $(where)"
done
