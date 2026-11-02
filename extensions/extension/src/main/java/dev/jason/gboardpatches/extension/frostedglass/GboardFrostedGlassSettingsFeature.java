package dev.jason.gboardpatches.extension.frostedglass;

import android.os.Build;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Collections;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.frostedglass.GboardFrostedGlassSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/** Settings entry for the Android 12+ live Window blur PoC. */
public final class GboardFrostedGlassSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";
    private final String entryTitle;
    private final String entrySummary;
    private final String headerBadge;
    private final String errorTitle;
    private final String errorSummary;
    private final String sectionTitle;
    private final String enabledTitle;
    private final String enabledSummary;
    private final String transparencyHelpTitle;
    private final String transparencyHelpSummary;
    private final String radiusTitle;
    private final String radiusSummary;
    private final String radiusHint;
    private final String transparencyTitle;
    private final String transparencySummary;
    private final String transparencyTheme;
    private final String transparencyCustom;
    private final String opacityTitle;
    private final String opacitySummary;
    private final String opacityHint;
    private final String keyTransparencyTitle;
    private final String keyTransparencySummary;
    private final String keyOpacityTitle;
    private final String keyOpacitySummary;
    private final String seamlessTitle;
    private final String seamlessSummary;

    public GboardFrostedGlassSettingsFeature(Context context) {
        entryTitle = text(context, R.string.gboard_patches_frosted_glass_title);
        entrySummary = text(context, R.string.gboard_patches_frosted_glass_summary);
        headerBadge = text(context, R.string.gboard_patches_header_badge);
        errorTitle = text(context, R.string.gboard_patches_frosted_glass_error_title);
        errorSummary = text(context, R.string.gboard_patches_frosted_glass_error_summary);
        sectionTitle = text(context, R.string.gboard_patches_frosted_glass_section);
        enabledTitle = text(context, R.string.gboard_patches_frosted_glass_enabled_title);
        enabledSummary = text(context, R.string.gboard_patches_frosted_glass_enabled_summary);
        transparencyHelpTitle = text(context, R.string.gboard_patches_frosted_glass_transparency_help_title);
        transparencyHelpSummary = text(context, R.string.gboard_patches_frosted_glass_transparency_help_summary);
        radiusTitle = text(context, R.string.gboard_patches_frosted_glass_radius_title);
        radiusSummary = text(context, R.string.gboard_patches_frosted_glass_radius_summary);
        radiusHint = text(context, R.string.gboard_patches_frosted_glass_radius_hint);
        transparencyTitle = text(context, R.string.gboard_patches_frosted_glass_transparency_title);
        transparencySummary = text(context, R.string.gboard_patches_frosted_glass_transparency_summary);
        transparencyTheme = text(context, R.string.gboard_patches_frosted_glass_transparency_theme);
        transparencyCustom = text(context, R.string.gboard_patches_frosted_glass_transparency_custom);
        opacityTitle = text(context, R.string.gboard_patches_frosted_glass_opacity_title);
        opacitySummary = text(context, R.string.gboard_patches_frosted_glass_opacity_summary);
        opacityHint = text(context, R.string.gboard_patches_frosted_glass_opacity_hint);
        keyTransparencyTitle = text(context, R.string.gboard_patches_frosted_glass_key_transparency_title);
        keyTransparencySummary = text(context, R.string.gboard_patches_frosted_glass_key_transparency_summary);
        keyOpacityTitle = text(context, R.string.gboard_patches_frosted_glass_key_opacity_title);
        keyOpacitySummary = text(context, R.string.gboard_patches_frosted_glass_key_opacity_summary);
        seamlessTitle = text(context, R.string.gboard_patches_frosted_glass_seamless_nav_bar_title);
        seamlessSummary = text(context, R.string.gboard_patches_frosted_glass_seamless_nav_bar_summary);
    }

    private static String text(Context context, int resourceId) {
        return GboardSettingsText.get(context, resourceId);
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
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && GboardPatchesFeatureAvailability.hasFeature(
                context, GboardPatchesFeatureAvailability.FEATURE_FROSTED_GLASS);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            if (host == null || host.getContext() == null) {
                return errorScreen();
            }
            Context context = host.getContext();
            SharedPreferences preferences = GboardPatchesSettings.preferences(context);
            GboardFrostedGlassSettings.ensureDefaults(preferences);
            boolean enabled = GboardFrostedGlassSettings.readEnabled(preferences);
            int strength = GboardFrostedGlassSettings.readBlurStrength(preferences);
            String transparencyMode = GboardFrostedGlassSettings.readTransparencyMode(preferences);
            int opacity = GboardFrostedGlassSettings.readCustomOpacity(preferences);
            java.util.List<GboardPatchesSettingsContract.Row> rows =
                    new java.util.ArrayList<>();
            rows.add(new GboardPatchesSettingsContract.ToggleRow(
                    enabledTitle, enabledSummary, true, enabled, value -> {
                        if (!GboardFrostedGlassSettings.writeEnabled(context, value)) {
                            Log.w(TAG, "Failed to persist frosted glass state");
                        }
                        GboardPatchesSettingsContract.refresh(host);
                        requestRestart(host);
                    }));
            rows.add(new GboardPatchesSettingsContract.InfoRow(
                    transparencyHelpTitle, transparencyHelpSummary, true));
            rows.add(new GboardPatchesSettingsContract.SelectorRow(
                    radiusTitle, radiusSummary, Integer.toString(strength), enabled,
                    () -> GboardPatchesSettingsContract.showPositiveIntegerDialog(host,
                            radiusTitle, radiusHint, strength, value -> {
                                if (!GboardFrostedGlassSettings.writeBlurStrength(context, value)) {
                                    Log.w(TAG, "Failed to persist frosted glass radius");
                                }
                                GboardPatchesSettingsContract.refresh(host);
                                requestRestart(host);
                            })));
            addTransparencyRows(rows, host, context, enabled, false, transparencyMode, opacity);
            addTransparencyRows(rows, host, context, enabled, true,
                    GboardFrostedGlassSettings.readKeyTransparencyMode(preferences),
                    GboardFrostedGlassSettings.readKeyCustomOpacity(preferences));
            rows.add(new GboardPatchesSettingsContract.ToggleRow(
                    seamlessTitle, seamlessSummary, enabled,
                    GboardFrostedGlassSettings.readSeamlessNavBarEnabled(preferences), value -> {
                        if (!GboardFrostedGlassSettings.writeSeamlessNavBarEnabled(context, value)) {
                            Log.w(TAG, "Failed to persist seamless navigation bar state");
                        }
                        GboardPatchesSettingsContract.refresh(host);
                        requestRestart(host);
                    }));
            return new GboardPatchesSettingsContract.Screen(
                    entryTitle,
                    headerBadge,
                    entryTitle,
                    "",
                    Collections.emptyList(),
                    Collections.singletonList(new GboardPatchesSettingsContract.Section(
                            sectionTitle,
                            rows)),
                    GboardPatchesSettingsContract.RefreshPolicy.none(),
                    GboardPatchesSettingsContract.PanelStyle.FLAT);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to render frosted glass settings", throwable);
            return errorScreen();
        }
    }

    private void addTransparencyRows(java.util.List<GboardPatchesSettingsContract.Row> rows,
            GboardPatchesSettingsContract.FeatureHost host, Context context, boolean enabled,
            boolean keys, String mode, int opacity) {
        String title = keys ? keyTransparencyTitle : transparencyTitle;
        String customTitle = keys ? keyOpacityTitle : opacityTitle;
        rows.add(new GboardPatchesSettingsContract.SelectorRow(
                title, keys ? keyTransparencySummary : transparencySummary,
                GboardFrostedGlassSettings.TRANSPARENCY_MODE_CUSTOM.equals(mode)
                        ? transparencyCustom : transparencyTheme,
                enabled, () -> GboardPatchesSettingsContract.showChoiceDialog(host, title,
                        new String[]{transparencyTheme, transparencyCustom},
                        new String[]{GboardFrostedGlassSettings.TRANSPARENCY_MODE_THEME,
                                GboardFrostedGlassSettings.TRANSPARENCY_MODE_CUSTOM},
                        mode, "", () -> { }, value -> {
                            if (keys) GboardFrostedGlassSettings.writeKeyTransparencyMode(context, value);
                            else GboardFrostedGlassSettings.writeTransparencyMode(context, value);
                            GboardPatchesSettingsContract.refresh(host);
                        })));
        if (GboardFrostedGlassSettings.TRANSPARENCY_MODE_CUSTOM.equals(mode)) {
            rows.add(new GboardPatchesSettingsContract.SelectorRow(
                    customTitle, keys ? keyOpacitySummary : opacitySummary, opacity + "%", enabled,
                    () -> GboardPatchesSettingsContract.showTextInputDialog(host, customTitle,
                            opacityHint, Integer.toString(opacity), value -> {
                                try {
                                    int parsed = Integer.parseInt(value.trim());
                                    if (keys) GboardFrostedGlassSettings.writeKeyCustomOpacity(context, parsed);
                                    else GboardFrostedGlassSettings.writeCustomOpacity(context, parsed);
                                    GboardPatchesSettingsContract.refresh(host);
                                } catch (NumberFormatException invalid) {
                                    GboardPatchesSettingsContract.showMessage(host, opacityHint);
                                }
                            })));
        }
    }

    private GboardPatchesSettingsContract.Screen errorScreen() {
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

    private static void requestRestart(GboardPatchesSettingsContract.FeatureHost host) {
        if (host instanceof GboardPatchesSettingsContract.Host) {
            try {
                ((GboardPatchesSettingsContract.Host) host).requestTargetRestart();
            } catch (Throwable ignored) {
                // The next keyboard lifecycle still picks up the persisted value.
            }
        }
    }
}
