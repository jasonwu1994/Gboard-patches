package dev.jason.gboardpatches.extension.rambler;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public final class GboardRamblerOfficialSelectionRuntimeTest {
    @After
    public void tearDown() {
        GboardRamblerOfficialSelectionRuntime.resetForTests();
    }

    @Test
    public void officialSelectorStateControlsAgenticCapabilityOutsideSettings() {
        GboardRamblerOfficialSelectionRuntime.updateOfficialSelection(false);
        Assert.assertFalse(
                GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());

        GboardRamblerOfficialSelectionRuntime.updateOfficialSelection(true);
        Assert.assertTrue(
                GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());
    }

    @Test
    public void voiceSettingsScopeTemporarilyExposesBothOfficialChoices() {
        GboardRamblerOfficialSelectionRuntime.updateOfficialSelection(false);
        GboardRamblerOfficialSelectionRuntime.enterVoiceSettingsScope();
        GboardRamblerOfficialSelectionRuntime.enterVoiceSettingsScope();

        Assert.assertTrue(
                GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());
        GboardRamblerOfficialSelectionRuntime.exitVoiceSettingsScope();
        Assert.assertTrue(
                GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());
        GboardRamblerOfficialSelectionRuntime.exitVoiceSettingsScope();
        Assert.assertFalse(
                GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());
    }

    @Test
    public void defaultSelectionSuppressionWinsAndBalancedExitRestoresOfficialState() {
        GboardRamblerOfficialSelectionRuntime.updateOfficialSelection(true);
        GboardRamblerOfficialSelectionRuntime.enterVoiceSettingsScope();
        GboardRamblerOfficialSelectionRuntime.enterDefaultSelectionSuppression();
        GboardRamblerOfficialSelectionRuntime.enterDefaultSelectionSuppression();

        Assert.assertFalse(
                GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());
        GboardRamblerOfficialSelectionRuntime.exitDefaultSelectionSuppression();
        Assert.assertFalse(
                GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());
        GboardRamblerOfficialSelectionRuntime.exitDefaultSelectionSuppression();
        Assert.assertTrue(
                GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());

        GboardRamblerOfficialSelectionRuntime.exitVoiceSettingsScope();
        Assert.assertTrue(
                GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());
    }
}
