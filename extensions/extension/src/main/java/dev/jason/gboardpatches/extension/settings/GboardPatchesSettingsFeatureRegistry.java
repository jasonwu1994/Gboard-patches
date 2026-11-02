package dev.jason.gboardpatches.extension.settings;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
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
        addIfAvailable(context, features, new GboardAiVoiceSettingsGroupFeature(context));
        addIfAvailable(context, features, new GboardLanguageModelSettingsFeature(context));
        addIfAvailable(context, features, new GboardKeyboardToolsSettingsGroupFeature(context));
        addIfAvailable(context, features, new GboardKeyboardLayoutSettingsGroupFeature(context));
        addIfAvailable(context, features, new GboardClipboardSettingsFeature());
        addIfAvailable(context, features, new GboardMoreSettingsGroupFeature(context));
        return Collections.unmodifiableList(features);
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
