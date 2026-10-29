package dev.jason.gboardpatches.extension.languagemodels;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelCatalog.Family;

public final class GboardLanguageModelSettings {
    public static final String ENABLED = "pref_language_models_enabled";
    public static final String OBSERVATION_ENABLED = "pref_language_models_observation_enabled";
    public static final String OBSERVATION_SESSION = "pref_language_models_observation_session";
    private GboardLanguageModelSettings() {}

    public static final class Observation {
        private final boolean enabled;
        private final String session;
        public Observation(boolean enabled, String session) { this.enabled = enabled; this.session = session; }
        public boolean enabled() { return enabled; }
        public String session() { return session; }
        public static Observation disabled() { return new Observation(false, ""); }
        @Override public boolean equals(Object value) {
            return value instanceof Observation other && enabled == other.enabled && java.util.Objects.equals(session, other.session);
        }
        @Override public int hashCode() { return java.util.Objects.hash(enabled, session); }
    }

    public static Observation observation(SharedPreferences preferences) {
        if (preferences == null) return Observation.disabled();
        try {
            Object value = preferences.getAll().get(OBSERVATION_ENABLED);
            boolean enabled = Boolean.TRUE.equals(value) || "true".equals(value);
            String session = preferences.getString(OBSERVATION_SESSION, "");
            return enabled && !session.isEmpty() ? new Observation(true, session) : Observation.disabled();
        } catch (Throwable unavailable) { return Observation.disabled(); }
    }

    public static boolean writeObservation(Context context, boolean enabled) {
        try {
            SharedPreferences preferences = GboardPatchesSettings.preferencesOrNull(context);
            if (preferences == null) return false;
            if (observation(preferences).enabled() == enabled) return true;
            return preferences.edit().putBoolean(OBSERVATION_ENABLED, enabled)
                    .putString(OBSERVATION_SESSION, java.util.UUID.randomUUID().toString()).commit();
        } catch (Throwable unavailable) { return false; }
    }

    public static Snapshot read(SharedPreferences preferences) {
        if (preferences == null) return null;
        try { return fromValues(preferences.getAll()); }
        catch (RuntimeException unavailable) { return null; }
    }

    public static Snapshot fromValues(Map<String, ?> values) {
        Object enabled = values.get(ENABLED);
        // Serialized scalar settings may use strings.
        if (enabled instanceof String text) {
            if ("true".equalsIgnoreCase(text)) enabled = Boolean.TRUE;
            else if ("false".equalsIgnoreCase(text)) enabled = Boolean.FALSE;
            else return null;
        }
        if (enabled != null && !(enabled instanceof Boolean)) return null;
        Map<Family, String> choices = new EnumMap<>(Family.class);
        for (Family family : Family.values()) {
            Object choice = values.get(family.preferenceKey());
            if (choice == null) choice = GboardLanguageModelCatalog.APK;
            if (!(choice instanceof String text)
                    || !GboardLanguageModelCatalog.choices(family).contains(text)) return null;
            choices.put(family, text);
        }
        return new Snapshot(Boolean.TRUE.equals(enabled), choices);
    }

    public static boolean write(Context context, Family family, String choice, Boolean enabled) {
        if (context == null || (family != null && !GboardLanguageModelCatalog.choices(family).contains(choice))) return false;
        SharedPreferences preferences = GboardPatchesSettings.preferencesOrNull(context);
        if (preferences == null) return false;
        SharedPreferences.Editor editor = preferences.edit();
        if (family != null) editor.putString(family.preferenceKey(), choice);
        if (enabled != null) editor.putBoolean(ENABLED, enabled);
        return editor.commit();
    }

    public static final class Snapshot {
        public final boolean enabled;
        private final Map<Family, String> choices;
        public Snapshot(boolean enabled, Map<Family, String> choices) {
            this.enabled = enabled;
            this.choices = Collections.unmodifiableMap(new EnumMap<>(choices));
        }
        public String choice(Family family) { return choices.get(family); }
        public String effectiveChoice(Family family) {
            return enabled ? choice(family) : GboardLanguageModelCatalog.APK;
        }
        public String signature() {
            StringBuilder value = new StringBuilder(Boolean.toString(enabled));
            for (Family family : Family.values()) value.append('|').append(family.id).append('=').append(choice(family));
            // Invalidate observations produced when disabled meant following remote configuration.
            return value.append("|policy=apk-default-v3").toString();
        }
    }
}
