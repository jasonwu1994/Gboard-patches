package dev.jason.gboardpatches.extension.backuprestore;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import dev.jason.gboardpatches.extension.spacebarlogo.GboardSpacebarLogoSettings;
import dev.jason.gboardpatches.extension.spacebarlogo.GboardSpacebarLogoImages;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import static org.junit.Assert.*;

/** Exercises the real Patches serializer/restorer with normalized image bytes, not a file URI. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GboardSpacebarBackupRoundTripTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Test public void customPngBytesColorSizeAndTintSurviveExportClearAndRestore() throws Exception {
        Map<String, SharedPreferences> stores = stores();
        SharedPreferences core = stores.get(GboardPatchesSettings.PREF_FILE);
        String image = image(Color.GREEN);
        assertTrue(GboardSpacebarLogoSettings.writeImage(core, image));
        assertTrue(core.edit().putBoolean(GboardSpacebarLogoSettings.PREF_KEY_ENABLED, true).commit());
        assertTrue(core.edit().putString(GboardSpacebarLogoSettings.PREF_KEY_COLOR, "#CC123456")
                .putString(GboardSpacebarLogoSettings.PREF_KEY_COLOR_SELECTION, "CUSTOM")
                .putString(GboardSpacebarLogoSettings.PREF_KEY_CUSTOM_COLOR, "#CC123456").commit());
        assertTrue(GboardSpacebarLogoSettings.writeImageTint(core, true));
        assertTrue(GboardSpacebarLogoSettings.writeSizeDp(core, 30));
        Map<String, ?> expected = core.getAll();

        String json = GboardPatchesBackupManager.exportBackup(stores);
        JSONObject entries = coreEntries(json);
        assertEquals("string", entries.getJSONObject(GboardSpacebarLogoSettings.PREF_KEY_IMAGE).getString("type"));
        assertEquals(image, entries.getJSONObject(GboardSpacebarLogoSettings.PREF_KEY_IMAGE).getString("value"));
        assertEquals("int", entries.getJSONObject(GboardSpacebarLogoSettings.PREF_KEY_SIZE_DP).getString("type"));
        assertFalse(json.contains("content://"));

        core.edit().clear().commit();
        // Warm the image cache with different pixels before restoring the portable backup.
        String replacement = image(Color.BLUE);
        GboardSpacebarLogoSettings.writeImage(core, replacement);
        GboardSpacebarLogoImages.customDrawable(context.getResources(), replacement);
        var restored = GboardPatchesBackupManager.restoreBackup(stores, json, Set.of("gboard-patches.core"));
        assertTrue(restored.isSuccess());
        assertEquals(expected, core.getAll());
        assertEquals(30, GboardSpacebarLogoSettings.readSizeDp(core));
        assertTrue(GboardSpacebarLogoSettings.readImageTint(core));
        assertArrayEquals(Base64.getDecoder().decode(image),
                Base64.getDecoder().decode(GboardSpacebarLogoSettings.readImage(core)));
        Bitmap bitmap = ((BitmapDrawable) GboardSpacebarLogoImages.customDrawable(context.getResources(),
                GboardSpacebarLogoSettings.readImage(core))).getBitmap();
        assertEquals(20, bitmap.getWidth());
        assertEquals(12, bitmap.getHeight());
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)));
        assertEquals(Color.GREEN, bitmap.getPixel(15, 6));
    }

    @Test public void allIconChoicesAndOriginalColorsRoundTripByStableValue() throws Exception {
        Map<String, SharedPreferences> stores = stores();
        SharedPreferences core = stores.get(GboardPatchesSettings.PREF_FILE);
        GboardSpacebarLogoSettings.writeImage(core, image(Color.RED));
        GboardSpacebarLogoSettings.writeSizeDp(core, 27);
        core.edit().putString(GboardSpacebarLogoSettings.PREF_KEY_COLOR, "AUTO").commit();
        for (String icon : new String[] {GboardSpacebarLogoSettings.ICON_GOOGLE_G,
                GboardSpacebarLogoSettings.ICON_GOOGLE_G_COLOR, GboardSpacebarLogoSettings.ICON_HEART,
                GboardSpacebarLogoSettings.ICON_EMOJI, GboardSpacebarLogoSettings.ICON_CUSTOM_IMAGE}) {
            core.edit().putString(GboardSpacebarLogoSettings.PREF_KEY_ICON, icon).commit();
            Map<String, ?> expected = core.getAll();
            String json = GboardPatchesBackupManager.exportBackup(stores);
            core.edit().clear().commit();
            assertTrue(GboardPatchesBackupManager.restoreBackup(stores, json).isSuccess());
            assertEquals(expected, core.getAll());
            assertEquals(icon, GboardSpacebarLogoSettings.readIcon(core));
            assertEquals(27, GboardSpacebarLogoSettings.readSizeDp(core));
            assertEquals("AUTO", GboardSpacebarLogoSettings.readColorHex(core));
            assertFalse(GboardSpacebarLogoSettings.readImageTint(core));
        }
    }

    @Test public void olderCoreBackupClearsAbsentImageAndUsesTheNewDefaultSize() throws Exception {
        Map<String, SharedPreferences> stores = stores();
        SharedPreferences core = stores.get(GboardPatchesSettings.PREF_FILE);
        core.edit().putString(GboardSpacebarLogoSettings.PREF_KEY_ICON, GboardSpacebarLogoSettings.ICON_HEART).commit();
        String oldBackup = GboardPatchesBackupManager.exportBackup(stores);
        GboardSpacebarLogoSettings.writeImage(core, image(Color.RED));
        GboardSpacebarLogoSettings.writeImageTint(core, true);
        GboardSpacebarLogoSettings.writeSizeDp(core, 40);
        assertTrue(GboardPatchesBackupManager.restoreBackup(stores, oldBackup).isSuccess());
        assertEquals(GboardSpacebarLogoSettings.ICON_HEART, GboardSpacebarLogoSettings.readIcon(core));
        assertEquals("", GboardSpacebarLogoSettings.readImage(core));
        assertFalse(GboardSpacebarLogoSettings.readImageTint(core));
        assertEquals(24, GboardSpacebarLogoSettings.readSizeDp(core));
    }

    @Test public void alternateCoreSpacebarKeysRestoreIntoTheCurrentSettings() throws Exception {
        Map<String, SharedPreferences> stores = stores();
        SharedPreferences core = stores.get(GboardPatchesSettings.PREF_FILE);
        GboardSpacebarLogoSettings.writeImage(core, image(Color.RED));
        GboardSpacebarLogoSettings.writeSizeDp(core, 30);
        Map<String, ?> expected = core.getAll();
        String portable = GboardPatchesBackupManager.exportBackup(stores)
                .replace("pref_spacebar_logo_", "pref_spacebar_g_logo_");
        core.edit().clear().commit();
        assertTrue(GboardPatchesBackupManager.restoreBackup(stores, portable).isSuccess());
        assertEquals(expected, core.getAll());
        assertEquals(30, GboardSpacebarLogoSettings.readSizeDp(core));
        assertFalse(GboardSpacebarLogoSettings.readImage(core).isEmpty());
    }

    @Test public void currentCoreKeysTakePrecedenceOverAnAlternateSpelling() throws Exception {
        Map<String, SharedPreferences> stores = stores();
        SharedPreferences core = stores.get(GboardPatchesSettings.PREF_FILE);
        GboardSpacebarLogoSettings.writeSizeDp(core, 27);
        JSONObject backup = new JSONObject(GboardPatchesBackupManager.exportBackup(stores));
        JSONArray modules = backup.getJSONObject("sections").getJSONObject("gboardPatches").getJSONArray("modules");
        modules.getJSONObject(0).getJSONObject("entries").put("pref_spacebar_g_logo_size_dp",
                new JSONObject().put("type", "int").put("value", "40"));
        core.edit().clear().commit();
        assertTrue(GboardPatchesBackupManager.restoreBackup(stores, backup.toString()).isSuccess());
        assertEquals(27, GboardSpacebarLogoSettings.readSizeDp(core));
    }

    @Test public void restoringCoreRefreshesTheBoundSpacebarWithoutARestart() throws Exception {
        SharedPreferences core = context.getSharedPreferences(GboardPatchesSettings.PREF_FILE, Context.MODE_PRIVATE);
        core.edit().clear().putBoolean(GboardSpacebarLogoSettings.PREF_KEY_ENABLED, true).commit();
        GboardSpacebarLogoSettings.writeImage(core, image(Color.GREEN));
        GboardSpacebarLogoSettings.writeSizeDp(core, 30);
        String backup = GboardPatchesBackupManager.exportBackup(context);
        GboardSpacebarLogoSettings.writeImage(core, image(Color.BLUE));
        GboardSpacebarLogoSettings.writeSizeDp(core, 12);
        android.app.Activity activity = org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();
        android.widget.FrameLayout root = new android.widget.FrameLayout(activity);
        activity.setContentView(root);
        int spaceId = android.view.View.generateViewId(), labelId = android.view.View.generateViewId();
        root.setId(spaceId);
        android.widget.TextView label = new android.widget.TextView(activity);
        label.setId(labelId); root.addView(label);
        var bindings = new dev.jason.gboardpatches.extension.spacebarlogo.GboardSpacebarLogoRuntime.ViewBindings(
                spaceId, labelId, 0, 0, 0);
        dev.jason.gboardpatches.extension.spacebarlogo.GboardSpacebarLogoRuntime.afterSoftKeyBound(
                root, true, GboardSpacebarLogoSettings.snapshot(context), bindings);
        assertEquals(Math.round(12 * context.getResources().getDisplayMetrics().density),
                root.getChildAt(1).getLayoutParams().width);
        assertTrue(GboardPatchesBackupManager.restoreBackup(context, backup, Set.of("gboard-patches.core")).isSuccess());
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(30, GboardSpacebarLogoSettings.readSizeDp(core));
        assertEquals(Math.round(30 * context.getResources().getDisplayMetrics().density),
                root.getChildAt(1).getLayoutParams().width);
        Bitmap bitmap = ((BitmapDrawable) ((android.widget.ImageView) root.getChildAt(1)).getDrawable()).getBitmap();
        assertEquals(Color.GREEN, bitmap.getPixel(15, 6));
    }

    private Map<String, SharedPreferences> stores() {
        Map<String, SharedPreferences> stores = new LinkedHashMap<>();
        for (String name : GboardPatchesBackupManager.physicalStoreNames()) {
            SharedPreferences preferences = context.getSharedPreferences("spacebar-backup-test-" + name, Context.MODE_PRIVATE);
            preferences.edit().clear().commit();
            stores.put(name, preferences);
        }
        return stores;
    }

    private static JSONObject coreEntries(String json) throws Exception {
        JSONArray modules = new JSONObject(json).getJSONObject("sections")
                .getJSONObject("gboardPatches").getJSONArray("modules");
        for (int i = 0; i < modules.length(); i++) {
            JSONObject module = modules.getJSONObject(i);
            if ("gboard-patches.core".equals(module.getString("moduleId"))) return module.getJSONObject("entries");
        }
        throw new AssertionError("Core backup module missing");
    }

    private static String image(int color) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(20, 12, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        bitmap.setPixel(0, 0, Color.TRANSPARENT);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        return GboardSpacebarLogoImages.importImage(output.toByteArray());
    }
}
