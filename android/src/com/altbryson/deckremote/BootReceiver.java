package com.altbryson.deckremote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts the Stream Deck service when the Fire TV boots. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) { DeckService.start(c); }
}
