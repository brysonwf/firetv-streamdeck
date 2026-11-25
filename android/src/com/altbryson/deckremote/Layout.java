package com.altbryson.deckremote;

/**
 * What each of the 15 keys does on the main (remote) page, left to right, top to bottom (5 x 3). Key images are
 * assets/keys/<id>.jpg (tools/make-keys.js). The Apps key opens the apps page ({@link AppLineup}): the apps in the
 * order of the Fire TV home screen's "Your apps and channels" row, drawn with each app's own artwork.
 *
 * Fire OS refuses adb connections that start on the TV itself, so nothing here injects raw key codes:
 *  NAV    arrows / OK / Back / Home / Sleep through the app's accessibility service (NavService)
 *  MEDIA  media keys sent to the playing app's media session (AudioManager.dispatchMediaKeyEvent)
 *  VOLUME Android's volume (AudioManager)
 *  FOLDER switch the deck to the apps page
 */
final class Layout {
    enum Kind { NAV, MEDIA, VOLUME, FOLDER }

    static final class Action {
        final String id; final Kind kind; final int code; final boolean repeat;
        Action(String id, Kind kind, int code, boolean repeat) { this.id = id; this.kind = kind; this.code = code; this.repeat = repeat; }
    }
    // NAV codes
    static final int BACK = 1, HOME = 2, UP = 3, DOWN = 4, LEFT = 5, RIGHT = 6, SELECT = 7, SLEEP = 8;
    // VOLUME codes
    static final int VOL_UP = 1, VOL_DOWN = 2, MUTE = 3;

    private static Action nav(String id, int c, boolean repeat) { return new Action(id, Kind.NAV, c, repeat); }
    private static Action media(String id, int keycode, boolean repeat) { return new Action(id, Kind.MEDIA, keycode, repeat); }
    private static Action vol(String id, int c, boolean repeat) { return new Action(id, Kind.VOLUME, c, repeat); }
    private static Action folder(String id) { return new Action(id, Kind.FOLDER, 0, false); }

    static final Action[] KEYS = {
            nav("back", BACK, false), nav("up", UP, true), nav("home", HOME, false), vol("volup", VOL_UP, true), folder("apps"),
            nav("left", LEFT, true), nav("select", SELECT, false), nav("right", RIGHT, true), vol("voldown", VOL_DOWN, true), media("rewind", 89, true),
            media("playpause", 85, false), nav("down", DOWN, true), media("ff", 90, true), vol("mute", MUTE, false), nav("sleep", SLEEP, false),
    };

    /** Apps the folder always offers, even when they are not in the home screen's row (Paramount+, Peacock, Prime Video; edit to taste). */
    static final String[] MUST_HAVE = {"com.cbs.ott", "com.peacock.peacockfiretv", "com.amazon.firebat"};
}
