package org.levimc.launcher.core.mods.inbuilt.shadow;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.inbuilt.overlay.ShadowTextOverlay;

import java.util.Calendar;

/**
 * Shadow Client's own HUD mods. Each one is a small text widget that updates on a
 * timer and only redraws when its text actually changes.
 */
public final class ShadowMods {
    public static final String KEYSTROKES = "shadow_keystrokes";
    public static final String CLOCK = "shadow_clock";
    public static final String BATTERY = "shadow_battery";
    public static final String SESSION_TIMER = "shadow_session_timer";
    public static final String CROSSHAIR = "shadow_crosshair";
    public static final String WATERMARK = "shadow_watermark";
    public static final String STOPWATCH = "shadow_stopwatch";
    public static final String UI_PACK = "shadow_ui_pack";
    public static final String PVP_PACK = "shadow_pvp_pack";
    public static final String ARMOR_HUD = "shadow_armor_hud";

    public static final String[] ALL = {ARMOR_HUD, KEYSTROKES, CROSSHAIR, PVP_PACK, UI_PACK, WATERMARK, STOPWATCH, CLOCK, BATTERY, SESSION_TIMER};

    private static final int COLOR_ON = 0xFF3DDC97;
    private static final int COLOR_OFF = 0xFF8C93A1;

    private static long sessionStart = SystemClock.elapsedRealtime();

    private ShadowMods() {
    }

    /** Mods that start switched on for a fresh install (players can still turn them off). */
    public static boolean defaultEnabled(String modId) {
        return KEYSTROKES.equals(modId)
                || ARMOR_HUD.equals(modId)
                || UI_PACK.equals(modId)
                || PVP_PACK.equals(modId)
                || "cps_display".equals(modId)
                || "fps_display".equals(modId);
    }

    /** Toggles for the resource packs bundled with Ghost Client (applied on the next launch). */
    public static boolean isPackMod(String modId) {
        return UI_PACK.equals(modId) || PVP_PACK.equals(modId);
    }

    private static final class NoHud implements org.levimc.launcher.core.mods.inbuilt.overlay.ShadowHudElement {
        private final String id;
        NoHud(String id) { this.id = id; }
        @Override public String getModId() { return id; }
        @Override public void show(int x, int y) {}
        @Override public void hide() {}
        @Override public void setVisibility(int visibility) {}
        @Override public void setHudEditorMode(boolean active) {}
        @Override public void applyConfigurationChanges() {}
        @Override public void updatePosition(int x, int y) {}
    }

    public static boolean isShadowMod(String modId) {
        for (String id : ALL) if (id.equals(modId)) return true;
        return false;
    }

    public static int nameRes(String modId) {
        switch (modId) {
            case ARMOR_HUD: return R.string.shadow_mod_armor_hud;
            case KEYSTROKES: return R.string.shadow_mod_keystrokes;
            case CLOCK: return R.string.shadow_mod_clock;
            case BATTERY: return R.string.shadow_mod_battery;
            case CROSSHAIR: return R.string.shadow_mod_crosshair;
            case UI_PACK: return R.string.shadow_mod_ui_pack;
            case WATERMARK: return R.string.shadow_mod_watermark;
            case STOPWATCH: return R.string.shadow_mod_stopwatch;
            case PVP_PACK: return R.string.shadow_mod_pvp_pack;
            default: return R.string.shadow_mod_session_timer;
        }
    }

    public static int descRes(String modId) {
        switch (modId) {
            case ARMOR_HUD: return R.string.shadow_mod_armor_hud_desc;
            case KEYSTROKES: return R.string.shadow_mod_keystrokes_desc;
            case CLOCK: return R.string.shadow_mod_clock_desc;
            case BATTERY: return R.string.shadow_mod_battery_desc;
            case CROSSHAIR: return R.string.shadow_mod_crosshair_desc;
            case UI_PACK: return R.string.shadow_mod_ui_pack_desc;
            case WATERMARK: return R.string.shadow_mod_watermark_desc;
            case STOPWATCH: return R.string.shadow_mod_stopwatch_desc;
            case PVP_PACK: return R.string.shadow_mod_pvp_pack_desc;
            default: return R.string.shadow_mod_session_timer_desc;
        }
    }

    public static int iconRes(String modId) {
        switch (modId) {
            case ARMOR_HUD: return R.drawable.ic_ghost_armor_chest;
            case KEYSTROKES: return R.drawable.ic_shadow_keystrokes;
            case CLOCK: return R.drawable.ic_shadow_clock;
            case BATTERY: return R.drawable.ic_shadow_battery;
            case CROSSHAIR: return R.drawable.ic_shadow_crosshair;
            case UI_PACK: return R.drawable.ic_ghost_logo;
            case WATERMARK: return R.drawable.ic_ghost_logo;
            case STOPWATCH: return R.drawable.ic_shadow_timer;
            case PVP_PACK: return R.drawable.ic_shadow_crosshair;
            default: return R.drawable.ic_shadow_timer;
        }
    }

    /** How often each widget refreshes, in milliseconds. */
    public static int updateIntervalMs(String modId) {
        switch (modId) {
            case KEYSTROKES: return 50;
            case ARMOR_HUD: return 150;
            case STOPWATCH: return 100;
            case WATERMARK: return 60_000;
            case BATTERY: return 30_000;
            default: return 1_000;
        }
    }

