package dev.jason.gboardpatches.extension.webclipboard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsActivity;

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
    private static final int MAX_INCOMING_IMAGE_FILES = 20;
    private static final String INCOMING_IMAGE_DIR_NAME = "clipboard_image";
    static final String INCOMING_IMAGE_PREFIX = "web-clip-";
    private static final long DUPLICATE_SUPPRESSION_WINDOW_MS = 750L;
    private static final AtomicLong START_COMMAND_SEQUENCE = new AtomicLong(1L);
    private static final AtomicLong INCOMING_IMAGE_SEQUENCE = new AtomicLong(1L);
    private static final AtomicLong LAST_HISTORY_CLIP_ID = new AtomicLong(0L);
    private static volatile ClipboardSyncService activeService;
    private static volatile ClipboardSyncWebPortal activePortal;

    private final Object incomingImageFileLock = new Object();

    private ClipboardManager serviceClipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener serviceClipboardListener;
    private boolean foregroundRefused;
    private ClipboardSyncWebPortal webPortal;
    private int currentPortalPort;
    private boolean currentPairingRequired;
    private String currentPairingCode = WebClipboardPreferences.DEFAULT_PAIRING_CODE;
    private String currentLoopbackIngressToken =
            WebClipboardPreferences.DEFAULT_LOOPBACK_INGRESS_TOKEN;
    private String lastPublishedText = "";
    private long lastPublishedAtElapsedMs;
    private String lastPublishedImageHash = "";
    private long lastPublishedImageAtElapsedMs;
    private final WebClipboardEchoSuppressor webClipboardEchoSuppressor =
            new WebClipboardEchoSuppressor(DUPLICATE_SUPPRESSION_WINDOW_MS);
    private final WebClipboardEchoSuppressor webImageEchoSuppressor =
            new WebClipboardEchoSuppressor(DUPLICATE_SUPPRESSION_WINDOW_MS);
    private final ExecutorService clipboardPublishExecutor = Executors.newSingleThreadExecutor();

    public static void startOrUpdate(Context context) {
        if (context == null || !WebClipboardLocalNetworkPermission.isGranted(context)) {
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
            try {
                startForeground("Starting Web Clipboard");
            } catch (RuntimeException failure) {
                Log.w(TAG, LOG_PREFIX + " foreground start refused; stopping", failure);
                foregroundRefused = true;
                stopSelf();
                return;
            }
            WebClipboardTileController.markRuntimeActive(this, false);
            registerClipboardListener();
            refreshWebPortal();
        } catch (Throwable throwable) {
            Log.w(TAG, "Web Clipboard service onCreate failed", throwable);
            stopSelf();
        }
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        try {
            Log.w(TAG, LOG_PREFIX + " foreground service timeout type=" + fgsType + "; stopping");
            stopWebPortal();
        } catch (Throwable ignored) {
        }
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            String action = intent != null ? intent.getAction() : ACTION_START_OR_UPDATE;
            long commandSeq = START_COMMAND_SEQUENCE.getAndIncrement();
            if (foregroundRefused) {
                stopSelf();
                return START_NOT_STICKY;
            }
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
        if (!WebClipboardLocalNetworkPermission.isGranted(this)) {
            stopWebPortal();
            WebClipboardTileController.markRuntimeActive(this,false);
            stopSelf();return;
        }
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
        ClipboardSyncWebPortal.ClipboardBridge bridge = new ClipboardSyncWebPortal.ClipboardBridge() {
            @Override
            public void applyDesktopClipboard(String text) {
                applyWebClipboardToPhone(text);
            }

            @Override
            public void applyDesktopClipboardImage(byte[] imageBytes, String mimeType,
                    String fileName) {
                applyWebClipboardImageToPhone(imageBytes, mimeType, fileName);
            }
        };
        File mediaCacheRoot = new File(getCacheDir(), "web-clipboard-media");
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                port,
                bridge,
                ClipboardSyncWebPortal.WebAssets.from(this),
                mediaCacheRoot,
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
            try {
                ClipData clipData = currentPrimaryClip(clipboardManager);
                if (clipData == null || isWebClipboardEcho(clipData)) {
                    return;
                }
                Uri imageUri = extractImageClipUri(clipData);
                if (imageUri != null) {
                    String mimeType = resolveImageClipMimeType(clipData, imageUri);
                    Log.i(TAG, LOG_PREFIX + " service clipboard listener fired image"
                            + ", portal=" + describePortal(webPortal));
                    publishClipboardImageUriToPortal(imageUri, mimeType);
                    return;
                }
                CharSequence currentText = currentClipboardText(clipboardManager);
                if (currentText != null) {
                    Log.i(TAG, LOG_PREFIX + " service clipboard listener fired"
                            + " len=" + currentText.length()
                            + ", portal=" + describePortal(webPortal));
                    publishClipboardToPortalIfNotWebEcho(currentText.toString());
                }
            } catch (Throwable throwable) {
                Log.w(TAG, LOG_PREFIX + " clipboard listener callback failed", throwable);
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
    }

    private void applyWebClipboardImageToPhone(byte[] imageBytes, String mimeType,
            String fileName) {
        if (imageBytes == null || imageBytes.length == 0
                || imageBytes.length > WebClipboardMediaStore.MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Invalid clipboard image payload");
        }
        String resolvedMime = WebClipboardMediaStore.detectMimeType(imageBytes, mimeType);
        if (resolvedMime == null) {
            throw new IllegalArgumentException("Unsupported clipboard image type");
        }
        ClipboardManager clipboardManager = serviceClipboardManager;
        if (clipboardManager == null) {
            clipboardManager = getSystemService(ClipboardManager.class);
        }
        if (clipboardManager == null) {
            throw new IllegalStateException("ClipboardManager unavailable");
        }
        String sha256 = WebClipboardMediaStore.sha256Hex(imageBytes);
        long clipTimestampMs = nextMonotonicClipTimestampMs(System.currentTimeMillis());
        File targetFile = writeIncomingImageFile(imageBytes, resolvedMime, clipTimestampMs);
        Uri contentUri = buildIncomingImageFileProviderUri(targetFile);
        ClipDescription description = new ClipDescription(
                ClipboardSyncIngressContract.WEB_CLIPBOARD_LABEL,
                new String[] { resolvedMime });
        ClipData clipData = new ClipData(description, new ClipData.Item(contentUri));
        try {
            webImageEchoSuppressor.markWebApplied(sha256, SystemClock.elapsedRealtime());
            clipboardManager.setPrimaryClip(clipData);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to apply web clipboard image to phone", throwable);
            throw new IllegalStateException("Failed to apply web clipboard image", throwable);
        }
        insertImageIntoClipboardHistoryBestEffort(contentUri, clipTimestampMs);
        updateNotification("Image applied from Web");
    }

    static long nextMonotonicClipTimestampMs(long nowMs) {
        long safeNow = Math.max(1L, nowMs);
        return LAST_HISTORY_CLIP_ID.updateAndGet(previous -> Math.max(safeNow, previous + 1L));
    }

    private File writeIncomingImageFile(byte[] imageBytes, String mimeType, long timestampMs) {
        synchronized (incomingImageFileLock) {
            File dir = new File(getFilesDir(), INCOMING_IMAGE_DIR_NAME);
            if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
                throw new IllegalStateException("Unable to create incoming image directory");
            }
            String ext = WebClipboardMediaStore.extensionForMimeType(mimeType);
            String stem = INCOMING_IMAGE_PREFIX + timestampMs + "-"
                    + INCOMING_IMAGE_SEQUENCE.getAndIncrement();
            File targetFile = new File(dir, stem + ext);
            try (FileOutputStream output = new FileOutputStream(targetFile)) {
                output.write(imageBytes);
                output.flush();
            } catch (Throwable throwable) {
                throw new IllegalStateException("Failed to write incoming image file", throwable);
            }
            pruneIncomingImageFilesLocked(dir, targetFile);
            return targetFile;
        }
    }

    private Uri buildIncomingImageFileProviderUri(File targetFile) {
        return new Uri.Builder()
                .scheme("content")
                .authority(getPackageName() + ".fileprovider")
                .appendPath(INCOMING_IMAGE_DIR_NAME)
                .appendPath(targetFile.getName())
                .build();
    }

    private void insertImageIntoClipboardHistoryBestEffort(Uri contentUri, long timestampMs) {
        if (contentUri == null) {
            return;
        }
        try {
            Uri clipsUri = new Uri.Builder()
                    .scheme("content")
                    .authority(getPackageName() + ".clipboard_content")
                    .appendPath("clips")
                    .build();
            ContentValues values = new ContentValues();
            values.put("_id", timestampMs);
            values.put("text", "");
            values.put("html_text", "");
            values.put("timestamp", timestampMs);
            values.put("item_type", 0);
            values.put("entity_type", 0);
            values.put("uri", contentUri.toString());
            values.put("sensitive", false);
            getContentResolver().insert(clipsUri, values);
        } catch (Throwable throwable) {
            Log.w(TAG, LOG_PREFIX + " clipboard history image insert skipped", throwable);
        }
    }

    private void pruneIncomingImageFilesLocked(File dir, File keepFile) {
        File[] files = dir.listFiles((currentDir, name) ->
                name != null && name.startsWith(INCOMING_IMAGE_PREFIX));
        if (files == null || files.length <= MAX_INCOMING_IMAGE_FILES) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified)
                .thenComparing(File::getName)
                .reversed());
        String keepName = keepFile == null ? "" : keepFile.getName();
        int kept = 0;
        for (File file : files) {
            if (keepName.equals(file.getName()) || kept < MAX_INCOMING_IMAGE_FILES) {
                kept++;
                continue;
            }
            try {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            } catch (Throwable ignored) {
                // Best effort cleanup.
            }
        }
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
        ClipData primaryClip = currentPrimaryClip(clipboardManager);
        Uri imageUri = extractImageClipUri(primaryClip);
        if (imageUri != null) {
            String mimeType = resolveImageClipMimeType(primaryClip, imageUri);
            publishClipboardImageUriToPortal(imageUri, mimeType);
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

    private void publishClipboardImageUriToPortal(Uri imageUri, String declaredMimeType) {
        if (imageUri == null) {
            return;
        }
        try {
            clipboardPublishExecutor.execute(
                    () -> publishClipboardImageUriInternal(imageUri, declaredMimeType));
        } catch (RejectedExecutionException ignored) {
            Log.i(TAG, LOG_PREFIX + " portal image publish ignored during teardown");
        }
    }

    private void publishClipboardImageUriInternal(Uri imageUri, String declaredMimeType) {
        try {
            ClipboardSyncWebPortal portal = webPortal;
            if (portal == null || imageUri == null) {
                return;
            }
            byte[] bytes = readUriBytesBounded(imageUri, WebClipboardMediaStore.MAX_IMAGE_BYTES);
            if (bytes == null || bytes.length == 0) {
                return;
            }
            String resolvedMime = WebClipboardMediaStore.detectMimeType(bytes, declaredMimeType);
            if (resolvedMime == null) {
                return;
            }
            String sha256 = WebClipboardMediaStore.sha256Hex(bytes);
            long now = SystemClock.elapsedRealtime();
            if (webImageEchoSuppressor.shouldSuppressClipboardEvent(sha256, now)) {
                Log.i(TAG, LOG_PREFIX + " service clipboard listener suppressed web image echo"
                        + " bytes=" + bytes.length);
                return;
            }
            if (sha256.equals(lastPublishedImageHash)
                    && now - lastPublishedImageAtElapsedMs <= DUPLICATE_SUPPRESSION_WINDOW_MS) {
                return;
            }
            lastPublishedImageHash = sha256;
            lastPublishedImageAtElapsedMs = now;
            String fileName = imageUri.getLastPathSegment();
            if (portal.publishPhoneClipboardImage(bytes, resolvedMime, fileName)) {
                updateNotification("Image available");
            }
        } catch (Throwable throwable) {
            Log.w(TAG, LOG_PREFIX + " failed to publish phone clipboard image", throwable);
        }
    }

    private ClipData currentPrimaryClip(ClipboardManager clipboardManager) {
        try {
            if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
                return null;
            }
            return clipboardManager.getPrimaryClip();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isWebClipboardEcho(ClipData clipData) {
        try {
            ClipDescription description = clipData == null ? null : clipData.getDescription();
            CharSequence label = description == null ? null : description.getLabel();
            return ClipboardSyncIngressContract.WEB_CLIPBOARD_LABEL.contentEquals(
                    label == null ? "" : label);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Uri extractImageClipUri(ClipData clipData) {
        try {
            if (clipData == null || clipData.getItemCount() <= 0) {
                return null;
            }
            ClipData.Item item = clipData.getItemAt(0);
            Uri uri = item == null ? null : item.getUri();
            if (uri == null || !"content".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            String mimeType = resolveImageClipMimeType(clipData, uri);
            return mimeType != null && mimeType.toLowerCase(Locale.US).startsWith("image/")
                    ? uri
                    : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String resolveImageClipMimeType(ClipData clipData, Uri uri) {
        try {
            ClipDescription description = clipData == null ? null : clipData.getDescription();
            if (description != null) {
                int count = description.getMimeTypeCount();
                for (int index = 0; index < count; index++) {
                    String mime = description.getMimeType(index);
                    if (mime != null && mime.toLowerCase(Locale.US).startsWith("image/")) {
                        return mime;
                    }
                }
            }
            if (uri != null) {
                String resolved = getContentResolver().getType(uri);
                if (resolved != null && resolved.toLowerCase(Locale.US).startsWith("image/")) {
                    return resolved;
                }
            }
        } catch (Throwable ignored) {
            // Best effort.
        }
        return null;
    }

    private byte[] readUriBytesBounded(Uri uri, int maxBytes) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return null;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16_384];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes) {
                    return null;
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (Throwable ignored) {
            return null;
        }
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
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
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

