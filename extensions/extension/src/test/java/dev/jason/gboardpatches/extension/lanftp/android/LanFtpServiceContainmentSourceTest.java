package dev.jason.gboardpatches.extension.lanftp.android;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.net.BindException;

import org.junit.Assert;
import org.junit.Test;

public final class LanFtpServiceContainmentSourceTest {
    @Test
    public void startupFailureTextClassifiesCauseWithoutLeakingUriOrPath() {
        String sensitive = "Unable to query content://secret/tree at /sdcard/private/file.zip";

        String storage = LanFtpService.startupFailureReason(new IOException(sensitive));
        String port = LanFtpService.startupFailureReason(new BindException(sensitive));
        String root = LanFtpService.startupFailureReason(
                new LanFtpService.StartupFailureException(
                        LanFtpService.StartupFailureReason.ROOT_PERMISSION,
                        new IOException(sensitive)));
        String wifi = LanFtpService.startupFailureReason(
                new LanFtpService.StartupFailureException(
                        LanFtpService.StartupFailureReason.PRIVATE_WIFI, null));

        Assert.assertEquals("FTP storage or network setup failed", storage);
        Assert.assertEquals("FTP control port is unavailable", port);
        Assert.assertEquals(
                "FTP root folder permission is unavailable; select the root folder again",
                root);
        Assert.assertEquals("Connect the phone to a private Wi-Fi LAN first", wifi);
        Assert.assertFalse(storage.contains("content://") || storage.contains("/sdcard"));
        Assert.assertFalse(port.contains("content://") || port.contains("/sdcard"));
        Assert.assertFalse(root.contains("content://") || root.contains("/sdcard"));
    }

    @Test
    public void serviceUsesNonStickyTimeoutContract()
            throws Exception {
        String service = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpService.java");

        Assert.assertTrue(service.contains("return START_NOT_STICKY"));
        Assert.assertTrue(service.contains("void onTimeout(int startId, int fgsType)"));
        Assert.assertFalse(service.contains("void onDestroy() {\n"
                + "        destroyed = true;\n"
                + "        generation.incrementAndGet();\n"
                + "        mainHandler.removeCallbacksAndMessages(null);\n"
                + "        shutdownRuntime();"));
    }

    @Test
    public void lifecycleAndFtpWorkAreBoundedAndCaughtAtThreadBoundary() throws Exception {
        String service = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpService.java");
        String engine = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/runtime/LanFtpEngine.java");
        String activity = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/runtime/LanFtpActivityFtplet.java");

        String lifecycle = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpLifecycleExecutor.java");
        Assert.assertTrue(lifecycle.contains("new ArrayBlockingQueue<>(1)"));
        Assert.assertTrue(service.contains("catch (Throwable throwable)"));
        Assert.assertTrue(service.contains("onLost(Network network)"));
        Assert.assertTrue(service.contains("onLinkPropertiesChanged("));
        Assert.assertTrue(engine.contains("factory.setMaxLogins(config.maxSessions())"));
        Assert.assertTrue(engine.contains("MAX_ACTIVE_TRANSFERS = 2"));
        Assert.assertTrue(engine.contains("MAX_PENDING_CONTROL_EVENTS"));
        Assert.assertTrue(engine.contains("new LanFtpBoundedServerContext("));
        Assert.assertTrue(engine.contains("listenerFactory.setSessionFilter("));
        Assert.assertTrue(engine.contains("new LanFtpConnectionAdmissionFilter("));
        Assert.assertTrue(activity.contains("LanFtpConnectionAdmissionFilter.release(session)"));
        Assert.assertTrue(activity.contains("containCallbackFailure(session)"));
        Assert.assertTrue(activity.contains("catch (Throwable ignored)"));
        Assert.assertTrue(engine.contains("serverFactory.setListeners("));
        Assert.assertFalse(engine.contains("serverFactory.addListener("));
    }

    @Test
    public void dataCommandCleanupClosesTheSocketEvenWhenStateResetThrows() throws Exception {
        String support = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/runtime/LanFtpDataCommandSupport.java");

        int finish = support.indexOf("static void finish(FtpIoSession session)");
        int reset = support.indexOf("session.resetState();", finish);
        int close = support.indexOf("closeDataConnection();", reset);
        int outerTry = support.indexOf("try {", finish);
        int outerFinally = support.indexOf("finally {", outerTry);
        Assert.assertTrue(finish >= 0 && reset > finish && close > reset);
        Assert.assertTrue(outerTry < reset && outerFinally > reset && outerFinally < close);
    }

    @Test
    public void providerContainsEveryCallBranchAtTheBinderBoundary() throws Exception {
        String provider = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpStatusProvider.java");

        Assert.assertTrue(provider.contains("return handleCall(method, arg, extras);"));
        Assert.assertTrue(provider.contains("catch (Throwable throwable)"));
        Assert.assertTrue(provider.contains("private Bundle handleCall("));
    }

