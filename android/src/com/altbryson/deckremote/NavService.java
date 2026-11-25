package com.altbryson.deckremote;

import android.accessibilityservice.AccessibilityService;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Accessibility service that moves the focus and clicks for the Stream Deck's arrows / OK, and does Back / Home.
 * Android lets an enabled accessibility service do this in other apps (no key injection needed).
 * Enable: Settings, or  adb shell settings put secure enabled_accessibility_services com.altbryson.deckremote/.NavService
 */
public class NavService extends AccessibilityService {
    static volatile NavService instance;

    @Override protected void onServiceConnected() { instance = this; DeckService.setStatus("navigation on"); }
    @Override public boolean onUnbind(android.content.Intent i) { instance = null; return super.onUnbind(i); }
    @Override public void onInterrupt() { }

    /** Returns false when the action could not be done (e.g. nothing focusable in that direction). */
    boolean nav(int code) {
        switch (code) {
            case Layout.BACK: return performGlobalAction(GLOBAL_ACTION_BACK);
            case Layout.HOME: return performGlobalAction(GLOBAL_ACTION_HOME);
            case Layout.SLEEP: return performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); // on Fire TV: sleep
            case Layout.SELECT: return click();
            case Layout.UP: return move(View.FOCUS_UP);
            case Layout.DOWN: return move(View.FOCUS_DOWN);
            case Layout.LEFT: return move(View.FOCUS_LEFT);
            case Layout.RIGHT: return move(View.FOCUS_RIGHT);
        }
        return false;
    }

    private AccessibilityNodeInfo focused() {
        AccessibilityNodeInfo f = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f != null) return f;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        return root == null ? null : firstFocusable(root, 0);
    }

    private AccessibilityNodeInfo firstFocusable(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 40) return null;
        if (n.isFocusable() && n.isVisibleToUser()) return n;
        for (int i = 0; i < n.getChildCount(); i++) { AccessibilityNodeInfo r = firstFocusable(n.getChild(i), depth + 1); if (r != null) return r; }
        return null;
    }

    /** The node we moved to last. Fire TV's home screen reports the whole screen as "focused", so the app keeps
     *  its own idea of where the highlight is and measures each arrow from there. Forgotten on a screen change. */
    private AccessibilityNodeInfo last;

    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            // the app says which item just got the highlight: the most reliable "where am I"
            AccessibilityNodeInfo s = e.getSource();
            if (s != null && !coversScreen(s)) { last = s; lastAt = android.os.SystemClock.uptimeMillis(); }
            return;
        }
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String pkg = String.valueOf(e.getPackageName());
        if (getPackageName().equals(pkg)) return; // our own highlight box appearing, not a screen change
        last = null;
        // the launcher sends window-state events while you move around it: keep the box there, drop it elsewhere
        if (!LAUNCHER.equals(pkg)) { a11yAt = null; hideBox(); }
        if ("com.amazon.tv.launcher".contentEquals(String.valueOf(e.getPackageName()))) {
            main.removeCallbacks(readLineup);
            main.postDelayed(readLineup, 2500); // the home screen fills its rows after it appears
        }
    }

    private final Runnable readLineup = this::readLineupNow;

    /** Copies the app order of the home screen's "Your apps and channels" row (when it is on screen). */
    void readLineupNow() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !"com.amazon.tv.launcher".contentEquals(String.valueOf(root.getPackageName()))) return;
        java.util.List<AccessibilityNodeInfo> all = new java.util.ArrayList<>();
        collectAll(root, all, 0);
        android.graphics.Rect row = null, r = new android.graphics.Rect();
        for (AccessibilityNodeInfo n : all) {
            CharSequence d = n.getContentDescription();
            if (d != null && d.toString().toLowerCase(java.util.Locale.US).startsWith("your apps")) { row = new android.graphics.Rect(); n.getBoundsInScreen(row); break; }
        }
        if (row == null) return;
        java.util.List<AccessibilityNodeInfo> tiles = new java.util.ArrayList<>();
        for (AccessibilityNodeInfo n : all) {
            if (!"TileTestTag".equals(n.getViewIdResourceName()) || n.getContentDescription() == null) continue;
            n.getBoundsInScreen(r);
            if (r.centerY() >= row.top - 20 && r.centerY() <= row.bottom + 20) tiles.add(n);
        }
        tiles.sort((x, y) -> { android.graphics.Rect p = new android.graphics.Rect(), q = new android.graphics.Rect(); x.getBoundsInScreen(p); y.getBoundsInScreen(q); return p.left - q.left; });
        java.util.List<String> names = new java.util.ArrayList<>();
        for (AccessibilityNodeInfo t : tiles) names.add(t.getContentDescription().toString().split("\n")[0]);
        java.util.List<String> pkgs = AppLineup.resolve(this, names);
        if (!pkgs.isEmpty()) {
            AppLineup.save(this, pkgs);
            android.util.Log.i(DeckService.TAG, "lineup from home screen: " + names + " -> " + pkgs);
            DeckService s = DeckService.instance; if (s != null) s.lineupChanged();
        }
    }

    private void collectAll(AccessibilityNodeInfo n, java.util.List<AccessibilityNodeInfo> out, int depth) {
        if (n == null || depth > 60) return;
        out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) collectAll(n.getChild(i), out, depth + 1);
    }

    private long lastAt;

    private AccessibilityNodeInfo current() {
        // 1. the item a focus event named (fresh, refreshed so its bounds are current)
        if (last != null && last.refresh() && last.isFocused() && !coversScreen(last)) return last;
        AccessibilityNodeInfo f = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f != null) f.refresh();
        if (f != null && !coversScreen(f)) { last = f; return f; }
        // Jetpack Compose screens (the Fire TV home screen) keep Android's focus on the whole-screen view and mark the
        // highlighted item with focused=true on its own node: find that, so the app always starts from the real highlight.
        AccessibilityNodeInfo marked = deepestFocused(getRootInActiveWindow(), 0);
        if (marked != null) { last = marked; return marked; }
        if (last != null && last.refresh() && last.isVisibleToUser()) return last;
        last = null;
        return f; // maybe the whole-screen node, or null
    }

    /** The last node marked focused in tree order: the home screen leaves the last-visited top-bar tab marked too,
     *  and the real highlight comes after it. */
    private AccessibilityNodeInfo deepestFocused(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 60) return null;
        n.refresh(); // don't trust the cached focus flag
        AccessibilityNodeInfo found = n.isFocused() && !coversScreen(n) ? n : null;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = deepestFocused(n.getChild(i), depth + 1);
            if (r != null) found = r;
        }
        return found;
    }

    private static boolean coversScreen(AccessibilityNodeInfo n) {
        android.graphics.Rect r = new android.graphics.Rect(); n.getBoundsInScreen(r);
        return r.width() >= 1700 && r.height() >= 950;
    }

    // ---- Home screen mode (2026-11-23): the Fire TV launcher pulls input focus back to its featured banner and then
    // hides its rows from apps, but it does accept accessibility ("screen-reader") focus on any item. On the
    // launcher, arrows move that focus between tiles and OK clicks the item it is on.
    private static final String LAUNCHER = "com.amazon.tv.launcher";
    private AccessibilityNodeInfo a11yAt;

    private boolean onLauncher() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        return root != null && LAUNCHER.contentEquals(String.valueOf(root.getPackageName()));
    }

    private boolean a11yMove(int direction) {
        AccessibilityNodeInfo from = findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        if (from == null && a11yAt != null && a11yAt.refresh()) from = a11yAt;
        if (from == null) from = current();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        java.util.List<AccessibilityNodeInfo> all = new java.util.ArrayList<>(), items = new java.util.ArrayList<>();
        collectAll(root, all, 0);
        android.graphics.Rect r = new android.graphics.Rect();
        for (AccessibilityNodeInfo n : all) { // tiles: visible, named or clickable, not containers
            n.getBoundsInScreen(r);
            if (r.isEmpty() || !n.isVisibleToUser() || r.width() > 1200 || r.height() > 500) continue;
            boolean named = n.getContentDescription() != null && n.getContentDescription().length() > 0;
            if (named || n.isClickable() || n.isFocusable()) items.add(n);
        }
        AccessibilityNodeInfo next;
        if (from == null) next = items.isEmpty() ? null : items.get(0);
        else {
            android.graphics.Rect a = new android.graphics.Rect(); from.getBoundsInScreen(a);
            boolean vertical = direction == View.FOCUS_UP || direction == View.FOCUS_DOWN;
            next = null;
            // no same-size pass here: rows differ in tile size (the apps row is smaller) and Down skipped it
            for (int pass = 1; pass < (vertical ? 3 : 2) && next == null; pass++) next = bestIn(items, a, direction, vertical, pass);
        }
        boolean ok = next != null && next.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        if (ok) { a11yAt = next; showBox(next); }
        android.util.Log.i(DeckService.TAG, "a11y move " + direction + " from " + describe(from) + " to " + describe(next) + " ok=" + ok);
        return ok;
    }

    /** The launcher doesn't draw accessibility focus, so draw our own highlight box over that item. */
    private android.view.View box;

    private void showBox(AccessibilityNodeInfo n) {
        android.graphics.Rect r = new android.graphics.Rect(); n.getBoundsInScreen(r);
        main.post(() -> {
            android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
            int pad = 6;
            android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams(
                r.width() + 2 * pad, r.height() + 2 * pad,
                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
            lp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
            lp.x = r.left - pad; lp.y = r.top - pad;
            if (box == null) {
                box = new android.view.View(this);
                android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
                d.setCornerRadius(14); d.setStroke(7, 0xFFFFFFFF); d.setColor(0x00000000);
                box.setBackground(d);
                wm.addView(box, lp);
            } else wm.updateViewLayout(box, lp);
            android.util.Log.i(DeckService.TAG, "box at " + r.toShortString());
        });
    }

    private void hideBox() {
        main.post(() -> {
            if (box == null) return;
            android.util.Log.i(DeckService.TAG, "box hidden");
            ((android.view.WindowManager) getSystemService(WINDOW_SERVICE)).removeView(box);
            box = null;
        });
    }

    private boolean a11yClick() {
        AccessibilityNodeInfo n = findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        if (n == null) n = a11yAt;
        for (AccessibilityNodeInfo p = n; p != null; p = p.getParent())
            if (p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { android.util.Log.i(DeckService.TAG, "a11y click " + describe(p)); return true; }
        android.util.Log.i(DeckService.TAG, "a11y click: nothing at " + describe(n));
        return false;
    }

    private boolean move(int direction) {
        if (onLauncher()) return a11yMove(direction);
        AccessibilityNodeInfo from = current();
        if (from == null) {
            AccessibilityNodeInfo first = focused();
            android.util.Log.i(DeckService.TAG, "no focus; first focusable " + describe(first));
            if (first != null && first.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) { last = first; return true; }
            return false;
        }
        // our own geometric search only: the app's focusSearch() answers nonsense across processes on Compose screens
        AccessibilityNodeInfo next = nearest(from, direction);
        boolean ok = next != null && next.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        if (ok) last = next;
        android.util.Log.i(DeckService.TAG, "move " + direction + " from " + describe(from) + " to " + describe(next) + " ok=" + ok);
        if (!ok && next == null && direction == View.FOCUS_DOWN) ok = openPeekRow();
        if (!ok) {
            ok = scroll(next != null ? next : from, direction);
            if (ok) { // the list moved: take the closest tile in that direction once it has settled
                try { Thread.sleep(350); } catch (InterruptedException ignored) { }
                if (last != null) last.refresh();
                AccessibilityNodeInfo again = last != null ? nearest(last, direction) : null;
                if (again != null && again.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) last = again;
            }
        }
        return ok;
    }

    /** The closest focusable, visible node whose centre lies in that direction (the app's own focusSearch is not
     *  reachable across processes on Fire OS). Distance weights the off-axis gap 3x, like Android's FocusFinder. */
    private AccessibilityNodeInfo nearest(AccessibilityNodeInfo from, int direction) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        android.graphics.Rect a = new android.graphics.Rect();
        from.getBoundsInScreen(a);
        java.util.List<AccessibilityNodeInfo> all = new java.util.ArrayList<>();
        collect(root, all, 0);
        // Pass 0: a tile of about the same size that lines up with this one (same column for up/down, same row for
        // left/right) - like Android's FocusFinder "beam". Pass 1: anything that lines up. Pass 2 (up/down only):
        // anything that way. Left/right never leave the row: on the home screen that jumped to the top menu.
        // (Before 2026-11-23 pass 1 was "same size, anywhere": from a 64px top-bar icon it matched the half-visible
        // tile at the screen edge, because its visible slice is small too.)
        boolean vertical = direction == View.FOCUS_UP || direction == View.FOCUS_DOWN;
        for (int pass = 0; pass < (vertical ? 3 : 2); pass++) {
            AccessibilityNodeInfo best = bestIn(all, a, direction, vertical, pass);
            if (best != null) return best;
        }
        return null;
    }

    private AccessibilityNodeInfo bestIn(java.util.List<AccessibilityNodeInfo> all, android.graphics.Rect a, int direction, boolean vertical, int pass) {
        AccessibilityNodeInfo best = null; long bestScore = Long.MAX_VALUE;
        android.graphics.Rect b = new android.graphics.Rect();
        for (AccessibilityNodeInfo n : all) {
            n.getBoundsInScreen(b);
            if (b.equals(a) || b.isEmpty()) continue;
            if (pass < 2) {
                boolean inBeam = vertical ? (b.right > a.left && b.left < a.right) : (b.bottom > a.top && b.top < a.bottom);
                if (!inBeam) continue;
                if (pass == 0) {
                    boolean similar = b.height() * 2 >= a.height() && b.height() <= a.height() * 2 && b.width() * 2 >= a.width() && b.width() <= a.width() * 2;
                    if (!similar) continue;
                }
            }
            // Android FocusFinder's rule: the candidate must lie past the current item in that direction (its near
            // edge beyond ours and its far edge further still), not just have its centre a little that way.
            long major, minor;
            switch (direction) {
                case View.FOCUS_UP:
                    if (!(b.bottom <= a.top + 8 && b.top < a.top)) continue;
                    major = a.top - b.bottom; minor = Math.abs(b.centerX() - a.centerX()); break;
                case View.FOCUS_DOWN:
                    if (!(b.top >= a.bottom - 8 && b.bottom > a.bottom)) continue;
                    major = b.top - a.bottom; minor = Math.abs(b.centerX() - a.centerX()); break;
                case View.FOCUS_LEFT:
                    if (!(b.right <= a.left + 8 && b.left < a.left)) continue;
                    major = a.left - b.right; minor = Math.abs(b.centerY() - a.centerY()); break;
                default:
                    if (!(b.left >= a.right - 8 && b.right > a.right)) continue;
                    major = b.left - a.right; minor = Math.abs(b.centerY() - a.centerY()); break;
            }
            major = Math.max(0, major);
            long score = 13 * major * major + minor * minor; // FocusFinder's weighting
            if (score < bestScore) { bestScore = score; best = n; }
        }
        return best;
    }

    private void collect(AccessibilityNodeInfo n, java.util.List<AccessibilityNodeInfo> out, int depth) {
        if (n == null || depth > 60) return;
        // off-screen tiles count too: focusing one makes the app scroll it into view
        // Skip the home screen's featured banner: once it has the highlight the launcher hides every other row from
        // accessibility (only a "peek" strip apps can't open), so arrows got stuck there (2026-11-23). It's a promo.
        String id = n.getViewIdResourceName();
        boolean banner = id != null && id.contains("featured_item_rotator");
        if (n.isFocusable() && n.isEnabled() && !banner) out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), out, depth + 1);
    }

    /** Fire TV home screen with the featured banner highlighted: only the banner is focusable and the rows sit in a
     *  "peek" strip at the bottom (yac_peek_overlay_row) that the real remote's Down opens. Try to open it ourselves. */
    private boolean openPeekRow() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        java.util.List<AccessibilityNodeInfo> all = new java.util.ArrayList<>();
        collectAll(root, all, 0);
        for (AccessibilityNodeInfo n : all) {
            String id = n.getViewIdResourceName();
            if (id == null || !id.endsWith("peek_overlay_row")) continue;
            StringBuilder acts = new StringBuilder();
            for (AccessibilityNodeInfo.AccessibilityAction a : n.getActionList()) acts.append(a.getId()).append(' ');
            int[] tries = { AccessibilityNodeInfo.ACTION_FOCUS, AccessibilityNodeInfo.ACTION_SELECT, AccessibilityNodeInfo.ACTION_CLICK, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD };
            for (int t : tries) {
                if (n.performAction(t)) { android.util.Log.i(DeckService.TAG, "peek row opened with action " + t + " (actions: " + acts + ")"); return true; }
            }
            android.util.Log.i(DeckService.TAG, "peek row found but no action worked (actions: " + acts + ")");
        }
        return false;
    }

    /** At the edge of a list: scroll it toward that direction so the next row/column comes on screen. Up/down use
     *  a tall scrollable (the page, e.g. the home screen's grid_container); left/right the nearest one (the row).
     *  Before 2026-11-23 up/down scrolled the tile's own row sideways, so Down got stuck on the last visible row. */
    private boolean scroll(AccessibilityNodeInfo from, int direction) {
        boolean vertical = direction == View.FOCUS_UP || direction == View.FOCUS_DOWN;
        int action = (direction == View.FOCUS_UP || direction == View.FOCUS_LEFT) ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
        android.graphics.Rect r = new android.graphics.Rect(), f = new android.graphics.Rect();
        from.getBoundsInScreen(f);
        for (AccessibilityNodeInfo n = from.getParent(); n != null; n = n.getParent()) {
            if (!n.isScrollable()) continue;
            n.getBoundsInScreen(r);
            if (vertical && r.height() < f.height() * 3) continue; // a sideways row, not the page
            if (n.performAction(action)) return true;
        }
        return false;
    }

    private static String describe(AccessibilityNodeInfo n) {
        if (n == null) return "null";
        android.graphics.Rect r = new android.graphics.Rect(); n.getBoundsInScreen(r);
        CharSequence t = n.getText() != null ? n.getText() : n.getContentDescription();
        return n.getClassName() + "[" + (t == null ? "" : t.toString().replace('\n', ' ')) + "]" + r.toShortString();
    }

    private boolean click() {
        if (onLauncher() && (a11yAt != null || findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) != null)) return a11yClick();
        AccessibilityNodeInfo start = current();
        if (start == null || coversScreen(start)) start = focused();
        // the tile itself first (the home screen's tiles accept a click without saying they are clickable)
        if (start != null && !coversScreen(start) && start.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            android.util.Log.i(DeckService.TAG, "click " + describe(start));
            return true;
        }
        for (AccessibilityNodeInfo n = start; n != null; n = n.getParent())
            if (n.isClickable() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                android.util.Log.i(DeckService.TAG, "click " + describe(n));
                return true;
            }
        android.util.Log.i(DeckService.TAG, "click: nothing clickable at " + describe(start));
        return false;
    }
}
