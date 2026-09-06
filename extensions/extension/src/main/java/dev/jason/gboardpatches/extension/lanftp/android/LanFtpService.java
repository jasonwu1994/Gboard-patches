package dev.jason.gboardpatches.extension.lanftp.android;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import dev.jason.gboardpatches.extension.lanftp.config.LanFtpPreferences;
import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpActivityObserver;
import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpEngine;
import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpServerConfig;
import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpSessionInfo;
import dev.jason.gboardpatches.extension.lanftp.runtime.fs.LanFtpFileSystemFactory;
import dev.jason.gboardpatches.extension.lanftp.runtime.fs.LanFtpDocumentStore;
import dev.jason.gboardpatches.extension.lanftp.runtime.fs.LanFtpStagingDocumentStore;
import dev.jason.gboardpatches.extension.lanftp.runtime.fs.LocalLanFtpDocumentStore;
import dev.jason.gboardpatches.extension.lanftp.runtime.fs.SafLanFtpDocumentStore;

public final class LanFtpService extends Service implements LanFtpActivityObserver {
    enum StartupFailureReason {
        LOCAL_NETWORK_PERMISSION("Local network permission is required to start FTP"),
        ALL_FILES_PERMISSION("All files access is required for the shared-storage root"),
        ROOT_PERMISSION(
                "FTP root folder permission is unavailable; select the root folder again"),
        PRIVATE_WIFI("Connect the phone to a private Wi-Fi LAN first");

        private final String userMessage;

        StartupFailureReason(String userMessage) {
            this.userMessage = userMessage;
        }
    }

    static final class StartupFailureException extends IllegalStateException {
        private final StartupFailureReason reason;

        StartupFailureException(StartupFailureReason reason, Throwable cause) {
            super(java.util.Objects.requireNonNull(reason, "reason").userMessage, cause);
            this.reason = reason;
        }
    }
    private static final String TAG = "GboardLanFtp";
    private static final long IDLE_TIMEOUT_CHECK_INTERVAL_MS = 30_000L;
    private static final long TRANSFER_SPEED_EXPIRY_REFRESH_MS = 2_100L;
    private static final AtomicReference<LanFtpService> ACTIVE_SERVICE =
            new AtomicReference<>();

    private final AtomicLong generation = new AtomicLong();
    private final Object runtimeLock = new Object();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LanFtpLifecycleExecutor lifecycleExecutor =
            new LanFtpLifecycleExecutor(new LanFtpThreadFactory());

    private LanFtpEngine engine;
    private LanFtpStagingDocumentStore exportedStore;
    private LanFtpNetworkResolver.Snapshot networkSnapshot;
    private ConnectivityManager.NetworkCallback networkCallback;
    private LanFtpRuntimeLockController runtimeLocks;
    private LanFtpNotificationController notifications;
    private long idleTimeoutMs = LanFtpPreferences.DEFAULT_IDLE_TIMEOUT_MS;
    private long lastActivityElapsedMs;
    private int sessionCount;
    private int transferCount;
    private volatile int latestStartId;
    private String endpoint = "";
    private volatile boolean failed;
    private volatile boolean destroyed;
    private long activePasswordRevision;
    private LanFtpRuntimeEventPublisher runtimeEvents;

    private final Runnable idleCheck = new Runnable() {
        @Override
        public void run() {
            runContainedCallback("idle handler", this::runContained);
        }

        private void runContained() {
            if (destroyed) {
                return;
            }
            boolean shouldStop;
            synchronized (runtimeLock) {
                shouldStop = engine != null
                        && sessionCount == 0
                        && transferCount == 0
                        && SystemClock.elapsedRealtime() - lastActivityElapsedMs >= idleTimeoutMs;
            }
            if (shouldStop) {
                requestOrderlyStop("Stopped after 15 minutes without FTP activity", false);
                return;
            }
            mainHandler.postDelayed(this, IDLE_TIMEOUT_CHECK_INTERVAL_MS);
        }
    };

