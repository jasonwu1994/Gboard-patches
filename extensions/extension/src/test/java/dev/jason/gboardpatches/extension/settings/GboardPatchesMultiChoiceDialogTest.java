package dev.jason.gboardpatches.extension.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.AlertDialog;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class GboardPatchesMultiChoiceDialogTest {
    @Test
    public void nativeMultiChoiceListDoesNotCompeteWithACustomPanel() throws Exception {
        String activity = new String(Files.readAllBytes(Path.of(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesSettingsActivity.java")), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        int methodStart = activity.indexOf("public void showMultiChoiceDialog(");
        int methodEnd = activity.indexOf("\n    @Override", methodStart + 1);
        String method = activity.substring(methodStart, methodEnd);

        assertFalse("A custom panel can consume the AlertDialog button panel on long lists",
                method.contains("builder.setView("));
    }

    @Test
    public void longModuleListKeepsConfirmationButtonOnScreen() {
        GboardPatchesSettingsActivity activity = Robolectric.buildActivity(
                GboardPatchesSettingsActivity.class).setup().get();
        String[] labels = new String[15];
        String[] values = new String[15];
        boolean[] selected = new boolean[15];
        for (int index = 0; index < labels.length; index++) {
            labels[index] = "Module " + index + " (1)";
            values[index] = "module-" + index;
            selected[index] = true;
        }

        activity.showMultiChoiceDialog(
                "Choose modules",
                labels,
                values,
                selected,
                "Restore selected",
                ignored -> { });

        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        assertNotNull(dialog.getListView());
        assertEquals(15, dialog.getListView().getAdapter().getCount());
        assertTrue("Module rows must be attached and visible", dialog.getListView().isShown());
        assertNotNull(dialog.getButton(AlertDialog.BUTTON_POSITIVE));

        View decor = dialog.getWindow().getDecorView();
        decor.measure(
                View.MeasureSpec.makeMeasureSpec(1220, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2712, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, decor.getMeasuredWidth(), decor.getMeasuredHeight());

        assertTrue("The confirmation button must retain visible height",
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).getHeight() > 0);

        dialog.dismiss();
        activity.finish();
    }
}
