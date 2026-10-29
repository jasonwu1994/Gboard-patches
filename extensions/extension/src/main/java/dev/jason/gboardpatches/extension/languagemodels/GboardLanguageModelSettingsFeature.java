package dev.jason.gboardpatches.extension.languagemodels;

import android.content.Context;
import android.content.SharedPreferences;
import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelContentVersions;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;
import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesIcons;
import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelCatalog;
import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelCatalog.Family;
import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardLanguageModelSettingsFeature implements GboardPatchesSettingsContract.Feature {
    private final Context context;
    public GboardLanguageModelSettingsFeature(Context context) { this.context = context; }
    private String text(int id) { return GboardSettingsText.get(context, id); }
    @Override public String getEntryTitle() { return text(R.string.language_models_entry_title); }
    @Override public String getEntrySummary() { return text(R.string.language_models_summary); }
    @Override public int getEntryIcon() { return GboardPatchesIcons.KEYBOARD; }
    @Override public String getEntryTone() { return "teal"; }
    @Override public List<GboardPatchesSettingsContract.Feature> getNavigationChildren() {
        return List.of(new ModelStatusFeature());
    }
    @Override public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(
                context, GboardPatchesFeatureAvailability.FEATURE_LANGUAGE_MODELS);
    }

    @Override public GboardPatchesSettingsContract.Screen buildScreen(GboardPatchesSettingsContract.FeatureHost host) {
        String pageTitle = text(R.string.language_models_title);
        GboardLanguageModelSettings.Snapshot state = GboardLanguageModelSettings.read(GboardPatchesSettings.preferencesOrNull(context));
        if (state == null) state = GboardLanguageModelSettings.fromValues(Collections.emptyMap());
        List<GboardPatchesSettingsContract.Section> sections = new ArrayList<>();
        sections.add(new GboardPatchesSettingsContract.Section("", Collections.singletonList(
                new GboardPatchesSettingsContract.ToggleRow(pageTitle, text(R.string.language_models_enabled_help),
                        true, state.enabled, value -> GboardLanguageModelSettings.write(context, null, null, value))
                        .setRestartRequired(GboardLanguageModelSettings.ENABLED))));
        sections.add(new GboardPatchesSettingsContract.Section(text(R.string.language_models_actual_title),
                List.of(new GboardPatchesSettingsContract.NavigationRow(text(R.string.language_models_details),
                        text(R.string.language_models_details_help), true, () -> GboardPatchesSettingsContract.openFeature(host, new ModelStatusFeature())))));
        sections.add(modelSection(R.string.language_models_group_suggestions, host, state,
                Family.HMM, Family.DELIGHT, Family.NWP, Family.MOZC));
        sections.add(modelSection(R.string.language_models_group_correction, host, state,
                Family.KEY_CORRECTION, Family.MOZC_SPELL, Family.GRAMMAR));
        sections.add(modelSection(R.string.language_models_group_auxiliary, host, state,
                Family.SAFT, Family.LANG_ID));
        sections.add(new GboardPatchesSettingsContract.Section(text(R.string.language_models_about),
                List.of(
                        explanation(R.string.language_models_hmm_about_title, R.string.language_models_hmm_about),
                        explanation(R.string.language_models_delight_about_title, R.string.language_models_delight_about),
                        explanation(R.string.language_models_nwp_about_title, R.string.language_models_nwp_about),
                        explanation(R.string.language_models_key_correction_about_title, R.string.language_models_key_correction_about),
                        explanation(R.string.language_models_saft_about_title, R.string.language_models_saft_about),
                        explanation(R.string.language_models_mozc_about_title, R.string.language_models_mozc_about),
                        explanation(R.string.language_models_mozc_spell_about_title, R.string.language_models_mozc_spell_about),
                        explanation(R.string.language_models_grammar_about_title, R.string.language_models_grammar_about),
                        explanation(R.string.language_models_langid_about_title, R.string.language_models_langid_about))));
        return new GboardPatchesSettingsContract.Screen(pageTitle, "", pageTitle, getEntrySummary(),
                List.of(), sections, GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT);
    }

    private GboardPatchesSettingsContract.Section modelSection(int heading,
            GboardPatchesSettingsContract.FeatureHost host, GboardLanguageModelSettings.Snapshot state,
            Family... families) {
        List<GboardPatchesSettingsContract.Row> rows = new ArrayList<>();
        for (Family family : families) {
            String choice = state.effectiveChoice(family);
            rows.add(new GboardPatchesSettingsContract.SelectorRow(title(family),
                    description(family) + "\n" + sourceLabel(family, choice),
                    selectionValue(family, choice), state.enabled,
                    () -> choose(host, family, state.choice(family))).setRestartRequired(family.preferenceKey()));
            if (family == Family.DELIGHT) rows.add(new GboardPatchesSettingsContract.NavigationRow(
                    text(R.string.language_models_dictionary_languages), text(R.string.language_models_dictionary_languages_help),
                    true, () -> GboardPatchesSettingsContract.openFeature(host, new GboardDictionaryLanguagesFeature(context, ""))));
        }
        return new GboardPatchesSettingsContract.Section(text(heading), rows);
    }

    private GboardPatchesSettingsContract.DetailRow explanation(int title, int summary) {
        return new GboardPatchesSettingsContract.DetailRow(text(title), text(summary), true);
    }

    private final class ModelStatusFeature implements GboardPatchesSettingsContract.Feature {
        private SharedPreferences corePreferences, reportPreferences;
        private SharedPreferences.OnSharedPreferenceChangeListener listener;
        @Override public String getEntryTitle() { return text(R.string.language_models_details); }
        @Override public String getEntrySummary() { return text(R.string.language_models_details_help); }
        @Override public boolean updatesDuringKeyboardPreview() { return true; }
        @Override public void onVisible(GboardPatchesSettingsContract.FeatureHost host) {
            if (listener != null) return;
            listener = (preferences, key) -> {
                if (key == null || "status".equals(key)
                        || GboardLanguageModelSettings.OBSERVATION_ENABLED.equals(key)
                        || GboardLanguageModelSettings.OBSERVATION_SESSION.equals(key)) {
                    try { GboardPatchesSettingsContract.refresh(host); } catch (Throwable ignored) { }
                }
            };
            try {
                GboardLanguageModelObservationControl.initialize(context);
                corePreferences = GboardPatchesSettings.preferencesOrNull(context);
                reportPreferences = GboardLanguageModelStatusStore.preferences(context);
                if (corePreferences != null) corePreferences.registerOnSharedPreferenceChangeListener(listener);
                if (reportPreferences != null) reportPreferences.registerOnSharedPreferenceChangeListener(listener);
            } catch (Throwable unavailable) { onHidden(host); }
        }
        @Override public void onHidden(GboardPatchesSettingsContract.FeatureHost host) {
            if (listener == null) return;
            try { if (corePreferences != null) corePreferences.unregisterOnSharedPreferenceChangeListener(listener); }
            catch (Throwable ignored) { }
            try { if (reportPreferences != null) reportPreferences.unregisterOnSharedPreferenceChangeListener(listener); }
            catch (Throwable ignored) { }
            corePreferences = null; reportPreferences = null; listener = null;
        }
        @Override public GboardPatchesSettingsContract.Screen buildScreen(GboardPatchesSettingsContract.FeatureHost host) {
            var state = GboardLanguageModelSettings.read(GboardPatchesSettings.preferencesOrNull(context));
            if (state == null) state = GboardLanguageModelSettings.fromValues(Collections.emptyMap());
            return new GboardPatchesSettingsContract.Screen(getEntryTitle(), "", getEntryTitle(), getEntrySummary(),
                    statusBlocks(state), List.of(new GboardPatchesSettingsContract.Section("", List.of(
                            new GboardPatchesSettingsContract.ToggleRow(text(R.string.language_models_observation_title),
                                    text(R.string.language_models_observation_help), true,
                                    GboardLanguageModelSettings.observation(GboardPatchesSettings.preferencesOrNull(context)).enabled(), value -> {
                                        boolean wasEnabled = GboardLanguageModelSettings.observation(GboardPatchesSettings.preferencesOrNull(context)).enabled();
                                        if (!GboardLanguageModelSettings.writeObservation(context, value))
                                            throw new IllegalStateException("Observation setting not saved");
                                        GboardPatchesSettingsContract.refresh(host);
                                        if (value && !wasEnabled) {
                                            try { GboardPatchesSettingsContract.requestTargetRestart(host); }
                                            catch (Throwable unavailable) {
                                                GboardPatchesSettingsContract.showMessage(host, text(R.string.language_models_observation_restart_failed));
                                            }
                                        }
                                    }))), new GboardPatchesSettingsContract.Section(text(R.string.language_models_about),
                            List.of(new GboardPatchesSettingsContract.DetailRow(text(R.string.language_models_detection_scope),
                                    text(R.string.language_models_detection_scope_help), true)))),
                    GboardPatchesSettingsContract.RefreshPolicy.none(),
                    GboardPatchesSettingsContract.PanelStyle.FLAT).setMasterToggleFirst(true);
        }
    }

    private void choose(GboardPatchesSettingsContract.FeatureHost host, Family family, String current) {
        List<String> values = GboardLanguageModelCatalog.choices(family);
        String[] labels = values.stream().map(value -> label(family, value)).toArray(String[]::new);
        GboardPatchesSettingsContract.showChoiceDialog(host, title(family), labels, values.toArray(new String[0]), current, null, null, value -> {
            if (!GboardLanguageModelSettings.write(context, family, value, null)) throw new IllegalStateException("Model selection not saved");
            GboardPatchesSettingsContract.refresh(host);
        });
    }

    private String sourceLabel(Family family, String choice) {
        if (GboardLanguageModelCatalog.APK.equals(choice)) return text(R.string.language_models_apk);
        if (GboardLanguageModelCatalog.NWP_2023.equals(choice) || GboardLanguageModelCatalog.S25.equals(choice))
            return text(R.string.language_models_nwp_2023);
        return "Pixel 11 XL";
    }

    private String selectionValue(Family family, String choice) {
        if (family == Family.MOZC_SPELL && GboardLanguageModelCatalog.APK.equals(choice))
            return text(R.string.language_models_not_configured);
        if (family == Family.NWP && GboardLanguageModelCatalog.PIXEL.equals(choice))
            return text(R.string.language_models_disabled);
        return GboardLanguageModelCatalog.version(family, choice);
    }

    private String label(Family family, String choice) {
        String version = selectionValue(family, choice);
        if (GboardLanguageModelCatalog.APK.equals(choice)) return text(R.string.language_models_apk) + " · " + version;
        if (GboardLanguageModelCatalog.NWP_2023.equals(choice) || GboardLanguageModelCatalog.S25.equals(choice))
            return text(R.string.language_models_nwp_2023) + " · " + version;
        if (family == Family.NWP) return text(R.string.language_models_pixel_disabled);
        return "Pixel · " + version;
    }

    private String title(Family family) {
        return text(switch (family) {
            case HMM -> R.string.language_models_hmm_title;
            case DELIGHT -> R.string.language_models_delight_title;
            case NWP -> R.string.language_models_nwp_title;
            case KEY_CORRECTION -> R.string.language_models_key_correction_title;
            case SAFT -> R.string.language_models_saft_title;
            case MOZC -> R.string.language_models_mozc_title;
            case MOZC_SPELL -> R.string.language_models_mozc_spell_title;
            case GRAMMAR -> R.string.language_models_grammar_title;
            case LANG_ID -> R.string.language_models_langid_title;
        });
    }

    private String description(Family family) {
        return text(switch (family) {
            case HMM -> R.string.language_models_hmm_help;
            case DELIGHT -> R.string.language_models_delight_help;
            case NWP -> R.string.language_models_nwp_help;
            case KEY_CORRECTION -> R.string.language_models_key_correction_help;
            case SAFT -> R.string.language_models_saft_help;
            case MOZC -> R.string.language_models_mozc_help;
            case MOZC_SPELL -> R.string.language_models_mozc_spell_help;
            case GRAMMAR -> R.string.language_models_grammar_help;
            case LANG_ID -> R.string.language_models_langid_help;
        });
    }

    private List<GboardPatchesSettingsContract.StatusBlock> statusBlocks(GboardLanguageModelSettings.Snapshot selected) {
        List<GboardPatchesSettingsContract.StatusBlock> blocks = new ArrayList<>();
        var observation = GboardLanguageModelSettings.observation(GboardPatchesSettings.preferencesOrNull(context));
        if (!observation.enabled()) {
            blocks.add(new GboardPatchesSettingsContract.StatusBlock(text(R.string.language_models_observation_title),
                    text(R.string.language_models_observation_off)));
            return blocks;
        }
        JSONObject status = GboardLanguageModelStatusStore.read(context);
        if (!observation.session().equals(status.optString("observation_session"))) {
            blocks.add(new GboardPatchesSettingsContract.StatusBlock(text(R.string.language_models_observation_wait_title),
                    text(R.string.language_models_observation_wait)));
            return blocks;
        }
        boolean stale = !selected.signature().equals(status.optString("signature"));
        if (stale || status.optInt("pid") <= 0) {
            blocks.add(new GboardPatchesSettingsContract.StatusBlock(text(R.string.language_models_observation_wait_title),
                    text(R.string.language_models_restart_help), GboardPatchesSettingsContract.StatusTone.INFO));
            return blocks;
        }
        JSONObject models = status.optJSONObject("models");
        if (models == null || models.length() == 0) {
            blocks.add(new GboardPatchesSettingsContract.StatusBlock(text(R.string.language_models_observation_wait_title),
                    text(R.string.language_models_observation_wait)));
            return blocks;
        }
        List<String> names = new ArrayList<>(); models.keys().forEachRemaining(names::add); Collections.sort(names);
        for (String name : names) {
            JSONObject model = models.optJSONObject(name);
            if (model == null) continue;
            Family family = GboardLanguageModelCatalog.forId(model.optString("family"));
            if (family == null) continue;
            String tag = model.optString("locale");
            String language = Locale.forLanguageTag("en-840".equals(tag) ? "en-US" : tag)
                    .getDisplayName(context.getResources().getConfiguration().getLocales().get(0));
            String stage = "loaded".equals(model.optString("stage")) ? text(R.string.language_models_loaded)
                    : "enrolled".equals(model.optString("stage")) ? text(R.string.language_models_enrolled)
                    : "ready".equals(model.optString("stage")) ? text(R.string.language_models_nwp_ready)
                    : text(R.string.language_models_submitted);
            long expected = GboardLanguageModelContentVersions.expected(
                    context, family, selected.effectiveChoice(family), tag);
            boolean mismatch = expected > 0 && expected != model.optLong("version");
            String summary = family.name() + " · " + tag + "\n" + stage;
            if (family == Family.KEY_CORRECTION) {
                summary += "\n" + model.optString("pack");
                if (model.optLong("version") > 0) summary += "\n" + text(R.string.language_models_hash_identity);
            }
            if (mismatch) summary += "\n" + context.getString(R.string.language_models_expected_version, expected);
            String observed = model.optLong("version") > 0 ? Long.toString(model.optLong("version"))
                    : text(R.string.language_models_unknown_version);
            blocks.add(new GboardPatchesSettingsContract.StatusBlock(language + " · " + observed,
                    summary, mismatch ? GboardPatchesSettingsContract.StatusTone.INFO : GboardPatchesSettingsContract.StatusTone.NEUTRAL));
        }
        return blocks;
    }
}
