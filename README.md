# Stream Deck Remote for Fire TV

Turn an Elgato Stream Deck into a Fire TV remote. Plug the Stream Deck straight into a Fire TV Cube's USB port and
this app (running on the Fire TV itself) draws the key images and drives the TV. No PC, no root.

Built and tested with a Stream Deck Original V2 (15 keys, USB `0fd9:006d`) on a Fire TV Cube (3rd gen, Fire OS 7).

## `android/` - the Fire TV app

| | | | | |
|---|---|---|---|---|
| Back | Up | Home | Volume + | Apps (folder) |
| Left | OK | Right | Volume - | Rewind |
| Play/Pause | Down | Fast forward | Mute | Sleep |

- **Apps folder:** your apps in the order of the home screen's "Your apps and channels" row (read live by the
  accessibility service) plus a few always-on apps (`Layout.MUST_HAVE`). Key images come from each app's banner;
  top-left goes back.
- Arrows, rewind/fast forward and volume repeat while held. The deck dims when the TV sleeps.
- Starts with the TV (boot receiver) and when the deck is plugged in; a status screen is in the Fire TV apps list.

### How it presses buttons (no root, no key injection)
- Arrows / OK / Back / Home: the app's accessibility service `NavService` moves focus and clicks.
- On the Fire TV home screen (Jetpack Compose), the launcher pulls input focus back to its banner, so the arrows move
  **accessibility focus** instead and the app draws its own highlight box; OK clicks that item. This needs touch
  exploration, which some apps read as "screen reader on".
- Play/pause/FF: media key events to the playing app. Volume: Android's volume. Apps: their launch intents.
- **Why not ADB?** Fire OS drops ADB connections that start on the TV itself (127.0.0.1 or its own LAN IP), so the
  "local ADB" trick older remapper apps used doesn't work. This app doesn't try to get around that.

### Known limits
- **Volume / Mute** only work if your TV honors HDMI-CEC from the Cube. Many don't; the Cube's IR volume control only
  follows real remote presses.

### Build (no Android Studio / Gradle)
1. `android/tools/wsl-setup-sdk.sh` once (WSL or Linux: OpenJDK 17, cmdline-tools, android-28, build-tools 34).
2. `node android/tools/make-keys.js <path to puppeteer>` draws the key images.
3. `android/tools/build.sh` -> `android/build/deckremote.apk` (makes a local signing key on first run; keep it to
   install updates over the same app).
4. Install: `adb -s <fire-tv-ip>:5555 install -r android/build/deckremote.apk`.

### Set up on the TV
1. Plug in the Stream Deck and accept the USB permission prompt.
2. Turn on the navigation service: Settings > Accessibility, or
   `adb shell settings put secure enabled_accessibility_services com.altbryson.deckremote/com.altbryson.deckremote.NavService`
   then `adb shell settings put secure accessibility_enabled 1`.
3. Change the layout in `android/src/com/altbryson/deckremote/Layout.java` (then rerun `make-keys.js`).

Testing from a PC: `android/tools/nav-test.sh <adb> <serial> up up right select` presses keys through the debug
`TestReceiver` and dumps the screen.

## `com.altbryson.firetv.sdPlugin/` - Stream Deck plugin for a PC (alternative)
For a Stream Deck plugged into a PC instead: "Remote key" and "Open app" actions that send ADB commands to a Fire TV
over the network (ADB debugging on). Set your TVs in `bin/catalog.js` (`DEVICES`) or pick "Other Fire TV" and type
its IP. Install: `npm install` inside the folder, then copy it to `%APPDATA%\Elgato\StreamDeck\Plugins`.
`tools/test-harness.js <ip>` exercises it without the Stream Deck app.

## License
MIT. Not affiliated with Elgato or Amazon. Made by [altbryson.com](https://altbryson.com).
