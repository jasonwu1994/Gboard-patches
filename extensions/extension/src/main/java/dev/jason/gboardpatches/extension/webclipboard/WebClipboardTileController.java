package dev.jason.gboardpatches.extension.webclipboard;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.TileService;
import android.util.Log;

import java.util.List;

public final class WebClipboardTileController {
    private static final String TAG = "GboardWebClipboard";

    private WebClipboardTileController() {
    }

    public static boolean isEnabled(SharedPreferences preferences) {
        return WebClipboardPreferences.isEnabled(preferences);
    }

    public static void setEnabled(SharedPreferences preferences, boolean enabled) {
        WebClipboardPreferences.setEnabled(preferences, enabled);
    }

    public static boolean isRuntimeActive(SharedPreferences preferences) {
        return WebClipboardPreferences.isRuntimeActive(preferences);
    }

    public static void setRuntimeActive(SharedPreferences preferences, boolean runtimeActive) {
        WebClipboardPreferences.setRuntimeActive(preferences, runtimeActive);
    }

    public static boolean isPairingRequired(SharedPreferences preferences) {
        return WebClipboardPreferences.isPairingRequired(preferences);
    }

    public static String getPairingCode(SharedPreferences preferences) {
        return WebClipboardPreferences.getPairingCode(preferences);
    }

    public static boolean toggle(SharedPreferences preferences) {
        boolean nextEnabled = !isEnabled(preferences);
        setEnabled(preferences, nextEnabled);
        return nextEnabled;
    }

    public static boolean applyEnabled(Context context, boolean enabled) {
        if (context == null) {
            return enabled;
        }
        try {
            Context appContext = context.getApplicationContext() != null
                    ? context.getApplicationContext()
                    : context;
            SharedPreferences preferences = WebClipboardPreferences.preferences(appContext);
            setEnabled(preferences, enabled);
            setRuntimeActive(preferences, false);
            if (enabled) {
                ClipboardSyncService.startOrUpdate(appContext);
            } else {
                ClipboardSyncService.requestStop(appContext);
            }
            requestTileRefresh(appContext);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to apply Web Clipboard enabled state", throwable);
        }
        return enabled;
    }

    public static boolean toggle(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences preferences = WebClipboardPreferences.preferences(context);
        return applyEnabled(context, !isEnabled(preferences));
    }

