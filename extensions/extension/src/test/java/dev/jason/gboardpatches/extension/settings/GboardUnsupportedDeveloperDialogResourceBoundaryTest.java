package dev.jason.gboardpatches.extension.settings;

import android.app.AlertDialog;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;
import dev.jason.gboardpatches.extension.R;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "en-rUS")
public final class GboardUnsupportedDeveloperDialogResourceBoundaryTest {
    private static final int TITLE = R.string.gboard_patches_developer_options_package_warning_title;
    private static final int CLOSE = R.string.gboard_patches_dialog_close;
    private static final int TRY_ANYWAY = R.string.gboard_patches_developer_options_try_anyway;
    private static final String PACKAGE = "dev.jason.unsupported.test";

    @Test public void missingModuleTitleStillShowsTheRealWarning() throws Throwable {
        assertProductionWarning(new int[]{TITLE}, true);
    }

    @Test public void missingModuleCloseLabelStillShowsTheRealWarning() throws Throwable {
        assertProductionWarning(new int[]{CLOSE}, true);
    }

    @Test public void missingModuleTryAnywayLabelStillShowsTheRealWarning() throws Throwable {
        assertProductionWarning(new int[]{TRY_ANYWAY}, true);
    }

    @Test public void reusedTargetIdsCannotReplaceTitleOrEitherButtonWithUnrelatedText() throws Throwable {
        assertProductionWarning(new int[]{TITLE, CLOSE, TRY_ANYWAY}, false);
    }

    private void assertProductionWarning(int[] unavailableOrReusedIds, boolean missing) throws Throwable {
        ActivityController<GboardPatchesSettingsActivity> controller = Robolectric.buildActivity(
                GboardPatchesSettingsActivity.class).setup();
        GboardPatchesSettingsActivity activity = controller.get();
        GboardPatchesSettings.preferences(activity).edit().putString("pref_settings_ui_language", "en").commit();
        Resources original = activity.getResources();
        TargetTableResources target = new TargetTableResources(original, unavailableOrReusedIds, missing);
        ReflectionHelpers.setField(activity, "mResources", target);
        assertSame("test must replace the real Context resource boundary", target, activity.getResources());
        try {
            for (int id : unavailableOrReusedIds) {
                if (missing) assertThrows(Resources.NotFoundException.class, () -> activity.getText(id));
                else assertEquals("UNRELATED_TARGET_TEXT_" + id, activity.getText(id).toString());
            }
            target.stringLookups.clear();

            Method show = GboardPatchesSettingsActivity.class.getDeclaredMethod(
                    "showUnsupportedDeveloperOptionsPackageDialog", String.class, int.class);
            show.setAccessible(true);
            try {
                show.invoke(activity, PACKAGE, 0x7f140a6d);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }

            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull("must be an actual production-created AlertDialog", dialog);
            assertTrue(dialog.isShowing());
            assertEquals("Unsupported Gboard package", Shadows.shadowOf(dialog).getTitle().toString());
            assertTrue(Shadows.shadowOf(dialog).getMessage().toString().contains(PACKAGE));
            assertEquals("Close", dialog.getButton(AlertDialog.BUTTON_NEGATIVE).getText().toString());
            assertEquals("Try anyway", dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
            assertTrue("none of the three library string IDs may be resolved through target Resources",
                    target.stringLookups.isEmpty());
            assertNull("rendering a warning must not launch the unsupported native Activity",
                    Shadows.shadowOf(activity).getNextStartedActivity());
        } finally {
            ReflectionHelpers.setField(activity, "mResources", original);
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
            controller.pause().stop().destroy();
        }
    }

    private static final class TargetTableResources extends Resources {
        final int[] watchedIds;
        final boolean missing;
        final List<Integer> stringLookups = new ArrayList<>();

        TargetTableResources(Resources original, int[] watchedIds, boolean missing) {
            super(original.getAssets(), original.getDisplayMetrics(), new Configuration(original.getConfiguration()));
            this.watchedIds = watchedIds;
            this.missing = missing;
        }

        @Override public CharSequence getText(int id) throws NotFoundException {
            for (int watched : watchedIds) {
                if (id == watched) {
                    stringLookups.add(id);
                    if (missing) throw new NotFoundException("module string absent from target table: " + id);
                    return "UNRELATED_TARGET_TEXT_" + id;
                }
            }
            return super.getText(id);
        }
    }
}
