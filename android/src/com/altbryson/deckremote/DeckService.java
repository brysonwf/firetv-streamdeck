package com.altbryson.deckremote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs the Stream Deck: finds it on USB, draws the keys, reads presses and turns them into remote actions
 * ({@link Layout}). Two pages: the remote, and the apps folder ({@link AppLineup}). Stays in the foreground;
 * picks the deck up again when it is replugged.
 */
public class DeckService extends Service {
    static final String TAG = "DeckRemote";
    static final String ACTION_PERMISSION = "com.altbryson.deckremote.USB_PERMISSION";
    static volatile String status = "starting";
    static volatile DeckService instance;
    private static final int REPEAT_DELAY = 420, REPEAT_EVERY = 160;

    private UsbManager usb;
    private final ExecutorService commands = Executors.newSingleThreadExecutor();
    private HandlerThread thread;
    private Handler handler;
    private volatile StreamDeck deck;
    private volatile boolean running = true;
    private byte[][] remoteImages;
    private byte[] appsBackImage;
    // page 0 = remote, 1 = apps folder
    private volatile int page = 0;
    private volatile List<String> apps;
    private volatile boolean redraw = true;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) { StreamDeck d = deck; if (d != null) d.setBrightness(5); }
            else if (Intent.ACTION_SCREEN_ON.equals(a)) { StreamDeck d = deck; if (d != null) d.setBrightness(70); }
            else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(a)) {
                UsbDevice dev = i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (dev != null && StreamDeck.isDeck(dev)) setStatus("Stream Deck unplugged");
            } else handler.post(DeckService.this::findDeck); // attached, or permission answered
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        startForeground(1, notification());
        instance = this;
        usb = (UsbManager) getSystemService(USB_SERVICE);
        remoteImages = loadImages();
        apps = AppLineup.load(this);
        thread = new HandlerThread("deck");
        thread.start();
        handler = new Handler(thread.getLooper());
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF); f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        f.addAction(ACTION_PERMISSION);
        registerReceiver(receiver, f);
        handler.post(this::findDeck);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (handler != null) handler.post(this::findDeck);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        instance = null;
        unregisterReceiver(receiver);
        StreamDeck d = deck; if (d != null) d.close();
        thread.quitSafely();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    static void setStatus(String s) { status = s; Log.i(TAG, s); }

    /** NavService read a new app order from the home screen. */
    void lineupChanged() { apps = AppLineup.load(this); if (page == 1) redraw = true; }

    /** For TestReceiver: act as if the key at this position (0-14) or with this id was pressed. */
    void press(String id) {
        try { onKey(Integer.parseInt(id)); return; } catch (NumberFormatException ignored) { }
        for (int k = 0; k < Layout.KEYS.length; k++) if (Layout.KEYS[k].id.equals(id)) { page = 0; onKey(k); }
    }

    private void findDeck() {
        if (deck != null) return;
        for (UsbDevice d : usb.getDeviceList().values()) {
            if (!StreamDeck.isDeck(d)) continue;
            if (!usb.hasPermission(d)) {
                setStatus("asking for USB permission: press OK on the TV");
                usb.requestPermission(d, PendingIntent.getBroadcast(this, 0, new Intent(ACTION_PERMISSION), 0));
                return;
            }
            try {
                deck = new StreamDeck(usb, d);
                redraw = true;
                new Thread(this::loop, "deck-loop").start();
            } catch (Exception e) { setStatus("Stream Deck: " + e.getMessage()); }
            return;
        }
        setStatus("plug in the Stream Deck");
    }

    private void draw(StreamDeck d) {
        int failed = 0;
        for (int k = 0; k < StreamDeck.KEYS; k++) {
            byte[] img;
            if (page == 0) img = remoteImages[k];
            else if (k == 0) img = appsBackImage;
            else img = k - 1 < apps.size() ? AppLineup.keyImage(this, apps.get(k - 1)) : StreamDeck.BLACK;
            try { d.setImage(k, img); } catch (Exception e) { failed++; }
        }
        if (failed > 0) Log.w(TAG, failed + " key images failed to draw; keys still work");
    }

    private void loop() {
        StreamDeck d = deck;
        try {
            d.setBrightness(70);
            setStatus("Stream Deck ready" + (NavService.instance != null ? "" : " (turn on navigation for arrows / OK / Back)"));
            boolean[] prev = new boolean[StreamDeck.KEYS];
            long[] downAt = new long[StreamDeck.KEYS], lastFire = new long[StreamDeck.KEYS];
            while (running) {
                if (redraw) { redraw = false; draw(d); }
                boolean[] s = d.readKeys(REPEAT_EVERY / 2);
                long now = System.currentTimeMillis();
                if (s == null) {
                    if (!stillAttached()) break;
                    s = prev; // no change: keep the held keys repeating
                }
                for (int k = 0; k < StreamDeck.KEYS; k++) {
                    if (s[k] && !prev[k]) { downAt[k] = now; lastFire[k] = now; onKey(k); }
                    else if (s[k] && page == 0 && Layout.KEYS[k].repeat && now - downAt[k] >= REPEAT_DELAY && now - lastFire[k] >= REPEAT_EVERY) {
                        lastFire[k] = now; fire(Layout.KEYS[k]);
                    }
                }
                prev = s;
            }
        } catch (Exception e) {
            setStatus("Stream Deck stopped: " + e.getMessage());
        }
        d.close();
        deck = null;
        if (running) handler.postDelayed(this::findDeck, 1500);
    }

    private void onKey(int k) {
        if (page == 1) {
            if (k == 0) { page = 0; redraw = true; return; }
            if (k - 1 < apps.size()) { launch(apps.get(k - 1)); page = 0; redraw = true; }
            return;
        }
        Layout.Action a = Layout.KEYS[k];
        if (a.kind == Layout.Kind.FOLDER) {
            NavService nav = NavService.instance;
            if (nav != null) nav.readLineupNow(); // fresh order if the home screen is up
            apps = AppLineup.load(this);
            page = 1; redraw = true;
            return;
        }
        fire(a);
    }

    private boolean stillAttached() {
        for (UsbDevice x : usb.getDeviceList().values()) if (StreamDeck.isDeck(x)) return true;
        setStatus("Stream Deck unplugged");
        return false;
    }

    private void launch(String pkg) {
        commands.execute(() -> {
            Intent i = AppLineup.launchIntent(getPackageManager(), pkg);
            if (i == null) { setStatus(pkg + " is not installed"); return; }
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        });
    }

    private void fire(Layout.Action a) {
        commands.execute(() -> {
            try { if (!perform(a)) Log.i(TAG, a.id + ": nothing to do"); }
            catch (Exception e) { setStatus(a.id + ": " + e.getMessage()); }
        });
    }

    private boolean perform(Layout.Action a) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        switch (a.kind) {
            case NAV: {
                NavService nav = NavService.instance;
                if (nav != null) return nav.nav(a.code);
                if (a.code == Layout.HOME) { startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true; }
                setStatus("arrows / OK / Back need the navigation (accessibility) service turned on");
                return false;
            }
            case MEDIA: {
                long t = SystemClock.uptimeMillis();
                am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, a.code, 0));
                am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, a.code, 0));
                return true;
            }
            case VOLUME: {
                // as volume key events through the media session service (the path a remote's volume keys take
                // after the window manager), so Fire TV's equipment control can forward them to the TV
                int code = a.code == Layout.VOL_UP ? KeyEvent.KEYCODE_VOLUME_UP : a.code == Layout.VOL_DOWN ? KeyEvent.KEYCODE_VOLUME_DOWN : KeyEvent.KEYCODE_VOLUME_MUTE;
                long t = SystemClock.uptimeMillis();
                am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
                am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
                return true;
            }
            default: return false;
        }
    }

    private byte[] readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        } catch (Exception e) { return StreamDeck.BLACK; }
    }

    private byte[][] loadImages() {
        Bitmap black = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888);
        black.eraseColor(Color.BLACK);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        black.compress(Bitmap.CompressFormat.JPEG, 90, bo);
        StreamDeck.BLACK = bo.toByteArray();
        appsBackImage = readAsset("keys/remote.jpg");
        byte[][] out = new byte[StreamDeck.KEYS][];
        for (int k = 0; k < StreamDeck.KEYS; k++) out[k] = readAsset("keys/" + Layout.KEYS[k].id + ".jpg");
        return out;
    }

    private Notification notification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("deck", "Stream Deck remote", NotificationManager.IMPORTANCE_MIN));
        return new Notification.Builder(this, "deck").setContentTitle("Stream Deck remote").setContentText("Running")
                .setSmallIcon(android.R.drawable.ic_media_play).build();
    }

    static void start(Context c) { c.startForegroundService(new Intent(c, DeckService.class)); }
}
