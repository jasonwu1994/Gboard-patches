package dev.jason.gboardpatches.extension.settings;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelSettingsFeature;
import dev.jason.gboardpatches.extension.clipboard.GboardClipboardSettingsFeature;
import dev.jason.gboardpatches.extension.keyboard.GboardAiVoiceSettingsGroupFeature;
import dev.jason.gboardpatches.extension.keyboard.GboardKeyboardLayoutSettingsGroupFeature;
import dev.jason.gboardpatches.extension.keyboard.GboardKeyboardToolsSettingsGroupFeature;

public final class GboardPatchesSettingsFeatureRegistry {
    private static final String TAG = "GboardPatches";

    private GboardPatchesSettingsFeatureRegistry() {
    }

    public static List<GboardPatchesSettingsContract.Feature> features(Context context) {
        List<GboardPatchesSettingsContract.Feature> features =
                new ArrayList<GboardPatchesSettingsContract.Feature>();
        for (GboardPatchesSettingsContract.Feature feature : allFeatures(context)) {
            addIfAvailable(context, features, feature);
        }
        return Collections.unmodifiableList(features);
    }

    /** Every home-page feature in display order, before availability filtering. */
    public static List<GboardPatchesSettingsContract.Feature> allFeatures(Context context) {
        return Collections.unmodifiableList(Arrays.asList(
                new GboardAiVoiceSettingsGroupFeature(context),
                new GboardLanguageModelSettingsFeature(context),
                new GboardKeyboardToolsSettingsGroupFeature(context),
                new GboardKeyboardLayoutSettingsGroupFeature(context),
                new GboardClipboardSettingsFeature(),
                new GboardMoreSettingsGroupFeature(context)));
    }

    private static void addIfAvailable(Context context,
            List<GboardPatchesSettingsContract.Feature> features,
            GboardPatchesSettingsContract.Feature feature) {
        try {
            if (feature.isAvailable(context)) {
                features.add(feature);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to evaluate feature availability: " + feature.getClass().getName(),
                    throwable);
        }
    }
}
