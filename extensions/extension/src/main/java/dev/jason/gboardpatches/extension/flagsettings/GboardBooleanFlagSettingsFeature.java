package dev.jason.gboardpatches.extension.flagsettings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Collections;

import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

public class GboardBooleanFlagSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    public interface SettingsStore {
        void ensureDefault(SharedPreferences preferences);

        boolean readEnabled(SharedPreferences preferences);

        boolean writeEnabled(SharedPreferences preferences, boolean enabled);
    }

    private static final String TAG = "GboardPatches";

    private final String featureMarker;
    private final String entryTitle;
    private final String entrySummary;
    private final String toggleTitle;
    private final String headerBadge;
    private final String errorTitle;
    private final String errorSummary;
    private final String sectionTitle;
    private final GboardPatchesSettingsContract.PreviewSpec previewSpec;
    private final SettingsStore settingsStore;
    private String restartKey;

    public GboardBooleanFlagSettingsFeature(
            String featureMarker,
            String entryTitle,
            String entrySummary,
            String toggleTitle,
            String headerBadge,
            String errorTitle,
            String errorSummary,
            String sectionTitle,
            GboardPatchesSettingsContract.PreviewSpec previewSpec,
            SettingsStore settingsStore) {
        this.featureMarker = featureMarker;
        this.entryTitle = entryTitle;
        this.entrySummary = entrySummary;
        this.toggleTitle = toggleTitle;
        this.headerBadge = headerBadge;
        this.errorTitle = errorTitle;
        this.errorSummary = errorSummary;
        this.sectionTitle = sectionTitle;
        this.previewSpec = previewSpec;
        this.settingsStore = settingsStore;
    }

    protected final void declareRestartRequired(String preferenceKey) {
        this.restartKey = preferenceKey;
    }

    @Override
    public String getEntryTitle() {
        return entryTitle;
    }

    @Override
    public String getEntrySummary() {
        return entrySummary;
    }

    @Override
    public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(context, featureMarker);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            if (host == null || host.getContext() == null || settingsStore == null) {
                return buildErrorScreen();
            }
            SharedPreferences preferences = GboardPatchesSettings.preferences(host.getContext());
            settingsStore.ensureDefault(preferences);
            return buildScreenForState(
                    settingsStore.readEnabled(preferences),
                    enabled -> settingsStore.writeEnabled(preferences, enabled));
        } catch (Throwable throwable) {
            try {
                Log.w(TAG, "Failed to render flag settings: " + featureMarker, throwable);
            } catch (Throwable ignored) {
                // Rendering the warning screen is the only remaining action.
            }
            return buildErrorScreen();
        }
    }

    public final GboardPatchesSettingsContract.Screen buildScreenForState(
            boolean enabled,
            GboardPatchesSettingsContract.ToggleAction toggleAction) {
        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                headerBadge,
                entryTitle,
                "",
                Collections.emptyList(),
                Collections.singletonList(new GboardPatchesSettingsContract.Section(
                        sectionTitle,
                        Collections.singletonList(new GboardPatchesSettingsContract.ToggleRow(
                                toggleTitle,
                                entrySummary,
                                true,
                                enabled,
                                toggleAction,
                                previewSpec)
                                .setRestartRequired(restartKey)))),
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT);
    }

    private GboardPatchesSettingsContract.Screen buildErrorScreen() {
        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                headerBadge,
                entryTitle,
                "",
                Collections.singletonList(new GboardPatchesSettingsContract.StatusBlock(
                        errorTitle,
                        errorSummary,
                        GboardPatchesSettingsContract.StatusTone.WARNING)),
                Collections.emptyList());
    }
}
