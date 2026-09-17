package dev.jason.gboardpatches.extension.webclipboard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsActivity;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class ClipboardSyncService extends Service {
    private static final String TAG = "GboardWebClipboard";
    private static final String LOG_PREFIX = "[gboard-clipboard-sync]";
    private static final String SERVICE_LABEL = "Web Clipboard";
    private static final String BIND_FAILED_TOAST =
            "Web Clipboard could not start on this port.";
    private static final String ACTION_START_OR_UPDATE =
            "dev.jason.gboardpatches.extension.webclipboard.action.START_OR_UPDATE";
    private static final String ACTION_STOP =
            "dev.jason.gboardpatches.extension.webclipboard.action.STOP";
    private static final String ACTION_LOCAL_CLIP =
            "dev.jason.gboardpatches.extension.webclipboard.action.LOCAL_CLIP";
    private static final String EXTRA_TEXT = "text";
    private static final String NOTIFICATION_CHANNEL_ID = "web_clipboard";
    private static final int NOTIFICATION_ID = 1002;
    private static final long DUPLICATE_SUPPRESSION_WINDOW_MS = 750L;
    private static final AtomicLong START_COMMAND_SEQUENCE = new AtomicLong(1L);
    private static volatile ClipboardSyncService activeService;
    private static volatile ClipboardSyncWebPortal activePortal;

    private ClipboardManager serviceClipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener serviceClipboardListener;
    private ClipboardSyncWebPortal webPortal;
    private int currentPortalPort;
    private boolean currentPairingRequired;
    private String currentPairingCode = WebClipboardPreferences.DEFAULT_PAIRING_CODE;
    private String currentLoopbackIngressToken =
            WebClipboardPreferences.DEFAULT_LOOPBACK_INGRESS_TOKEN;
    private String lastPublishedText = "";
    private long lastPublishedAtElapsedMs;
    private final WebClipboardEchoSuppressor webClipboardEchoSuppressor =
            new WebClipboardEchoSuppressor(DUPLICATE_SUPPRESSION_WINDOW_MS);
    private final ExecutorService clipboardPublishExecutor = Executors.newSingleThreadExecutor();

    public static void startOrUpdate(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        Intent intent = new Intent(appContext, ClipboardSyncService.class);
        intent.setAction(ACTION_START_OR_UPDATE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent);
            } else {
                appContext.startService(intent);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to start Web Clipboard service", throwable);
        }
    }

    public static void requestStop(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        Intent intent = new Intent(appContext, ClipboardSyncService.class);
        intent.setAction(ACTION_STOP);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent);
            } else {
                appContext.startService(intent);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to request Web Clipboard service stop", throwable);
            appContext.stopService(new Intent(appContext, ClipboardSyncService.class));
        }
    }

    public static void submitLocalClipboard(Context context, String text) {
        if (context == null || !hasClipboardText(text)) {
            return;
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        Intent intent = new Intent(appContext, ClipboardSyncService.class);
        intent.setAction(ACTION_LOCAL_CLIP);
        intent.putExtra(EXTRA_TEXT, text);
        try {
            Log.i(TAG, LOG_PREFIX + " submit local clipboard len=" + text.length());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent);
            } else {
                appContext.startService(intent);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to submit local clipboard event", throwable);
        }
    }

    public static List<ClipboardSyncWebPortal.ConnectedClientSnapshot>
            getConnectedClientSnapshots() {
        ClipboardSyncWebPortal portal = activePortal;
        return portal == null ? Collections.emptyList() : portal.getConnectedClients();
    }

    public static boolean kickConnectedClient(String clientId) {
        ClipboardSyncWebPortal portal = activePortal;
        return portal != null && portal.kickClient(clientId);
    }

    public static boolean publishPhoneClipboardIfActive(String text) {
        ClipboardSyncService service = activeService;
        if (service == null || !hasClipboardText(text)) {
            return false;
        }
        if (service.webPortal == null) {
            return false;
        }
        service.publishClipboardToPortal(text);
        return true;
    }

    @Override
    public void onCreate() {
        try {
            super.onCreate();
            activeService = this;
            Log.i(TAG, LOG_PREFIX + " service onCreate");
            ensureNotificationChannel();
            startForeground("Starting Web Clipboard");
            WebClipboardTileController.markRuntimeActive(this, false);
            registerClipboardListener();
            refreshWebPortal();
        } catch (Throwable throwable) {
            Log.w(TAG, "Web Clipboard service onCreate failed", throwable);
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            String action = intent != null ? intent.getAction() : ACTION_START_OR_UPDATE;
            long commandSeq = START_COMMAND_SEQUENCE.getAndIncrement();
            Log.i(TAG, LOG_PREFIX + " onStartCommand"
                    + " seq=" + commandSeq
                    + ", action=" + action
                    + ", startId=" + startId
                    + ", portal=" + describePortal(webPortal));
            if (ACTION_STOP.equals(action)) {
                SharedPreferences preferences = WebClipboardPreferences.preferences(this);
                Log.i(TAG, LOG_PREFIX + " stop requested"
                        + " seq=" + commandSeq
                        + ", enabled=" + WebClipboardPreferences.isEnabled(preferences));
                if (!WebClipboardTileController.shouldStopService(preferences)) {
                    Log.i(TAG, LOG_PREFIX + " stop ignored because Web Clipboard was re-enabled"
                            + " seq=" + commandSeq);
                    refreshWebPortal();
                    return START_STICKY;
                }
                stopSelf();
                return START_NOT_STICKY;
            }
            if (ACTION_LOCAL_CLIP.equals(action)) {
                String text = intent != null ? intent.getStringExtra(EXTRA_TEXT) : null;
                if (hasClipboardText(text)) {
                    Log.i(TAG, LOG_PREFIX + " service local clip received action=" + action
                            + ", len=" + text.length()
                            + ", seq=" + commandSeq);
                    publishClipboardToPortal(text);
                }
            }
            refreshWebPortal();
            return START_STICKY;
        } catch (Throwable throwable) {
            Log.w(TAG, "Web Clipboard service onStartCommand failed", throwable);
            return START_NOT_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        try {
            Log.i(TAG, LOG_PREFIX + " service onDestroy portal=" + describePortal(webPortal));
            if (activeService == this) {
                activeService = null;
            }
            unregisterClipboardListener();
            clipboardPublishExecutor.shutdownNow();
            stopWebPortal();
            WebClipboardTileController.requestTileRefresh(this);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Web Clipboard service onDestroy cleanup failed", throwable);
        } finally {
            super.onDestroy();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void refreshWebPortal() {
        SharedPreferences preferences = WebClipboardPreferences.preferences(this);
        boolean enabled = WebClipboardPreferences.isEnabled(preferences);
        int desiredPort = WebClipboardPreferences.getPort(preferences);
        boolean pairingRequired = WebClipboardPreferences.isPairingRequired(preferences);
        String pairingCode = WebClipboardPreferences.getPairingCode(preferences);
        String loopbackIngressToken = WebClipboardPreferences.getLoopbackIngressToken(preferences);
        boolean needsRestart = webPortal == null
                || currentPortalPort != desiredPort
                || currentPairingRequired != pairingRequired
                || !currentPairingCode.equals(pairingCode)
                || !currentLoopbackIngressToken.equals(loopbackIngressToken);
        Log.i(TAG, LOG_PREFIX + " refresh portal"
                + " enabled=" + enabled
                + ", desiredPort=" + desiredPort
                + ", pairingRequired=" + pairingRequired
                + ", pairingCode=" + summarizePairingCode(pairingCode)
                + ", loopbackToken=" + summarizeLoopbackToken(loopbackIngressToken)
                + ", currentPortal=" + describePortal(webPortal)
                + ", currentPort=" + currentPortalPort
                + ", currentPairingRequired=" + currentPairingRequired
                + ", currentPairingCode=" + summarizePairingCode(currentPairingCode)
                + ", currentLoopbackToken=" + summarizeLoopbackToken(currentLoopbackIngressToken)
                + ", needsRestart=" + needsRestart);
        if (!enabled) {
            Log.i(TAG, LOG_PREFIX + " refresh portal stopping because feature disabled");
            stopWebPortal();
            stopSelf();
            return;
        }
        if (needsRestart) {
            Log.i(TAG, LOG_PREFIX + " refresh portal restarting current=" + describePortal(webPortal));
            stopWebPortal();
            if (!startWebPortal(desiredPort, pairingRequired, pairingCode, loopbackIngressToken)) {
                return;
            }
        } else {
            WebClipboardTileController.markRuntimeActive(this, true);
        }
        updateNotification("Open " + firstUrl(desiredPort));
        WebClipboardTileController.requestTileRefresh(this);
    }

    private boolean startWebPortal(int port, boolean pairingRequired, String pairingCode,
            String loopbackIngressToken) {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                port,
                this::applyWebClipboardToPhone,
                ClipboardSyncWebPortal.WebAssets.from(this),
                new ClipboardSyncWebPortal.SecurityConfig(
                        pairingRequired,
                        pairingCode,
                        loopbackIngressToken));
        try {
            portal.start();
            webPortal = portal;
            activePortal = portal;
            currentPortalPort = port;
            currentPairingRequired = pairingRequired;
            currentPairingCode = pairingCode;
            currentLoopbackIngressToken = loopbackIngressToken;
            seedPortalFromCurrentClipboard(portal);
            WebClipboardTileController.markRuntimeActive(this, true);
            Log.i(TAG, LOG_PREFIX + " startWebPortal success"
                    + " instance=" + describePortal(portal)
                    + ", port=" + port
                    + ", pairingRequired=" + pairingRequired
                    + ", pairingCode=" + summarizePairingCode(pairingCode)
                    + ", loopbackToken=" + summarizeLoopbackToken(loopbackIngressToken));
            return true;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to start Web Clipboard portal", throwable);
            portal.stop();
            handlePortalStartFailure();
            return false;
        }
    }

    private void handlePortalStartFailure() {
        SharedPreferences preferences = WebClipboardPreferences.preferences(this);
        WebClipboardTileController.setEnabled(preferences, false);
        WebClipboardTileController.setRuntimeActive(preferences, false);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(
                () -> Toast.makeText(getApplicationContext(), BIND_FAILED_TOAST,
                        Toast.LENGTH_LONG).show());
        WebClipboardTileController.requestTileRefresh(this);
        stopSelf();
    }

    private void stopWebPortal() {
        ClipboardSyncWebPortal portal = webPortal;
        Log.i(TAG, LOG_PREFIX + " stopWebPortal"
                + " current=" + describePortal(portal)
                + ", active=" + describePortal(activePortal));
        webPortal = null;
        if (activePortal == portal) {
            activePortal = null;
        }
        currentPortalPort = 0;
        currentPairingRequired = false;
        currentPairingCode = WebClipboardPreferences.DEFAULT_PAIRING_CODE;
        currentLoopbackIngressToken = WebClipboardPreferences.DEFAULT_LOOPBACK_INGRESS_TOKEN;
        if (portal != null) {
            portal.stop();
        }
        WebClipboardTileController.markRuntimeActive(this, false);
    }

    private void registerClipboardListener() {
        ClipboardManager clipboardManager = getSystemService(ClipboardManager.class);
        if (clipboardManager == null) {
            return;
        }
        ClipboardManager.OnPrimaryClipChangedListener listener = () -> {
            CharSequence currentText = currentClipboardText(clipboardManager);
            if (currentText != null) {
                Log.i(TAG, LOG_PREFIX + " service clipboard listener fired"
                        + " len=" + currentText.length()
                        + ", portal=" + describePortal(webPortal));
                publishClipboardToPortalIfNotWebEcho(currentText.toString());
            }
        };
        try {
            clipboardManager.addPrimaryClipChangedListener(listener);
            serviceClipboardManager = clipboardManager;
            serviceClipboardListener = listener;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to register clipboard listener", throwable);
        }
    }

    private void unregisterClipboardListener() {
        if (serviceClipboardManager == null || serviceClipboardListener == null) {
            return;
        }
        try {
            serviceClipboardManager.removePrimaryClipChangedListener(serviceClipboardListener);
        } catch (Throwable ignored) {
            // Best effort.
        }
        serviceClipboardManager = null;
        serviceClipboardListener = null;
    }

    private void applyWebClipboardToPhone(String text) {
        if (!hasClipboardText(text)) {
            return;
        }
        ClipboardManager clipboardManager = serviceClipboardManager;
        if (clipboardManager == null) {
            clipboardManager = getSystemService(ClipboardManager.class);
        }
        if (clipboardManager == null) {
            return;
        }
        try {
            webClipboardEchoSuppressor.markWebApplied(text, SystemClock.elapsedRealtime());
            clipboardManager.setPrimaryClip(ClipData.newPlainText(
                    ClipboardSyncIngressContract.WEB_CLIPBOARD_LABEL,
                    text));
        } catch (Throwable throwable) {
            webClipboardEchoSuppressor.clearWebApplied();
            Log.w(TAG, "Failed to apply web clipboard to phone", throwable);
            return;
        }
        updateNotification("Clipboard applied from Web");

        SharedPreferences preferences = WebClipboardPreferences.preferences(this);
        if (WebClipboardPreferences.isToastEnabled(preferences)) {
            showAppliedToast(text);
        }
    }

    private void showAppliedToast(String text) {
        String preview = smartClip(text, 30);
        String message = GboardSettingsText.format(
                this,
                R.string.gboard_patches_web_clipboard_toast_applied,
                preview);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(
                () -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }

    private static String smartClip(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        int clipIndex = maxLength;
        int searchLimit = Math.max(0, maxLength - 10);
        for (int i = maxLength; i >= searchLimit; i--) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == '-' || c == '_' || c == '/' || c == ':') {
                clipIndex = i;
                break;
            }
        }
        String clipped = text.substring(0, clipIndex);
        int last = clipped.length() - 1;
        while (last >= 0 && Character.isWhitespace(clipped.charAt(last))) {
            last--;
        }
        return clipped.substring(0, last + 1) + "\u2026";
    }

    private void publishClipboardToPortalIfNotWebEcho(String text) {
        if (webClipboardEchoSuppressor.shouldSuppressClipboardEvent(
                text,
                SystemClock.elapsedRealtime())) {
            Log.i(TAG, LOG_PREFIX + " service clipboard listener suppressed web echo"
                    + " len=" + text.length());
            return;
        }
        publishClipboardToPortal(text);
    }

    private void seedPortalFromCurrentClipboard(ClipboardSyncWebPortal portal) {
        if (portal == null) {
            return;
        }
        ClipboardManager clipboardManager = serviceClipboardManager;
        if (clipboardManager == null) {
            clipboardManager = getSystemService(ClipboardManager.class);
        }
        if (clipboardManager == null) {
            return;
        }
        CharSequence currentText = currentClipboardText(clipboardManager);
        if (currentText == null || currentText.length() == 0) {
            return;
        }
        String text = currentText.toString();
        lastPublishedText = text;
        lastPublishedAtElapsedMs = SystemClock.elapsedRealtime();
        portal.publishPhoneClipboard(text);
    }

    private void publishClipboardToPortal(String text) {
        if (!hasClipboardText(text)) {
            return;
        }
        try {
            clipboardPublishExecutor.execute(() -> publishClipboardToPortalInternal(text));
        } catch (RejectedExecutionException ignored) {
            Log.i(TAG, LOG_PREFIX + " portal publish ignored during teardown");
        }
    }

    private void publishClipboardToPortalInternal(String text) {
        ClipboardSyncWebPortal portal = webPortal;
        if (portal == null || !hasClipboardText(text)) {
            Log.i(TAG, LOG_PREFIX + " portal publish skipped"
                    + ", portalReady=" + (portal != null)
                    + ", hasText=" + hasClipboardText(text));
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (text.equals(lastPublishedText)
                && now - lastPublishedAtElapsedMs <= DUPLICATE_SUPPRESSION_WINDOW_MS) {
            Log.i(TAG, LOG_PREFIX + " portal publish suppressed duplicate len=" + text.length());
            return;
        }
        lastPublishedText = text;
        lastPublishedAtElapsedMs = now;
        Log.i(TAG, LOG_PREFIX + " service publishing phone clipboard"
                + " len=" + text.length()
                + ", portal=" + describePortal(portal));
        portal.publishPhoneClipboard(text);
        updateNotification("Clipboard available");
    }

    private String describePortal(ClipboardSyncWebPortal portal) {
        if (portal == null) {
            return "none";
        }
        return portal.getPortalInstanceId();
    }

    private String summarizePairingCode(String pairingCode) {
        if (pairingCode == null || pairingCode.isBlank()) {
            return "blank";
        }
        String trimmed = pairingCode.trim();
        if (trimmed.length() <= 2) {
            return "len=" + trimmed.length();
        }
        return "len=" + trimmed.length() + ", suffix=" + trimmed.substring(trimmed.length() - 2);
    }

    private String summarizeLoopbackToken(String loopbackIngressToken) {
        if (loopbackIngressToken == null || loopbackIngressToken.isEmpty()) {
            return "empty";
        }
        int suffixStart = Math.max(0, loopbackIngressToken.length() - 4);
        return "len=" + loopbackIngressToken.length()
                + ", suffix=" + loopbackIngressToken.substring(suffixStart);
    }

    private CharSequence currentClipboardText(ClipboardManager clipboardManager) {
        try {
            if (!clipboardManager.hasPrimaryClip()) {
                return null;
            }
            ClipData clipData = clipboardManager.getPrimaryClip();
            if (clipData == null || clipData.getItemCount() <= 0) {
                return null;
            }
            return clipData.getItemAt(0).coerceToText(this);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void startForeground(String statusText) {
        Notification notification = buildNotification(statusText);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            return;
        }
        startForeground(NOTIFICATION_ID, notification);
    }

    private void updateNotification(String statusText) {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return;
        }
        notificationManager.notify(NOTIFICATION_ID, buildNotification(statusText));
    }

    private Notification buildNotification(String statusText) {
        Intent settingsIntent = new Intent(this, GboardPatchesSettingsActivity.class);
        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder.setContentTitle(SERVICE_LABEL)
                .setContentText(statusText)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                SERVICE_LABEL,
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Foreground status for the phone-hosted Web Clipboard service.");
        notificationManager.createNotificationChannel(channel);
    }

    private String firstUrl(int port) {
        List<String> urls = WebClipboardEndpointResolver.resolveUrls(port);
        return urls.isEmpty() ? "http://<phone LAN IP>:" + port + "/" : urls.get(0);
    }

    private static boolean hasClipboardText(String text) {
        return text != null && !text.isEmpty();
    }

}

