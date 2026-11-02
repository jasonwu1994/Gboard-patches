package dev.jason.gboardpatches.extension.webclipboard;

import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

public final class WebClipboardTileService extends TileService {
    private static final String TAG = "GboardWebClipboard";
    private static final String TILE_LABEL = "Web Clipboard";

    @Override
    public void onStartListening() {
        try {
            super.onStartListening();
            syncTileState();
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to start listening Web Clipboard tile", throwable);
        }
    }

    @Override
    public void onTileAdded() {
        try {
            super.onTileAdded();
            syncTileState();
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to add Web Clipboard tile", throwable);
        }
    }

    @Override
    public void onClick() {
        try {
            super.onClick();
            if (isLocked()) {
                unlockAndRun(this::toggleTile);
                return;
            }
            toggleTile();
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to handle Web Clipboard tile click", throwable);
        }
    }

    @SuppressWarnings("deprecation")
    private void toggleTile() {
        if(!WebClipboardPreferences.isEnabled(WebClipboardPreferences.preferences(this))
                && !WebClipboardLocalNetworkPermission.isGranted(this)) {
            android.content.Intent settings=new android.content.Intent(this,
                    dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putStringArrayListExtra(
                        dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsActivity.EXTRA_NAVIGATION_PATH,
                        new java.util.ArrayList<>(java.util.Arrays.asList(
                            "dev.jason.gboardpatches.extension.clipboard.GboardClipboardSettingsFeature",
                            "dev.jason.gboardpatches.extension.clipboard.GboardWebClipboardSettingsFeature")));
            if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(android.app.PendingIntent.getActivity(
                    this,0x5743,settings,android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE));
            else startActivityAndCollapse(settings);
            android.widget.Toast.makeText(this,"Grant local network permission, then enable Web Clipboard",android.widget.Toast.LENGTH_LONG).show();
            updateTile(false);return;
        }
        boolean enabled = WebClipboardTileController.toggle(this);
        updateTile(enabled);
    }

    private void syncTileState() {
        SharedPreferences preferences = WebClipboardPreferences.preferences(this);
        boolean serviceRunning = WebClipboardTileController.isServiceRunning(this);
        if (WebClipboardTileController.shouldRestoreService(preferences, serviceRunning)) {
            ClipboardSyncService.startOrUpdate(this);
        }
        updateTile(WebClipboardTileController.isTileActive(preferences, serviceRunning));
    }

    private void updateTile(boolean enabled) {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        SharedPreferences preferences = WebClipboardPreferences.preferences(this);
        int port = WebClipboardPreferences.getPort(preferences);
        java.util.List<String> urls = WebClipboardEndpointResolver.resolveUrls(port);
        tile.setLabel(TILE_LABEL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(WebClipboardTileController.buildStatusSubtitle(urls));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.setStateDescription(
                    WebClipboardTileController.buildStateDescription(enabled, urls));
        }
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}

