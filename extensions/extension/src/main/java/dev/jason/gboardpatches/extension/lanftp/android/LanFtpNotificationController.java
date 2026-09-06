package dev.jason.gboardpatches.extension.lanftp.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.SystemClock;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpSessionInfo;

/** Owns notification rendering and foreground-service publication. */
final class LanFtpNotificationController {
    private static final String CHANNEL_ID = "lan_ftp_server";
    private static final int NOTIFICATION_ID = 1012;
    private static final long PROGRESS_UPDATE_INTERVAL_MS = 500L;

    private final Service service;
    private final LanFtpServerController controller;
    private long lastProgressUpdateElapsedMs;
    private String lastProgressSessionId = "";
    private boolean failurePublished;

    LanFtpNotificationController(Service service) {
        this.service = service;
        controller = LanFtpServerController.from(service);
    }

    void startForeground() {
        ensureChannel();
        failurePublished = false;
        Notification notification = build("Starting LAN FTP Server");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            service.startForeground(NOTIFICATION_ID, notification);
        }
    }

    synchronized void beginStart() {
        failurePublished = false;
        lastProgressSessionId = "";
        lastProgressUpdateElapsedMs = 0L;
    }

    void update(String status) {
        update(status, null);
    }

    synchronized void update(String status, LanFtpSessionInfo activeTransfer) {
        if (failurePublished) {
            return;
        }
        if (!shouldPublish(activeTransfer)) {
            return;
        }
        try {
            NotificationManager manager = service.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, build(status, activeTransfer));
            }
        } catch (Throwable ignored) {
            // Runtime status remains available through the private provider.
        }
    }

    synchronized void updateFailure(String status) {
        failurePublished = true;
        try {
            NotificationManager manager = service.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, build(status, null, true));
            }
        } catch (Throwable ignored) {
            // Runtime status remains available through the private provider.
        }
    }

    private boolean shouldPublish(LanFtpSessionInfo activeTransfer) {
        if (activeTransfer == null || !activeTransfer.isTransferActive()) {
            lastProgressSessionId = "";
            lastProgressUpdateElapsedMs = 0L;
            return true;
        }
        long now = SystemClock.elapsedRealtime();
        if (!activeTransfer.id().equals(lastProgressSessionId)
                || now - lastProgressUpdateElapsedMs >= PROGRESS_UPDATE_INTERVAL_MS) {
            lastProgressSessionId = activeTransfer.id();
            lastProgressUpdateElapsedMs = now;
            return true;
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    void stop(boolean keepFailureNotification) {
        try {
            if (keepFailureNotification) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    service.stopForeground(Service.STOP_FOREGROUND_DETACH);
                } else {
                    service.stopForeground(false);
                }
            } else {
                service.stopForeground(Service.STOP_FOREGROUND_REMOVE);
            }
        } catch (Throwable ignored) {
            // Best effort.
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = service.getSystemService(NotificationManager.class);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "LAN FTP Server", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Foreground status for the LAN FTP file server.");
            manager.createNotificationChannel(channel);
        }
    }

    private Notification build(String status) {
        return build(status, null);
    }

    private Notification build(String status, LanFtpSessionInfo activeTransfer) {
        return build(status, activeTransfer, false);
    }

    private Notification build(String status, LanFtpSessionInfo activeTransfer,
            boolean failure) {
        PendingIntent openSettings = controller.settingsPendingIntent(0);
        PendingIntent stop = controller.stopPendingIntent(1);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(service, CHANNEL_ID)
                : new Notification.Builder(service);
        LanFtpNotificationContent content = LanFtpNotificationContent.from(
                status, activeTransfer);
        builder
                .setSmallIcon(R.drawable.ic_lan_ftp_tile)
                .setContentTitle("LAN FTP Server")
                .setContentText(content.text())
                .setContentIntent(openSettings)
                .setOngoing(!failure)
                .setAutoCancel(failure)
                .setOnlyAlertOnce(true);
        if (!failure) {
            builder.addAction(new Notification.Action.Builder(0, "Stop", stop).build());
        }
        if (content.indeterminate()) {
            builder.setProgress(0, 0, true);
        } else if (activeTransfer != null && activeTransfer.isTransferActive()) {
            builder.setProgress(100, content.progress(), false);
        } else {
            builder.setProgress(0, 0, false);
        }
        return builder.build();
    }
}
