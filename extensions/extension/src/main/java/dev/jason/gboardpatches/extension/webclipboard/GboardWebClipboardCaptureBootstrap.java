package dev.jason.gboardpatches.extension.webclipboard;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GboardWebClipboardCaptureBootstrap {
    private static final String TAG = "GboardWebClipboard";
    private static final long DUPLICATE_SUPPRESSION_WINDOW_MS = 1_000L;
    private static final int PORTAL_PROOF_ATTEMPTS = 3;
    private static final long PORTAL_PROOF_RETRY_DELAY_MS = 150L;
    private static final Object LISTENER_LOCK = new Object();
    private static final ExecutorService LOOPBACK_DISPATCH_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "GboardWebClipboardLoopbackIngress");
                thread.setDaemon(true);
                return thread;
            });

    private static volatile Context installedContext;
    private static volatile ClipboardManager installedClipboardManager;
    private static volatile ClipboardManager.OnPrimaryClipChangedListener installedListener;
    private static volatile String lastDispatchedHash;
    private static volatile long lastDispatchedElapsedMs;

    private GboardWebClipboardCaptureBootstrap() {
    }

    public static void afterLatinImeOnCreate(Object receiver) {
        try {
            if (!(receiver instanceof Context context)) {
                return;
            }
            ensureClipboardListenerInstalled(context);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to install Web Clipboard capture bootstrap", throwable);
        }
    }

    private static void ensureClipboardListenerInstalled(Context context) {
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        synchronized (LISTENER_LOCK) {
            if (installedContext == appContext && installedListener != null) {
                return;
            }

            if (installedClipboardManager != null && installedListener != null) {
                try {
                    installedClipboardManager.removePrimaryClipChangedListener(installedListener);
                } catch (Throwable ignored) {
                    // Best effort.
                }
            }

            ClipboardManager clipboardManager = appContext.getSystemService(ClipboardManager.class);
            if (clipboardManager == null) {
                return;
            }

            ClipboardManager.OnPrimaryClipChangedListener listener =
                    () -> dispatchClipboardEvent(appContext, clipboardManager);
            clipboardManager.addPrimaryClipChangedListener(listener);
            installedContext = appContext;
            installedClipboardManager = clipboardManager;
            installedListener = listener;
        }
    }

    private static void dispatchClipboardEvent(Context context, ClipboardManager clipboardManager) {
        if (context == null || clipboardManager == null) {
            return;
        }
        try {
            LOOPBACK_DISPATCH_EXECUTOR.execute(
                    () -> dispatchLoopbackIngress(context, clipboardManager));
        } catch (Throwable ignored) {
            // Best effort.
        }
    }

    private static void dispatchLoopbackIngress(Context context,
            ClipboardManager clipboardManager) {
        RuntimeLookup runtimeLookup = webClipboardRuntimeConfig(context);
        if (runtimeLookup.config == null) {
            return;
        }
        if (!isExpectedPortalWithRetry(runtimeLookup.config)) {
            return;
        }
        ClipData clipData = currentClipboardData(clipboardManager);
        if (clipData == null || isWebClipboardEcho(clipData)) {
            return;
        }
        CharSequence text = currentClipboardText(context, clipData);
        if (text == null) {
            return;
        }
        String normalizedText = text.toString();
        if (normalizedText.isEmpty()) {
            return;
        }

        String hash = sha256Hex(normalizedText);
        long now = SystemClock.elapsedRealtime();
        if (hash.equals(lastDispatchedHash)
                && now - lastDispatchedElapsedMs <= DUPLICATE_SUPPRESSION_WINDOW_MS) {
            return;
        }

        try {
            boolean accepted = ClipboardSyncLoopbackIngressClient.submitPhoneClipboard(
                    normalizedText,
                    runtimeLookup.config.port,
                    runtimeLookup.config.loopbackIngressToken);
            if (accepted) {
                lastDispatchedHash = hash;
                lastDispatchedElapsedMs = SystemClock.elapsedRealtime();
            }
        } catch (Throwable ignored) {
            // Best effort.
        }
    }

    private static boolean isExpectedPortalWithRetry(RuntimeConfig runtimeConfig) {
        if (runtimeConfig == null) {
            return false;
        }
        for (int attempt = 1; attempt <= PORTAL_PROOF_ATTEMPTS; attempt++) {
            if (ClipboardSyncLoopbackIngressClient.isExpectedPortal(
                    runtimeConfig.port,
                    runtimeConfig.loopbackIngressToken)) {
                return true;
            }
            if (attempt < PORTAL_PROOF_ATTEMPTS) {
                sleepBeforePortalProofRetry();
            }
        }
        return false;
    }

    private static void sleepBeforePortalProofRetry() {
        try {
            Thread.sleep(PORTAL_PROOF_RETRY_DELAY_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static RuntimeLookup webClipboardRuntimeConfig(Context context) {
        try {
            SharedPreferences preferences = WebClipboardPreferences.preferences(context);
            if (!WebClipboardPreferences.isEnabled(preferences)) {
                return RuntimeLookup.unavailable();
            }
            String loopbackIngressToken =
                    WebClipboardPreferences.getLoopbackIngressToken(preferences);
            if (loopbackIngressToken == null || loopbackIngressToken.isEmpty()) {
                return RuntimeLookup.unavailable();
            }
            return RuntimeLookup.available(new RuntimeConfig(
                    WebClipboardPreferences.readPort(preferences),
                    loopbackIngressToken));
        } catch (Throwable ignored) {
            return RuntimeLookup.unavailable();
        }
    }

    private static ClipData currentClipboardData(ClipboardManager clipboardManager) {
        try {
            if (!clipboardManager.hasPrimaryClip()) {
                return null;
            }
            return clipboardManager.getPrimaryClip();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isWebClipboardEcho(ClipData clipData) {
        try {
            ClipDescription description = clipData.getDescription();
            CharSequence label = description == null ? null : description.getLabel();
            return ClipboardSyncIngressContract.WEB_CLIPBOARD_LABEL.contentEquals(
                    label == null ? "" : label);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static CharSequence currentClipboardText(Context context, ClipData clipData) {
        try {
            if (clipData == null || clipData.getItemCount() <= 0) {
                return null;
            }
            return clipData.getItemAt(0).coerceToText(context);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                builder.append(Character.forDigit((current >> 4) & 0xF, 16));
                builder.append(Character.forDigit(current & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static final class RuntimeConfig {
        final int port;
        final String loopbackIngressToken;

        RuntimeConfig(int port, String loopbackIngressToken) {
            this.port = port;
            this.loopbackIngressToken = loopbackIngressToken;
        }
    }

    private static final class RuntimeLookup {
        final RuntimeConfig config;

        private RuntimeLookup(RuntimeConfig config) {
            this.config = config;
        }

        static RuntimeLookup available(RuntimeConfig config) {
            return new RuntimeLookup(config);
        }

        static RuntimeLookup unavailable() {
            return new RuntimeLookup(null);
        }
    }
}
