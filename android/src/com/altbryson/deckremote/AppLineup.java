package com.altbryson.deckremote;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * The apps page: the app order of the Fire TV home screen's "Your apps and channels" row (read by NavService when
 * the home screen is showing, saved here), plus Layout.MUST_HAVE, each key drawn from the app's own banner/icon.
 */
final class AppLineup {
    static final int SLOTS = 14; // key 0 on the apps page is "back to the remote"
    private static final String PREFS = "lineup", KEY = "packages";

    /** Tile names as shown on the home screen -> installed package names (in order, no duplicates). */
    static List<String> resolve(Context c, List<String> names) {
        PackageManager pm = c.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<String> labels = new ArrayList<>();
        for (ApplicationInfo a : apps) labels.add(norm(String.valueOf(pm.getApplicationLabel(a))));
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String name : names) {
            String n = norm(name);
            String hit = null;
            for (int i = 0; i < apps.size() && hit == null; i++) if (labels.get(i).equals(n) && launchable(pm, apps.get(i).packageName)) hit = apps.get(i).packageName;
            for (int i = 0; i < apps.size() && hit == null; i++) {
                String l = labels.get(i);
                if (l.length() >= 4 && n.length() >= 4 && (l.startsWith(n) || n.startsWith(l)) && launchable(pm, apps.get(i).packageName)) hit = apps.get(i).packageName;
            }
            if (hit != null) out.add(hit); else Log.i(DeckService.TAG, "lineup: no app for \"" + name + "\"");
        }
        return new ArrayList<>(out);
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.US).replace(" for fire tv", "").replace(" - web browser", "").replaceAll("[^a-z0-9+]", "");
    }

    static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    private static boolean launchable(PackageManager pm, String pkg) { return launchIntent(pm, pkg) != null; }

    static void save(Context c, List<String> pkgs) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, String.join(",", pkgs)).apply();
    }

    /** Saved home-screen order + the must-have apps, installed ones only, at most SLOTS. */
    static List<String> load(Context c) {
        PackageManager pm = c.getPackageManager();
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        LinkedHashSet<String> all = new LinkedHashSet<>();
        for (String s : p.getString(KEY, "").split(",")) if (!s.isEmpty()) all.add(s);
        for (String s : Layout.MUST_HAVE) all.add(s);
        List<String> out = new ArrayList<>();
        for (String s : all) if (launchable(pm, s)) out.add(s);
        // keep the must-haves even when the home row is long: they replace the last slots
        while (out.size() > SLOTS) {
            int drop = -1;
            for (int i = out.size() - 1; i >= 0 && drop < 0; i--) if (!java.util.Arrays.asList(Layout.MUST_HAVE).contains(out.get(i))) drop = i;
            out.remove(drop < 0 ? out.size() - 1 : drop);
        }
        return out;
    }

    /** A 72x72 key image (rotated 180 degrees for the deck) from the app's banner, else its icon + name. */
    static byte[] keyImage(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Bitmap b = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.drawColor(Color.BLACK);
        cv.rotate(180, 36, 36);
        Drawable art = null; boolean banner = true;
        try { art = pm.getApplicationBanner(pkg); } catch (Exception ignored) { }
        if (art == null) { Intent i = launchIntent(pm, pkg); try { if (i != null) art = pm.getActivityBanner(i); } catch (Exception ignored) { } }
        if (art == null) { banner = false; try { art = pm.getApplicationIcon(pkg); } catch (Exception ignored) { } }
        String label = pkg;
        try { label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))); } catch (Exception ignored) { }
        if (art != null && banner) {
            int w = art.getIntrinsicWidth() > 0 ? art.getIntrinsicWidth() : 320, h = art.getIntrinsicHeight() > 0 ? art.getIntrinsicHeight() : 180;
            int dh = Math.round(72f * h / w);
            art.setBounds(0, (72 - dh) / 2, 72, (72 - dh) / 2 + dh);
            art.draw(cv);
        } else {
            if (art != null) { art.setBounds(14, 6, 58, 50); art.draw(cv); }
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true);
            p.setTextSize(11);
            String t = label.length() > 12 ? label.substring(0, 11) + "…" : label;
            cv.drawText(t, 36, art != null ? 64 : 40, p);
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 92, o);
        return o.toByteArray();
    }
}
