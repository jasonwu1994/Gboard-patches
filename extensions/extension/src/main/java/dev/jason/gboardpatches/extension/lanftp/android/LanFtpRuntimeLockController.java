package dev.jason.gboardpatches.extension.lanftp.android;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.util.Log;

/** Owns wake/Wi-Fi locks for the lifetime of an active FTP listener. */
final class LanFtpRuntimeLockController {
    private static final String TAG = "GboardLanFtp";
    private static final int RELEASE_ATTEMPTS = 2;

    private final Context context;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    LanFtpRuntimeLockController(Context context) {
        this.context = context;
    }

    void acquire() {
        try {
            if (wakeLock == null) {
                PowerManager manager = context.getSystemService(PowerManager.class);
                if (manager != null) {
                    wakeLock = manager.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK, "GboardPatches:LanFtpRuntime");
                    wakeLock.setReferenceCounted(false);
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire();
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Could not acquire FTP runtime wake lock", throwable);
        }
        try {
            if (wifiLock == null) {
                WifiManager manager = context.getSystemService(WifiManager.class);
                if (manager != null) {
                    wifiLock = manager.createWifiLock(
                            WifiManager.WIFI_MODE_FULL_HIGH_PERF, "GboardPatches:LanFtpWifi");
                    wifiLock.setReferenceCounted(false);
                }
            }
            if (wifiLock != null && !wifiLock.isHeld()) {
                wifiLock.acquire();
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Could not acquire FTP runtime Wi-Fi lock", throwable);
        }
    }

    void release() {
        for (int attempt = 0; wakeLock != null && attempt < RELEASE_ATTEMPTS; attempt++) {
            try {
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
                wakeLock = null;
            } catch (Throwable throwable) {
                if (attempt + 1 == RELEASE_ATTEMPTS) {
                    Log.w(TAG, "Could not release FTP runtime wake lock", throwable);
                }
            }
        }
        for (int attempt = 0; wifiLock != null && attempt < RELEASE_ATTEMPTS; attempt++) {
            try {
                if (wifiLock.isHeld()) {
                    wifiLock.release();
                }
                wifiLock = null;
            } catch (Throwable throwable) {
                if (attempt + 1 == RELEASE_ATTEMPTS) {
                    Log.w(TAG, "Could not release FTP runtime Wi-Fi lock", throwable);
                }
            }
        }
    }
}
