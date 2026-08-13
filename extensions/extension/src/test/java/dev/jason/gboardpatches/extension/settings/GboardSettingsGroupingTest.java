package dev.jason.gboardpatches.extension.settings;

import android.content.Context;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class GboardSettingsGroupingTest {
    @Test
    public void featureGroupBuildsSingleNavigationSectionForAvailableChildren() throws Exception {
        FakeFeature unavailableFeature = new FakeFeature("Legacy", "Hidden", false);
        FakeFeature availableFeature = new FakeFeature("Tab Order", "Reorder tabs", true);
        RecordingHost host = new RecordingHost();
        GboardFeatureGroup group = new GboardFeatureGroup(
                "Keyboard",
                "Keyboard-related patch settings.",
                "Gboard",
                "Keyboard-level settings for the patches included in this build.",
                "No keyboard settings available",
                "This build does not include any keyboard settings features.",
                Arrays.asList(unavailableFeature, availableFeature));

        Assert.assertTrue(group.isAvailable(null));

        GboardPatchesSettingsContract.Screen screen = group.buildScreen(host);

        Assert.assertEquals("Keyboard", screen.getToolbarTitle());
        List<?> sections = reflectList(screen, "getSections");
        Assert.assertEquals(1, sections.size());

        Object section = sections.get(0);
        Assert.assertEquals("Features", reflectString(section, "getTitle"));
        List<?> items = reflectList(section, "getItems");
        Assert.assertEquals(1, items.size());

        Object item = items.get(0);
        Assert.assertEquals("NavigationRow", item.getClass().getSimpleName());
        Assert.assertEquals("Tab Order", reflectCharSequence(item, "getTitle"));
        Assert.assertEquals("Reorder tabs", reflectString(item, "getSummary"));

        reflectRunnable(item, "getAction").run();

        Assert.assertSame(availableFeature, host.openedFeature);
    }

    @Test
    public void featureGroupShowsEmptyStateAsStatusBlockWhenNoChildrenAreAvailable()
            throws Exception {
        GboardFeatureGroup group = new GboardFeatureGroup(
                "Keyboard",
                "Keyboard-related patch settings.",
                "Gboard",
                "Keyboard-level settings for the patches included in this build.",
                "No keyboard settings available",
                "This build does not include any keyboard settings features.",
                Collections.singletonList(new FakeFeature("Tab Order", "Reorder tabs", false)));

        Assert.assertFalse(group.isAvailable(null));

        GboardPatchesSettingsContract.Screen screen = group.buildScreen(new RecordingHost());

        Assert.assertEquals(0, reflectList(screen, "getSections").size());
        List<?> statusBlocks = reflectList(screen, "getStatusBlocks");
        Assert.assertEquals(1, statusBlocks.size());
        Object statusBlock = statusBlocks.get(0);
        Assert.assertEquals("No keyboard settings available", reflectString(statusBlock, "getTitle"));
        Assert.assertEquals(
                "This build does not include any keyboard settings features.",
                reflectString(statusBlock, "getSummary"));
    }

    @Test
    public void registryUsesFocusedKeyboardGroupsInRequestedOrder() throws Exception {
        String registrySource = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesSettingsFeatureRegistry.java");
        String aiVoiceGroupSource = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/keyboard/"
                        + "GboardAiVoiceSettingsGroupFeature.java");
        String toolsGroupSource = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/keyboard/"
                        + "GboardKeyboardToolsSettingsGroupFeature.java");
        String layoutGroupSource = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/keyboard/"
                        + "GboardKeyboardLayoutSettingsGroupFeature.java");

        assertInOrder(registrySource,
                "new GboardAiVoiceSettingsGroupFeature(context)",
                "new GboardKeyboardToolsSettingsGroupFeature(context)",
                "new GboardKeyboardLayoutSettingsGroupFeature(context)",
                "new GboardClipboardSettingsFeature()",
                "new GboardSettingsHomepageSettingsFeature()",
                "new GboardDeveloperOptionsSettingsFeature(context)");
        assertInOrder(aiVoiceGroupSource.substring(aiVoiceGroupSource.indexOf("Arrays.asList(")),
                "new GboardAiWritingToolsSettingsFeature(context)",
                "new GboardAdvancedVoiceSettingsFeature(context)",
                "new GboardBluetoothMicrophoneSettingsFeature(context)");
        assertInOrder(toolsGroupSource.substring(toolsGroupSource.indexOf("Arrays.asList(")),
                "new GboardTopRowSwipeSettingsFeature(context)",
                "new GboardManualIncognitoSettingsFeature(context)",
                "new GboardLongPressQuickActionsSettingsFeature(context)",
                "new GboardOcrSettingsFeature(context)");
        assertInOrder(layoutGroupSource.substring(layoutGroupSource.indexOf("Arrays.asList(")),
                "new GboardLatinGlobeKeyIgnoreIntervalSettingsFeature(context)",
                "new GboardEnglishUppercaseToggleSettingsFeature(context)",
                "new GboardZhuyinBottomRowWeightSettingsFeature(context)",
                "new GboardSymbolFooterOrderSettingsFeature(context)");
        Assert.assertFalse(registrySource.contains("GboardKeyboardSettingsGroupFeature"));
    }

    @Test
    public void bluetoothMicrophoneUsesIndependentMarker()
            throws Exception {
        String availabilitySource = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesFeatureAvailability.java");

        Assert.assertTrue(availabilitySource.contains("FEATURE_BLUETOOTH_MICROPHONE"));
        Assert.assertTrue(availabilitySource.contains(
                "dev.jason.gboardpatches.feature.bluetooth_microphone"));
    }

    @Test
    public void featureAvailabilityIncludesLatinGlobeMarkerAndRegistryUsesIt() throws Exception {
        String availabilitySource = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesFeatureAvailability.java");
        String patchSource = readSource(
                "../../patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/"
                        + "GboardPatchRegistry.kt");

        Assert.assertTrue(availabilitySource.contains(
                "FEATURE_LATIN_GLOBE_KEY_IGNORE_INTERVAL"));
        Assert.assertTrue(availabilitySource.contains(
                "dev.jason.gboardpatches.feature.latin_globe_key_ignore_interval"));
        Assert.assertTrue(availabilitySource.contains("FEATURE_ENGLISH_UPPERCASE_TOGGLE"));
        Assert.assertTrue(availabilitySource.contains(
                "dev.jason.gboardpatches.feature.english_uppercase_toggle"));
        Assert.assertTrue(availabilitySource.contains("FEATURE_AI_WRITING_TOOLS"));
        Assert.assertTrue(availabilitySource.contains(
                "dev.jason.gboardpatches.feature.ai_writing_tools"));
        Assert.assertTrue(availabilitySource.contains("FEATURE_ZHUYIN_BOTTOM_ROW_WEIGHT"));
        Assert.assertTrue(availabilitySource.contains(
                "dev.jason.gboardpatches.feature.zhuyin_bottom_row_weight"));
        Assert.assertTrue(patchSource.contains(
                "gboardLatinGlobeKeyIgnoreIntervalFeatureMarkerPatch"));
        Assert.assertTrue(patchSource.contains(
                "gboardEnglishUppercaseToggleFeatureMarkerPatch"));
        Assert.assertTrue(patchSource.contains("gboardAiWritingToolsFeatureMarkerPatch"));
        Assert.assertTrue(patchSource.contains(
                "gboardZhuyinBottomRowWeightFeatureMarkerPatch"));
    }

    @Test
    public void rootScreenSeparatesPreferenceFeatureAndAboutSections() throws Exception {
        RecordingHost host = new RecordingHost();
        FakeFeature symbolFooterFeature = new FakeFeature(
                "Emojis, stickers & GIFs Tab Order",
                "Reorder tabs",
                true);
        GboardFeatureGroup keyboardGroup = new GboardFeatureGroup(
                "Keyboard",
                "Keyboard-related patch settings.",
                "Gboard",
                "Keyboard-level settings for the patches included in this build.",
                "No keyboard settings available",
                "This build does not include any keyboard settings features.",
                Collections.singletonList(symbolFooterFeature));

        GboardPatchesSettingsContract.Screen rootScreen =
                GboardPatchesSettingsActivity.createRootScreen(
                        host,
                        Collections.singletonList(keyboardGroup),
                        "author",
                        "version",
                        () -> {
                        },
                        () -> {
                        });

        List<?> rootSections = reflectList(rootScreen, "getSections");
        Assert.assertEquals(3, rootSections.size());
        Assert.assertEquals("Features", reflectString(rootSections.get(0), "getTitle"));
        Assert.assertEquals("About", reflectString(rootSections.get(1), "getTitle"));
        Assert.assertEquals("Preferences", reflectString(rootSections.get(2), "getTitle"));

        Object keyboardRow = findItem(rootScreen, "Keyboard");
        Assert.assertEquals("NavigationRow", keyboardRow.getClass().getSimpleName());
        reflectRunnable(keyboardRow, "getAction").run();

        Assert.assertSame(keyboardGroup, host.openedFeature);
    }

    private static String readSource(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }

    private static void assertInOrder(String source, String... needles) {
        int previousIndex = -1;
        for (String needle : needles) {
            int index = source.indexOf(needle);
            Assert.assertTrue("Missing expected row: " + needle, index >= 0);
            Assert.assertTrue("Row is out of order: " + needle, index > previousIndex);
            previousIndex = index;
        }
    }

    private static Object findItem(GboardPatchesSettingsContract.Screen screen, String titlePrefix)
            throws Exception {
        for (Object section : reflectList(screen, "getSections")) {
            for (Object item : reflectList(section, "getItems")) {
                if (reflectCharSequence(item, "getTitle").startsWith(titlePrefix)) {
                    return item;
                }
            }
        }
        throw new AssertionError("Missing item with title prefix: " + titlePrefix);
    }

    @SuppressWarnings("unchecked")
    private static List<?> reflectList(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return (List<?>) method.invoke(target);
    }

    private static String reflectString(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        Object value = method.invoke(target);
        return value == null ? null : value.toString();
    }

    private static String reflectCharSequence(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        Object value = method.invoke(target);
        return value == null ? null : value.toString();
    }

    private static Runnable reflectRunnable(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return (Runnable) method.invoke(target);
    }

    private static final class RecordingHost extends dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsTestHost {
        private GboardPatchesSettingsContract.Feature openedFeature;

        @Override
        public Context getContext() {
            return null;
        }

        @Override
        public void refresh() {
        }

        @Override
        public void openFeature(GboardPatchesSettingsContract.Feature feature) {
            openedFeature = feature;
        }

        @Override
        public void showChoiceDialog(String title, String[] labels, String[] values,
                String currentValue, String customValue, Runnable customAction,
                GboardPatchesSettingsContract.StringValueConsumer valueConsumer) {
        }

        @Override
        public void showPositiveIntegerDialog(String title, String hint, int initialValue,
                GboardPatchesSettingsContract.PositiveIntegerConsumer consumer) {
        }

        @Override
        public void showTextInputDialog(String title, String hint, String initialValue,
                GboardPatchesSettingsContract.TextValueConsumer consumer) {
        }

        @Override
        public void showPreviewDialog(GboardPatchesSettingsContract.PreviewSpec previewSpec) {
        }

        @Override
        public void createTextDocument(String fileName, String mimeType, String text,
                Runnable completionAction) {
        }

        @Override
        public void openTextDocument(String[] mimeTypes,
                GboardPatchesSettingsContract.StringValueConsumer valueConsumer) {
        }
    }

    private static final class FakeFeature implements GboardPatchesSettingsContract.Feature {
        private final String title;
        private final String summary;
        private final boolean available;

        private FakeFeature(String title, String summary, boolean available) {
            this.title = title;
            this.summary = summary;
            this.available = available;
        }

        @Override
        public String getEntryTitle() {
            return title;
        }

        @Override
        public String getEntrySummary() {
            return summary;
        }

        @Override
        public boolean isAvailable(Context context) {
            return available;
        }

        @Override
        public GboardPatchesSettingsContract.Screen buildScreen(
                GboardPatchesSettingsContract.FeatureHost host) {
            return new GboardPatchesSettingsContract.Screen(
                    title,
                    "Gboard",
                    title,
                    summary,
                    Collections.emptyList());
        }
    }
}
