package dev.jason.gboardpatches.extension.keypresshaptics;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Read-only device capability, not proof that Gboard is currently using the effect. */
public final class GboardKeypressHapticsSupport {
    private GboardKeypressHapticsSupport() {}

    public static Boolean read(Context context) {
        if (context == null) return null;
        if (Build.VERSION.SDK_INT < 30) return Boolean.FALSE;
        try {
            Vibrator vibrator = context.getSystemService(Vibrator.class);
            return vibrator != null && vibrator.hasVibrator()
                    && vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK);
        } catch (Throwable unavailable) {
            return null;
        }
    }
}
