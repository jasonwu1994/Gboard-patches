package dev.jason.gboardpatches.extension.settings;

import android.content.Context;
import java.util.List;
import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.backuprestore.GboardBackupRestoreSettingsFeature;
import dev.jason.gboardpatches.extension.developeroptions.GboardDeveloperOptionsSettingsFeature;
import dev.jason.gboardpatches.extension.lanftp.settings.GboardLanFtpSettingsFeature;
import dev.jason.gboardpatches.extension.settingshomepage.GboardSettingsHomepageSettingsFeature;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardFeatureGroup;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardMoreSettingsGroupFeature implements GboardPatchesSettingsContract.Feature {
    private final GboardFeatureGroup delegate;

    public GboardMoreSettingsGroupFeature(Context context) {
        String title = GboardSettingsText.get(context, R.string.gboard_patches_group_more_title);
        String summary = GboardSettingsText.get(context, R.string.gboard_patches_group_more_summary);
        delegate = new GboardFeatureGroup(title, summary,
                GboardSettingsText.get(context, R.string.gboard_patches_header_badge), summary,
                GboardSettingsText.get(context, R.string.gboard_patches_group_more_empty_title),
                GboardSettingsText.get(context, R.string.gboard_patches_group_more_empty_summary),
                List.of(new GboardLanFtpSettingsFeature(context),
                        new GboardSettingsHomepageSettingsFeature(),
                        new GboardBackupRestoreSettingsFeature(context),
                        new GboardDeveloperOptionsSettingsFeature(context)));
    }

    @Override public String getEntryTitle() { return delegate.getEntryTitle(); }
    @Override public String getEntrySummary() { return delegate.getEntrySummary(); }
    @Override public int getEntryIcon() { return GboardPatchesIcons.MORE; }
    @Override public String getEntryTone() { return "slate"; }
    @Override public boolean isAvailable(Context context) { return delegate.isAvailable(context); }
    @Override public List<GboardPatchesSettingsContract.Feature> getNavigationChildren() {
        return delegate.getNavigationChildren();
    }
    @Override public GboardPatchesSettingsContract.Screen buildScreen(GboardPatchesSettingsContract.FeatureHost host) {
        return delegate.buildScreen(host);
    }
}
