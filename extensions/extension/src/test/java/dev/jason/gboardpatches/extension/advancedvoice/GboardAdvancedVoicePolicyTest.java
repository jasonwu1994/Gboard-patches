package dev.jason.gboardpatches.extension.advancedvoice;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Locale;

public final class GboardAdvancedVoicePolicyTest {
    private static final String POLICY_CLASS =
            "dev.jason.gboardpatches.extension.advancedvoice."
                    + "GboardAdvancedVoicePolicy";

    @Test
    public void forcesOnlyTheSixAdvancedVoiceBooleanFlags() throws Exception {
        Method maybeForceFlag = policyMethod(
                "maybeForceFlag", String.class, Object.class);

        String[] forcedFlags = {
                "enable_nga",
                "enable_advanced_features_in_consolidated_sd_stack",
                "enable_dictation_feature_split_install",
                "handle_fallback_inside_sd_stack",
                "enable_sticky_mic_background",
                "enable_soda_longform_experiment"
        };
        for (String flagName : forcedFlags) {
            Assert.assertEquals(Boolean.TRUE,
                    maybeForceFlag.invoke(null, flagName, Boolean.FALSE));
            Assert.assertEquals(Boolean.TRUE,
                    maybeForceFlag.invoke(null, flagName, Boolean.TRUE));
            Assert.assertEquals("not-a-boolean",
                    maybeForceFlag.invoke(null, flagName, "not-a-boolean"));
        }

        Assert.assertEquals(Boolean.FALSE,
                maybeForceFlag.invoke(null, "unrelated", Boolean.FALSE));
        Assert.assertNull(maybeForceFlag.invoke(null, "enable_nga", null));
    }

    @Test
    public void promotesReadinessOnlyAfterARealNativeLoad() throws Exception {
        Method shouldPromote = policyMethod(
                "shouldPromoteNativeReadiness", Object.class, boolean.class);

        Assert.assertEquals(Boolean.TRUE,
                shouldPromote.invoke(null, Boolean.FALSE, true));
        Assert.assertEquals(Boolean.FALSE,
                shouldPromote.invoke(null, Boolean.FALSE, false));
        Assert.assertEquals(Boolean.FALSE,
                shouldPromote.invoke(null, Boolean.TRUE, true));
        Assert.assertEquals(Boolean.FALSE,
                shouldPromote.invoke(null, null, true));
        Assert.assertEquals(Boolean.FALSE,
                shouldPromote.invoke(null, "false", true));
    }

    @Test
    public void enablesFormatterOnlyForExactZhTwWhenStockAdvancedFeaturesAreEnabled()
            throws Exception {
        Method maybeEnableFormatter = policyMethod(
                "maybeEnableExactZhTwFormatter",
                Locale.class, boolean.class, Object.class);

        Assert.assertEquals(Boolean.FALSE, maybeEnableFormatter.invoke(
                null, Locale.forLanguageTag("zh-TW"), false, Boolean.TRUE));
        Assert.assertEquals(Boolean.TRUE, maybeEnableFormatter.invoke(
                null, Locale.forLanguageTag("zh-TW"), true, Boolean.TRUE));
        Assert.assertEquals(Boolean.FALSE, maybeEnableFormatter.invoke(
                null, Locale.forLanguageTag("zh-TW"), false, Boolean.FALSE));
        Assert.assertEquals("disabled", maybeEnableFormatter.invoke(
                null, Locale.forLanguageTag("zh-TW"), false, "disabled"));
        Assert.assertEquals(Boolean.TRUE, maybeEnableFormatter.invoke(
                null, Locale.forLanguageTag("zh-CN"), false, Boolean.TRUE));
        Assert.assertEquals(Boolean.TRUE, maybeEnableFormatter.invoke(
                null, Locale.forLanguageTag("zh-Hant-TW"), false, Boolean.TRUE));
        Assert.assertEquals(Boolean.TRUE, maybeEnableFormatter.invoke(
                null, Locale.US, false, Boolean.TRUE));
        Assert.assertEquals(Boolean.TRUE, maybeEnableFormatter.invoke(
                null, null, false, Boolean.TRUE));
        Assert.assertNull(maybeEnableFormatter.invoke(
                null, Locale.forLanguageTag("zh-TW"), false, null));
    }

    private static Method policyMethod(String name, Class<?>... parameterTypes)
            throws Exception {
        Class<?> policy;
        try {
            policy = Class.forName(POLICY_CLASS);
        } catch (ClassNotFoundException missing) {
            Assert.fail("missing 18.0.3 Advanced Voice policy class");
            return null;
        }
        Method method = policy.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }
}
