package dev.jason.gboardpatches.extension.rambler;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import dev.jason.gboardpatches.extension.advancedvoice.GboardAdvancedVoiceRuntimeSettings;

public final class GboardRamblerOfficialSelectionRuntimeTest {
    @After
    public void tearDown() {
        GboardRamblerOfficialSelectionRuntime.resetForTests();
        GboardAdvancedVoiceRuntimeSettings.clearEnabledOverrideForTest();
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

    @Test
    public void passiveFalseReadDuringDefaultSuppressionDoesNotPoisonActiveSelection() {
        GboardRamblerOfficialSelectionRuntime.updateOfficialSelection(true);
        GboardRamblerOfficialSelectionRuntime.enterDefaultSelectionSuppression();

        Assert.assertFalse(
                GboardRamblerOfficialSelectionRuntime.observeOfficialSelectionRead(null, false));
        Assert.assertFalse(
                GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());

        GboardRamblerOfficialSelectionRuntime.exitDefaultSelectionSuppression();
        Assert.assertTrue(
                GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());
    }

    @Test
    public void passiveFalseReadPreservesActiveRamblerSelectionOutsideSuppression() {
        GboardRamblerOfficialSelectionRuntime.updateOfficialSelection(true);

        Assert.assertTrue(
                GboardRamblerOfficialSelectionRuntime.observeOfficialSelectionRead(null, false));
        Assert.assertTrue(
                GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());
    }

    @Test
    public void passiveTrueReadPromotesSelectionToRambler() {
        Assert.assertTrue(
                GboardRamblerOfficialSelectionRuntime.observeOfficialSelectionRead(null, true));
        Assert.assertTrue(
                GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());
    }

    @Test
    public void masterToggleOffReturnsStockFalseOnPassiveRead() {
        GboardRamblerOfficialSelectionRuntime.updateOfficialSelection(true);
        GboardAdvancedVoiceRuntimeSettings.setEnabledOverrideForTest(false);

        Assert.assertFalse(
                GboardRamblerOfficialSelectionRuntime.observeOfficialSelectionRead(null, false));
    }
}