    @Test
    public void foregroundStartFailureDisablesTheSavedRequestAndPropagatesToController()
            throws Exception {
        String service = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpService.java");
        String controller = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpServerController.java");

        Assert.assertTrue(service.contains("static boolean requestStart("));
        Assert.assertTrue(service.contains("LanFtpPreferences.setEnabled(preferences, false);"));
        Assert.assertTrue(controller.contains("if (!LanFtpService.requestStart("));
        Assert.assertTrue(controller.contains("LanFtpPreferences.setEnabled(preferences(), false)"));
    }

    @Test
    public void destroyPublishesStoppedAndRemovesNotificationOnlyAfterRuntimeShutdown()
            throws Exception {
        String service = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpService.java");

        int onDestroy = service.indexOf("public void onDestroy()");
        int onBind = service.indexOf("public IBinder onBind", onDestroy);
        String destroyBody = service.substring(onDestroy, onBind);
        Assert.assertTrue(destroyBody.contains(
                "scheduleRuntimeShutdown(this::completeDestroyAfterRuntimeShutdown);"));
        Assert.assertFalse(destroyBody.contains("LanFtpRuntimeRegistry.reset();"));
        Assert.assertFalse(destroyBody.contains("notifications.stop(failed);"));

        int shutdown = service.indexOf("shutdownRuntime();",
                service.indexOf("private void scheduleRuntimeShutdown"));
        int completion = service.indexOf("afterShutdown.run();", shutdown);
        Assert.assertTrue(shutdown >= 0 && completion > shutdown);
        Assert.assertTrue(service.contains(
                "releasedActiveService = ACTIVE_SERVICE.compareAndSet(this, null);"));
        Assert.assertTrue(service.contains("if (releasedActiveService && !failed)"));
        Assert.assertTrue(service.contains("if (releasedActiveService && notifications != null)"));
    }

    @Test
    public void callbacksUseOneFailClosedThrowableBoundary() throws Exception {
        String service = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpService.java");

        for (String boundary : new String[]{
                "idle handler", "transfer status handler", "session-count observer",
                "transfer-count observer", "session-activity observer", "network lost",
                "network properties changed", "foreground-service timeout"}) {
            Assert.assertTrue(boundary,
                    service.contains("runContainedCallback(\"" + boundary + "\""));
        }
        Assert.assertTrue(service.contains("handleContainedCallbackFailure("));
    }

    @Test
    public void tileContainsSynchronousAndDeferredSystemUiCallbacks() throws Exception {
        String tile = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpTileService.java");

        int listening = tile.indexOf("public void onStartListening()");
        int added = tile.indexOf("public void onTileAdded()", listening);
        int click = tile.indexOf("public void onClick()", added);
        int toggle = tile.indexOf("private void toggleTileSafely()", click);
        int permission = tile.indexOf("private void openPermissionSettings(", toggle);

        String listeningBody = tile.substring(listening, added);
        String addedBody = tile.substring(added, click);
        String clickBody = tile.substring(click, toggle);
        String toggleBody = tile.substring(toggle, permission);
        for (String callback : new String[]{listeningBody, addedBody, clickBody, toggleBody}) {
            Assert.assertTrue(callback.contains("try {"));
            Assert.assertTrue(callback.contains("catch (Throwable ignored)"));
        }
        Assert.assertTrue(clickBody.contains("unlockAndRun(this::toggleTileSafely)"));
    }

    @Test
    public void tileImmediatelyReflectsRequestedStateAndBoundedlyReconcilesRuntime()
            throws Exception {
        String tile = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpTileService.java");
        String activity = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "settings/GboardPatchesSettingsActivity.java");

