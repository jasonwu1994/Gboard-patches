package dev.jason.gboardpatches.extension.spacebarlogo;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.graphics.drawable.Drawable;

import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardSpacebarLogoSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";
    private static final String[] ICON_VALUES = {
            GboardSpacebarLogoSettings.ICON_GOOGLE_G,
            GboardSpacebarLogoSettings.ICON_GOOGLE_G_COLOR,
            GboardSpacebarLogoSettings.ICON_HEART,
            GboardSpacebarLogoSettings.ICON_EMOJI,
            GboardSpacebarLogoSettings.ICON_CUSTOM_IMAGE
    };
    private static final int[] ICON_DRAWABLE_IDS =
            GboardSpacebarLogoAdapter.iconDrawableResourceIds();
    private static final String[] COLOR_VALUES = {
            GboardSpacebarLogoSettings.COLOR_VALUE_AUTO,
            GboardSpacebarLogoSettings.GOOGLE_BLUE,
            GboardSpacebarLogoSettings.GOOGLE_RED,
            GboardSpacebarLogoSettings.GOOGLE_YELLOW,
            GboardSpacebarLogoSettings.GOOGLE_GREEN,
            GboardSpacebarLogoSettings.BLACK,
            GboardSpacebarLogoSettings.WHITE,
            GboardSpacebarLogoSettings.COLOR_SELECTION_CUSTOM
    };

    private final Context context;
    private final String entryTitle;
    private final String entrySummary;
    private final String headerBadge;
    private final String sectionAppearance;
    private final String iconTitle;
    private final String iconSummary;
    private final String iconDialogTitle;
    private final String[] iconLabels;
    private final String colorTitle;
    private final String colorSummary;
    private final String colorDialogTitle;
    private final String colorOpacityLabel;
    private final String colorCodeLabel;
    private final String colorCodeHint;
    private final String colorCodeInvalid;
    private final String colorAutomaticSummary;
    private final String[] colorLabels;
    private final String errorTitle;
    private final String errorSummary;

    public GboardSpacebarLogoSettingsFeature(Context context) {
        this.context = context;
        entryTitle = textOrFallback(R.string.gboard_patches_spacebar_logo_title, "G logo");
        entrySummary = textOrFallback(R.string.gboard_patches_spacebar_logo_summary, "");
        headerBadge = textOrFallback(R.string.gboard_patches_header_badge, "GBOARD");
        sectionAppearance = textOrFallback(
                R.string.gboard_patches_spacebar_logo_section_appearance, "Appearance");
        iconTitle = textOrFallback(
                R.string.gboard_patches_spacebar_logo_icon_title, "Spacebar icon");
        iconSummary = textOrFallback(R.string.gboard_patches_spacebar_logo_icon_summary, "");
        iconDialogTitle = textOrFallback(
                R.string.gboard_patches_spacebar_logo_icon_dialog_title, "Spacebar icon");
        iconLabels = new String[] {
                textOrFallback(R.string.gboard_patches_spacebar_logo_icon_google_g, "Google G"),
                textOrFallback(R.string.gboard_patches_spacebar_logo_icon_google_color, "Google G (color)"),
                textOrFallback(R.string.gboard_patches_spacebar_logo_icon_heart, "Heart"),
                textOrFallback(R.string.gboard_patches_spacebar_logo_icon_emoji, "Emoji"),
                textOrFallback(R.string.gboard_patches_spacebar_logo_icon_image, "Custom image")
        };
        colorTitle = textOrFallback(R.string.gboard_patches_spacebar_logo_color_title, "Logo color");
        colorSummary = textOrFallback(R.string.gboard_patches_spacebar_logo_color_summary, "");
        colorDialogTitle = textOrFallback(
                R.string.gboard_patches_spacebar_logo_color_dialog_title, "Logo color");
        colorOpacityLabel = textOrFallback(
                R.string.gboard_patches_spacebar_logo_color_opacity, "Opacity");
        colorCodeLabel = textOrFallback(
                R.string.gboard_patches_spacebar_logo_color_code, "Color code");
        colorCodeHint = textOrFallback(
                R.string.gboard_patches_spacebar_logo_color_code_hint, "#RRGGBB");
        colorCodeInvalid = textOrFallback(
                R.string.gboard_patches_spacebar_logo_color_code_invalid, "Invalid color code");
        colorAutomaticSummary = textOrFallback(
                R.string.gboard_patches_spacebar_logo_color_automatic_summary, "");
        colorLabels = new String[] {
                textOrFallback(R.string.gboard_patches_spacebar_logo_color_automatic, "Automatic"),
                textOrFallback(R.string.gboard_patches_spacebar_logo_color_google_blue, "Blue"),
                textOrFallback(R.string.gboard_patches_spacebar_logo_color_google_red, "Red"),
                textOrFallback(R.string.gboard_patches_spacebar_logo_color_google_yellow, "Yellow"),
                textOrFallback(R.string.gboard_patches_spacebar_logo_color_google_green, "Green"),
                textOrFallback(R.string.gboard_patches_spacebar_logo_color_black, "Black"),
                textOrFallback(R.string.gboard_patches_spacebar_logo_color_white, "White"),
                textOrFallback(R.string.gboard_patches_spacebar_logo_color_custom, "Custom")
        };
        errorTitle = textOrFallback(
                R.string.gboard_patches_spacebar_logo_error_title, "Spacebar Logo unavailable");
        errorSummary = textOrFallback(
                R.string.gboard_patches_spacebar_logo_error_summary, "");
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
                context, GboardPatchesFeatureAvailability.FEATURE_SPACEBAR_LOGO);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            Context hostContext = host == null ? null : host.getContext();
            if (hostContext == null) return errorScreen();
            SharedPreferences preferences = GboardPatchesSettings.preferences(hostContext);
            GboardSpacebarLogoSettings.ensureDefaults(preferences);
            boolean enabled = GboardSpacebarLogoSettings.readEnabled(preferences);
            String color = GboardSpacebarLogoSettings.readColorHex(preferences);
            String selection = GboardSpacebarLogoSettings.readColorSelection(preferences);
            String custom = GboardSpacebarLogoSettings.readCustomColorHex(preferences);
            String icon = GboardSpacebarLogoSettings.readIcon(preferences);
            boolean hasImage = !GboardSpacebarLogoSettings.readImage(preferences).isEmpty();
            boolean imageSelected = GboardSpacebarLogoSettings.ICON_CUSTOM_IMAGE.equals(icon);
            boolean tintImage = GboardSpacebarLogoSettings.readImageTint(preferences);
            boolean colorEnabled = !GboardSpacebarLogoSettings.ICON_GOOGLE_G_COLOR.equals(icon)
                    && (!imageSelected || tintImage);
            List<GboardPatchesSettingsContract.Row> rows = new ArrayList<>();
            rows.add(new GboardPatchesSettingsContract.ToggleRow(
                    entryTitle, entrySummary, true, enabled,
                    value -> runSafely("update enabled state", () -> {
                        GboardSpacebarLogoSettings.writeEnabled(hostContext, value);
                        GboardPatchesSettingsContract.refresh(host);
                    })).setMasterToggle(true));
            rows.add(new GboardPatchesSettingsContract.SelectorRow(
                    iconTitle, iconSummary, displayIcon(icon), true,
                    () -> showIconDialog(host, hostContext, icon, preferences)));
            rows.add(new GboardPatchesSettingsContract.SelectorRow(
                    colorTitle, colorEnabled ? colorSummary : textOrFallback(
                            R.string.gboard_patches_spacebar_image_original_summary, "This image keeps its original colors."),
                    colorEnabled ? displayColor(color) : textOrFallback(
                            R.string.gboard_patches_spacebar_image_original, "Original colors"), colorEnabled,
                    () -> showColorDialog(host, hostContext, color, selection, custom, imageSelected)));
            rows.add(new GboardPatchesSettingsContract.ActionRow(
                    textOrFallback(hasImage ? R.string.gboard_patches_spacebar_image_replace
                            : R.string.gboard_patches_spacebar_image_import, "Import image"),
                    textOrFallback(R.string.gboard_patches_spacebar_image_import_summary, "PNG, WebP or JPEG, up to 8 MB."),
                    true, () -> runSafely("import spacebar image", () -> importImage(host, hostContext))));
            if (imageSelected && hasImage) {
                rows.add(new GboardPatchesSettingsContract.ToggleRow(
                        textOrFallback(R.string.gboard_patches_spacebar_image_tint, "Apply a single color"),
                        textOrFallback(R.string.gboard_patches_spacebar_image_tint_summary, ""), true, tintImage,
                        value -> runSafely("change image color mode", () -> {
                            GboardSpacebarLogoSettings.writeImageTint(hostContext, value);
                            GboardPatchesSettingsContract.refresh(host);
                        })));
            }
            if (hasImage) {
                rows.add(new GboardPatchesSettingsContract.ActionRow(
                        textOrFallback(R.string.gboard_patches_spacebar_image_remove, "Remove imported image"), "", true,
                        () -> runSafely("remove spacebar image", () -> {
                            GboardSpacebarLogoSettings.removeImage(hostContext);
                            GboardPatchesSettingsContract.refresh(host);
                        })));
            }
            int sizePercent = GboardSpacebarLogoSettings.readSizePercent(preferences);
            rows.add(new GboardPatchesSettingsContract.SelectorRow(
                    textOrFallback(R.string.gboard_patches_spacebar_logo_size_title, "Icon size"),
                    textOrFallback(R.string.gboard_patches_spacebar_logo_size_summary, ""), sizePercent + "%", true,
                    () -> runSafely("choose spacebar image size", () -> showSizeDialog(host, hostContext, sizePercent))));
            return new GboardPatchesSettingsContract.Screen(
                    entryTitle, headerBadge, entryTitle, "", Collections.emptyList(),
                    Collections.singletonList(new GboardPatchesSettingsContract.Section(sectionAppearance, rows)),
                    GboardPatchesSettingsContract.RefreshPolicy.none(), GboardPatchesSettingsContract.PanelStyle.FLAT);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to render spacebar logo settings", throwable);
            return errorScreen();
        }
    }

    private void showIconDialog(GboardPatchesSettingsContract.FeatureHost host,
            Context hostContext, String current, SharedPreferences preferences) {
        runSafely("show icon dialog", () -> {
            Drawable[] previews = new Drawable[ICON_VALUES.length];
            int[] stockPositions = {0, 2, 3};
            for (int i = 0; i < ICON_DRAWABLE_IDS.length; i++) {
                try { previews[stockPositions[i]] = hostContext.getDrawable(ICON_DRAWABLE_IDS[i]); }
                catch (Throwable ignored) { }
            }
            previews[1] = GboardSpacebarLogoImages.googleDrawable(hostContext.getResources());
            previews[4] = GboardSpacebarLogoImages.customDrawable(hostContext.getResources(),
                    GboardSpacebarLogoSettings.readImage(preferences));
            GboardPatchesSettingsContract.showManagedDialog(host,
                    GboardSpacebarLogoDialogs.iconDialog(hostContext, iconDialogTitle, iconLabels, ICON_VALUES,
                            previews, current, value -> runSafely("update icon", () -> {
                                if (GboardSpacebarLogoSettings.ICON_CUSTOM_IMAGE.equals(value)
                                        && GboardSpacebarLogoSettings.readImage(preferences).isEmpty()) {
                                    importImage(host, hostContext);
                                } else {
                                    GboardSpacebarLogoSettings.writeIcon(hostContext, value);
                                    GboardPatchesSettingsContract.refresh(host);
                                }
                            })));
        });
    }

    private void importImage(GboardPatchesSettingsContract.FeatureHost host, Context hostContext) {
        GboardPatchesSettingsContract.openBinaryDocument(host,
                new String[] {"image/png", "image/webp", "image/jpeg"}, GboardSpacebarLogoImages.MAX_IMPORT_BYTES,
                document -> {
                    try {
                        String image = GboardSpacebarLogoImages.importImage(document.getData());
                        if (!GboardSpacebarLogoSettings.writeImage(hostContext, image)) {
                            throw new java.io.IOException("Image setting could not be saved");
                        }
                        GboardPatchesSettingsContract.showMessage(host,
                                textOrFallback(R.string.gboard_patches_spacebar_image_imported, "Image imported."));
                        GboardPatchesSettingsContract.refresh(host);
                    } catch (Throwable failure) {
                        Log.w(TAG, "Failed to import spacebar image", failure);
                        GboardPatchesSettingsContract.showMessage(host,
                                textOrFallback(R.string.gboard_patches_spacebar_image_failed, "Could not import this image."));
                    }
                });
    }

    private void showSizeDialog(GboardPatchesSettingsContract.FeatureHost host, Context hostContext, int currentSize) {
        String title = textOrFallback(R.string.gboard_patches_spacebar_logo_size_title, "Icon size");
        String[] values = {"30", "60", "80", "100", "CUSTOM"};
        String[] labels = {"30%", textOrFallback(R.string.gboard_patches_spacebar_logo_size_default, "60% (default)"),
                "80%", textOrFallback(R.string.gboard_patches_spacebar_logo_size_max, "100% (largest)"),
                colorLabels[colorLabels.length - 1]};
        java.util.function.IntConsumer save = value -> runSafely("save image size", () -> {
            if (!GboardSpacebarLogoContract.isValidSizePercent(value)) {
                GboardPatchesSettingsContract.showMessage(host,
                        textOrFallback(R.string.gboard_patches_spacebar_logo_size_invalid, "Enter a size between 10 and 100%."));
            } else if (GboardSpacebarLogoSettings.writeSizePercent(hostContext, value)) {
                GboardPatchesSettingsContract.refresh(host);
            }
        });
        GboardPatchesSettingsContract.showChoiceDialog(host, title, labels, values,
                Integer.toString(currentSize), "CUSTOM", () -> GboardPatchesSettingsContract.showPositiveIntegerDialog(
                        host, title, textOrFallback(R.string.gboard_patches_spacebar_logo_size_hint, "10–100% (default: 60)"),
                        currentSize, save::accept), value -> save.accept(Integer.parseInt(value)));
    }

    private void showColorDialog(GboardPatchesSettingsContract.FeatureHost host,
            Context hostContext, String currentColor, String currentSelection,
            String customColor, boolean imageSelected) {
        String[] summaries = {
                colorAutomaticSummary,
                COLOR_VALUES[1], COLOR_VALUES[2], COLOR_VALUES[3], COLOR_VALUES[4],
                COLOR_VALUES[5], COLOR_VALUES[6], customColor == null ? "" : customColor
        };
        runSafely("show color dialog", () ->
                GboardPatchesSettingsContract.showManagedDialog(host,
                    GboardSpacebarLogoDialogs.paletteDialog(
                        hostContext, colorDialogTitle,
                        Arrays.copyOfRange(colorLabels, imageSelected ? 1 : 0, colorLabels.length),
                        Arrays.copyOfRange(summaries, imageSelected ? 1 : 0, summaries.length),
                        Arrays.copyOfRange(COLOR_VALUES, imageSelected ? 1 : 0, COLOR_VALUES.length),
                        currentSelection, GboardSpacebarLogoSettings.COLOR_SELECTION_CUSTOM,
                        () -> showCustomColorDialog(
                                host, hostContext, currentColor, customColor),
                        value -> runSafely("update color", () -> {
                            GboardSpacebarLogoSettings.writeColorHex(hostContext, value);
                            GboardPatchesSettingsContract.refresh(host);
                        }))));
    }

    private void showCustomColorDialog(GboardPatchesSettingsContract.FeatureHost host,
            Context hostContext, String currentColor, String customColor) {
        String initial = customColor != null ? customColor
                : GboardSpacebarLogoSettings.isAutomaticColor(currentColor)
                        ? GboardSpacebarLogoSettings.DEFAULT_COLOR_HEX : currentColor;
        runSafely("show custom color dialog", () ->
                GboardPatchesSettingsContract.showManagedDialog(host,
                    GboardSpacebarLogoDialogs.colorPickerDialog(
                        hostContext, colorDialogTitle, colorOpacityLabel, colorCodeLabel,
                        colorCodeHint, colorCodeInvalid, initial,
                        value -> runSafely("update custom color", () -> {
                            GboardSpacebarLogoSettings.writeCustomColor(hostContext, value);
                            GboardPatchesSettingsContract.refresh(host);
                        }))));
    }

    private String displayIcon(String icon) {
        for (int index = 0; index < ICON_VALUES.length; index++) {
            if (ICON_VALUES[index].equals(icon)) return iconLabels[index];
        }
        return iconLabels[0];
    }

    private String displayColor(String color) {
        return GboardSpacebarLogoSettings.isAutomaticColor(color) ? colorLabels[0] : color;
    }

    private GboardPatchesSettingsContract.Screen errorScreen() {
        return new GboardPatchesSettingsContract.Screen(
                entryTitle, headerBadge, entryTitle, "",
                Collections.singletonList(new GboardPatchesSettingsContract.StatusBlock(
                        errorTitle, errorSummary,
                        GboardPatchesSettingsContract.StatusTone.WARNING)),
                Collections.emptyList());
    }

    private String textOrFallback(int resourceId, String fallback) {
        try {
            return GboardSettingsText.get(context, resourceId);
        } catch (Throwable throwable) {
            try {
                Log.w(TAG, "Failed to resolve spacebar logo settings text", throwable);
            } catch (Throwable ignored) {
                // Constructor fallback must remain available even if logging fails.
            }
            return fallback;
        }
    }

    private static void runSafely(String operation, Runnable action) {
        if (action == null) return;
        try {
            action.run();
        } catch (Throwable throwable) {
            try {
                Log.w(TAG, "Failed to " + operation, throwable);
            } catch (Throwable ignored) {
                // Feature callbacks must not propagate into the settings host.
            }
        }
    }
}
