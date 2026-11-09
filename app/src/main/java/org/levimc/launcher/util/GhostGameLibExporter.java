package org.levimc.launcher.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Ghost Client: exports the original libminecraftpe.so from the player's own installed
 * Minecraft (Google Play copy) as a zip, so Ghost Client's PvP mods can be developed against
 * the exact game version. Reads only Minecraft's own app files; nothing is uploaded anywhere.
 */
public final class GhostGameLibExporter {
    private static final String MC_PACKAGE = "com.mojang.minecraftpe";
    private static final String LIB = "libminecraftpe.so";

    private GhostGameLibExporter() {
    }

    /** Suggested file name, e.g. libminecraftpe_1.26.60.4_arm64-v8a.zip */
    public static String suggestedName(Context context) {
        return "libminecraftpe_" + minecraftVersion(context) + "_" + abi() + ".zip";
    }

    public static void export(Context context, Uri target) throws IOException {
        PackageManager pm = context.getPackageManager();
        ApplicationInfo mc;
        try {
            mc = pm.getApplicationInfo(MC_PACKAGE, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IOException("Minecraft from Google Play is not installed");
        }
        try (OutputStream raw = context.getContentResolver().openOutputStream(target)) {
            if (raw == null) throw new IOException("Cannot write to the chosen location");
            try (ZipOutputStream zip = new ZipOutputStream(raw)) {
                String source = copyLibrary(mc, zip);
                zip.putNextEntry(new ZipEntry("info.txt"));
                String info = "minecraft_version=" + minecraftVersion(context) + "\n"
                        + "abi=" + abi() + "\n"
                        + "source=" + source + "\n"
                        + "android_sdk=" + Build.VERSION.SDK_INT + "\n";
                zip.write(info.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static String copyLibrary(ApplicationInfo mc, ZipOutputStream zip) throws IOException {
        // 1. Already extracted by Android into Minecraft's native library folder.
        if (mc.nativeLibraryDir != null) {
            File extracted = new File(mc.nativeLibraryDir, LIB);
            if (extracted.isFile() && extracted.canRead()) {
                try (InputStream in = new FileInputStream(extracted)) {
                    writeEntry(zip, in);
                }
                return "nativeLibraryDir";
            }
        }
        // 2. Inside Minecraft's base or split APKs (lib/<abi>/libminecraftpe.so).
        List<String> apks = new ArrayList<>();
        if (mc.sourceDir != null) apks.add(mc.sourceDir);
        if (mc.splitSourceDirs != null) for (String split : mc.splitSourceDirs) apks.add(split);
        String wanted = "lib/" + abi() + "/" + LIB;
        for (String apk : apks) {
            try (ZipFile zf = new ZipFile(apk)) {
                ZipEntry entry = zf.getEntry(wanted);
                if (entry == null) continue;
                try (InputStream in = zf.getInputStream(entry)) {
                    writeEntry(zip, in);
                }
                return new File(apk).getName();
            } catch (IOException ignored) {
                // try the next APK
            }
        }
        throw new IOException("Could not find " + LIB + " in the installed Minecraft");
    }

    private static void writeEntry(ZipOutputStream zip, InputStream in) throws IOException {
        zip.putNextEntry(new ZipEntry(LIB));
        byte[] buffer = new byte[256 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) zip.write(buffer, 0, read);
        zip.closeEntry();
    }

    private static String minecraftVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(MC_PACKAGE, 0);
            return info.versionName != null ? info.versionName : "unknown";
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }

    private static String abi() {
        return Build.SUPPORTED_64_BIT_ABIS.length > 0 ? Build.SUPPORTED_64_BIT_ABIS[0] : "arm64-v8a";
    }
}
