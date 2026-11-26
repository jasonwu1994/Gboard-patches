package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardEditingAccessPointsSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String PREVIEW_IMAGE_ENABLED =
            "settings-previews/editingaccesspoints/01_toolbar_editing_buttons.png";
    private final Context context;

    public GboardEditingAccessPointsSettingsFeature(Context context) { this.context = context; }

    @Override public String getEntryTitle() { return text(R.string.gboard_edit_ap_title); }
    @Override public String getEntrySummary() { return text(R.string.gboard_edit_ap_summary); }
    @Override public boolean isAvailable(Context host) {
        return GboardPatchesFeatureAvailability.hasFeature(host,
                GboardPatchesFeatureAvailability.FEATURE_EDITING_ACCESS_POINTS);
    }

    @Override public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            return buildSettingsScreen(host);
        } catch (Throwable ignored) {
            return new GboardPatchesSettingsContract.Screen(
                    getEntryTitle(), text(R.string.gboard_patches_header_badge), getEntryTitle(),
                    "", List.of(new GboardPatchesSettingsContract.StatusBlock(
                            getEntryTitle(), text(R.string.gboard_edit_ap_error),
                            GboardPatchesSettingsContract.StatusTone.WARNING)), List.of());
        }
    }

    private GboardPatchesSettingsContract.Screen buildSettingsScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        Context owner = host.getContext();
        SharedPreferences preferences = GboardPatchesSettings.preferences(owner);
        boolean enabled = GboardEditingAccessPointsSettings.read(preferences,
                GboardEditingAccessPointsSettings.ENABLED);
        List<GboardPatchesSettingsContract.Row> rows = new ArrayList<>();
        rows.add(new GboardPatchesSettingsContract.ToggleRow(
                getEntryTitle(), text(R.string.gboard_edit_ap_restart), true, enabled,
                value -> write(owner, GboardEditingAccessPointsSettings.ENABLED, value),
                new GboardPatchesSettingsContract.PreviewSpec(
                        getEntryTitle(),
                        "",
                        new GboardPatchesSettingsContract.PreviewImage(
                                PREVIEW_IMAGE_ENABLED, "")))
                .setRestartRequired(GboardEditingAccessPointsSettings.ENABLED)
                .setMasterToggle(true));
        for (EditorAction action : EditorAction.values()) {
            String key = GboardEditingAccessPointsSettings.key(action);
            rows.add(new GboardPatchesSettingsContract.ToggleRow(
                    text(label(action)), "", enabled,
                    GboardEditingAccessPointsSettings.read(preferences, key),
                    value -> write(owner, key, value))
                    .setRestartRequired(key));
        }
        rows.add(new GboardPatchesSettingsContract.SwitchRow(
                text(R.string.gboard_edit_ap_select_all_unselected_title),
                text(R.string.gboard_edit_ap_select_all_unselected_summary), enabled,
                GboardEditingAccessPointsSettings.read(preferences,
                        GboardEditingAccessPointsSettings.SELECT_ALL_WHEN_UNSELECTED),
                value -> write(owner,
                        GboardEditingAccessPointsSettings.SELECT_ALL_WHEN_UNSELECTED, value)));
        return new GboardPatchesSettingsContract.Screen(
                getEntryTitle(), text(R.string.gboard_patches_header_badge), getEntryTitle(),
                getEntrySummary(), List.of(),
                List.of(new GboardPatchesSettingsContract.Section(
                        text(R.string.gboard_edit_ap_section_feature), rows)),
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT);
    }

    @SuppressLint("ApplySharedPref")
    private static void write(Context context, String key, boolean value) {
        if (!GboardPatchesSettings.preferences(context).edit().putBoolean(key, value).commit()) {
            throw new IllegalStateException("Unable to save editing buttons");
        }
    }

    private int label(EditorAction action) {
        return switch (action) {
            case SELECT_ALL -> R.string.gboard_edit_ap_select_all;
            case COPY -> R.string.gboard_edit_ap_copy;
            case CUT -> R.string.gboard_edit_ap_cut;
            case PASTE -> R.string.gboard_edit_ap_paste;
            case FORWARD_DELETE -> R.string.gboard_edit_ap_forward_delete;
            case PASTE_PLAIN_TEXT -> R.string.gboard_edit_ap_paste_plain_text;
            case HOME -> R.string.gboard_edit_ap_home;
            case END -> R.string.gboard_edit_ap_end;
            case TAB -> R.string.gboard_edit_ap_tab;
            case ARROW_LEFT -> R.string.gboard_edit_ap_arrow_left;
            case ARROW_RIGHT -> R.string.gboard_edit_ap_arrow_right;
            case ARROW_UP -> R.string.gboard_edit_ap_arrow_up;
            case ARROW_DOWN -> R.string.gboard_edit_ap_arrow_down;
        };
    }

    private String text(int id) { return GboardSettingsText.get(context, id); }
}
