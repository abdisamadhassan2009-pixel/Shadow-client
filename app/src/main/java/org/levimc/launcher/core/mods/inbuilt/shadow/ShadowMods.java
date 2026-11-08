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

    public static final String[] ALL = {KEYSTROKES, CLOCK, BATTERY, SESSION_TIMER};

    private static final int COLOR_ON = 0xFF3DDC97;
    private static final int COLOR_OFF = 0xFF8C93A1;

    private static long sessionStart = SystemClock.elapsedRealtime();

    private ShadowMods() {
    }

    /** Mods that start switched on for a fresh install (players can still turn them off). */
    public static boolean defaultEnabled(String modId) {
        return KEYSTROKES.equals(modId)
                || "cps_display".equals(modId)
                || "fps_display".equals(modId);
    }

    public static boolean isShadowMod(String modId) {
        for (String id : ALL) if (id.equals(modId)) return true;
        return false;
    }

    public static int nameRes(String modId) {
        switch (modId) {
            case KEYSTROKES: return R.string.shadow_mod_keystrokes;
            case CLOCK: return R.string.shadow_mod_clock;
            case BATTERY: return R.string.shadow_mod_battery;
            default: return R.string.shadow_mod_session_timer;
        }
    }

    public static int descRes(String modId) {
        switch (modId) {
            case KEYSTROKES: return R.string.shadow_mod_keystrokes_desc;
            case CLOCK: return R.string.shadow_mod_clock_desc;
            case BATTERY: return R.string.shadow_mod_battery_desc;
            default: return R.string.shadow_mod_session_timer_desc;
        }
    }

    public static int iconRes(String modId) {
        switch (modId) {
            case KEYSTROKES: return R.drawable.ic_shadow_keystrokes;
            case CLOCK: return R.drawable.ic_shadow_clock;
            case BATTERY: return R.drawable.ic_shadow_battery;
            default: return R.drawable.ic_shadow_timer;
        }
    }

    /** How often each widget refreshes, in milliseconds. */
    public static int updateIntervalMs(String modId) {
        switch (modId) {
            case KEYSTROKES: return 50;
            case BATTERY: return 30_000;
            default: return 1_000;
        }
    }

    public static ShadowTextOverlay.TextProvider provider(Context context, String modId) {
        switch (modId) {
            case KEYSTROKES: return ShadowMods::keystrokes;
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

    private static CharSequence sessionTimer() {
        long secs = (SystemClock.elapsedRealtime() - sessionStart) / 1000L;
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        StringBuilder sb = new StringBuilder("Session ");
        if (h > 0) sb.append(h).append(':').append(m < 10 ? "0" : "");
        sb.append(m).append(':').append(s < 10 ? "0" : "").append(s);
        return sb;
    }
}