    private final Runnable transferSpeedExpiryRefresh = () ->
            runContainedCallback("transfer status handler", () -> {
        String notificationStatus;
        LanFtpSessionInfo activeTransfer;
        synchronized (runtimeLock) {
            if (engine == null || transferCount == 0 || destroyed) {
                return;
            }
            publishRunningStateLocked(true);
            notificationStatus = runningNotificationStatusLocked();
            activeTransfer = activeTransferLocked();
        }
        notifications.update(notificationStatus, activeTransfer);
    });

    private volatile boolean configuredReadOnly = true;

    static boolean requestStart(Context context, LanFtpServerConfigSnapshot snapshot) {
        Context appContext = applicationContext(context);
        if (appContext == null) {
            return false;
        }
        SharedPreferences preferences = null;
        try {
            if (!LanFtpLocalNetworkPermission.isGranted(appContext)) {
                requestStop(appContext);
                return false;
            }
            if (snapshot == null || !snapshot.enabled() || snapshot.rootTreeUri().isEmpty()) {
                requestStop(appContext);
                return false;
            }
            if (LanFtpPreferences.isSharedStorageRoot(snapshot.rootTreeUri())
                    && !LanFtpAllFilesAccess.isGranted(appContext)) {
                requestStop(appContext);
                return false;
            }
            preferences = LanFtpPreferences.preferences(appContext);
            LanFtpPreferences.Snapshot privateSnapshot =
                    LanFtpPreferences.read(preferences);
            if (!snapshot.equals(privateSnapshot.toConfigSnapshot())) {
                throw new IllegalStateException("LAN FTP persisted configuration changed");
            }
            Intent intent = LanFtpServiceCommand.startIntent(
                    appContext, snapshot, privateSnapshot);
            startForegroundCommand(appContext, intent);
            return true;
        } catch (Throwable throwable) {
            if (preferences != null) {
                LanFtpPreferences.setEnabled(preferences, false);
            }
            logContainedFailure("Foreground-service start request failed", throwable);
            requestStop(appContext);
            return false;
        }
    }

    static void requestStop(Context context) {
        Context appContext = applicationContext(context);
        if (appContext == null) {
            return;
        }
        try {
            Intent intent = LanFtpServiceCommand.stopIntent(appContext);
            startForegroundCommand(appContext, intent);
        } catch (Throwable throwable) {
            Log.w(TAG, "Could not request LAN FTP stop", throwable);
            try {
                appContext.stopService(new Intent(appContext, LanFtpService.class));
            } catch (Throwable ignored) {
                // Best effort.
            }
        }
    }

