package dev.jason.gboardpatches.extension.keyboard;

import android.content.Context;

import java.util.Arrays;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardFeatureGroup;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;
import dev.jason.gboardpatches.extension.symbolfooter.GboardSymbolFooterOrderSettingsFeature;
import dev.jason.gboardpatches.extension.zhuyinbottomrow.GboardZhuyinBottomRowWeightSettingsFeature;

public final class GboardKeyboardLayoutSettingsGroupFeature
        implements GboardPatchesSettingsContract.Feature {
    private final GboardFeatureGroup delegate;

    public GboardKeyboardLayoutSettingsGroupFeature(Context context) {
        delegate = new GboardFeatureGroup(
                GboardSettingsText.get(context,
                        R.string.gboard_patches_group_keyboard_layout_title),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_group_keyboard_layout_summary),
                GboardSettingsText.get(context, R.string.gboard_patches_header_badge),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_group_keyboard_header_summary),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_group_keyboard_empty_title),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_group_keyboard_empty_summary),
                Arrays.asList(
                        new GboardLatinGlobeKeyIgnoreIntervalSettingsFeature(context),
                        new GboardEnglishUppercaseToggleSettingsFeature(context),
                        new GboardZhuyinBottomRowWeightSettingsFeature(context),
                        new GboardSymbolFooterOrderSettingsFeature(context)));
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