    /** Builds the on-screen widget for a Ghost mod. */
    public static org.levimc.launcher.core.mods.inbuilt.overlay.ShadowHudElement createOverlay(android.app.Activity activity, String modId) {
        if (isPackMod(modId)) {
            return new NoHud(modId); // packs apply at launch; nothing to draw
        }
        if (CROSSHAIR.equals(modId)) {
            return new org.levimc.launcher.core.mods.inbuilt.overlay.ShadowCrosshairOverlay(activity, modId);
        }
        ShadowTextOverlay overlay = new ShadowTextOverlay(activity, modId, updateIntervalMs(modId), provider(activity, modId));
        if (STOPWATCH.equals(modId)) overlay.setTapHandler(ShadowMods::stopwatchTap);
        return overlay;
    }

    public static ShadowTextOverlay.TextProvider provider(Context context, String modId) {
        switch (modId) {
            case ARMOR_HUD: return () -> ArmorHud.text(context);
            case KEYSTROKES: return ShadowMods::keystrokes;
            case WATERMARK: return ShadowMods::watermark;
            case STOPWATCH: return ShadowMods::stopwatch;
            case CLOCK: return ShadowMods::clock;
            case BATTERY: return () -> battery(context.getApplicationContext());
            default: return ShadowMods::sessionTimer;
        }
    }

    public static void resetSession() {
        sessionStart = SystemClock.elapsedRealtime();
    }

    private static CharSequence keystrokes() {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (ShadowInputTracker.keyboardSeen()) {
            sb.append("    ");
            key(sb, " W ", ShadowInputTracker.w());
            sb.append('\n');
            key(sb, " A ", ShadowInputTracker.a());
            sb.append(' ');
            key(sb, " S ", ShadowInputTracker.s());
            sb.append(' ');
            key(sb, " D ", ShadowInputTracker.d());
            sb.append('\n');
            key(sb, "LMB " + pad(ShadowInputTracker.cps(ShadowInputTracker.CLICK_LEFT)), ShadowInputTracker.mouseLeft());
            sb.append(' ');
            key(sb, "RMB " + pad(ShadowInputTracker.cps(ShadowInputTracker.CLICK_RIGHT)), ShadowInputTracker.mouseRight());
            sb.append('\n');
            key(sb, "  SPACE  ", ShadowInputTracker.space());
            sb.append(' ');
            key(sb, "SHIFT", ShadowInputTracker.shift());
        } else {
            int taps = ShadowInputTracker.cps(ShadowInputTracker.CLICK_TAP);
            key(sb, "TAP " + pad(taps) + " CPS", taps > 0);
        }
        return sb;
    }

    private static void key(SpannableStringBuilder sb, String label, boolean pressed) {
        int start = sb.length();
        sb.append(label);
        sb.setSpan(new ForegroundColorSpan(pressed ? COLOR_ON : COLOR_OFF), start, sb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (pressed) {
            sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static String pad(int n) {
        return n < 10 ? " " + n : String.valueOf(n);
    }

    private static CharSequence clock() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR);
        if (h == 0) h = 12;
        int m = c.get(Calendar.MINUTE);
        return h + ":" + (m < 10 ? "0" : "") + m + (c.get(Calendar.AM_PM) == Calendar.AM ? " AM" : " PM");
    }

    private static CharSequence battery(Context context) {
        Intent status = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (status == null) return "Battery --";
        int level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int plugged = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        if (level < 0 || scale <= 0) return "Battery --";
        int pct = Math.round(level * 100f / scale);
        return "Battery " + pct + "%" + (plugged != 0 ? " +" : "");
    }

    private static CharSequence watermark() {
        return "GHOST CLIENT  " + org.levimc.launcher.BuildConfig.VERSION_NAME;
    }

    // Stopwatch: tap to start/stop, hold to reset.
    private static long stopwatchStart;     // elapsedRealtime when running, 0 when stopped
    private static long stopwatchElapsed;   // accumulated ms while stopped

    private static void stopwatchTap(boolean longPress) {
        long now = SystemClock.elapsedRealtime();
        if (longPress) {
            stopwatchStart = 0;
            stopwatchElapsed = 0;
        } else if (stopwatchStart == 0) {
            stopwatchStart = now;
        } else {
            stopwatchElapsed += now - stopwatchStart;
            stopwatchStart = 0;
        }
    }

    private static CharSequence stopwatch() {
        long ms = stopwatchElapsed + (stopwatchStart == 0 ? 0 : SystemClock.elapsedRealtime() - stopwatchStart);
        long m = ms / 60000, s = (ms / 1000) % 60, t = (ms / 100) % 10;
        return (stopwatchStart == 0 ? "Stopwatch " : "\u25B6 ") + m + ":" + (s < 10 ? "0" : "") + s + "." + t;
    }

    private static CharSequence sessionTimer() {
        long secs = (SystemClock.elapsedRealtime() - sessionStart) / 1000L;
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        StringBuilder sb = new StringBuilder("Session ");
        if (h > 0) sb.append(h).append(':').append(m < 10 ? "0" : "");
        sb.append(m).append(':').append(s < 10 ? "0" : "").append(s);
        return sb;
    }
}
