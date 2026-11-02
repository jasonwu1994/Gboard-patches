package dev.jason.gboardpatches.extension.keypresshaptics;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Vibrator;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowVibrator;
import static org.junit.Assert.*;
import dev.jason.gboardpatches.extension.keypresshaptics.GboardKeypressHapticsSupport;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class GboardKeypressHapticsSupportTest {
    @Test public void supportQueryMatchesClickCapabilityAndNeverPlaysAnEffect() {
        Context context = RuntimeEnvironment.getApplication();
        ShadowVibrator vibrator = Shadows.shadowOf(context.getSystemService(Vibrator.class));
        vibrator.setHasVibrator(true);
        vibrator.setSupportedPrimitives(Arrays.asList(1));
        assertEquals(Boolean.TRUE, GboardKeypressHapticsSupport.read(context));
        assertFalse(vibrator.isVibrating());
        assertTrue(vibrator.getPrimitiveEffects().isEmpty());
        vibrator.setSupportedPrimitives(Arrays.asList(7));
        assertEquals(Boolean.FALSE, GboardKeypressHapticsSupport.read(context));
        vibrator.setHasVibrator(false);
        assertEquals(Boolean.FALSE, GboardKeypressHapticsSupport.read(context));
    }

    @Test public void failedQueryIsUnknownRatherThanClaimingUnsupported() {
        Context context = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public Object getSystemService(String name) {
                throw new SecurityException("capability query unavailable");
            }
        };
        assertNull(GboardKeypressHapticsSupport.read(context));
        assertNull(GboardKeypressHapticsSupport.read(null));
    }
}
