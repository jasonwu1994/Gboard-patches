package dev.jason.gboardpatches.extension.roundedkeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardRoundedKeyboardSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";
    private static final String UNUSED_VALUE = "__unused__";

    private final Context stringContext;
    private final String entryTitle;
    private final String entrySummary;
    private final String headerBadge;
    private final String errorTitle;
    private final String errorSummary;
    private final String sectionTitle;
    private final String enabledTitle;
    private final String enabledSummary;
    private final String modeTitle;
    private final String modeSummary;
    private final String modeDialogTitle;
    private final String[] modeLabels;
    private final String[] modeValues;
    private final String topRadiusTitle;
    private final String topRadiusSummary;
    private final String topRadiusDialogTitle;
    private final String bottomRadiusTitle;
    private final String bottomRadiusSummary;
    private final String bottomRadiusDialogTitle;
    private final String radiusDialogHint;
    private final String radiusDialogError;
    private final String advancedSectionTitle;
    private final String resetTitle;
    private final String resetSummary;
    private final String resetConfirmTitle;
    private final String resetConfirmMessage;

    public GboardRoundedKeyboardSettingsFeature(Context context) {
        stringContext = context;
        entryTitle = text(context, R.string.gboard_patches_rounded_keyboard_title);
        entrySummary = text(context, R.string.gboard_patches_rounded_keyboard_summary);
        headerBadge = text(context, R.string.gboard_patches_header_badge);
        errorTitle = text(context, R.string.gboard_patches_rounded_keyboard_error_title);
        errorSummary = text(context, R.string.gboard_patches_rounded_keyboard_error_summary);
        sectionTitle = text(context, R.string.gboard_patches_rounded_keyboard_section);
        enabledTitle = text(context, R.string.gboard_patches_rounded_keyboard_enabled_title);
        enabledSummary = text(context, R.string.gboard_patches_rounded_keyboard_enabled_summary);
        modeTitle = text(context, R.string.gboard_patches_rounded_keyboard_mode_title);
        modeSummary = text(context, R.string.gboard_patches_rounded_keyboard_mode_summary);
        modeDialogTitle = text(context,
                R.string.gboard_patches_rounded_keyboard_mode_dialog_title);
        modeLabels = new String[] {
                text(context, R.string.gboard_patches_rounded_keyboard_mode_all),
                text(context, R.string.gboard_patches_rounded_keyboard_mode_top),
                text(context, R.string.gboard_patches_rounded_keyboard_mode_bottom)
        };
        modeValues = new String[] {"all", "top", "bottom"};
        topRadiusTitle = text(context,
                R.string.gboard_patches_rounded_keyboard_top_radius_title);
        topRadiusSummary = text(context,
                R.string.gboard_patches_rounded_keyboard_top_radius_summary);
        topRadiusDialogTitle = text(context,
                R.string.gboard_patches_rounded_keyboard_top_radius_dialog_title);
        bottomRadiusTitle = text(context,
                R.string.gboard_patches_rounded_keyboard_bottom_radius_title);
        bottomRadiusSummary = text(context,
                R.string.gboard_patches_rounded_keyboard_bottom_radius_summary);
        bottomRadiusDialogTitle = text(context,
                R.string.gboard_patches_rounded_keyboard_bottom_radius_dialog_title);
        radiusDialogHint = text(context,
                R.string.gboard_patches_rounded_keyboard_radius_dialog_hint);
        radiusDialogError = text(context,
                R.string.gboard_patches_rounded_keyboard_radius_dialog_error);
        advancedSectionTitle = text(context,
                R.string.gboard_patches_rounded_keyboard_advanced_section);
        resetTitle = text(context, R.string.gboard_patches_rounded_keyboard_reset_title);
        resetSummary = text(context, R.string.gboard_patches_rounded_keyboard_reset_summary);
        resetConfirmTitle = text(context,
                R.string.gboard_patches_rounded_keyboard_reset_confirm_title);
        resetConfirmMessage = text(context,
                R.string.gboard_patches_rounded_keyboard_reset_confirm_message);
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
        return GboardPatchesFeatureAvailability.hasFeature(
                context,
                GboardPatchesFeatureAvailability.FEATURE_ROUNDED_KEYBOARD_PANEL);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            if (host == null || host.getContext() == null) {
                return buildErrorScreen();
            }
            SharedPreferences preferences =
                    GboardPatchesSettings.preferences(host.getContext());
            GboardRoundedKeyboardSettings.ensureDefaults(preferences);
            GboardRoundedKeyboardConfig snapshot =
                    GboardRoundedKeyboardSettings.readSnapshot(preferences);
            return buildScreenForState(
                    snapshot,
                    enabled -> completeWrite(host,
                            GboardRoundedKeyboardSettings.writeEnabled(preferences, enabled)),
                    () -> showModeDialog(host, preferences, snapshot.getMode()),
                    () -> showRadiusDialog(host, preferences, true,
                            snapshot.getTopRadiusDp()),
                    () -> showRadiusDialog(host, preferences, false,
                            snapshot.getBottomRadiusDp()),
                    () -> completeWrite(host,
                            GboardRoundedKeyboardSettings.restoreDefaultRadii(preferences)));
        } catch (Throwable failure) {
            Log.w(TAG, "Failed to render rounded keyboard settings", failure);
            return buildErrorScreen();
        }
    }

    GboardPatchesSettingsContract.Screen buildScreenForState(
            GboardRoundedKeyboardConfig snapshot,
            GboardPatchesSettingsContract.ToggleAction toggleAction,
            Runnable modeAction,
            Runnable topRadiusAction,
            Runnable bottomRadiusAction,
            Runnable resetAction) {
        boolean enabled = snapshot.isEnabled();
        GboardRoundedKeyboardConfig.Mode mode = snapshot.getMode();
        List<GboardPatchesSettingsContract.Row> rows = new ArrayList<>();
        rows.add(new GboardPatchesSettingsContract.ToggleRow(
                enabledTitle, enabledSummary, true, enabled, toggleAction)
                .setRestartRequired(GboardRoundedKeyboardSettings.PREF_KEY_ENABLED));
        rows.add(new GboardPatchesSettingsContract.SelectorRow(
                modeTitle, modeSummary, modeLabels[mode.ordinal()], enabled, modeAction)
                .setRestartRequired(GboardRoundedKeyboardSettings.PREF_KEY_MODE));
        rows.add(new GboardPatchesSettingsContract.SelectorRow(
                topRadiusTitle,
                topRadiusSummary,
                radiusLabel(snapshot.getTopRadiusDp()),
                enabled && mode != GboardRoundedKeyboardConfig.Mode.BOTTOM,
                topRadiusAction)
                .setRestartRequired(GboardRoundedKeyboardSettings.PREF_KEY_TOP_RADIUS_DP));
        rows.add(new GboardPatchesSettingsContract.SelectorRow(
                bottomRadiusTitle,
                bottomRadiusSummary,
                radiusLabel(snapshot.getBottomRadiusDp()),
                enabled && mode != GboardRoundedKeyboardConfig.Mode.TOP,
                bottomRadiusAction)
                .setRestartRequired(GboardRoundedKeyboardSettings.PREF_KEY_BOTTOM_RADIUS_DP));
        List<GboardPatchesSettingsContract.Row> advancedRows =
                Collections.singletonList(new GboardPatchesSettingsContract.DangerRow(
                        resetTitle,
                        resetSummary,
                        true,
                        resetAction,
                        resetConfirmTitle,
                        resetConfirmMessage));
        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                headerBadge,
                entryTitle,
                "",
                Collections.emptyList(),
                Arrays.asList(
                        new GboardPatchesSettingsContract.Section(sectionTitle, rows),
                        new GboardPatchesSettingsContract.Section(
                                advancedSectionTitle,
                                null,
                                GboardPatchesSettingsContract.SectionStyle.ADVANCED,
                                advancedRows)),
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT);
    }

    private void showModeDialog(GboardPatchesSettingsContract.FeatureHost host,
            SharedPreferences preferences, GboardRoundedKeyboardConfig.Mode currentMode) {
        GboardPatchesSettingsContract.showChoiceDialog(
                host,
                modeDialogTitle,
                modeLabels,
                modeValues,
                currentMode.storedValue(),
                UNUSED_VALUE,
                () -> { },
                value -> {
                    GboardRoundedKeyboardConfig.Mode requested =
                            GboardRoundedKeyboardConfig.Mode.fromStoredValue(value);
                    if (requested != null) {
                        completeWrite(host,
                                GboardRoundedKeyboardSettings.writeMode(
                                        preferences, requested));
                    }
                });
    }

    private void showRadiusDialog(GboardPatchesSettingsContract.FeatureHost host,
            SharedPreferences preferences, boolean top, int currentRadiusDp) {
        GboardPatchesSettingsContract.showTextInputDialog(
                host,
                top ? topRadiusDialogTitle : bottomRadiusDialogTitle,
                radiusDialogHint,
                Integer.toString(currentRadiusDp),
                value -> {
                    int radiusDp = parseRadiusDp(value);
                    completeWrite(host, top
                            ? GboardRoundedKeyboardSettings.writeTopRadiusDp(
                                    preferences, radiusDp)
                            : GboardRoundedKeyboardSettings.writeBottomRadiusDp(
                                    preferences, radiusDp));
                });
    }

    int parseRadiusDp(String value) {
        final int parsed;
        try {
            parsed = Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(radiusDialogError);
        }
        if (parsed < GboardRoundedKeyboardConfig.MIN_RADIUS_DP
                || parsed > GboardRoundedKeyboardConfig.MAX_RADIUS_DP) {
            throw new IllegalArgumentException(radiusDialogError);
        }
        return parsed;
    }

    private String radiusLabel(int radiusDp) {
        if (stringContext == null) {
            return radiusDp + " dp";
        }
        return GboardSettingsText.format(
                stringContext,
                R.string.gboard_patches_rounded_keyboard_radius_value,
                radiusDp);
    }

    private static void completeWrite(
            GboardPatchesSettingsContract.FeatureHost host, boolean succeeded) {
        if (!succeeded) {
            Log.w(TAG, "Failed to persist rounded keyboard settings");
            return;
        }
        GboardPatchesSettingsContract.refresh(host);
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

    private static String text(Context context, int resourceId) {
        return GboardSettingsText.get(context, resourceId);
    }
}
