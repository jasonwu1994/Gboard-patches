package dev.jason.gboardpatches.extension.languagemodels;

import android.content.Context;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;
import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelContentVersions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesIcons;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

/** Search the reviewed manifest by human language names; no network access while rendering. */
final class GboardDictionaryLanguagesFeature implements GboardPatchesSettingsContract.Feature {
    private final Context context;
    private String query;
    GboardDictionaryLanguagesFeature(Context context, String query) { this.context = context; this.query = query; }
    @Override public String getEntryTitle() { return GboardSettingsText.format(context, R.string.language_models_dictionary_languages); }
    @Override public String getEntrySummary() { return GboardSettingsText.format(context, R.string.language_models_dictionary_languages_help); }

    @Override public GboardPatchesSettingsContract.Screen buildScreen(GboardPatchesSettingsContract.FeatureHost host) {
        List<GboardPatchesSettingsContract.Row> rows = new ArrayList<>();
        var search = new GboardPatchesSettingsContract.SearchRow(GboardSettingsText.format(context, R.string.language_models_search),
                query.isEmpty() ? GboardSettingsText.format(context, R.string.language_models_search_hint) : query,
                () -> GboardPatchesSettingsContract.showTextInputDialog(host, getEntryTitle(), GboardSettingsText.format(context, R.string.language_models_search_hint), query,
                        value -> { query = value.trim(); GboardPatchesSettingsContract.refresh(host); }));
        search.setDecoration(new GboardPatchesSettingsContract.RowDecoration()
                .leadingIcon(GboardPatchesIcons.SEARCH, "teal"));
        int matched = 0;
        try {
            JSONArray list = GboardLanguageModelContentVersions.dictionaries(context);
            Locale uiLocale = context.getResources().getConfiguration().getLocales().get(0);
            String needle = query.toLowerCase(Locale.ROOT);
            for (int i = 0; i < list.length(); i++) {
                JSONObject entry = list.getJSONObject(i);
                String tag = entry.getString("locale");
                Locale language = Locale.forLanguageTag(tag);
                String display = language.getDisplayName(uiLocale);
                String english = language.getDisplayName(Locale.ENGLISH);
                if (!needle.isEmpty() && !(tag + " " + display + " " + english).toLowerCase(Locale.ROOT).contains(needle)) continue;
                matched++;
                rows.add(new GboardPatchesSettingsContract.DetailRow(display + " · " + tag,
                        GboardSettingsText.format(context, R.string.language_models_apk) + ": " + entry.opt("apk")
                                + "\nPixel 2026-10-27: " + entry.opt("pixel"), true));
            }
            if (matched == 0) rows.add(new GboardPatchesSettingsContract.DetailRow(
                    GboardSettingsText.format(context, R.string.language_models_search), GboardSettingsText.format(context, R.string.language_models_refine_search), true));
        } catch (Exception unavailable) {
            rows.add(new GboardPatchesSettingsContract.DetailRow(getEntryTitle(), GboardSettingsText.format(context, R.string.language_models_unavailable), true));
        }
        return new GboardPatchesSettingsContract.Screen(getEntryTitle(), "", getEntryTitle(), getEntrySummary(), List.of(),
                List.of(new GboardPatchesSettingsContract.Section("", List.of(search)),
                        new GboardPatchesSettingsContract.Section(
                                GboardSettingsText.format(context, R.string.language_models_results_count, matched), rows)));
    }
}
