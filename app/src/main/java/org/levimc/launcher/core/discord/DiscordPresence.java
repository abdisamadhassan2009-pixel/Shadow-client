package org.levimc.launcher.core.discord;

import android.app.Application;

/**
 * Shadow Client: Discord Rich Presence is removed (no Discord SDK, no network).
 * The public API is kept as no-ops so existing callers compile unchanged.
 */
public final class DiscordPresence {
    private DiscordPresence() {
    }

    public static void initialize(Application application) {
    }

    public static boolean isConfigured() {
        return false;
    }

    public static void refresh() {
    }

    public static void shutdown() {
    }
}
