package org.levimc.launcher.core.discord;

/**
 * Ghost Client: kept only because the prebuilt libgxcore.so registers these native
 * methods when it loads. If this class is missing, gxcore fails to load and Minecraft
 * cannot launch. Nothing in Ghost Client calls these methods (Discord is removed).
 */
final class DiscordPresenceNative {
    private DiscordPresenceNative() {}

    static native long create(long applicationId);
    static native void update(long handle, String details, String state, long startedAt, String largeImage);
    static native void tick(long handle);
    static native void destroy(long handle);
}
