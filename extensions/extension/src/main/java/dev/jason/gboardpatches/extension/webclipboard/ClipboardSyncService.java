package dev.jason.gboardpatches.extension.webclipboard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
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
    private static final long DUPLICATE_SUPPRESSION_WINDOW_MS = 750L;
    private static final AtomicLong START_COMMAND_SEQUENCE = new AtomicLong(1L);
    private static volatile ClipboardSyncService activeService;
    private static volatile ClipboardSyncWebPortal activePortal;

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
    private volatile Uri latestPhoneImageUri;
    private volatile String latestPhoneImageMime = "image/png";
    private volatile File latestPhoneImageFile;
    private ContentObserver screenshotMediaObserver;
    private long lastScreenshotDateAdded = 0L;
    private final WebClipboardEchoSuppressor webClipboardEchoSuppressor =
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
            unregisterMediaObserver();
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
        registerMediaObserverIfEnabled();
        updateNotification("Open " + firstUrl(desiredPort));
        WebClipboardTileController.requestTileRefresh(this);
    }

    private boolean startWebPortal(int port, boolean pairingRequired, String pairingCode,
            String loopbackIngressToken) {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                port,
                new ServiceClipboardBridge(),
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
        unregisterMediaObserver();
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
                ClipboardManager cm = serviceClipboardManager != null
                        ? serviceClipboardManager
                        : getSystemService(ClipboardManager.class);
                if (cm == null || !cm.hasPrimaryClip()) {
                    return;
                }
                ClipData clipData = cm.getPrimaryClip();
                if (clipData == null) {
                    return;
                }
                if (isWebClipboardEcho(clipData)) {
                    Log.i(TAG, LOG_PREFIX + " service clipboard listener suppressed web echo");
                    return;
                }
                SharedPreferences preferences = WebClipboardPreferences.preferences(this);
                if (WebClipboardPreferences.isImageSyncEnabled(preferences)) {
                    Uri imgUri = resolveImageClip(clipData);
                    String imgMime = resolveClipMime(clipData, imgUri);
                    if (imgMime != null && imgUri != null) {
                        Log.i(TAG, LOG_PREFIX + " service clipboard listener detected image clip: " + imgMime);
                        handlePhoneImageClip(imgUri, imgMime);
                        return;
                    }
                }
                CharSequence currentText = currentClipboardText(cm);
                if (currentText != null) {
                    Log.i(TAG, LOG_PREFIX + " service clipboard listener fired"
                            + " len=" + currentText.length()
                            + ", portal=" + describePortal(webPortal));
                    publishClipboardToPortalIfNotWebEcho(currentText.toString());
                }
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed handling primary clip change", throwable);
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
        if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
            return;
        }
        try {
            ClipData clipData = clipboardManager.getPrimaryClip();
            if (clipData == null) {
                return;
            }
            SharedPreferences preferences = WebClipboardPreferences.preferences(this);
            if (WebClipboardPreferences.isImageSyncEnabled(preferences)) {
                Uri imgUri = resolveImageClip(clipData);
                String imgMime = resolveClipMime(clipData, imgUri);
                if (imgMime != null && imgUri != null) {
                    handlePhoneImageClip(imgUri, imgMime);
                    return;
                }
            }
            CharSequence currentText = currentClipboardText(clipboardManager);
            if (currentText == null || currentText.length() == 0) {
                return;
            }
            String text = currentText.toString();
            lastPublishedText = text;
            lastPublishedAtElapsedMs = SystemClock.elapsedRealtime();
            portal.publishPhoneClipboard(text);
        } catch (Throwable ignored) {
        }
    }

    private final class ServiceClipboardBridge implements ClipboardSyncWebPortal.ClipboardBridge {
        @Override
        public void applyDesktopClipboard(String text) {
            applyWebClipboardToPhone(text);
        }

        @Override
        public boolean applyDesktopImage(InputStream stream, String mimeType, long contentLength) {
            return applyWebImageToPhone(stream, mimeType, contentLength);
        }

        @Override
        public InputStream openPhoneImageStream() {
            try {
                if (latestPhoneImageFile != null && latestPhoneImageFile.exists()) {
                    return new FileInputStream(latestPhoneImageFile);
                }
                if (latestPhoneImageUri != null) {
                    if ("content".equalsIgnoreCase(latestPhoneImageUri.getScheme())) {
                        return getContentResolver().openInputStream(latestPhoneImageUri);
                    } else if ("file".equalsIgnoreCase(latestPhoneImageUri.getScheme()) && latestPhoneImageUri.getPath() != null) {
                        return new FileInputStream(new File(latestPhoneImageUri.getPath()));
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to open phone image stream", t);
            }
            return null;
        }

        @Override
        public long getPhoneImageLength() {
            if (latestPhoneImageFile != null && latestPhoneImageFile.exists()) {
                return latestPhoneImageFile.length();
            }
            if (latestPhoneImageUri != null) {
                if ("content".equalsIgnoreCase(latestPhoneImageUri.getScheme())) {
                    try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(
                            latestPhoneImageUri, "r")) {
                        if (pfd != null) {
                            return pfd.getStatSize();
                        }
                    } catch (Throwable ignored) {
                    }
                } else if ("file".equalsIgnoreCase(latestPhoneImageUri.getScheme()) && latestPhoneImageUri.getPath() != null) {
                    File f = new File(latestPhoneImageUri.getPath());
                    if (f.exists()) {
                        return f.length();
                    }
                }
            }
            return 0L;
        }

        @Override
        public String getPhoneImageMimeType() {
            return latestPhoneImageMime != null ? latestPhoneImageMime : "image/png";
        }

        @Override
        public boolean hasPhoneImage() {
            return (latestPhoneImageFile != null && latestPhoneImageFile.exists())
                    || (latestPhoneImageUri != null && ("content".equalsIgnoreCase(latestPhoneImageUri.getScheme())
                            || "file".equalsIgnoreCase(latestPhoneImageUri.getScheme())));
        }

        @Override
        public void recordPhoneImage(String mimeType, String uri) {
            if (uri == null || !uri.startsWith("content://")) {
                return;
            }
            if (!isWhitelistedImageMime(mimeType)) {
                return;
            }
            latestPhoneImageMime = mimeType.replaceAll("[\\r\\n]", "").trim().toLowerCase(Locale.ROOT);
            latestPhoneImageUri = Uri.parse(uri);
            latestPhoneImageFile = null;
        }
    }

    private static boolean isWhitelistedImageMime(String mime) {
        if (mime == null) {
            return false;
        }
        String clean = mime.replaceAll("[\\r\\n]", "").trim().toLowerCase(Locale.ROOT);
        return "image/png".equals(clean)
                || "image/jpeg".equals(clean)
                || "image/jpg".equals(clean)
                || "image/webp".equals(clean)
                || "image/gif".equals(clean);
    }

    private boolean applyWebImageToPhone(InputStream stream, String mimeType, long contentLength) {
        SharedPreferences preferences = WebClipboardPreferences.preferences(this);
        if (!WebClipboardPreferences.isImageSyncEnabled(preferences)) {
            Log.w(TAG, LOG_PREFIX + " image sync disabled, rejecting desktop image");
            return false;
        }
        if (!isWhitelistedImageMime(mimeType)) {
            Log.w(TAG, LOG_PREFIX + " rejected non-whitelisted image mime: " + mimeType);
            return false;
        }
        try {
            File cacheDir = WebClipboardImageProvider.getImageCacheDir(this);
            cleanImageCacheDir(cacheDir);

            String cleanMime = mimeType.replaceAll("[\\r\\n]", "").trim().toLowerCase(Locale.ROOT);
            String ext = ".png";
            if (cleanMime.contains("jpeg") || cleanMime.contains("jpg")) {
                ext = ".jpg";
            } else if (cleanMime.contains("webp")) {
                ext = ".webp";
            } else if (cleanMime.contains("gif")) {
                ext = ".gif";
            }
            File outFile = new File(cacheDir, "desktop_clip_" + System.currentTimeMillis() + ext);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
                fos.flush();
            }

            Uri contentUri = WebClipboardImageProvider.getUriForFile(this, outFile);
            latestPhoneImageFile = outFile;
            latestPhoneImageUri = contentUri;
            latestPhoneImageMime = cleanMime;

            ClipboardManager cm = serviceClipboardManager != null
                    ? serviceClipboardManager
                    : getSystemService(ClipboardManager.class);
            if (cm == null) {
                return false;
            }
            ClipData clipData = new ClipData(
                    ClipboardSyncIngressContract.WEB_CLIPBOARD_LABEL,
                    new String[] { cleanMime },
                    new ClipData.Item(contentUri));
            webClipboardEchoSuppressor.markWebApplied(contentUri.toString(), SystemClock.elapsedRealtime());
            cm.setPrimaryClip(clipData);
            updateNotification("Image applied from Web");
            return true;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to apply web image to phone", throwable);
            return false;
        }
    }

    private static void cleanImageCacheDir(File cacheDir) {
        if (cacheDir == null || !cacheDir.isDirectory()) {
            return;
        }
        try {
            File[] files = cacheDir.listFiles();
            if (files == null || files.length == 0) {
                return;
            }
            if (files.length >= 5) {
                Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                int deleteCount = files.length - 4;
                for (int i = 0; i < deleteCount; i++) {
                    files[i].delete();
                }
            }
            long cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L;
            for (File file : files) {
                if (file.exists() && file.lastModified() < cutoff) {
                    file.delete();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void handlePhoneImageClip(Uri uri, String mime) {
        if (uri == null) {
            return;
        }
        if (!isWhitelistedImageMime(mime)) {
            return;
        }
        String effectiveMime = mime.replaceAll("[\\r\\n]", "").trim().toLowerCase(Locale.ROOT);
        latestPhoneImageUri = uri;
        latestPhoneImageMime = effectiveMime;
        latestPhoneImageFile = null;

        long length = 0L;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
                if (pfd != null) {
                    length = pfd.getStatSize();
                }
            } catch (Throwable ignored) {
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
            File file = new File(uri.getPath());
            if (file.exists()) {
                length = file.length();
                latestPhoneImageFile = file;
            }
        }

        ClipboardSyncWebPortal portal = webPortal;
        if (portal != null) {
            portal.publishPhoneImage(effectiveMime, length);
            updateNotification("Image available");
        }
    }

    private void registerMediaObserverIfEnabled() {
        SharedPreferences preferences = WebClipboardPreferences.preferences(this);
        if (!WebClipboardPreferences.isImageSyncEnabled(preferences)) {
            unregisterMediaObserver();
            return;
        }
        if (screenshotMediaObserver != null) {
            return;
        }
        try {
            screenshotMediaObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    onMediaStoreChanged();
                }
            };
            getContentResolver().registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    screenshotMediaObserver);
            Log.i(TAG, LOG_PREFIX + " registered MediaStore screenshot observer");
            onMediaStoreChanged();
        } catch (Throwable t) {
            Log.w(TAG, "Failed to register MediaStore observer", t);
        }
    }

    private void unregisterMediaObserver() {
        if (screenshotMediaObserver == null) {
            return;
        }
        try {
            getContentResolver().unregisterContentObserver(screenshotMediaObserver);
        } catch (Throwable ignored) {
        }
        screenshotMediaObserver = null;
    }

    private void onMediaStoreChanged() {
        clipboardPublishExecutor.execute(() -> {
            try {
                String[] projection = new String[] {
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DATE_ADDED,
                        MediaStore.Images.Media.MIME_TYPE
                };
                long cutoff = (System.currentTimeMillis() / 1000L) - 86400;
                String selection = MediaStore.Images.Media.DATE_ADDED + " >= ? AND ("
                        + MediaStore.Images.Media.BUCKET_DISPLAY_NAME + " COLLATE NOCASE = 'Screenshots' OR "
                        + MediaStore.Images.Media.BUCKET_DISPLAY_NAME + " COLLATE NOCASE = 'Screenshot' OR "
                        + MediaStore.Images.Media.RELATIVE_PATH + " LIKE '%Screenshots/%' OR "
                        + MediaStore.Images.Media.DATA + " LIKE '%/Screenshots/%')";
                String[] selectionArgs = new String[] { String.valueOf(cutoff) };
                try (Cursor cursor = getContentResolver().query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                    if (cursor != null && cursor.moveToFirst()) {
                        long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                        long dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED));
                        String mime = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE));
                        if (dateAdded <= lastScreenshotDateAdded || !isWhitelistedImageMime(mime)) {
                            return;
                        }
                        lastScreenshotDateAdded = dateAdded;
                        Uri imageUri = ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                        Log.i(TAG, LOG_PREFIX + " detected screenshot: id=" + id + " mime=" + mime);
                        handlePhoneImageClip(imageUri, mime);
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to inspect recent screenshot media", t);
            }
        });
    }

    private static boolean isWebClipboardEcho(ClipData clipData) {
        if (clipData == null || clipData.getDescription() == null) {
            return false;
        }
        CharSequence label = clipData.getDescription().getLabel();
        return ClipboardSyncIngressContract.WEB_CLIPBOARD_LABEL.contentEquals(
                label != null ? label : "");
    }

    private String resolveClipMime(ClipData clipData, Uri uri) {
        if (clipData != null && clipData.getDescription() != null) {
            for (int i = 0; i < clipData.getDescription().getMimeTypeCount(); i++) {
                String mime = clipData.getDescription().getMimeType(i);
                if (isWhitelistedImageMime(mime)) {
                    return mime.replaceAll("[\\r\\n]", "").trim().toLowerCase(Locale.ROOT);
                }
            }
        }
        if (uri != null && "content".equalsIgnoreCase(uri.getScheme())) {
            try {
                String type = getContentResolver().getType(uri);
                if (isWhitelistedImageMime(type)) {
                    return type.replaceAll("[\\r\\n]", "").trim().toLowerCase(Locale.ROOT);
                }
            } catch (Throwable ignored) {
            }
        }
        if (uri != null) {
            String path = uri.getPath();
            if (path != null) {
                String lower = path.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".png")) return "image/png";
                if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
                if (lower.endsWith(".webp")) return "image/webp";
                if (lower.endsWith(".gif")) return "image/gif";
            }
        }
        return null;
    }

    private static Uri resolveImageClip(ClipData clipData) {
        if (clipData == null) {
            return null;
        }
        for (int i = 0; i < clipData.getItemCount(); i++) {
            ClipData.Item item = clipData.getItemAt(i);
            if (item == null) {
                continue;
            }
            Uri uri = item.getUri();
            if (uri == null && item.getIntent() != null) {
                uri = item.getIntent().getData();
            }
            if (uri != null) {
                return uri;
            }
        }
        return null;
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
            try {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
                return;
            } catch (Throwable t) {
                Log.w(TAG, LOG_PREFIX + " Failed startForeground with connectedDevice, trying dataSync", t);
                try {
                    startForeground(NOTIFICATION_ID, notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                    return;
                } catch (Throwable t2) {
                    Log.w(TAG, LOG_PREFIX + " Failed startForeground with dataSync, falling back to untyped", t2);
                }
            }
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

