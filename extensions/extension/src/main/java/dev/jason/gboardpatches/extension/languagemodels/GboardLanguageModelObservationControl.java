package dev.jason.gboardpatches.extension.languagemodels;

import android.content.Context;
import android.content.SharedPreferences;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelSettings.Observation;

/** Cached diagnostic control; no polling or preference reads on normal native callbacks. */
final class GboardLanguageModelObservationControl {
    private static volatile Observation state = Observation.disabled();
    private static volatile SharedPreferences watched;
    private static final Object RELOAD_LOCK = new Object();
    private static final SharedPreferences.OnSharedPreferenceChangeListener listener = (preferences, key) -> {
        if (key == null || GboardLanguageModelSettings.OBSERVATION_ENABLED.equals(key)
                || GboardLanguageModelSettings.OBSERVATION_SESSION.equals(key)) reload(preferences);
    };
    private GboardLanguageModelObservationControl() { }

    static Observation state() {
        try {
            if (watched == null) initialize();
            return state;
        } catch (Throwable unavailable) { return Observation.disabled(); }
    }

    private static void initialize() throws Exception {
        initialize(GboardLanguageModelRuntime.application());
    }

    static void initialize(Context context) {
        SharedPreferences preferences = GboardPatchesSettings.preferencesOrNull(context);
        if (preferences == null) return;
        synchronized (GboardLanguageModelObservationControl.class) {
            if (watched == preferences) return;
            if (watched != null) watched.unregisterOnSharedPreferenceChangeListener(listener);
            preferences.registerOnSharedPreferenceChangeListener(listener);
            watched = preferences;
        }
        reload(preferences);
    }

    private static void reload(SharedPreferences preferences) {
        try {
            boolean changed;
            synchronized (RELOAD_LOCK) {
                Observation next = GboardLanguageModelSettings.observation(preferences);
                changed = !next.equals(state);
                state = next;
            }
            if (changed) GboardLanguageModelRuntime.resetObservation();
        } catch (Throwable unavailable) {
            state = Observation.disabled();
            GboardLanguageModelRuntime.resetObservation();
        }
    }
}
