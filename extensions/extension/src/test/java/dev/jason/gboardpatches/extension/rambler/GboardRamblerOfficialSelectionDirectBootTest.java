package dev.jason.gboardpatches.extension.rambler;

import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.UserManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowUserManager;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import dev.jason.gboardpatches.extension.advancedvoice.GboardAdvancedVoiceRuntime;
import dev.jason.gboardpatches.extension.advancedvoice.GboardAdvancedVoiceRuntimeSettings;
import dev.jason.gboardpatches.extension.advancedvoice.GboardAdvancedVoiceSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class GboardRamblerOfficialSelectionDirectBootTest {
    private static final String DIRECT_BOOT_MESSAGE =
            "SharedPreferences in credential encrypted storage are not available "
                    + "until after user (id 0) is unlocked";

    private Context application;
    private ShadowUserManager users;
    private AtomicInteger ceAccessAttempts;
    private Context directBootContext;

    @Before
    public void setUp() {
        application = RuntimeEnvironment.getApplication();
        users = shadowOf(application.getSystemService(UserManager.class));
        users.setUserUnlocked(true);
        ceAccessAttempts = new AtomicInteger();

        GboardPatchesSettings.setStorage(null);
        GboardPatchesSettings.setDeviceProtectedStorage(null);
        GboardRamblerOfficialSelectionRuntime.resetForTests();
        GboardAdvancedVoiceRuntimeSettings.clearEnabledOverrideForTest();
        FakeStockSelector.reset();
        RecursiveFakeSelector.reset();

        GboardPatchesSettings.preferences(application).edit().clear().commit();
        SharedPreferences dePrefs = GboardPatchesSettings.deviceProtectedPreferencesOrNull(
                application);
        if (dePrefs != null) {
            dePrefs.edit().clear().commit();
        }

        directBootContext = new ContextWrapper(application) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                if (GboardPatchesSettings.PREF_FILE.equals(name)) {
                    ceAccessAttempts.incrementAndGet();
                    if (!GboardPatchesSettings.isUserUnlocked(this)) {
                        throw new IllegalStateException(DIRECT_BOOT_MESSAGE);
                    }
                }
                return super.getSharedPreferences(name, mode);
            }
        };
    }

    @After
    public void tearDown() {
        users.setUserUnlocked(true);
        GboardPatchesSettings.setStorage(null);
        GboardPatchesSettings.setDeviceProtectedStorage(null);
        GboardRamblerOfficialSelectionRuntime.resetForTests();
        GboardAdvancedVoiceRuntimeSettings.clearEnabledOverrideForTest();
        FakeStockSelector.reset();
        RecursiveFakeSelector.reset();
    }

    @Test
    public void persistedRamblerSelectionSurvivesLockedColdBootWithoutTouchingCeStorage() {
        GboardAdvancedVoiceRuntime.seedApplicationContext(directBootContext);
        GboardRamblerOfficialSelectionRuntime.updateOfficialSelection(true);

        SharedPreferences dePrefs = GboardPatchesSettings.deviceProtectedPreferencesOrNull(
                directBootContext);
        Assert.assertNotNull(dePrefs);
        Assert.assertTrue(dePrefs.getBoolean(
                GboardRamblerOfficialSelectionRuntime.PREF_KEY_OFFICIAL_RAMBLER_SELECTED, false));

        simulateProcessRestart();
        users.setUserUnlocked(false);
        ceAccessAttempts.set(0);

        GboardAdvancedVoiceRuntime.seedApplicationContext(directBootContext);

        Assert.assertTrue(GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());
        Assert.assertEquals(
                Boolean.TRUE,
                GboardAdvancedVoiceRuntime.afterFlagValue(
                        new FlagReceiver(GboardRamblerStockPolicy.ENABLE_AGENTIC_DICTATION_FLAG),
                        Boolean.FALSE));
        Assert.assertTrue(
                GboardRamblerOfficialSelectionRuntime.observeOfficialSelectionRead(
                        directBootContext, false));
        Assert.assertEquals(0, ceAccessAttempts.get());
    }

    @Test
    public void lockedColdBootWithoutDeMigrationDoesNotLatchFalseAndRecoversAfterUnlock() {
        GboardRamblerOfficialSelectionRuntime.setSelectionClassNameForTests(
                FakeStockSelector.class.getName());
        FakeStockSelector.unlockedResult = true;

        users.setUserUnlocked(false);
        GboardAdvancedVoiceRuntime.seedApplicationContext(directBootContext);

        Assert.assertFalse(GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());
        Assert.assertNull(GboardRamblerOfficialSelectionRuntime.cachedOfficialSelectionForTests());
        Assert.assertFalse(
                GboardRamblerOfficialSelectionRuntime.observeOfficialSelectionRead(
                        directBootContext, false));
        Assert.assertNull(GboardRamblerOfficialSelectionRuntime.cachedOfficialSelectionForTests());
        Assert.assertEquals(0, FakeStockSelector.probeCount.get());

        users.setUserUnlocked(true);
        Assert.assertTrue(GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());
        Assert.assertEquals(1, FakeStockSelector.probeCount.get());
        Assert.assertEquals(
                Boolean.TRUE,
                GboardRamblerOfficialSelectionRuntime.cachedOfficialSelectionForTests());

        // Subsequent locked cold start resolves directly from migrated DE storage.
        simulateProcessRestart();
        users.setUserUnlocked(false);
        GboardAdvancedVoiceRuntime.seedApplicationContext(directBootContext);
        Assert.assertTrue(GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());
    }

    @Test
    public void recursiveStockSelectionProbeDoesNotStackOverflowOrPoisonState() {
        GboardRamblerOfficialSelectionRuntime.setSelectionClassNameForTests(
                RecursiveFakeSelector.class.getName());
        GboardAdvancedVoiceRuntime.seedApplicationContext(directBootContext);

        Assert.assertTrue(GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());
        Assert.assertTrue(RecursiveFakeSelector.observedAgenticEnabledInsideProbe.get());
        Assert.assertFalse(RecursiveFakeSelector.observedPassiveReadResultInsideProbe.get());
        Assert.assertEquals(1, RecursiveFakeSelector.probeCount.get());
        Assert.assertEquals(
                Boolean.TRUE,
                GboardRamblerOfficialSelectionRuntime.cachedOfficialSelectionForTests());

        SharedPreferences dePrefs = GboardPatchesSettings.deviceProtectedPreferencesOrNull(
                directBootContext);
        Assert.assertNotNull(dePrefs);
        Assert.assertTrue(dePrefs.getBoolean(
                GboardRamblerOfficialSelectionRuntime.PREF_KEY_OFFICIAL_RAMBLER_SELECTED, false));
    }

    @Test
    public void suppressionAndSpuriousBackgroundFalseReadsNeverEraseRamblerSelection() {
        GboardAdvancedVoiceRuntime.seedApplicationContext(directBootContext);
        GboardRamblerOfficialSelectionRuntime.updateOfficialSelection(true);

        GboardRamblerOfficialSelectionRuntime.enterDefaultSelectionSuppression();
        Assert.assertFalse(GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());
        Assert.assertFalse(
                GboardRamblerOfficialSelectionRuntime.observeOfficialSelectionRead(
                        directBootContext, false));
        GboardRamblerOfficialSelectionRuntime.exitDefaultSelectionSuppression();

        Assert.assertTrue(
                GboardRamblerOfficialSelectionRuntime.observeOfficialSelectionRead(
                        directBootContext, false));
        Assert.assertTrue(GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());

        SharedPreferences dePrefs = GboardPatchesSettings.deviceProtectedPreferencesOrNull(
                directBootContext);
        Assert.assertNotNull(dePrefs);
        Assert.assertTrue(dePrefs.getBoolean(
                GboardRamblerOfficialSelectionRuntime.PREF_KEY_OFFICIAL_RAMBLER_SELECTED, false));
    }

    @Test
    public void explicitSwitchToStandardPersistsFalseAcrossRestart() {
        GboardAdvancedVoiceRuntime.seedApplicationContext(directBootContext);
        GboardRamblerOfficialSelectionRuntime.updateOfficialSelection(true);
        Assert.assertTrue(GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());

        GboardRamblerOfficialSelectionRuntime.updateOfficialSelection(false);
        Assert.assertFalse(GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());

        simulateProcessRestart();
        users.setUserUnlocked(false);
        GboardAdvancedVoiceRuntime.seedApplicationContext(directBootContext);
        Assert.assertFalse(GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());
        Assert.assertFalse(
                GboardRamblerOfficialSelectionRuntime.observeOfficialSelectionRead(
                        directBootContext, false));

        users.setUserUnlocked(true);
        Assert.assertFalse(GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());
        Assert.assertFalse(
                GboardRamblerOfficialSelectionRuntime.observeOfficialSelectionRead(
                        directBootContext, false));
    }

    @Test
    public void advancedVoiceRuntimeSettingsMirrorsToDeAndRecoversCeAfterUnlock()
            throws Exception {
        GboardAdvancedVoiceRuntime.seedApplicationContext(directBootContext);
        GboardRamblerOfficialSelectionRuntime.updateOfficialSelection(true);
        Assert.assertTrue(GboardAdvancedVoiceSettings.writeEnabled(directBootContext, false));

        simulateProcessRestart();
        users.setUserUnlocked(false);
        ceAccessAttempts.set(0);
        GboardAdvancedVoiceRuntime.seedApplicationContext(directBootContext);

        Assert.assertFalse(GboardAdvancedVoiceRuntimeSettings.isEnabled());
        Assert.assertEquals(0, ceAccessAttempts.get());
        Assert.assertFalse(
                GboardRamblerOfficialSelectionRuntime.observeOfficialSelectionRead(
                        directBootContext, false));

        Field cachedSnapshotField = GboardAdvancedVoiceRuntimeSettings.class
                .getDeclaredField("cachedSnapshot");
        cachedSnapshotField.setAccessible(true);
        Assert.assertNull(cachedSnapshotField.get(null));

        users.setUserUnlocked(true);
        Assert.assertTrue(GboardAdvancedVoiceSettings.writeEnabled(directBootContext, true));
        Assert.assertTrue(GboardAdvancedVoiceRuntimeSettings.isEnabled());
        Assert.assertNotNull(cachedSnapshotField.get(null));
        Assert.assertTrue(
                GboardRamblerOfficialSelectionRuntime.observeOfficialSelectionRead(
                        directBootContext, false));
    }

    private static void simulateProcessRestart() {
        GboardRamblerOfficialSelectionRuntime.resetForTests();
        GboardAdvancedVoiceRuntimeSettings.clearEnabledOverrideForTest();
    }

    private static final class FlagReceiver {
        private final String a;

        private FlagReceiver(String flagName) {
            a = flagName;
        }
    }

    public static final class FakeStockSelector {
        static volatile boolean unlockedResult;
        static final AtomicInteger probeCount = new AtomicInteger();

        static void reset() {
            unlockedResult = false;
            probeCount.set(0);
        }

        public static boolean morpheRamblerStocka(Context context) {
            probeCount.incrementAndGet();
            return GboardPatchesSettings.isUserUnlocked(context) && unlockedResult;
        }
    }

    public static final class RecursiveFakeSelector {
        static final AtomicInteger probeCount = new AtomicInteger();
        static final AtomicBoolean observedAgenticEnabledInsideProbe = new AtomicBoolean();
        static final AtomicBoolean observedPassiveReadResultInsideProbe = new AtomicBoolean();

        static void reset() {
            probeCount.set(0);
            observedAgenticEnabledInsideProbe.set(false);
            observedPassiveReadResultInsideProbe.set(false);
        }

        public static boolean morpheRamblerStocka(Context context) {
            probeCount.incrementAndGet();
            observedAgenticEnabledInsideProbe.set(
                    GboardRamblerOfficialSelectionRuntime.shouldEnableAgenticDictation());
            observedPassiveReadResultInsideProbe.set(
                    GboardRamblerOfficialSelectionRuntime.observeOfficialSelectionRead(
                            context, false));
            return true;
        }
    }
}
