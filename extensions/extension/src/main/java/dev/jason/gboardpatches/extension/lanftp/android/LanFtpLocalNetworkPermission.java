package dev.jason.gboardpatches.extension.lanftp.android;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

/** Compatibility boundary for Android 17 local-network runtime permission enforcement. */
public final class LanFtpLocalNetworkPermission {
    public static final String NAME = "android.permission.ACCESS_LOCAL_NETWORK";
    private static final int ANDROID_17_API = 37;

    private LanFtpLocalNetworkPermission() {
    }

    public static boolean isGranted(Context context) {
        if (context == null) {
            return false;
        }
        int targetSdk = context.getApplicationInfo().targetSdkVersion;
        if (!isRuntimePermissionRequired(Build.VERSION.SDK_INT, targetSdk)) {
            return true;
        }
        return context.checkSelfPermission(NAME) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean isRuntimePermissionRequired(int platformSdk, int targetSdk) {
        return platformSdk >= ANDROID_17_API && targetSdk >= ANDROID_17_API;
    }
}
