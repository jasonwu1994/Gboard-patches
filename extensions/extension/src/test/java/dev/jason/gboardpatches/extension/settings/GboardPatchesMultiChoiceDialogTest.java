package dev.jason.gboardpatches.extension.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
        AtomicReference<List<String>> chosen = new AtomicReference<>();

        activity.showMultiChoiceDialog(
                "Choose modules",
                labels,
                values,
                selected,
                "Restore selected",
                chosen::set);

        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        View decor = dialog.getWindow().getDecorView();
        decor.measure(
                View.MeasureSpec.makeMeasureSpec(1220, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2712, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, decor.getMeasuredWidth(), decor.getMeasuredHeight());

        List<CheckBox> checkBoxes = new ArrayList<>();
        collect(decor, CheckBox.class, checkBoxes);
        assertEquals(15, checkBoxes.size());
        TextView positive = findText(decor, "Restore selected");
        assertNotNull(positive);
        assertTrue("The confirmation button must retain visible height",
                positive.getHeight() > 0);

        // Clearing one row and confirming returns the remaining selection only.
        ((View) checkBoxes.get(0).getParent()).performClick();
        assertTrue(positive.performClick());
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertNotNull(chosen.get());
        assertEquals(14, chosen.get().size());
        assertFalse(chosen.get().contains("module-0"));

        activity.finish();
    }

    @Test
    public void sheetIsFullWidthBeforeItsFirstFrame() {
        GboardPatchesSettingsActivity activity = Robolectric.buildActivity(
                GboardPatchesSettingsActivity.class).setup().get();
        activity.showMultiChoiceDialog(
                "Choose modules",
                new String[] {"Only"},
                new String[] {"only"},
                new boolean[] {true},
                "Export",
                ignored -> { });

        // The show listener is still queued here, so only a synchronous resize counts.
        Dialog dialog = ShadowDialog.getLatestDialog();
        android.view.WindowManager.LayoutParams attributes = dialog.getWindow().getAttributes();
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, attributes.width);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, attributes.height);
        dialog.dismiss();
        activity.finish();
    }

    @Test
    public void confirmationIsDisabledWhenNothingIsSelected() {
        GboardPatchesSettingsActivity activity = Robolectric.buildActivity(
                GboardPatchesSettingsActivity.class).setup().get();
        activity.showMultiChoiceDialog(
                "Choose modules",
                new String[] {"Only"},
                new String[] {"only"},
                new boolean[] {false},
                "Restore selected",
                ignored -> { });

        Dialog dialog = ShadowDialog.getLatestDialog();
        TextView positive = findText(dialog.getWindow().getDecorView(), "Restore selected");
        assertNotNull(positive);
        assertFalse(positive.isEnabled());
        dialog.dismiss();
        activity.finish();
    }

    private static <T extends View> void collect(View view, Class<T> type, List<T> out) {
        if (type.isInstance(view)) {
            out.add(type.cast(view));
        }
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                collect(group.getChildAt(index), type, out);
            }
        }
    }

    private static TextView findText(View root, String text) {
        List<TextView> views = new ArrayList<>();
        collect(root, TextView.class, views);
        for (TextView view : views) {
            if (!(view instanceof CheckBox) && text.contentEquals(view.getText())) {
                return view;
            }
        }
        return null;
    }
}
