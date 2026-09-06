package dev.jason.gboardpatches.extension.settings;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;

/** Generic Quick Settings preferences dispatcher driven by tile-service metadata. */
public final class GboardTilePreferencesActivity extends Activity {
    public static final String META_DATA_NAVIGATION_PATH =
            "dev.jason.gboardpatches.tile.NAVIGATION_PATH";
    public static final String PATH_SEPARATOR = ";";
    private static final String TAG = "GboardTileSettings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            ArrayList<String> navigationPath = resolveNavigationPath(getIntent());
            if (!navigationPath.isEmpty()) {
                Intent settings = new Intent(this, GboardPatchesSettingsActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putStringArrayListExtra(
                                GboardPatchesSettingsActivity.EXTRA_NAVIGATION_PATH,
                                navigationPath);
                startActivity(settings);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to dispatch tile preferences", throwable);
        } finally {
            finish();
        }
    }

    private ArrayList<String> resolveNavigationPath(Intent intent)
            throws PackageManager.NameNotFoundException {
        ArrayList<String> result = new ArrayList<>();
        ComponentName tile = intent == null
                ? null
                : intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME);
        if (tile == null || !getPackageName().equals(tile.getPackageName())) {
            return result;
        }
        ServiceInfo service = getPackageManager().getServiceInfo(
                tile, PackageManager.GET_META_DATA);
        String encoded = service.metaData == null
                ? null
                : service.metaData.getString(META_DATA_NAVIGATION_PATH);
        if (encoded == null) {
            return result;
        }
        for (String item : encoded.split(PATH_SEPARATOR, -1)) {
            String id = item.trim();
            if (!id.isEmpty()) {
                result.add(id);
            }
        }
        return result;
    }
}
