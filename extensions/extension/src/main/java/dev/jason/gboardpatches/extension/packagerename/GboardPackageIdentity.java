package dev.jason.gboardpatches.extension.packagerename;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Decides whether the running Gboard package is one these patches support.
 *
 * <p>Supported packages are the official Gboard package, and a package whose manifest carries the
 * identity written by this bundle's own Package Rename patch. The identity is the SHA-256 of the
 * final package name rather than the name itself, so a later text-based rename of the manifest
 * cannot rewrite it into a matching value. A package renamed by any other tool has no identity, or
 * one that no longer matches, and is reported as unsupported.
 */
public final class GboardPackageIdentity {
    public static final String OFFICIAL_PACKAGE = "com.google.android.inputmethod.latin";
    public static final String IDENTITY_META_DATA_NAME =
            "dev.jason.gboardpatches.package_identity";
    static final String IDENTITY_PREFIX = "sha256:";

    private static volatile String cachedPackage;
    private static volatile boolean cachedSupported;

    private GboardPackageIdentity() {
    }

    /** Returns whether {@code context}'s own package is supported; never throws. */
    public static boolean isSupported(Context context) {
        String packageName;
        try {
            packageName = context == null ? null : context.getPackageName();
        } catch (Throwable unavailable) {
            return false;
        }
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        if (OFFICIAL_PACKAGE.equals(packageName)) {
            return true;
        }
        if (packageName.equals(cachedPackage)) {
            return cachedSupported;
        }
        boolean supported = isSupportedPackage(packageName, readIdentity(context, packageName));
        cachedSupported = supported;
        cachedPackage = packageName;
        return supported;
    }

    /** Pure decision used by {@link #isSupported(Context)}. */
    public static boolean isSupportedPackage(String packageName, String identity) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        if (OFFICIAL_PACKAGE.equals(packageName)) {
            return true;
        }
        String expected = identityOf(packageName);
        return identity != null && expected != null && expected.equalsIgnoreCase(identity.trim());
    }

    /** The identity value the Package Rename patch writes for {@code packageName}. */
    public static String identityOf(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(packageName.getBytes(StandardCharsets.UTF_8));
            // The prefix keeps the manifest value a plain string; an all-digit or exponent-shaped
            // hex digest would otherwise be compiled as a number.
            StringBuilder hex = new StringBuilder(IDENTITY_PREFIX.length() + digest.length * 2)
                    .append(IDENTITY_PREFIX);
            for (byte value : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return hex.toString();
        } catch (Throwable unavailable) {
            return null;
        }
    }

    private static String readIdentity(Context context, String packageName) {
        try {
            PackageManager packageManager = context.getPackageManager();
            if (packageManager == null) {
                return null;
            }
            ApplicationInfo info =
                    packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            Bundle metaData = info == null ? null : info.metaData;
            Object value = metaData == null ? null : metaData.get(IDENTITY_META_DATA_NAME);
            return value == null ? null : String.valueOf(value);
        } catch (Throwable unavailable) {
            return null;
        }
    }

    static void resetCacheForTest() {
        cachedPackage = null;
        cachedSupported = false;
    }
}
