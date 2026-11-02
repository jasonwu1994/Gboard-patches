package dev.jason.gboardpatches.extension.settings;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GboardRestartPendingChangesTest {
    private static final String ZH = "zh-TW";

    @Before
    public void setUp() {
        GboardRestartPendingChanges.resetForTest();
    }

    @After
    public void tearDown() {
        GboardRestartPendingChanges.resetForTest();
    }

    @Test
    public void firstObservationIsBaselineAndChangeBecomesPending() {
        GboardRestartPendingChanges.observe("flow", "false", ZH);
        Assert.assertEquals(0, GboardRestartPendingChanges.pendingCount());

        GboardRestartPendingChanges.observe("flow", "true", ZH);
        Assert.assertEquals(1, GboardRestartPendingChanges.pendingCount());
        Assert.assertEquals(Arrays.asList("flow"), GboardRestartPendingChanges.pendingKeys());
    }

    @Test
    public void switchingBackCancelsThePendingChange() {
        GboardRestartPendingChanges.observe("flow", "false", ZH);
        GboardRestartPendingChanges.observe("flow", "true", ZH);
        GboardRestartPendingChanges.observe("flow", "false", ZH);
        Assert.assertEquals(0, GboardRestartPendingChanges.pendingCount());
    }

    @Test
    public void countsDistinctKeysAndSelectorValues() {
        GboardRestartPendingChanges.observe("a", "false", ZH);
        GboardRestartPendingChanges.observe("count", "8", ZH);
        GboardRestartPendingChanges.observe("a", "true", ZH);
        GboardRestartPendingChanges.observe("count", "10", ZH);
        GboardRestartPendingChanges.observe("count", "12", ZH);
        Assert.assertEquals(2, GboardRestartPendingChanges.pendingCount());
    }

    @Test
    public void ignoresEmptyKeysAndNullValues() {
        GboardRestartPendingChanges.observe(null, "true", ZH);
        GboardRestartPendingChanges.observe("", "true", ZH);
        GboardRestartPendingChanges.observe("x", null, ZH);
        GboardRestartPendingChanges.observe("x", "true", ZH);
        Assert.assertEquals(0, GboardRestartPendingChanges.pendingCount());
    }

    @Test
    public void successfulRestartClearsAndRebaselines() {
        GboardRestartPendingChanges.observe("flow", "false", ZH);
        GboardRestartPendingChanges.observe("flow", "true", ZH);
        GboardRestartPendingChanges.onTargetRestarted();
        Assert.assertEquals(0, GboardRestartPendingChanges.pendingCount());

        GboardRestartPendingChanges.observe("flow", "true", ZH);
        Assert.assertEquals(0, GboardRestartPendingChanges.pendingCount());
        GboardRestartPendingChanges.observe("flow", "false", ZH);
        Assert.assertEquals(1, GboardRestartPendingChanges.pendingCount());
    }

    @Test
    public void languageChangeKeepsPendingKeysAndRebaselinesOthers() {
        GboardRestartPendingChanges.observe("mode", "四角", ZH);
        GboardRestartPendingChanges.observe("other", "上方", ZH);
        GboardRestartPendingChanges.observe("mode", "上方", ZH);
        Assert.assertEquals(1, GboardRestartPendingChanges.pendingCount());

        GboardRestartPendingChanges.observe("mode", "All corners", "en");
        GboardRestartPendingChanges.observe("other", "Top", "en");
        Assert.assertEquals(Arrays.asList("mode"), GboardRestartPendingChanges.pendingKeys());
    }

    @Test
    public void listenersHearOnlyCountChanges() {
        List<Integer> counts = new ArrayList<>();
        GboardRestartPendingChanges.Listener listener = counts::add;
        GboardRestartPendingChanges.addListener(listener);

        GboardRestartPendingChanges.observe("a", "false", ZH);
        GboardRestartPendingChanges.observe("a", "true", ZH);
        GboardRestartPendingChanges.observe("a", "true", ZH);
        GboardRestartPendingChanges.onTargetRestarted();
        GboardRestartPendingChanges.onTargetRestarted();
        GboardRestartPendingChanges.removeListener(listener);
        GboardRestartPendingChanges.observe("b", "false", ZH);
        GboardRestartPendingChanges.observe("b", "true", ZH);

        Assert.assertEquals(Arrays.asList(1, 0), counts);
    }

    @Test
    public void shellWiresDeclarationTrackerAndBadge() throws Exception {
        String contract = read("settings/GboardPatchesSettingsContract.java");
        String activity = read("settings/GboardPatchesSettingsActivity.java");
        String entryState = read("settings/GboardFeatureEntryState.java");
        String flagFeature = read("flagsettings/GboardBooleanFlagSettingsFeature.java");

        Assert.assertTrue(contract.contains("public Row setRestartRequired(String restartKey)"));
        Assert.assertTrue(activity.contains("observeRestartRequiredRows(screen);"));
        Assert.assertTrue(activity.contains("decoration.getEntryToggle()"));
        Assert.assertTrue(entryState.contains(
                "converted.setRestartRequired(switchRow.getRestartKey());"));
        Assert.assertTrue(activity.contains(
                "GboardRestartPendingChanges.addListener(restartPendingListener)"));
        Assert.assertTrue(activity.contains("palette.restartPending"));
        Assert.assertTrue(activity.contains("R.plurals.gboard_patches_restart_action_pending"));
        Assert.assertTrue(flagFeature.contains(".setRestartRequired(restartKey)"));
    }

    @Test
    public void documentedRestartRequiredSettingsDeclareTheirPreferenceKeys() throws Exception {
        assertDeclares("accesspointcount/GboardAccessPointCountSettingsFeature.java",
                "GboardAccessPointCountSettings.PREF_KEY_ENABLED",
                "GboardAccessPointCountSettings.PREF_KEY_COUNT");
        assertDeclaresVia("closeproactivesuggestions/"
                        + "GboardCloseProactiveSuggestionsSettingsFeature.java",
                "GboardCloseProactiveSuggestionsSettings.PREF_KEY_ENABLED");
        assertDeclares("roundedkeyboard/GboardRoundedKeyboardSettingsFeature.java",
                "GboardRoundedKeyboardSettings.PREF_KEY_ENABLED",
                "GboardRoundedKeyboardSettings.PREF_KEY_MODE",
                "GboardRoundedKeyboardSettings.PREF_KEY_TOP_RADIUS_DP",
                "GboardRoundedKeyboardSettings.PREF_KEY_BOTTOM_RADIUS_DP");
        assertDeclares("advancedvoice/GboardAdvancedVoiceSettingsFeature.java",
                "GboardAdvancedVoiceSettings.PREF_KEY_ENABLED",
                "GboardAdvancedVoiceSettings.PREF_KEY_ZH_TW_PUNCTUATION_ENABLED");
        assertDeclares("websearch/GboardFloatingWebSearchSettingsFeature.java",
                "GboardFloatingWebSearchSettings.PREF_KEY_ENABLED");
        assertDeclares("editingaccesspoints/GboardEditingAccessPointsSettingsFeature.java",
                "GboardEditingAccessPointsSettings.ENABLED", "key");
        assertDeclares("manualincognito/GboardManualIncognitoSettingsFeature.java",
                "GboardManualIncognitoSettings.PREF_FEATURE_ENABLED");
        assertDeclares("writingtools/GboardAiWritingToolsSettingsFeature.java",
                "GboardAiWritingToolsSettings.PREF_KEY_ENABLED",
                "GboardAiWritingToolsSettings.PREF_KEY_ALL_KEYBOARDS");
        assertDeclaresVia("quickinsert/GboardQuickInsertSettingsFeature.java",
                "GboardQuickInsertSettings.PREF_KEY_ENABLED");
    }

    private static void assertDeclares(String relativePath, String... keys) throws Exception {
        String source = read(relativePath).replaceAll("\\s+", "");
        for (String key : keys) {
            Assert.assertTrue(relativePath + " should declare restart key " + key,
                    source.contains(".setRestartRequired(" + key));
        }
    }

    private static void assertDeclaresVia(String relativePath, String key) throws Exception {
        String source = read(relativePath).replaceAll("\\s+", "");
        Assert.assertTrue(relativePath + " should declare restart key " + key,
                source.contains("declareRestartRequired(" + key + ")"));
    }

    private static String read(String relativePath) throws Exception {
        return new String(Files.readAllBytes(Path.of(
                "src/main/java/dev/jason/gboardpatches/extension/" + relativePath)),
                StandardCharsets.UTF_8);
    }
}
