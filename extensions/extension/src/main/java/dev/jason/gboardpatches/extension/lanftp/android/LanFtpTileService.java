package dev.jason.gboardpatches.extension.lanftp.android;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class LanFtpTileService extends TileService {
    private static final long[] RUNTIME_RECONCILIATION_DELAYS_MS = {
            250L, 500L, 1_000L, 2_000L, 4_000L, 8_000L, 16_000L
    };

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable runtimeReconciliation = this::reconcileRuntimeSafely;
    private int runtimeReconciliationIndex;

    @Override
    public void onStartListening() {
        try {
            super.onStartListening();
            syncTileState();
        } catch (Throwable ignored) {
            updateTile(false);
        }
    }

    @Override
    public void onTileAdded() {
        try {
            super.onTileAdded();
            syncTileState();
        } catch (Throwable ignored) {
            updateTile(false);
        }
    }

    @Override
    public void onClick() {
        try {
            super.onClick();
            if (isLocked()) {
                unlockAndRun(this::toggleTileSafely);
            } else {
                toggleTileSafely();
            }
        } catch (Throwable ignored) {
            updateTile(false);
        }
    }

    private void toggleTileSafely() {
        try {
            LanFtpServerController controller = LanFtpServerController.from(this);
            LanFtpServerState state = controller.requestState();
            if (!state.localNetworkAccess()) {
                openPermissionSettings("Grant local network access, then enable LAN FTP");
                updateTile(false);
                return;
            }
            if (state.config().isSharedStorageRoot() && !state.allFilesAccess()) {
                openPermissionSettings(GboardSettingsText.get(this,
                        R.string.gboard_patches_lan_ftp_all_files_required_message));
                updateTile(false);
                return;
            }
            boolean requestedEnabled = controller.toggle();
            updateTile(requestedEnabled);
            if (requestedEnabled) {
                scheduleRuntimeReconciliation();
            } else {
                cancelRuntimeReconciliation();
            }
        } catch (Throwable ignored) {
            updateTile(false);
        }
    }

    @SuppressWarnings("deprecation")
    private void openPermissionSettings(String message) {
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pendingIntent = LanFtpServerController.from(this)
                    .settingsPendingIntent(0x4c46);
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapse(LanFtpServerController.from(this).settingsIntent());
        }
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG).show();
    }

    private void syncTileState() {
        try {
            LanFtpServerState state = LanFtpServerController.from(this).requestState();
            updateTile(isRuntimeActive(state), state);
        } catch (Throwable ignored) {
            updateTile(false);
        }
    }

    private void scheduleRuntimeReconciliation() {
        try {
            mainHandler.removeCallbacks(runtimeReconciliation);
            runtimeReconciliationIndex = 0;
            scheduleNextRuntimeReconciliation();
        } catch (Throwable ignored) {
            updateTile(false);
        }
    }

    private void scheduleNextRuntimeReconciliation() {
        if (runtimeReconciliationIndex >= RUNTIME_RECONCILIATION_DELAYS_MS.length) {
            return;
        }
        long delayMs = RUNTIME_RECONCILIATION_DELAYS_MS[runtimeReconciliationIndex++];
        mainHandler.postDelayed(runtimeReconciliation, delayMs);
    }

    private void reconcileRuntimeSafely() {
        boolean retry = false;
        try {
            LanFtpServerState state = LanFtpServerController.from(this).requestState();
            boolean active = isRuntimeActive(state);
            updateTile(active, state);
            retry = state.config().enabled() && !active;
        } catch (Throwable ignored) {
            updateTile(false);
            retry = true;
        }
        if (retry) {
            try {
                scheduleNextRuntimeReconciliation();
            } catch (Throwable ignored) {
                updateTile(false);
            }
        }
    }

    private void cancelRuntimeReconciliation() {
        try {
            mainHandler.removeCallbacks(runtimeReconciliation);
            runtimeReconciliationIndex = RUNTIME_RECONCILIATION_DELAYS_MS.length;
        } catch (Throwable ignored) {
            // The requested inactive state was already painted synchronously.
        }
    }

    private static boolean isRuntimeActive(LanFtpServerState state) {
        return state != null
                && state.config().enabled()
                && !state.config().rootTreeUri().isEmpty()
                && state.active();
    }

    private void updateTile(boolean active) {
        updateTile(active, null);
    }

    private void updateTile(boolean active, LanFtpServerState knownState) {
        Tile tile;
        try {
            tile = getQsTile();
        } catch (Throwable ignored) {
            return;
        }
        if (tile == null) {
            return;
        }
        try {
            tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.updateTile();
        } catch (Throwable ignored) {
            // Optional presentation updates below must remain independently contained.
        }
        LanFtpServerController controller;
        try {
            controller = LanFtpServerController.from(this);
        } catch (Throwable ignored) {
            return;
        }
        try {
            tile.setLabel(GboardSettingsText.get(this, R.string.lan_ftp_tile_label));
        } catch (Throwable ignored) {
            // The manifest label remains available if injected resources cannot be resolved.
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                tile.setSubtitle(controller.subtitle(knownState));
            } catch (Throwable ignored) {
                // Vendor System UI implementations may reject optional subtitle updates.
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                tile.setStateDescription(controller.stateDescription(active, knownState));
            } catch (Throwable ignored) {
                // State color must still update if accessibility text cannot be applied.
            }
        }
        try {
            tile.updateTile();
        } catch (Throwable ignored) {
            // Tile callbacks must never escape into System UI.
        }
    }
}