    public static void applyPort(Context context, int port) {
        if (context == null) {
            return;
        }
        try {
            Context appContext = context.getApplicationContext() != null
                    ? context.getApplicationContext()
                    : context;
            SharedPreferences preferences = WebClipboardPreferences.preferences(appContext);
            WebClipboardPreferences.setPort(preferences, port);
            if (isEnabled(preferences)) {
                ClipboardSyncService.startOrUpdate(appContext);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to apply Web Clipboard port", throwable);
        }
    }

    public static void applyPairingRequired(Context context, boolean required) {
        if (context == null) {
            return;
        }
        try {
            Context appContext = context.getApplicationContext() != null
                    ? context.getApplicationContext()
                    : context;
            SharedPreferences preferences = WebClipboardPreferences.preferences(appContext);
            WebClipboardPreferences.setPairingRequired(preferences, required);
            if (required && WebClipboardPreferences.DEFAULT_PAIRING_CODE.equals(
                    WebClipboardPreferences.getPairingCode(preferences))) {
                WebClipboardPreferences.regeneratePairingCode(preferences);
            }
            if (isEnabled(preferences)) {
                ClipboardSyncService.startOrUpdate(appContext);
            }
            requestTileRefresh(appContext);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to apply Web Clipboard pairing-required state", throwable);
        }
    }

    public static void applyPairingCode(Context context, String code) {
        if (context == null) {
            return;
        }
        try {
            Context appContext = context.getApplicationContext() != null
                    ? context.getApplicationContext()
                    : context;
            SharedPreferences preferences = WebClipboardPreferences.preferences(appContext);
            WebClipboardPreferences.setPairingCode(preferences, code);
            if (isEnabled(preferences)) {
                ClipboardSyncService.startOrUpdate(appContext);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to apply Web Clipboard pairing code", throwable);
        }
    }

    public static void applyToastEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        try {
            Context appContext = context.getApplicationContext() != null
                    ? context.getApplicationContext()
                    : context;
            SharedPreferences preferences = WebClipboardPreferences.preferences(appContext);
            WebClipboardPreferences.setToastEnabled(preferences, enabled);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to apply Web Clipboard toast-enabled state", throwable);
        }
    }

    public static String regeneratePairingCode(Context context) {
        if (context == null) {
            return WebClipboardPreferences.DEFAULT_PAIRING_CODE;
        }
        try {
            Context appContext = context.getApplicationContext() != null
                    ? context.getApplicationContext()
                    : context;
            SharedPreferences preferences = WebClipboardPreferences.preferences(appContext);
            String code = WebClipboardPreferences.regeneratePairingCode(preferences);
            if (isEnabled(preferences)) {
                ClipboardSyncService.startOrUpdate(appContext);
            }
            return code;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to regenerate Web Clipboard pairing code", throwable);
            return WebClipboardPreferences.DEFAULT_PAIRING_CODE;
        }
    }

    public static boolean isTileActive(SharedPreferences preferences, boolean serviceRunning) {
        return isEnabled(preferences) && isRuntimeActive(preferences);
    }

    public static boolean shouldRestoreService(SharedPreferences preferences,
            boolean serviceRunning) {
        return isEnabled(preferences) && !serviceRunning;
    }

    public static boolean shouldStopService(SharedPreferences preferences) {
        return !isEnabled(preferences);
    }

    static String buildStatusSubtitle(List<String> urls) {
        if (urls != null) {
            for (String url : urls) {
                if (url != null && url.startsWith("http://")) {
                    return url;
                }
            }
            for (String url : urls) {
                if (url != null && !url.isBlank()) {
                    return url;
                }
            }
        }
        return "http://<phone LAN IP>:" + WebClipboardPreferences.DEFAULT_PORT + "/";
    }

    static String buildStateDescription(boolean enabled, List<String> urls) {
        return "Web Clipboard " + (enabled ? "enabled at " : "disabled. Endpoint ")
                + buildStatusSubtitle(urls);
    }

    public static boolean isServiceRunning(Context context) {
        if (context == null) {
            return false;
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        ActivityManager activityManager = appContext.getSystemService(ActivityManager.class);
        if (activityManager == null) {
            return false;
        }
        try {
            List<ActivityManager.RunningServiceInfo> runningServices =
                    activityManager.getRunningServices(Integer.MAX_VALUE);
            if (runningServices == null) {
                return false;
            }
            String serviceClassName = ClipboardSyncService.class.getName();
            for (ActivityManager.RunningServiceInfo info : runningServices) {
                if (info != null && info.service != null
                        && serviceClassName.equals(info.service.getClassName())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }

    public static void markRuntimeActive(Context context, boolean runtimeActive) {
        if (context == null) {
            return;
        }
        try {
            Context appContext = context.getApplicationContext() != null
                    ? context.getApplicationContext()
                    : context;
            SharedPreferences preferences = WebClipboardPreferences.preferences(appContext);
            setRuntimeActive(preferences, runtimeActive);
            requestTileRefresh(appContext);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to mark Web Clipboard runtime active state", throwable);
        }
    }

    public static void reconcileAfterPackageReplace(Context context) {
        if (context == null) {
            return;
        }
        try {
            Context appContext = context.getApplicationContext() != null
                    ? context.getApplicationContext()
                    : context;
            SharedPreferences preferences = WebClipboardPreferences.preferences(appContext);
            setRuntimeActive(preferences, false);
            if (isEnabled(preferences)) {
                ClipboardSyncService.startOrUpdate(appContext);
            }
            requestTileRefresh(appContext);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to reconcile Web Clipboard after package replace", throwable);
        }
    }

    public static void requestTileRefresh(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        try {
            TileService.requestListeningState(
                    context,
                    new ComponentName(context, WebClipboardTileService.class));
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to request Web Clipboard tile refresh", throwable);
        }
    }
}

