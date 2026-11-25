package com.altbryson.deckremote;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Status screen (from the Fire TV apps list, or opened when the Stream Deck is plugged in). Starts the service. */
public class MainActivity extends Activity {
    private TextView state;
    private final Handler h = new Handler();
    private final Runnable tick = new Runnable() { @Override public void run() { state.setText(DeckService.status); h.postDelayed(this, 500); } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        DeckService.start(this);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(Color.rgb(20, 22, 27));
        l.setPadding(80, 60, 80, 60);
        TextView title = new TextView(this);
        title.setText("Stream Deck Remote");
        title.setTextSize(34); title.setTextColor(Color.WHITE);
        state = new TextView(this);
        state.setTextSize(22); state.setTextColor(Color.rgb(255, 153, 0)); state.setPadding(0, 24, 0, 24);
        TextView help = new TextView(this);
        help.setTextSize(16); help.setTextColor(Color.rgb(170, 176, 188));
        help.setText("Keys:  Back  Up  Home  Vol+  Paramount+\n        Left  OK  Right  Vol-  Peacock\n        Play/Pause  Down  Fast forward  Mute  Prime Video\n\n"
                + "Arrows, OK and Back use the app's navigation service (Settings > Accessibility).\n"
                + "Runs in the background and starts with the TV. You can press Back to leave this screen.");
        l.addView(title); l.addView(state); l.addView(help);
        setContentView(l);
    }

    @Override protected void onResume() { super.onResume(); h.post(tick); }
    @Override protected void onPause() { h.removeCallbacks(tick); super.onPause(); }
}
