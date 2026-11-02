package dev.jason.gboardpatches.extension.rambler;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public final class GboardRamblerStockPolicyTest {
    @After
    public void tearDown() {
        GboardRamblerOfficialSelectionRuntime.resetForTests();
    }

    @Test
    public void formalBooleanAndLongFlagsRetainTheirTypes() {
        Assert.assertSame(Boolean.TRUE,
                GboardRamblerStockPolicy.maybeForceStockRouteValue(
                        GboardRamblerStockPolicy.SHOW_RAMBLER_DICT_SETTINGS_FLAG,
                        Boolean.FALSE));
        Assert.assertEquals(Long.valueOf(2L),
                GboardRamblerStockPolicy.maybeForceStockRouteValue(
                        GboardRamblerStockPolicy.AD_ACTIVATION_TYPE_FLAG,
                        Long.valueOf(0L)));
        Integer wrongType = Integer.valueOf(2);
        Assert.assertSame(wrongType,
                GboardRamblerStockPolicy.maybeForceStockRouteValue(
                        GboardRamblerStockPolicy.AD_ACTIVATION_TYPE_FLAG,
                        wrongType));
    }

    @Test
    public void agenticCapabilityFollowsOfficialSelectionAndScopes() {
        GboardRamblerOfficialSelectionRuntime.updateOfficialSelection(false);
        Assert.assertSame(Boolean.FALSE,
                GboardRamblerStockPolicy.maybeForceStockRouteValue(
                        GboardRamblerStockPolicy.ENABLE_AGENTIC_DICTATION_FLAG,
                        Boolean.FALSE));

        GboardRamblerOfficialSelectionRuntime.enterVoiceSettingsScope();
        Assert.assertSame(Boolean.TRUE,
                GboardRamblerStockPolicy.maybeForceStockRouteValue(
                        GboardRamblerStockPolicy.ENABLE_AGENTIC_DICTATION_FLAG,
                        Boolean.FALSE));
        GboardRamblerOfficialSelectionRuntime.exitVoiceSettingsScope();

        GboardRamblerOfficialSelectionRuntime.updateOfficialSelection(true);
        Assert.assertSame(Boolean.TRUE,
                GboardRamblerStockPolicy.maybeForceStockRouteValue(
                        GboardRamblerStockPolicy.ENABLE_AGENTIC_DICTATION_FLAG,
                        Boolean.FALSE));
        GboardRamblerOfficialSelectionRuntime.enterDefaultSelectionSuppression();
        Assert.assertSame(Boolean.FALSE,
                GboardRamblerStockPolicy.maybeForceStockRouteValue(
                        GboardRamblerStockPolicy.ENABLE_AGENTIC_DICTATION_FLAG,
                        Boolean.FALSE));
    }
}
