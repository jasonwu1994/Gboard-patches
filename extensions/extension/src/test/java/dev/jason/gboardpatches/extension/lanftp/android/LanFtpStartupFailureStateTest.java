package dev.jason.gboardpatches.extension.lanftp.android;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowContentResolver;

import dev.jason.gboardpatches.extension.lanftp.config.LanFtpPreferences;

@RunWith(RobolectricTestRunner.class)
public final class LanFtpStartupFailureStateTest {
    private Context context;
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        preferences = LanFtpPreferences.preferences(context);
        preferences.edit().clear().commit();
        LanFtpPreferences.ensureDefaults(preferences);
        Assert.assertTrue(LanFtpPreferences.setEnabled(preferences, true));

        LanFtpStatusProvider provider = new LanFtpStatusProvider();
        provider.onCreate();
        ShadowContentResolver.registerProviderInternal(
                context.getPackageName() + LanFtpStatusProvider.AUTHORITY_SUFFIX,
                provider);
    }

    @After
    public void tearDown() {
        LanFtpRuntimeRegistry.reset();
        preferences.edit().clear().commit();
    }

    @Test
    public void startupFailureDisablesAndReturnsFreshConfigurationInOneRead() {
        LanFtpRuntimeRegistry.publish(
                LanFtpRuntimeStatus.failed("FTP control port is unavailable"));

        LanFtpServerState state = LanFtpServerController.from(context).requestState();

        Assert.assertFalse(state.active());
        Assert.assertEquals("FTP control port is unavailable", state.status());
        Assert.assertFalse(state.config().enabled());
        Assert.assertFalse(LanFtpPreferences.read(preferences).enabled);
    }
}
