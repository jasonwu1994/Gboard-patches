package dev.jason.gboardpatches.extension.lanftp.android;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.service.quicksettings.TileService;

import java.util.ArrayList;
import java.util.List;

import dev.jason.gboardpatches.extension.lanftp.config.LanFtpPreferences;
import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpSessionInfo;
import dev.jason.gboardpatches.extension.lanftp.settings.GboardLanFtpSettingsFeature;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsActivity;

/** The single control seam for LAN FTP callers outside the runtime implementation. */
public final class LanFtpServerController {
    private final Context context;

    private LanFtpServerController(Context context) {
        this.context = context;
    }

    public static LanFtpServerController from(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }
        Context application = context.getApplicationContext();
        return new LanFtpServerController(application == null ? context : application);
    }

    public LanFtpServerConfigSnapshot config() {
        return LanFtpPreferences.read(preferences()).toConfigSnapshot();
    }

    public boolean start() {
        return applyConfig(config().withEnabled(true));
    }

    public void stop() {
        LanFtpServerConfigSnapshot current = config();
        LanFtpPreferences.write(preferences(), current.withEnabled(false));
        LanFtpService.requestStop(context);
        requestTileRefresh();
    }

    public boolean toggle() {
        LanFtpServerState current = requestState();
        if (current.config().enabled()) {
            stop();
            return false;
        }
        return start();
    }

    public boolean applyConfig(LanFtpServerConfigSnapshot requested) {
        if (requested == null) {
            return false;
        }
        if (requested.enabled() && !hasLocalNetworkAccess()) {
            stop();
            return false;
        }
        if (requested.enabled() && isSharedStorageRoot(requested.rootTreeUri())
                && !hasAllFilesAccess()) {
            stop();
            return false;
        }
        if (!LanFtpPreferences.write(preferences(), requested)) {
            return false;
        }
        LanFtpServerConfigSnapshot persisted = config();
        if (persisted.enabled()) {
            if (!LanFtpService.requestStart(context, persisted)) {
                LanFtpPreferences.setEnabled(preferences(), false);
                requestTileRefresh();
                return false;
            }
        } else {
            LanFtpService.requestStop(context);
        }
        requestTileRefresh();
        return persisted.enabled() == requested.enabled();
    }

    public boolean setPassword(String password) {
        if (!LanFtpPreferences.setPassword(preferences(), password)) {
            return false;
        }
        reloadIfEnabled();
        return true;
    }

    public String regeneratePassword() {
        String password = LanFtpPreferences.regeneratePassword(preferences());
        if (!password.isEmpty()) {
            reloadIfEnabled();
        }
        return password;
    }

    public LanFtpServerState requestState() {
        LanFtpServerConfigSnapshot config = config();
        LanFtpRuntimeStatus runtime = queryRuntime();
        if (runtime.startupFailed && config.enabled()) {
            LanFtpPreferences.setEnabled(preferences(), false);
            config = config();
            requestTileRefresh();
        }
        boolean localNetworkAccess = runtime.active || hasLocalNetworkAccess();
        boolean allFilesAccess = runtime.active || hasAllFilesAccess();
        return new LanFtpServerState(config, runtime.active, runtime.status, runtime.endpoint,
                runtime.clients, runtime.transferCount, runtime.resumeSupported,
                runtime.passwordRevision, localNetworkAccess, allFilesAccess);
    }

    public boolean kick(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        return callSuccess(LanFtpStatusProvider.METHOD_KICK_SESSION, sessionId, null);
    }

    public boolean hasLocalNetworkAccess() {
        return LanFtpLocalNetworkPermission.isGranted(context);
    }

    public boolean hasAllFilesAccess() {
        return LanFtpAllFilesAccess.isGranted(context);
    }

    public boolean isSharedStorageRoot(String root) {
        return LanFtpPreferences.isSharedStorageRoot(root);
    }

    public String subtitle(LanFtpServerState state) {
        if (state != null && state.active() && !state.endpoint().isEmpty()) {
            return state.endpoint();
        }
        return "FTP :" + LanFtpPreferences.sanitizePort(
                state == null ? config().controlPort() : state.config().controlPort());
    }

    public String stateDescription(boolean active, LanFtpServerState state) {
        if (!active && state != null
                && !"Stopped".equals(state.status())
                && !"Unavailable".equals(state.status())) {
            return "LAN FTP inactive: " + state.status();
        }
        return "LAN FTP " + (active ? "active at " : "inactive. Endpoint ")
                + subtitle(state);
    }

    public PendingIntent settingsPendingIntent(int requestCode) {
        return PendingIntent.getActivity(context, requestCode, settingsIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public Intent settingsIntent() {
        Intent intent = new Intent(context, GboardPatchesSettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ArrayList<String> path = new ArrayList<>();
        path.add(GboardLanFtpSettingsFeature.class.getName());
        intent.putStringArrayListExtra(GboardPatchesSettingsActivity.EXTRA_NAVIGATION_PATH, path);
        return intent;
    }

    public PendingIntent stopPendingIntent(int requestCode) {
        return PendingIntent.getService(context, requestCode,
                LanFtpServiceCommand.stopIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    void requestTileRefresh() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        try {
            TileService.requestListeningState(context,
                    new ComponentName(context, LanFtpTileService.class));
        } catch (Throwable ignored) {
            // Best effort.
        }
    }

    private SharedPreferences preferences() {
        return LanFtpPreferences.preferences(context);
    }

    private void reloadIfEnabled() {
        LanFtpServerConfigSnapshot current = config();
        if (current.enabled() && !LanFtpService.requestStart(context, current)) {
            LanFtpPreferences.setEnabled(preferences(), false);
        }
        requestTileRefresh();
    }

    private LanFtpRuntimeStatus queryRuntime() {
        try {
            Bundle result = context.getContentResolver().call(
                    LanFtpStatusProvider.contentUri(context),
                    LanFtpStatusProvider.METHOD_GET_STATUS, null, null);
            if (result == null) {
                return LanFtpRuntimeStatus.unavailable();
            }
            List<LanFtpSessionInfo> clients = LanFtpSessionBundleCodec.decode(
                    result.getParcelableArrayList(LanFtpStatusProvider.KEY_CLIENTS));
            return new LanFtpRuntimeStatus(
                    result.getBoolean(LanFtpStatusProvider.KEY_ACTIVE, false),
                    result.getString(LanFtpStatusProvider.KEY_STATUS, "Unavailable"),
                    result.getString(LanFtpStatusProvider.KEY_ENDPOINT, ""), clients,
                    result.getInt(LanFtpStatusProvider.KEY_TRANSFER_COUNT, 0),
                    result.getBoolean(LanFtpStatusProvider.KEY_RESUME_SUPPORTED, true),
                    result.getLong(LanFtpStatusProvider.KEY_PASSWORD_REVISION, 0L),
                    result.getBoolean(LanFtpStatusProvider.KEY_STARTUP_FAILED, false));
        } catch (Throwable ignored) {
            return LanFtpRuntimeStatus.unavailable();
        }
    }

    private boolean callSuccess(String method, String argument, Bundle extras) {
        try {
            Bundle result = context.getContentResolver().call(
                    LanFtpStatusProvider.contentUri(context), method, argument, extras);
            return result != null && result.getBoolean(LanFtpStatusProvider.KEY_SUCCESS, false);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
