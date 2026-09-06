package dev.jason.gboardpatches.extension.lanftp.android;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

/** Android shared-storage gate for the module-owned /sdcard FTP backend. */
public final class LanFtpAllFilesAccess {
    private LanFtpAllFilesAccess() {
    }

    public static boolean isGranted(Context context) {
        if (context == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false;
        }
        try {
            return Environment.isExternalStorageManager();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
