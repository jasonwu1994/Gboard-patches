package dev.jason.gboardpatches.extension.keyboard;

import android.content.Context;

import java.util.Arrays;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.advancedvoice.GboardAdvancedVoiceSettingsFeature;
import dev.jason.gboardpatches.extension.bluetoothmicrophone.GboardBluetoothMicrophoneSettingsFeature;
import dev.jason.gboardpatches.extension.settings.GboardFeatureGroup;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;
import dev.jason.gboardpatches.extension.writingtools.GboardAiWritingToolsSettingsFeature;

public final class GboardAiVoiceSettingsGroupFeature
        implements GboardPatchesSettingsContract.Feature {
    private final GboardFeatureGroup delegate;

    public GboardAiVoiceSettingsGroupFeature(Context context) {
        delegate = new GboardFeatureGroup(
                GboardSettingsText.get(context, R.string.gboard_patches_group_ai_voice_title),
                GboardSettingsText.get(context, R.string.gboard_patches_group_ai_voice_summary),
                GboardSettingsText.get(context, R.string.gboard_patches_header_badge),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_group_keyboard_header_summary),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_group_keyboard_empty_title),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_group_keyboard_empty_summary),
                Arrays.asList(
                        new GboardAiWritingToolsSettingsFeature(context),
                        new GboardAdvancedVoiceSettingsFeature(context),
                        new GboardBluetoothMicrophoneSettingsFeature(context)));
    }

    @Override
    public String getEntryTitle() {
        return delegate.getEntryTitle();
    }

    @Override
    public String getEntrySummary() {
        return delegate.getEntrySummary();
    }

    @Override
    public boolean isAvailable(Context context) {
        return delegate.isAvailable(context);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        return delegate.buildScreen(host);
    }
}
