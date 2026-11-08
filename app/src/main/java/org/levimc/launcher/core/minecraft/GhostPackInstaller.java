package org.levimc.launcher.core.minecraft;

import android.content.Context;
import android.content.res.AssetManager;

import org.levimc.launcher.BuildConfig;
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.core.mods.inbuilt.shadow.ShadowMods;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Ghost Client: unpacks the resource packs that ship inside the APK (assets/ghost_packs/...)
 * and returns the ones the player has switched on. BundledResourcePackInstaller then copies
 * them into Minecraft and adds them to global_resource_packs.json, and removes any that were
 * switched off, exactly like packs bundled with native mods.
 */
final class GhostPackInstaller {
    private static final String ASSET_ROOT = "ghost_packs";
    private static final String STAMP_FILE = ".ghost_pack_build";

    private GhostPackInstaller() {
    }

    /** Pack folders (each containing manifest.json) that should be active for this launch. */
    static List<File> enabledPackRoots(Context context) throws IOException {
        Context app = context.getApplicationContext();
        File root = new File(app.getFilesDir(), ASSET_ROOT);
        unpackIfNeeded(app.getAssets(), root);
        InbuiltModManager manager = InbuiltModManager.getInstance(app);
        List<File> result = new ArrayList<>();
        addIfEnabled(result, manager, ShadowMods.UI_PACK, new File(root, "ghost_ui"));
        addIfEnabled(result, manager, ShadowMods.PVP_PACK, new File(root, "ghost_pvp"));
        return result;
    }

    private static void addIfEnabled(List<File> out, InbuiltModManager manager, String modId, File packRoot) {
        if (!new File(packRoot, "manifest.json").isFile()) return;
        if (manager.resolveInbuiltModEnabled(modId, ShadowMods.defaultEnabled(modId))) out.add(packRoot);
    }

    /** Re-extracts the packs only when the app build changes, so normal launches skip the copy. */
    private static void unpackIfNeeded(AssetManager assets, File root) throws IOException {
        String build = BuildConfig.VERSION_NAME + "/" + BuildConfig.VERSION_CODE;
        File stamp = new File(root, STAMP_FILE);
        if (stamp.isFile() && build.equals(new String(Files.readAllBytes(stamp.toPath()), StandardCharsets.UTF_8).trim())) {
            return;
        }
        deleteRecursively(root);
        if (!root.mkdirs() && !root.isDirectory()) throw new IOException("Cannot create " + root);
        copyAssetDir(assets, ASSET_ROOT, root);
        Files.write(stamp.toPath(), build.getBytes(StandardCharsets.UTF_8));
    }

    private static void copyAssetDir(AssetManager assets, String assetPath, File target) throws IOException {
        String[] children = assets.list(assetPath);
        if (children == null || children.length == 0) {
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
            try (InputStream in = assets.open(assetPath); OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            return;
        }
        if (!target.isDirectory() && !target.mkdirs()) throw new IOException("Cannot create " + target);
        for (String child : children) {
            copyAssetDir(assets, assetPath + "/" + child, new File(target, child));
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
