package dev.jason.gboardpatches.extension.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public final class GboardBackupSettingsTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void exportAndImportRestoresAllTypes() {
        // Prepare some settings
        String prefName = "test_prefs";
        SharedPreferences prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
        Set<String> stringSet = new HashSet<>();
        stringSet.add("item1");
        stringSet.add("item2");

        prefs.edit()
                .putString("string_key", "string_value")
                .putBoolean("bool_key", true)
                .putInt("int_key", 123)
                .putLong("long_key", 456L)
                .putFloat("float_key", 7.89f)
                .putStringSet("set_key", stringSet)
                .apply();

        // Export
        String json = GboardBackupSettings.exportSettings(context);
        Assert.assertNotNull(json);
        Assert.assertTrue(json.contains("test_prefs:ce"));
        Assert.assertTrue(json.contains("\"t\": \"string\""));
        Assert.assertTrue(json.contains("\"v\": \"string_value\""));

        // Clear and Verify cleared
        prefs.edit().clear().apply();
        Assert.assertFalse(prefs.contains("string_key"));

        // Import
        boolean success = GboardBackupSettings.importSettings(context, json);
        Assert.assertTrue(success);

        // Verify restored
        Assert.assertEquals("string_value", prefs.getString("string_key", null));
        Assert.assertTrue(prefs.getBoolean("bool_key", false));
        Assert.assertEquals(123, prefs.getInt("int_key", 0));
        Assert.assertEquals(456L, prefs.getLong("long_key", 0L));
        Assert.assertEquals(7.89f, prefs.getFloat("float_key", 0f), 0.001f);
        Assert.assertEquals(stringSet, prefs.getStringSet("set_key", null));
    }

    @Test
    public void importWithInvalidJsonReturnsFalse() {
        Assert.assertFalse(GboardBackupSettings.importSettings(context, "invalid json"));
    }
}
