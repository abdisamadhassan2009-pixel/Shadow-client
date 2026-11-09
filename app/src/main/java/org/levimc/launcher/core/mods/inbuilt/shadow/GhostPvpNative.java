package org.levimc.launcher.core.mods.inbuilt.shadow;

import android.util.Log;

/**
 * Ghost Client: bridge to libghostpvp.so, which reads the player's equipment inside Minecraft
 * (read-only) so the Armor HUD can show real durability.
 */
public final class GhostPvpNative {
    private static final String TAG = "GhostPvP";
    private static boolean loaded;
    private static boolean ready;

    private GhostPvpNative() {
    }

    /** Call once after libminecraftpe.so and the game loader are loaded. Safe to call again. */
    public static synchronized boolean init() {
        if (ready) return true;
        try {
            if (!loaded) {
                System.loadLibrary("ghostpvp");
                loaded = true;
            }
            ready = nativeInit();
            if (!ready) Log.w(TAG, "Ghost PvP unavailable: " + nativeStatus());
        } catch (Throwable t) {
            Log.w(TAG, "Ghost PvP failed to load", t);
            ready = false;
        }
        return ready;
    }

    public static boolean isReady() {
        return ready;
    }

    public static String status() {
        try {
            return loaded ? nativeStatus() : "not loaded";
        } catch (Throwable t) {
            return t.getMessage();
        }
    }

    /** One equipment slot; {@code present == false} means the slot is empty. */
    public static final class Item {
        public boolean present;
        public String id = "";
        public int damage, max, count;

        public int remaining() {
            return Math.max(0, max - damage);
        }

        public float fraction() {
            return max > 0 ? remaining() / (float) max : 1f;
        }
    }

    public static final int HELMET = 0, CHEST = 1, LEGS = 2, BOOTS = 3, MAIN_HAND = 4, OFF_HAND = 5;

    /** Latest equipment, or null when not in a world (or the native side is unavailable). */
    public static Item[] equipment() {
        if (!ready) return null;
        String raw;
        try {
            raw = nativeEquipment();
        } catch (Throwable t) {
            return null;
        }
        if (raw == null || raw.isEmpty()) return null;
        String[] lines = raw.split("\n", -1);
        Item[] out = new Item[6];
        for (int i = 0; i < out.length; i++) {
            Item item = new Item();
            out[i] = item;
            if (i >= lines.length || lines[i].isEmpty()) continue;
            String[] f = lines[i].split("\\|");
            if (f.length < 4) continue;
            item.present = true;
            item.id = f[0];
            item.damage = parse(f[1]);
            item.max = parse(f[2]);
            item.count = parse(f[3]);
        }
        return out;
    }

    public static void setEnabled(boolean enabled) {
        if (!loaded) return;
        try {
            nativeSetEnabled(enabled);
        } catch (Throwable ignored) {
        }
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static native boolean nativeInit();

    private static native String nativeStatus();

    private static native void nativeSetEnabled(boolean enabled);

    private static native String nativeEquipment();
}
