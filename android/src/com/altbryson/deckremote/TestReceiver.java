package com.altbryson.deckremote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Test hook: "press" a Stream Deck key from adb without touching the deck.
 *   adb shell am broadcast -a com.altbryson.deckremote.PRESS --es key up -n com.altbryson.deckremote/.TestReceiver
 * Guarded by android.permission.DUMP in the manifest, which only the adb shell and the system hold.
 */
public class TestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String id = i.getStringExtra("key");
        DeckService s = DeckService.instance;
        if (s != null && id != null) s.press(id);
    }
}
