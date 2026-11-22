package dev.jason.gboardpatches.extension.rambler;

import android.content.Context;
import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

/**
 * Stored choice for stopping Rambler voice typing after a stretch without speech.
 *
 * <p>The value is a number of seconds; {@link #OFF} keeps Gboard's own session limit. The
 * settings screen offers {@link #presets()} and a custom value from {@link #MIN_SECONDS} to
 * {@link #MAX_SECONDS}.</p>
 */
public final class GboardRamblerAutoStopSettings {
    public static final String PREF_KEY_SECONDS = "pref_rambler_auto_stop_seconds";
    public static final int OFF = 0;
    public static final int DEFAULT_SECONDS = OFF;
    public static final int MIN_SECONDS = 1;
    public static final int MAX_SECONDS = 59;
    private static final int[] PRESETS = {OFF, 3, 5};

    private GboardRamblerAutoStopSettings() {
    }

    /** The preset values in display order, starting with {@link #OFF}. */
    public static int[] presets() {
        return PRESETS.clone();
    }

    public static boolean isPreset(int seconds) {
        for (int preset : PRESETS) {
            if (preset == seconds) {
                return true;
            }
        }
        return false;
    }

    /** {@link #OFF}, or a silence length from {@link #MIN_SECONDS} to {@link #MAX_SECONDS}. */
    public static boolean isValidSeconds(int seconds) {
        return seconds == OFF || seconds >= MIN_SECONDS && seconds <= MAX_SECONDS;
    }

    /** Reads the stored choice; anything missing or unexpected reads as {@link #OFF}. */
    public static int readSeconds(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_SECONDS;
        }
        try {
            Object raw = preferences.getAll().get(PREF_KEY_SECONDS);
            int seconds;
            if (raw instanceof Number number) {
                long value = number.longValue();
                if (value != number.doubleValue() || value < OFF || value > MAX_SECONDS) {
                    return DEFAULT_SECONDS;
                }
                seconds = (int) value;
            } else if (raw instanceof String text) {
                seconds = Integer.parseInt(text.trim());
            } else {
                return DEFAULT_SECONDS;
            }
            return isValidSeconds(seconds) ? seconds : DEFAULT_SECONDS;
        } catch (Throwable unreadable) {
            return DEFAULT_SECONDS;
        }
    }

    /** Runtime read inside the keyboard process; unavailable storage keeps the feature off. */
    public static int readRuntimeSeconds(Context context) {
        try {
            Integer seconds = GboardPatchesSettings.readRuntimeSetting(
                    context, GboardRamblerAutoStopSettings::readSeconds, DEFAULT_SECONDS);
            return seconds == null ? DEFAULT_SECONDS : seconds.intValue();
        } catch (Throwable unreadable) {
            return DEFAULT_SECONDS;
        }
    }

    public static boolean writeSeconds(SharedPreferences preferences, int seconds) {
        if (preferences == null || !isValidSeconds(seconds)) {
            return false;
        }
        try {
            return preferences.edit().putInt(PREF_KEY_SECONDS, seconds).commit();
        } catch (Throwable unavailable) {
            return false;
        }
    }
}
