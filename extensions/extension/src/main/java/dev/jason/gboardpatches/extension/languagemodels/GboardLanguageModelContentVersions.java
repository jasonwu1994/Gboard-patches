package dev.jason.gboardpatches.extension.languagemodels;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelCatalog.Family;

/** Reviewed manifest contents, separate from catalog versions and live observations. */
public final class GboardLanguageModelContentVersions {
    private static volatile JSONArray dictionaries;
    private static volatile JSONObject hmm;
    private GboardLanguageModelContentVersions() {}

    private static String asset(Context context, String name) throws Exception {
        try (var stream = context.getAssets().open("language-models/" + name)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }

    public static JSONArray dictionaries(Context context) throws Exception {
        JSONArray result = dictionaries;
        if (result == null) dictionaries = result = new JSONArray(asset(context, "delight-versions.json"));
        return result;
    }

    public static long expected(Context context, Family family, String choice, String locale) {
        try {
            if (family == Family.NWP) return GboardLanguageModelCatalog.PIXEL.equals(choice) ? -1
                    : Long.parseLong(GboardLanguageModelCatalog.version(family, choice));
            if (family == Family.KEY_CORRECTION) return GboardLanguageModelCatalog.S25.equals(choice) ? 2024052201L : -1;
            if (family == Family.HMM) {
                JSONObject contents = hmm;
                if (contents == null) hmm = contents = new JSONObject(asset(context, "hmm-versions.json"));
                JSONObject row = contents.optJSONObject(locale);
                String key = GboardLanguageModelCatalog.APK.equals(choice) ? "apk"
                        : GboardLanguageModelCatalog.PREVIOUS.equals(choice) ? "previous" : "pixel";
                return row == null ? -1 : row.optLong(key, -1);
            }
            if (family == Family.DELIGHT) {
                JSONArray contents = dictionaries(context);
                for (int i = 0; i < contents.length(); i++) {
                    JSONObject row = contents.getJSONObject(i);
                    if (locale.equals(row.getString("locale"))) return row.optLong(
                            GboardLanguageModelCatalog.APK.equals(choice) ? "apk" : "pixel", -1);
                }
            }
        } catch (Exception unavailable) { /* An unknown expectation must not replace a real observation. */ }
        return -1;
    }
}