    @Override
    public void onCreate() {
        try {
            super.onCreate();
            runtimeEvents = new LanFtpRuntimeEventPublisher(this);
            ACTIVE_SERVICE.set(this);
            runtimeLocks = new LanFtpRuntimeLockController(this);
            notifications = new LanFtpNotificationController(this);
            notifications.startForeground();
            publishRuntimeStatus(LanFtpRuntimeStatus.starting(), false);
            lastActivityElapsedMs = SystemClock.elapsedRealtime();
        } catch (Throwable throwable) {
            handleContainedCallbackFailure("service create", throwable);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            latestStartId = startId;
            String action = intent == null ? "" : intent.getAction();
            if (LanFtpServiceCommand.ACTION_STOP.equals(action)) {
                requestOrderlyStop("Stopped by user", false);
                return START_NOT_STICKY;
            }
            if (!LanFtpServiceCommand.ACTION_START_OR_UPDATE.equals(action) || intent == null) {
                requestOrderlyStop("Stopped after process restart", false);
                return START_NOT_STICKY;
            }
            long requestedGeneration = generation.incrementAndGet();
            quiesceCurrentRuntime();
            failed = false;
            publishRuntimeStatus(LanFtpRuntimeStatus.starting(), false);
            if (notifications != null) {
                notifications.beginStart();
                notifications.update("Applying LAN FTP configuration");
            }
            LanFtpServerController.from(this).requestTileRefresh();
            Intent snapshotIntent = new Intent(intent);
            if (!lifecycleExecutor.replaceQueuedTask(() -> applySnapshotSafely(
                    requestedGeneration, snapshotIntent, startId))) {
                failAndStop("Server is busy applying another configuration",
                        new IllegalStateException("LAN FTP lifecycle queue is saturated"));
            }
        } catch (Throwable throwable) {
            handleContainedCallbackFailure("service start command", throwable);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        synchronized (runtimeLock) {
            destroyed = true;
            generation.incrementAndGet();
        }
        quiesceCurrentRuntime();
        try {
            mainHandler.removeCallbacksAndMessages(null);
        } catch (Throwable throwable) {
            logContainedFailure("Destroy callback cancellation failed", throwable);
        }
        scheduleRuntimeShutdown(this::completeDestroyAfterRuntimeShutdown);
        try {
            lifecycleExecutor.shutdown();
        } catch (Throwable throwable) {
            logContainedFailure("Lifecycle executor shutdown failed", throwable);
        } finally {
            try {
                super.onDestroy();
            } catch (Throwable throwable) {
                logContainedFailure("Service superclass destroy failed", throwable);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void completeDestroyAfterRuntimeShutdown() {
        boolean releasedActiveService = false;
        try {
            releasedActiveService = ACTIVE_SERVICE.compareAndSet(this, null);
        } catch (Throwable throwable) {
            logContainedFailure("Active-service release failed", throwable);
        }
        try {
            if (releasedActiveService && !failed) {
                resetRuntimeStatus();
            }
        } catch (Throwable throwable) {
            logContainedFailure("Stopped-state publication failed", throwable);
        }
        if (releasedActiveService) {
            try {
                LanFtpServerController.from(this).requestTileRefresh();
            } catch (Throwable throwable) {
                logContainedFailure("Final Tile refresh failed", throwable);
            }
        }
        try {
            if (releasedActiveService && notifications != null) {
                notifications.stop(failed);
            }
        } catch (Throwable throwable) {
            logContainedFailure("Foreground notification removal failed", throwable);
        }
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        runContainedCallback("foreground-service timeout", () ->
                requestOrderlyStop("Android foreground-service time limit reached", true));
    }

    @Override
    public void onSessionCountChanged(int count) {
        runContainedCallback("session-count observer", () -> updateSessionCount(count));
    }

    private void updateSessionCount(int count) {
        String notificationStatus;
        LanFtpSessionInfo activeTransfer;
        synchronized (runtimeLock) {
            sessionCount = Math.max(0, count);
            lastActivityElapsedMs = SystemClock.elapsedRealtime();
            updateRuntimeLocksLocked();
            publishRunningStateLocked(false);
            notificationStatus = runningNotificationStatusLocked();
            activeTransfer = activeTransferLocked();
        }
        notifications.update(notificationStatus, activeTransfer);
        LanFtpServerController.from(this).requestTileRefresh();
    }

    @Override
    public void onTransferCountChanged(int count) {
        runContainedCallback("transfer-count observer", () -> updateTransferCount(count));
    }

    private void updateTransferCount(int count) {
        String notificationStatus;
        LanFtpSessionInfo activeTransfer;
        synchronized (runtimeLock) {
            boolean runtimeAttached = engine != null && !destroyed;
            transferCount = runtimeAttached ? Math.max(0, count) : 0;
            lastActivityElapsedMs = SystemClock.elapsedRealtime();
            updateRuntimeLocksLocked();
            if (runtimeAttached) {
                publishRunningStateLocked(false);
            }
            notificationStatus = runningNotificationStatusLocked();
            activeTransfer = activeTransferLocked();
        }
        if (count <= 0) {
            mainHandler.removeCallbacks(transferSpeedExpiryRefresh);
        }
        notifications.update(notificationStatus, activeTransfer);
    }

    @Override
    public void onSessionActivityChanged() {
        runContainedCallback("session-activity observer", this::updateSessionActivity);
    }

    private void updateSessionActivity() {
        String notificationStatus;
        LanFtpSessionInfo activeTransfer;
        synchronized (runtimeLock) {
            lastActivityElapsedMs = SystemClock.elapsedRealtime();
            publishRunningStateLocked(true);
            notificationStatus = runningNotificationStatusLocked();
            activeTransfer = activeTransferLocked();
        }
        notifications.update(notificationStatus, activeTransfer);
        mainHandler.removeCallbacks(transferSpeedExpiryRefresh);
        mainHandler.postDelayed(
                transferSpeedExpiryRefresh, TRANSFER_SPEED_EXPIRY_REFRESH_MS);
    }

    private void applySnapshotSafely(long requestedGeneration, Intent intent, int startId) {
        try {
            quiesceCurrentRuntime();
            shutdownRuntime();
            if (destroyed || requestedGeneration != generation.get()) {
                return;
            }
            if (!LanFtpLocalNetworkPermission.isGranted(this)) {
                throw new StartupFailureException(
                        StartupFailureReason.LOCAL_NETWORK_PERMISSION, null);
            }
            LanFtpServiceCommand.Decoded command = LanFtpServiceCommand.decode(intent);
            LanFtpServerConfig config = command.config();
            boolean sharedStorageRoot = LanFtpPreferences.isSharedStorageRoot(
                    config.rootTreeUri());
            Uri rootUri = sharedStorageRoot ? null : Uri.parse(config.rootTreeUri());
            if (sharedStorageRoot) {
                if (!LanFtpAllFilesAccess.isGranted(this)) {
                    throw new StartupFailureException(
                            StartupFailureReason.ALL_FILES_PERMISSION, null);
                }
            } else if (!hasPersistedRootGrant(rootUri, config.readOnly())) {
                throw new StartupFailureException(
                        StartupFailureReason.ROOT_PERMISSION, null);
            }
            LanFtpNetworkResolver.Snapshot network = LanFtpNetworkResolver.resolve(this);
            if (network == null) {
                throw new StartupFailureException(
                        StartupFailureReason.PRIVATE_WIFI, null);
            }
            LanFtpLocalNetworkPreflight.verifyLanListenerAllowed(network.address);
            LanFtpDocumentStore store = sharedStorageRoot
                    ? new LocalLanFtpDocumentStore(
                            Environment.getExternalStorageDirectory(), config.readOnly())
                    : new SafLanFtpDocumentStore(
                            this,
                            rootUri,
                            config.readOnly(),
                            () -> requestOrderlyStop(
                                    "Root folder permission was revoked", true));
            LanFtpStagingDocumentStore stagingStore =
                    new LanFtpStagingDocumentStore(store);
            LanFtpFileSystemFactory fileSystem = new LanFtpFileSystemFactory(
                    stagingStore,
                    config.username(),
                    config.readOnly(),
                    config.allowAnonymous());
            LanFtpEngine replacement = new LanFtpEngine(
                    config,
                    network.address.getHostAddress(),
                    fileSystem,
                    this);

            synchronized (runtimeLock) {
                if (destroyed || requestedGeneration != generation.get()) {
                    return;
                }
                engine = replacement;
                exportedStore = stagingStore;
                networkSnapshot = network;
                configuredReadOnly = config.readOnly();
                idleTimeoutMs = command.idleTimeoutMs();
                activePasswordRevision = command.passwordRevision();
                endpoint = network.endpoint(config.controlPort());
                sessionCount = 0;
                transferCount = 0;
                lastActivityElapsedMs = SystemClock.elapsedRealtime();
                failed = false;
            }
            replacement.start();
            registerNetworkCallback(network);
            boolean obsolete;
            synchronized (runtimeLock) {
                obsolete = destroyed
                        || requestedGeneration != generation.get()
                        || engine != replacement;
                if (!obsolete) {
                    updateRuntimeLocksLocked();
                    publishRunningStateLocked(false);
                }
            }
            if (obsolete) {
                replacement.quiesce();
                shutdownRuntime();
                return;
            }
            mainHandler.removeCallbacks(idleCheck);
            mainHandler.postDelayed(idleCheck, IDLE_TIMEOUT_CHECK_INTERVAL_MS);
            synchronized (runtimeLock) {
                notifications.update(runningNotificationStatusLocked());
            }
            LanFtpServerController.from(this).requestTileRefresh();
        } catch (Throwable throwable) {
            if (requestedGeneration == generation.get()) {
                failAndStop(startupFailureReason(throwable), throwable);
            }
        }
    }

    private void requestOrderlyStop(String reason, boolean publishFailure) {
        int requestedStopStartId = latestStartId;
        long requestedStopGeneration = generation.incrementAndGet();
        if (!publishFailure) {
            publishRuntimeStatus(LanFtpRuntimeStatus.stopping(), false);
        }
        quiesceCurrentRuntime();
        if (publishFailure) {
            failed = true;
            publishRuntimeStatus(LanFtpRuntimeStatus.failed(reason), false);
            notifications.updateFailure(reason);
        }
        scheduleRuntimeShutdown(() -> {
            if (requestedStopGeneration != generation.get()) {
                return;
            }
            if (!publishFailure) {
                resetRuntimeStatus();
            }
            stopSelfResult(requestedStopStartId);
        });
    }

    private void failAndStop(String reason, Throwable throwable) {
        int requestedStopStartId = latestStartId;
        long requestedStopGeneration = generation.incrementAndGet();
        quiesceCurrentRuntime();
        failed = true;
        try {
            LanFtpPreferences.setEnabled(LanFtpPreferences.preferences(this), false);
        } catch (Throwable preferenceFailure) {
            logContainedFailure("Failed-state persistence failed", preferenceFailure);
        }
        logContainedFailure(reason, throwable);
        publishRuntimeStatus(LanFtpRuntimeStatus.failed(reason), false);
        notifications.updateFailure(reason);
        LanFtpServerController.from(this).requestTileRefresh();
        scheduleRuntimeShutdown(() -> {
            if (requestedStopGeneration != generation.get()) {
                return;
            }
            stopSelfResult(requestedStopStartId);
        });
    }

    private void scheduleRuntimeShutdown(Runnable afterShutdown) {
        long requestedStopGeneration = generation.get();
        int requestedStopStartId = latestStartId;
        Runnable cleanup = () -> {
            shutdownRuntime();
            if (afterShutdown != null) {
                try {
                    afterShutdown.run();
                } catch (Throwable throwable) {
                    Log.w(TAG, "LAN FTP post-shutdown action failed", throwable);
                }
            }
        };
        try {
            lifecycleExecutor.replaceQueueWithShutdown(cleanup);
        } catch (Throwable throwable) {
            logContainedFailure("Runtime shutdown scheduling failed", throwable);
            startEmergencyShutdown(
                    cleanup, requestedStopGeneration, requestedStopStartId);
        }
    }

    private void startEmergencyShutdown(Runnable cleanup,
            long requestedStopGeneration, int requestedStopStartId) {
        try {
            Thread emergency = new Thread(() -> {
                try {
                    cleanup.run();
                } catch (Throwable throwable) {
                    failed = true;
                    logContainedFailure("Emergency cleanup task failed", throwable);
                    try {
                        stopServiceAfterFailure(
                                requestedStopGeneration, requestedStopStartId);
                    } catch (Throwable stopFailure) {
                        logContainedFailure("Emergency stopSelf failed", stopFailure);
                    }
                }
            }, "LanFtpEmergencyShutdown");
            emergency.setDaemon(true);
            emergency.start();
        } catch (Throwable throwable) {
            logContainedFailure("Emergency runtime shutdown failed", throwable);
            try {
                stopServiceAfterFailure(requestedStopGeneration, requestedStopStartId);
            } catch (Throwable stopFailure) {
                logContainedFailure("Emergency fallback stopSelf failed", stopFailure);
            }
        }
    }

    private void shutdownRuntime() {
        try {
            mainHandler.removeCallbacks(idleCheck);
            mainHandler.removeCallbacks(transferSpeedExpiryRefresh);
        } catch (Throwable throwable) {
            logContainedFailure("Runtime callback cancellation failed", throwable);
        }
        LanFtpEngine currentEngine;
        LanFtpNetworkResolver.Snapshot currentNetwork;
        ConnectivityManager.NetworkCallback currentCallback;
        synchronized (runtimeLock) {
            currentEngine = engine;
            engine = null;
            exportedStore = null;
            currentNetwork = networkSnapshot;
            networkSnapshot = null;
            currentCallback = networkCallback;
            networkCallback = null;
            sessionCount = 0;
            transferCount = 0;
            endpoint = "";
            configuredReadOnly = true;
            activePasswordRevision = 0L;
        }
        try {
            if (currentEngine != null) {
                try {
                    currentEngine.stop();
                } catch (Throwable throwable) {
                    logContainedFailure("FTP engine shutdown failed", throwable);
                }
            }
            if (currentNetwork != null && currentCallback != null) {
                try {
                    currentNetwork.manager.unregisterNetworkCallback(currentCallback);
                } catch (Throwable throwable) {
                    logContainedFailure("FTP network callback release failed", throwable);
                }
            }
        } finally {
            synchronized (runtimeLock) {
                if (runtimeLocks != null) {
                    runtimeLocks.release();
                }
            }
        }
    }

    private void quiesceCurrentRuntime() {
        LanFtpEngine current;
        synchronized (runtimeLock) {
            current = engine;
        }
        if (current == null) {
            return;
        }
        try {
            current.quiesce();
        } catch (Throwable throwable) {
            logContainedFailure("FTP runtime quiesce failed", throwable);
        }
    }

    private void registerNetworkCallback(LanFtpNetworkResolver.Snapshot snapshot) {
        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLost(Network network) {
                runContainedCallback("network lost", () -> {
                    if (snapshot.network.equals(network)) {
                        requestOrderlyStop("Wi-Fi network was lost", true);
                    }
                });
            }

            @Override
            public void onLinkPropertiesChanged(Network network, LinkProperties properties) {
                runContainedCallback("network properties changed", () -> {
                    if (!snapshot.network.equals(network) || properties == null) {
                        return;
                    }
                    boolean addressStillPresent = false;
                    for (LinkAddress address : properties.getLinkAddresses()) {
                        if (snapshot.address.equals(address.getAddress())) {
                            addressStillPresent = true;
                            break;
                        }
                    }
                    if (!addressStillPresent) {
                        requestOrderlyStop("Wi-Fi address changed", true);
                    }
                });
            }
        };
        snapshot.manager.registerDefaultNetworkCallback(callback);
        synchronized (runtimeLock) {
            networkCallback = callback;
        }
    }

    private boolean hasPersistedRootGrant(Uri uri, boolean readOnly) {
        if (uri == null || !"content".equals(uri.getScheme())) {
            return false;
        }
        try {
            for (UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
                if (!uri.equals(permission.getUri()) || !permission.isReadPermission()) {
                    continue;
                }
                if (readOnly || permission.isWritePermission()) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // Missing or inaccessible permission is a hard fail.
        }
        return false;
    }

    private void publishRunningStateLocked(boolean progress) {
        if (engine == null || endpoint.isEmpty()) {
            return;
        }
        publishRuntimeStatus(LanFtpRuntimeStatus.running(
                endpoint,
                engine.sessionInfos(),
                transferCount,
                exportedStore == null || exportedStore.supportsRandomAccess(),
                activePasswordRevision), progress);
    }

    private void publishRuntimeStatus(LanFtpRuntimeStatus status, boolean progress) {
        LanFtpRuntimeEventPublisher publisher = runtimeEvents;
        if (publisher == null) {
            LanFtpRuntimeRegistry.publish(status);
            return;
        }
        publisher.publish(status, progress);
    }

    private void resetRuntimeStatus() {
        LanFtpRuntimeEventPublisher publisher = runtimeEvents;
        if (publisher == null) {
            LanFtpRuntimeRegistry.reset();
            return;
        }
        publisher.reset();
    }

    private void updateRuntimeLocksLocked() {
        if (runtimeLocks == null) {
            return;
        }
        if (engine != null && !destroyed) {
            runtimeLocks.acquire();
        } else {
            runtimeLocks.release();
        }
    }

    private String runningNotificationStatusLocked() {
        if (endpoint.isEmpty()) {
            return "LAN FTP stopped";
        }
        return endpoint
                + (configuredReadOnly ? " • read-only" : " • read-write")
                + " • " + sessionCount + " clients"
                + " • " + transferCount + " transfers";
    }

    private LanFtpSessionInfo activeTransferLocked() {
        if (engine == null || transferCount == 0) {
            return null;
        }
        LanFtpSessionInfo newest = null;
        List<LanFtpSessionInfo> sessions = engine.sessionInfos();
        for (LanFtpSessionInfo session : sessions) {
            if (!session.isTransferActive()) {
                continue;
            }
            if (newest == null
                    || session.lastActivityMillis() > newest.lastActivityMillis()
                    || (session.lastActivityMillis() == newest.lastActivityMillis()
                    && session.loginTimeMillis() > newest.loginTimeMillis())
                    || (session.lastActivityMillis() == newest.lastActivityMillis()
                    && session.loginTimeMillis() == newest.loginTimeMillis()
                    && session.id().compareTo(newest.id()) > 0)) {
                newest = session;
            }
        }
        return newest;
    }

    static boolean kickSession(String sessionId) {
        LanFtpService service = ACTIVE_SERVICE.get();
        if (service == null || sessionId == null) {
            return false;
        }
        try {
            LanFtpEngine current;
            synchronized (service.runtimeLock) {
                current = service.engine;
            }
            return current != null && current.kickSession(UUID.fromString(sessionId));
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean isServiceCreated() {
        return ACTIVE_SERVICE.get() != null;
    }

    private static void startForegroundCommand(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private static Context applicationContext(Context context) {
        if (context == null) {
            return null;
        }
        Context appContext = context.getApplicationContext();
        return appContext != null ? appContext : context;
    }

    static String startupFailureReason(Throwable throwable) {
        boolean permissionFailure = false;
        boolean ioFailure = false;
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof StartupFailureException startupFailure) {
                return startupFailure.reason.userMessage;
            }
            if (current instanceof java.net.BindException) {
                return "FTP control port is unavailable";
            }
            permissionFailure |= current instanceof SecurityException;
            ioFailure |= current instanceof java.io.IOException;
            Throwable next = current.getCause();
            current = next == current ? null : next;
        }
        if (permissionFailure) {
            return "FTP storage or network permission is unavailable";
        }
        return ioFailure
                ? "FTP storage or network setup failed"
                : "FTP server could not start";
    }

    private void runContainedCallback(String boundary, Runnable callback) {
        try {
            callback.run();
        } catch (Throwable throwable) {
            handleContainedCallbackFailure(boundary, throwable);
        }
    }

    private void handleContainedCallbackFailure(String boundary, Throwable throwable) {
        String safeBoundary = boundary == null || boundary.isBlank()
                ? "callback"
                : boundary;
        logContainedFailure("Contained " + safeBoundary + " failure", throwable);
        int requestedStopStartId = latestStartId;
        long requestedStopGeneration = generation.incrementAndGet();
        quiesceCurrentRuntime();
        failed = true;
        String reason = "LAN FTP stopped after an internal " + safeBoundary + " failure";
        try {
            publishRuntimeStatus(LanFtpRuntimeStatus.failed(reason), false);
        } catch (Throwable publishFailure) {
            logContainedFailure("Failure-state publication failed", publishFailure);
        }
        try {
            if (notifications != null) {
                notifications.updateFailure(reason);
            }
        } catch (Throwable notificationFailure) {
            logContainedFailure("Failure notification update failed", notificationFailure);
        }
        try {
            scheduleRuntimeShutdown(() -> stopServiceAfterFailure(
                    requestedStopGeneration, requestedStopStartId));
        } catch (Throwable shutdownFailure) {
            logContainedFailure("Fail-closed shutdown request failed", shutdownFailure);
            startEmergencyShutdown(() -> {
                shutdownRuntime();
                stopServiceAfterFailure(requestedStopGeneration, requestedStopStartId);
            }, requestedStopGeneration, requestedStopStartId);
        }
    }

    private void stopServiceAfterFailure(
            long requestedStopGeneration, int requestedStopStartId) {
        if (requestedStopGeneration != generation.get() || ACTIVE_SERVICE.get() != this) {
            return;
        }
        if (requestedStopStartId > 0) {
            stopSelfResult(requestedStopStartId);
        } else {
            stopSelf();
        }
    }

    private static void logContainedFailure(String boundary, Throwable throwable) {
        String failureType = throwable == null
                ? "unknown"
                : throwable.getClass().getSimpleName();
        try {
            Log.w(TAG, boundary + " [" + failureType + "]");
        } catch (Throwable ignored) {
            // Logging is the final diagnostic boundary and must not revive the failure.
        }
    }

    private final class LanFtpThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(() -> {
                try {
                    runnable.run();
                } catch (Throwable throwable) {
                    handleContainedCallbackFailure("lifecycle worker", throwable);
                }
            }, "LanFtpLifecycle");
            thread.setDaemon(true);
            return thread;
        }
    }
}
