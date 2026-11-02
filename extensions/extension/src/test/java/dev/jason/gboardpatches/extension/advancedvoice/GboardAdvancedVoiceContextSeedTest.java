package dev.jason.gboardpatches.extension.advancedvoice;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public final class GboardAdvancedVoiceContextSeedTest {
    @After
    public void tearDown() {
        GboardAdvancedVoiceRuntimeSettings.clearEnabledOverrideForTest();
    }

    @Test
    public void constructorContextMakesTheFirstVoiceDecisionUsePersistedSettings() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences preferences = GboardAdvancedVoiceSettings.preferences(context);
        preferences.edit()
                .putBoolean(GboardAdvancedVoiceSettings.PREF_KEY_ENABLED, true)
                .putBoolean(
                        GboardAdvancedVoiceSettings.PREF_KEY_ZH_TW_PUNCTUATION_ENABLED,
                        true)
                .commit();

        GboardAdvancedVoiceRuntimeSettings.clearEnabledOverrideForTest();
        GboardAdvancedVoiceRuntime.seedApplicationContext(context);

        Assert.assertEquals(
                Boolean.TRUE,
                GboardAdvancedVoiceRuntime.afterFlagValue(
                        new FlagReceiver("enable_nga"),
                        Boolean.FALSE));
        Set<?> locales = (Set<?>) GboardAdvancedVoiceRuntime
                .includeExactZhTwSupportedLocale(Collections.emptySet());
        Assert.assertTrue(locales.contains(Locale.forLanguageTag("zh-TW")));
    }

    @Test
    public void nullConstructorContextIsSafe() {
        GboardAdvancedVoiceRuntime.seedApplicationContext(null);
    }

    private static final class FlagReceiver {
        private final String a;

        private FlagReceiver(String flagName) {
            a = flagName;
        }
    }
}
