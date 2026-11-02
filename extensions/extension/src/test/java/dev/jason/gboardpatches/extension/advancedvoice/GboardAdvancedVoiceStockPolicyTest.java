package dev.jason.gboardpatches.extension.advancedvoice;

import org.junit.Assert;
import org.junit.Test;

public final class GboardAdvancedVoiceStockPolicyTest {
    @Test
    public void sixFormalBooleanFlagsAreEnabledWithStrictTyping() {
        String[] flags = new String[] {
                GboardAdvancedVoiceStockPolicy.ENABLE_NGA_FLAG,
                GboardAdvancedVoiceStockPolicy.ENABLE_ADVANCED_FEATURES_FLAG,
                GboardAdvancedVoiceStockPolicy.ENABLE_DICTATION_SPLIT_INSTALL_FLAG,
                GboardAdvancedVoiceStockPolicy.HANDLE_FALLBACK_INSIDE_SD_STACK_FLAG,
                GboardAdvancedVoiceStockPolicy.ENABLE_STICKY_MIC_BACKGROUND_FLAG,
                GboardAdvancedVoiceStockPolicy.ENABLE_SODA_LONGFORM_EXPERIMENT_FLAG
        };
        for (String flag : flags) {
            Assert.assertSame(Boolean.TRUE,
                    GboardAdvancedVoiceStockPolicy.maybeForceStockFlag(
                            flag, Boolean.FALSE));
        }
        Object wrongType = "false";
        Assert.assertSame(wrongType,
                GboardAdvancedVoiceStockPolicy.maybeForceStockFlag(
                        flags[0], wrongType));
    }
}