        Assert.assertTrue(tile.contains(
                "boolean requestedEnabled = controller.toggle();"));
        Assert.assertTrue(tile.contains("updateTile(requestedEnabled);"));
        Assert.assertFalse(tile.contains("requestVisibleFeatureRefresh("));
        Assert.assertTrue(tile.contains("scheduleRuntimeReconciliation();"));
        Assert.assertTrue(tile.contains("RUNTIME_RECONCILIATION_DELAYS_MS"));
        Assert.assertTrue(tile.contains("mainHandler.postDelayed("));
        Assert.assertTrue(tile.contains("state.active()"));
        Assert.assertTrue(tile.contains("GboardSettingsText.get(this,"));
        Assert.assertFalse(tile.contains("getString("));
        Assert.assertTrue(tile.indexOf("tile.setState(")
                < tile.indexOf("tile.setSubtitle("));
        Assert.assertTrue(tile.indexOf("tile.updateTile();")
                < tile.indexOf("tile.setSubtitle("));
        Assert.assertTrue(tile.contains("catch (Throwable ignored)"));
        Assert.assertFalse(tile.contains("postAtTime("));
        Assert.assertTrue(activity.contains("syncVisibleFeatureLifecycle();"));
    }

    @Test
    public void quiescePrecedesQueuedShutdownAndLocksReleaseLast() throws Exception {
        String service = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpService.java");

        int onDestroy = service.indexOf("public void onDestroy()");
        int onBind = service.indexOf("public IBinder onBind", onDestroy);
        String destroyBody = service.substring(onDestroy, onBind);
        Assert.assertTrue(destroyBody.indexOf("quiesceCurrentRuntime();")
                < destroyBody.indexOf("scheduleRuntimeShutdown("));

        int shutdown = service.indexOf("private void shutdownRuntime()");
        int registerNetwork = service.indexOf("private void registerNetworkCallback", shutdown);
        String shutdownBody = service.substring(shutdown, registerNetwork);
        Assert.assertTrue(shutdownBody.indexOf("currentEngine.stop();")
                < shutdownBody.lastIndexOf("runtimeLocks.release();"));

        int apply = service.indexOf("private void applySnapshotSafely");
        int orderlyStop = service.indexOf("private void requestOrderlyStop", apply);
        String applyBody = service.substring(apply, orderlyStop);
        Assert.assertFalse(applyBody.contains("cleanupExpiredPartials("));
        Assert.assertTrue(applyBody.indexOf("shutdownRuntime();")
                < applyBody.indexOf("replacement.start();"));
        Assert.assertTrue(applyBody.contains("engine != replacement"));
    }

    @Test
    public void asynchronousStartupFailurePersistsDisabledAndIsObservedByController()
            throws Exception {
        String service = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpService.java");
        String provider = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpStatusProvider.java");
        String controller = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpServerController.java");

        int failure = service.indexOf("private void failAndStop(");
        int scheduling = service.indexOf("private void scheduleRuntimeShutdown", failure);
        String body = service.substring(failure, scheduling);
        Assert.assertTrue(body.indexOf("LanFtpPreferences.setEnabled(")
                < body.indexOf("publishRuntimeStatus("));
        Assert.assertTrue(provider.contains("KEY_STARTUP_FAILED"));
        Assert.assertTrue(controller.contains(
                "result.getBoolean(LanFtpStatusProvider.KEY_STARTUP_FAILED"));
    }

    @Test
    public void rapidToggleUsesLatestLifecycleCommandAndCanStopInactiveEnabledState()
            throws Exception {
        String service = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpService.java");
        String controller = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpServerController.java");
        String notification = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpNotificationController.java");

        Assert.assertTrue(controller.contains("if (current.config().enabled())"));
        Assert.assertFalse(controller.contains(
                "if (current.config().enabled() && current.active())"));
        Assert.assertTrue(service.contains("lifecycleExecutor.replaceQueuedTask("));
        Assert.assertTrue(service.contains("int requestedStopStartId = latestStartId;"));
        Assert.assertTrue(service.contains(
                "long requestedStopGeneration = generation.incrementAndGet();"));
        Assert.assertTrue(service.contains(
                "if (requestedStopGeneration != generation.get())"));
        Assert.assertTrue(service.contains("stopSelfResult(requestedStopStartId)"));
        Assert.assertTrue(service.contains(
                "stopServiceAfterFailure(requestedStopGeneration, requestedStopStartId)"));
        Assert.assertFalse(service.contains("stopSelfResult(latestStartId)"));
        Assert.assertTrue(service.contains("failed = false;\n"
                + "            publishRuntimeStatus(LanFtpRuntimeStatus.starting(), false);"));
        Assert.assertTrue(notification.contains(
                "ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE"));
        Assert.assertFalse(notification.contains(
                "ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC"));
    }

    @Test
    public void runtimeLocksUseHighPerformanceWifiMode() throws Exception {
        String locks = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpRuntimeLockController.java");

        Assert.assertTrue(locks.contains("WifiManager.WIFI_MODE_FULL_HIGH_PERF"));
        Assert.assertFalse(locks.contains("WifiManager.WIFI_MODE_FULL,"));
        Assert.assertTrue(locks.contains("GboardPatches:LanFtpRuntime"));
    }

    @Test
    public void runtimeLockReleaseRetriesWithoutDiscardingFailedHandles() throws Exception {
        String locks = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpRuntimeLockController.java");

        Assert.assertTrue(locks.contains("RELEASE_ATTEMPTS = 2"));
        Assert.assertTrue(locks.contains(
                "for (int attempt = 0; wakeLock != null && attempt < RELEASE_ATTEMPTS;"));
        Assert.assertTrue(locks.contains(
                "for (int attempt = 0; wifiLock != null && attempt < RELEASE_ATTEMPTS;"));
        Assert.assertFalse(locks.contains("}\n        wakeLock = null;"));
        Assert.assertFalse(locks.contains("}\n        wifiLock = null;"));
    }

    @Test
    public void engineTeardownContainsEachIndependentResourcePhase() throws Exception {
        String engine = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/runtime/LanFtpEngine.java");

        Assert.assertTrue(engine.contains("closeTransfers(configuredTransfers);"));
        Assert.assertTrue(engine.contains("closeTransfers(currentTransfers);"));
        Assert.assertFalse(engine.contains("configuredTransfers.close();"));
        Assert.assertTrue(engine.contains(
                "currentSessionExecutor.shutdownNow();\n"
                        + "            } catch (Throwable ignored)"));
        Assert.assertTrue(engine.contains(
                "currentTransfers.quiesce();\n"
                        + "            } catch (Throwable ignored)"));
    }

    @Test
    public void activeListenerKeepsRuntimeLocksHeldBeforeAnyClientConnects() throws Exception {
        String service = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpService.java");

        int lockUpdateStart = service.indexOf("private void updateRuntimeLocksLocked()");
        int lockUpdateEnd = service.indexOf(
                "private String runningNotificationStatusLocked()", lockUpdateStart);
        Assert.assertTrue(lockUpdateStart >= 0 && lockUpdateEnd > lockUpdateStart);
        String lockUpdate = service.substring(lockUpdateStart, lockUpdateEnd);
        Assert.assertTrue(lockUpdate.contains("if (engine != null && !destroyed)"));
        Assert.assertFalse(lockUpdate.contains("sessionCount > 0 || transferCount > 0"));
        Assert.assertTrue(service.indexOf("updateRuntimeLocksLocked()")
                != service.lastIndexOf("updateRuntimeLocksLocked()"));
        int listenerStarted = service.indexOf("replacement.start();");
        int runningPublished = service.indexOf(
                "publishRunningStateLocked(false);", listenerStarted);
        Assert.assertTrue(listenerStarted >= 0 && runningPublished > listenerStarted);
        Assert.assertTrue(service.substring(listenerStarted, runningPublished)
                .contains("updateRuntimeLocksLocked();"));
    }

    @Test
    public void engineOwnsSessionCallbacksBeforeListenerCanAccept() throws Exception {
        String service = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpService.java");

        int enginePublished = service.indexOf("engine = replacement;");
        int listenerStarted = service.indexOf("replacement.start();");
        int networkCallbackRegistered = service.indexOf("registerNetworkCallback(network);");
        Assert.assertTrue(enginePublished >= 0);
        Assert.assertTrue(listenerStarted >= 0);
        Assert.assertTrue(networkCallbackRegistered >= 0);
        Assert.assertTrue(enginePublished < listenerStarted);
        Assert.assertFalse(service.substring(listenerStarted, networkCallbackRegistered)
                .contains("sessionCount = 0;"));
    }

    @Test
    public void startupFailureLeavesAnActionableNonOngoingNotification() throws Exception {
        String service = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpService.java");
        String notifications = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpNotificationController.java");

        Assert.assertTrue(service.contains("notifications.updateFailure(reason);"));
        Assert.assertTrue(service.contains("notifications.stop(failed);"));
        Assert.assertTrue(notifications.contains("void updateFailure(String status)"));
        Assert.assertTrue(notifications.contains("failurePublished"));
        Assert.assertTrue(notifications.contains("if (failurePublished)"));
        Assert.assertTrue(notifications.contains(".setOngoing(!failure)"));
        Assert.assertTrue(notifications.contains("if (!failure)"));
        Assert.assertTrue(notifications.contains("STOP_FOREGROUND_DETACH"));
        Assert.assertTrue(notifications.contains("controller.settingsPendingIntent(0)"));
        Assert.assertTrue(notifications.contains("controller.stopPendingIntent(1)"));
    }

    @Test
    public void startCommandCarriesCompleteImmutableConfigurationSnapshot() throws Exception {
        String service = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpService.java");
        String codec = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpServiceCommand.java");
        for (String extra : new String[]{
                "EXTRA_CONTROL_PORT",
                "EXTRA_PASSIVE_START",
                "EXTRA_PASSIVE_END",
                "EXTRA_MAX_SESSIONS",
                "EXTRA_IDLE_TIMEOUT_MS",
                "EXTRA_READ_ONLY",
                "EXTRA_ALLOW_ANONYMOUS",
                "EXTRA_USERNAME",
                "EXTRA_PASSWORD",
                "EXTRA_ROOT_TREE_URI"}) {
            Assert.assertTrue(extra, codec.contains(".putExtra(" + extra));
        }
        Assert.assertTrue(service.contains("Intent snapshotIntent = new Intent(intent)"));
        Assert.assertTrue(service.contains("LanFtpServiceCommand.decode(intent)"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
