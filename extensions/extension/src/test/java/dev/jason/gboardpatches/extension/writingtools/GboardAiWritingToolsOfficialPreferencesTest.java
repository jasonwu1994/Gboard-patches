package dev.jason.gboardpatches.extension.writingtools;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public final class GboardAiWritingToolsOfficialPreferencesTest {
    @After
    public void tearDown() {
        GboardAiWritingToolsOfficialPreferences.clearForTest();
    }

    @Test
    public void observesOnlyTargetNativeWritingToolsPreferences() {
        GboardAiWritingToolsOfficialPreferences.notePreferenceValue(
                GboardAiWritingToolsOfficialPreferences.PROOFREAD_PREF_KEY,
                true);
        GboardAiWritingToolsOfficialPreferences.notePreferenceValue(
                GboardAiWritingToolsOfficialPreferences.WRITING_TOOLS_PREF_KEY,
                false);
        GboardAiWritingToolsOfficialPreferences.notePreferenceValue(0x7f140001, true);

        GboardAiWritingToolsOfficialPreferences.Snapshot snapshot =
                GboardAiWritingToolsOfficialPreferences.snapshot();

        Assert.assertEquals(Boolean.TRUE, snapshot.proofreadEnabled);
        Assert.assertEquals(Boolean.FALSE, snapshot.writingToolsEnabled);
        Assert.assertTrue(snapshot.shouldEnableProofread());
        Assert.assertFalse(snapshot.shouldEnableWritingTools());
        Assert.assertTrue(snapshot.shouldEnableAnyFeature());
    }

    @Test
    public void unknownValuesStayReachableAndBothFalseDisableSharedHelper() {
        GboardAiWritingToolsOfficialPreferences.Snapshot unknown =
                GboardAiWritingToolsOfficialPreferences.snapshot();
        Assert.assertTrue(unknown.shouldEnableProofread());
        Assert.assertTrue(unknown.shouldEnableWritingTools());
        Assert.assertTrue(unknown.shouldEnableAnyFeature());

        GboardAiWritingToolsOfficialPreferences.notePreferenceValue(
                GboardAiWritingToolsOfficialPreferences.PROOFREAD_PREF_KEY,
                false);
        GboardAiWritingToolsOfficialPreferences.notePreferenceValue(
                GboardAiWritingToolsOfficialPreferences.WRITING_TOOLS_PREF_KEY,
                false);

        Assert.assertFalse(GboardAiWritingToolsOfficialPreferences.snapshot()
                .shouldEnableAnyFeature());
    }

    @Test
    public void exposesOnlyTargetNativeKeys() {
        Assert.assertEquals(0x7f1409b3,
                GboardAiWritingToolsOfficialPreferences.PROOFREAD_PREF_KEY);
        Assert.assertEquals(0x7f140abd,
                GboardAiWritingToolsOfficialPreferences.WRITING_TOOLS_PREF_KEY);
        Assert.assertEquals(0x7f140cb1,
                GboardAiWritingToolsOfficialPreferences.WRITING_TOOLS_CATEGORY_KEY);
    }
}
