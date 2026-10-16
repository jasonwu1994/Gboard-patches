package dev.jason.gboardpatches.extension.duckemail;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowAlertDialog;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsTestHost;
import static dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class DuckEmailSettingsFeatureTest {
    private Activity activity;
    private DuckEmailSettingsFeature feature;
    private GboardPatchesSettingsTestHost host;
    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences(DuckEmailStore.FILE, 0).edit().clear().commit();
        GboardPatchesSettings.preferences(activity).edit()
                .putString("pref_settings_ui_language", "en").commit();
        activity.getApplicationInfo().metaData = new Bundle();
        feature = new DuckEmailSettingsFeature(activity);
        host = new GboardPatchesSettingsTestHost(activity);
    }
    @Test public void settingsRequireOwnPatchAndToggleControlsMenuAdmission() {
        assertFalse(feature.isAvailable(activity));
        activity.getApplicationInfo().metaData.putBoolean(
                GboardPatchesFeatureAvailability.FEATURE_DUCK_EMAIL, true);
        assertTrue(feature.isAvailable(activity));
        Screen screen = feature.buildScreen(host);
        assertFalse(screen.getRows().get(3).isEnabled());
        ToggleRow toggle = (ToggleRow) screen.getRows().get(0);
        assertTrue(toggle.isChecked());
        toggle.getToggleAction().accept(false);
        assertFalse(DuckEmailRuntime.enabled(activity));
    }
    @Test public void addressDialogKeepsInvalidInputOpenWithoutSendingAndCancelClearsIt() {
        ((ActionRow) feature.buildScreen(host).getRows().get(2)).getAction().run();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        assertEquals("Send code", dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        EditText input = input(dialog.getWindow().getDecorView());
        input.setText("not-a-duck@gmail.com");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(dialog.isShowing());
        assertNotNull(input.getError());
        assertEquals("", new DuckEmailStore(activity).pending());
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("", input.getText().toString());
        assertEquals(1, host.getManagedDialogDismissCount());
    }
    @Test public void pendingLoginCanBeResumedWithExplicitSignInAndCodeIsNotRetained() {
        DuckEmailStore store = new DuckEmailStore(activity);
        assertTrue(store.pending("example", store.revision()));
        ActionRow code = (ActionRow) feature.buildScreen(host).getRows().get(3);
        assertTrue(code.isEnabled());
        code.getAction().run();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("Sign in", dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        EditText input = input(dialog.getWindow().getDecorView());
        input.setText("one two three four");
        dialog.cancel();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("", input.getText().toString());
        assertFalse(activity.getSharedPreferences(DuckEmailStore.FILE, 0).getAll()
                .containsValue("one two three four"));
    }
    @Test public void copyToggleDefaultsOffAndPersistsAcrossSettingsAndSignOut() {
        ToggleRow toggle = (ToggleRow) feature.buildScreen(host).getRows().get(1);
        assertEquals("Copy generated email to clipboard", toggle.getTitle().toString());
        assertFalse(toggle.isChecked());
        toggle.getToggleAction().accept(true);
        new DuckEmailStore(activity).signOut();
        DuckEmailSettingsFeature reopened = new DuckEmailSettingsFeature(activity);
        ToggleRow restored = (ToggleRow) reopened.buildScreen(host).getRows().get(1);
        assertTrue(restored.isChecked());
        restored.getToggleAction().accept(false);
        assertFalse(new DuckEmailStore(activity).copyGeneratedEmail());
    }
    private EditText input(View view) {
        if (view instanceof EditText field) return field;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText field = input(group.getChildAt(i));
                if (field != null) return field;
            }
        }
        return null;
    }
}
