package org.levimc.launcher.util;

/**
 * Shadow Client branding and update source.
 *
 * Shadow Client is based on LeviLauncher for Android by LiteLDev (Apache License 2.0).
 * Set UPDATE_OWNER / UPDATE_REPO to the GitHub repo that publishes Shadow Client
 * releases. While UPDATE_OWNER is empty, update checks are skipped so the
 * upstream LeviLauncher APK is never offered as an "update".
 */
public final class ShadowBranding {
    public static final String UPDATE_OWNER = "";
    public static final String UPDATE_REPO = "shadow-client";
    public static final String DISCORD_URL = "https://discord.gg/shadowpvp";

    private ShadowBranding() {
    }

    public static boolean updatesConfigured() {
        return !UPDATE_OWNER.isEmpty();
    }
}
