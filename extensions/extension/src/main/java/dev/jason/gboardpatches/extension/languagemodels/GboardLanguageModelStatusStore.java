package dev.jason.gboardpatches.extension.languagemodels;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

/** Device-local observations, kept outside portable user settings. */
final class GboardLanguageModelStatusStore {
    private static final String FILE = "gboard_language_model_status";
    private GboardLanguageModelStatusStore() { }

    static SharedPreferences preferences(Context context) {
        if (!GboardPatchesSettings.isUserUnlocked(context)) return null;
        try { return context.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
        catch (Throwable unavailable) { return null; }
    }

    static JSONObject read(Context context) {
        try {
            SharedPreferences preferences = preferences(context);
            return preferences == null ? new JSONObject() : new JSONObject(preferences.getString("status", "{}"));
        } catch (Throwable unavailable) { return new JSONObject(); }
    }

    static void write(Context context, JSONObject status) {
        try {
            SharedPreferences preferences = preferences(context);
            if (preferences == null) return;
            var observation = GboardLanguageModelSettings.observation(GboardPatchesSettings.preferencesOrNull(context));
            if (!observation.enabled() || !observation.session().equals(status.optString("observation_session"))) return;
            preferences.edit().putString("status", status.toString()).apply();
        } catch (Throwable unavailable) { /* Observation storage is optional. */ }
    }
}
