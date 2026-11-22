package dev.jason.gboardpatches.extension.rambler;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.lang.reflect.Proxy;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

@RunWith(RobolectricTestRunner.class)
public final class GboardRamblerAutoStopFailureBoundaryTest {
    private static final Length LIMIT = Length.ofSeconds(300L);
    private Context context;
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        GboardRamblerAutoStopRuntime.resetForTests();
        GboardPatchesSettings.setStorage(null);
        context = RuntimeEnvironment.getApplication();
        preferences = GboardPatchesSettings.preferences(context);
        preferences.edit().clear().commit();
    }

    @After
    public void tearDown() {
        GboardPatchesSettings.setStorage(null);
        GboardRamblerAutoStopRuntime.resetForTests();
        preferences.edit().clear().commit();
    }

    @Test
    public void offByDefaultAndOnlyRamblerSessionsAreShortened() {
        Assert.assertNull(GboardRamblerAutoStopRuntime.onSessionStart(context, true, LIMIT));
        Assert.assertFalse(GboardRamblerAutoStopRuntime.onSpeechLevel(true, 100));

        Assert.assertTrue(GboardRamblerAutoStopSettings.writeSeconds(preferences, 5));
        Assert.assertNull(GboardRamblerAutoStopRuntime.onSessionStart(context, false, LIMIT));
        Object shortened = GboardRamblerAutoStopRuntime.onSessionStart(context, true, LIMIT);
        Assert.assertEquals(Length.ofSeconds(5L), shortened);
        Assert.assertTrue(GboardRamblerAutoStopRuntime.onSpeechLevel(true, 100));
        Assert.assertSame("the next standard session gets the keyboard's length back", LIMIT,
                GboardRamblerAutoStopRuntime.onSessionStart(context, false, shortened));
    }

    @Test
    public void unreadableSettingsLeaveTheTimerAlone() {
        GboardPatchesSettings.setStorage(ignored -> (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class},
                (proxy, method, args) -> {
                    throw new LinkageError("settings unavailable");
                }));
        Assert.assertEquals(0, GboardRamblerAutoStopSettings.readRuntimeSeconds(context));
        Assert.assertNull(GboardRamblerAutoStopRuntime.onSessionStart(context, true, LIMIT));
        Assert.assertFalse(GboardRamblerAutoStopRuntime.onSpeechLevel(true, 100));
    }

    @Test
    public void failingChecksNeverRestartTheTimer() {
        Assert.assertTrue(GboardRamblerAutoStopSettings.writeSeconds(preferences, 3));
        Assert.assertNotNull(GboardRamblerAutoStopRuntime.onSessionStart(context, true, LIMIT));
        GboardRamblerAutoStopRuntime.setClockForTests(() -> {
            throw new LinkageError("clock unavailable");
        });
        Assert.assertFalse(GboardRamblerAutoStopRuntime.onSpeechLevel(true, 100));
        Assert.assertNull(GboardRamblerAutoStopRuntime.onSessionStart(context, true, LIMIT));
        Assert.assertEquals("a failed session start still disarms the previous session",
                0, GboardRamblerAutoStopRuntime.armedSeconds());
    }

    @Test
    public void chosenSilenceNeverExtendsPastTheSessionLimit() {
        Assert.assertTrue(GboardRamblerAutoStopSettings.writeSeconds(preferences, 30));
        Length shortLimit = Length.ofSeconds(10L);
        Assert.assertNull(GboardRamblerAutoStopRuntime.onSessionStart(context, true, shortLimit));
        Assert.assertEquals(0, GboardRamblerAutoStopRuntime.armedSeconds());
    }

    @Test
    public void unknownLengthTypesAreNotChanged() {
        Assert.assertTrue(GboardRamblerAutoStopSettings.writeSeconds(preferences, 3));
        Assert.assertNull(GboardRamblerAutoStopRuntime.onSessionStart(context, true, "300s"));
        Assert.assertNull(GboardRamblerAutoStopRuntime.onSessionStart(context, true, null));
        Assert.assertNull(GboardRamblerAutoStopRuntime.onSessionStart(context, true, new Throwing()));
    }

    /** Stands in for the keyboard's duration class. */
    public static final class Length {
        private final long seconds;

        private Length(long seconds) {
            this.seconds = seconds;
        }

        public static Length ofSeconds(long seconds) {
            return new Length(seconds);
        }

        public long toMillis() {
            return seconds * 1000L;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Length && ((Length) other).seconds == seconds;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(seconds);
        }
    }

    /** A length type whose factory fails. */
    public static final class Throwing {
        public static Throwing ofSeconds(long seconds) {
            throw new LinkageError("factory unavailable");
        }
    }
}
