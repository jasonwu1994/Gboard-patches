package dev.jason.gboardpatches.extension.lanftp.settings;

import android.content.Context;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

/** Exercises the same controller-backed render path used by the patched settings activity. */
@RunWith(RobolectricTestRunner.class)
public final class GboardLanFtpSettingsRuntimeTest {
    @Test
    public void controllerBackedPageDoesNotFallBackToFtpSettingsUnavailable() {
        Context context = RuntimeEnvironment.getApplication();
        GboardLanFtpSettingsFeature feature = new GboardLanFtpSettingsFeature(context);

        GboardPatchesSettingsContract.Screen screen = feature.buildScreen(
                new RuntimeHost(context));

        Assert.assertTrue(screen.getStatusBlocks().stream().noneMatch(block ->
                "FTP settings unavailable".contentEquals(block.getTitle())
                        || "無法使用 FTP 設定".contentEquals(block.getTitle())));
        Assert.assertTrue(screen.getRows().stream().anyMatch(row ->
                "Enabled".contentEquals(row.getTitle())
                        || "啟用".contentEquals(row.getTitle())));
    }

    private static final class RuntimeHost implements GboardPatchesSettingsContract.Host {
        private final Context context;

        private RuntimeHost(Context context) {
            this.context = context;
        }

        @Override public Context getContext() { return context; }
        @Override public void refresh() { }
        @Override public void openFeature(GboardPatchesSettingsContract.Feature feature) { }
        @Override public void showChoiceDialog(String title, String[] labels, String[] values,
                String currentValue, String customValue, Runnable customAction,
                GboardPatchesSettingsContract.StringValueConsumer valueConsumer) { }
        @Override public void showPositiveIntegerDialog(String title, String hint,
                int initialValue,
                GboardPatchesSettingsContract.PositiveIntegerConsumer consumer) { }
        @Override public void showTextInputDialog(String title, String hint, String initialValue,
                GboardPatchesSettingsContract.TextValueConsumer consumer) { }
        @Override public void showMessage(String value) { }
        @Override public void showPreviewDialog(
                GboardPatchesSettingsContract.PreviewSpec previewSpec) { }
        @Override public void showManagedDialog(
                GboardPatchesSettingsContract.ManagedDialogAction action) { }
        @Override public void createTextDocument(String fileName, String mimeType, String text,
                Runnable completionAction) { }
        @Override public void openTextDocument(String[] mimeTypes,
                GboardPatchesSettingsContract.StringValueConsumer valueConsumer) { }
        @Override public void openDocumentTree(String initialTreeUri,
                GboardPatchesSettingsContract.StringValueConsumer valueConsumer) { }
        @Override public void openAllFilesAccessSettings(String unavailableMessage) { }
        @Override public void openBatteryOptimizationSettings(String unavailableMessage) { }
    }
}
