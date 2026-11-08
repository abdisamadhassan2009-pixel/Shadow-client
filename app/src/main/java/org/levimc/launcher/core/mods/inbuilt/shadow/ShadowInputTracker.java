package org.levimc.launcher.core.mods.inbuilt.shadow;

import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * Shadow Client: watches input (keyboard, mouse, touch) for the Keystrokes HUD.
 * It only observes events; it never consumes them, so gameplay input is unchanged.
 * All calls happen on the UI thread. Click times live in fixed ring buffers,
 * so nothing is allocated while you play.
 */
public final class ShadowInputTracker {
    public static final int CLICK_LEFT = 0;
    public static final int CLICK_RIGHT = 1;
    public static final int CLICK_TAP = 2;

    private static final long CPS_WINDOW_MS = 1000L;
    private static final int RING_SIZE = 64;
    /** Taps on the left part of the screen are treated as joystick/movement, not attacks. */
    private static final float TAP_ZONE_START = 0.4f;

    private static final long[][] clicks = new long[3][RING_SIZE];
    private static final int[] heads = new int[3];

    private static boolean keyW, keyA, keyS, keyD, keySpace, keyShift;
    private static boolean mouseLeft, mouseRight;
    private static boolean keyboardSeen;
    private static int lastButtonState;

    private ShadowInputTracker() {
    }

    public static void onKey(KeyEvent event) {
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (event.getAction() != KeyEvent.ACTION_DOWN && event.getAction() != KeyEvent.ACTION_UP) return;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_W: keyW = down; break;
            case KeyEvent.KEYCODE_A: keyA = down; break;
            case KeyEvent.KEYCODE_S: keyS = down; break;
            case KeyEvent.KEYCODE_D: keyD = down; break;
            case KeyEvent.KEYCODE_SPACE: keySpace = down; break;
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT: keyShift = down; break;
            default: return;
        }
        keyboardSeen = true;
    }

    public static void onMouse(MotionEvent event) {
        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_BUTTON_PRESS && action != MotionEvent.ACTION_BUTTON_RELEASE) return;
        int state = event.getButtonState();
        boolean left = (state & MotionEvent.BUTTON_PRIMARY) != 0;
        boolean right = (state & MotionEvent.BUTTON_SECONDARY) != 0;
        if (left && (lastButtonState & MotionEvent.BUTTON_PRIMARY) == 0) record(CLICK_LEFT);
        if (right && (lastButtonState & MotionEvent.BUTTON_SECONDARY) == 0) record(CLICK_RIGHT);
        mouseLeft = left;
        mouseRight = right;
        lastButtonState = state;
        keyboardSeen = true;
    }

    public static void onTouch(MotionEvent event, int screenWidth) {
        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_POINTER_DOWN) return;
        if (event.getButtonState() != 0) return;
        int index = event.getActionIndex();
        if (screenWidth > 0 && event.getX(index) < screenWidth * TAP_ZONE_START) return;
        record(CLICK_TAP);
    }

    private static void record(int type) {
        clicks[type][heads[type]] = android.os.SystemClock.uptimeMillis();
        heads[type] = (heads[type] + 1) % RING_SIZE;
    }

    public static int cps(int type) {
        long now = android.os.SystemClock.uptimeMillis();
        int count = 0;
        long[] ring = clicks[type];
        for (long t : ring) {
            if (t != 0 && now - t <= CPS_WINDOW_MS) count++;
        }
        return count;
    }

    public static boolean w() { return keyW; }
    public static boolean a() { return keyA; }
    public static boolean s() { return keyS; }
    public static boolean d() { return keyD; }
    public static boolean space() { return keySpace; }
    public static boolean shift() { return keyShift; }
    public static boolean mouseLeft() { return mouseLeft; }
    public static boolean mouseRight() { return mouseRight; }
    public static boolean keyboardSeen() { return keyboardSeen; }
}
