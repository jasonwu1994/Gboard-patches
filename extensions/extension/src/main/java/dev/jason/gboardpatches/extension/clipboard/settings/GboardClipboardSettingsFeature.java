package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.clipboardcontentlimit.GboardClipboardContentLimitSettingsRow;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesIcons;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardClipboardSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardClipboard";
    private static final String EMPTY_PREVIEW_MESSAGE = "";
    private static final String FALLBACK_HEADER_BADGE = "Gboard";
    private static final String FALLBACK_ENTRY_TITLE = "Clipboard";
    private static final String FALLBACK_ENTRY_SUMMARY =
            "Adjusts clipboard retention, item limits, preview lines, metadata labels, order"
                    + " index, columns, and the phone-hosted Web Clipboard.";
    private static final String FALLBACK_ERROR_TITLE = "Clipboard settings unavailable";
    private static final String FALLBACK_ERROR_SUMMARY =
            "The clipboard settings screen failed to load. Reopen Gboard settings and try"
                    + " again.";
    private static final String FALLBACK_SECTION_GENERAL = "General";
    private static final String FALLBACK_SECTION_METADATA = "Metadata";
    private static final String FALLBACK_SECTION_LAYOUT = "Layout";
    private static final String FALLBACK_SECTION_RETENTION = "Retention";
    private static final String FALLBACK_SECTION_EXTENSIONS = "Extensions";

    private final GboardClipboardMetadataSettingsSection metadataSection =
            new GboardClipboardMetadataSettingsSection();
    private final GboardClipboardLayoutSettingsSection layoutSection =
            new GboardClipboardLayoutSettingsSection();
    private final GboardClipboardRetentionSettingsSection retentionSection =
            new GboardClipboardRetentionSettingsSection();
    private final GboardClipboardContentLimitSettingsRow contentLimitSettingsRow =
            new GboardClipboardContentLimitSettingsRow();
    private final GboardWebClipboardSettingsFeature webClipboardFeature =
            new GboardWebClipboardSettingsFeature();

    private String headerBadge = FALLBACK_HEADER_BADGE;
    private String entryTitle = FALLBACK_ENTRY_TITLE;
    private String entrySummary = FALLBACK_ENTRY_SUMMARY;
    private String errorTitle = FALLBACK_ERROR_TITLE;
    private String errorSummary = FALLBACK_ERROR_SUMMARY;
    private String sectionGeneral = FALLBACK_SECTION_GENERAL;
    private String sectionMetadata = FALLBACK_SECTION_METADATA;
    private String sectionLayout = FALLBACK_SECTION_LAYOUT;
    private String sectionRetention = FALLBACK_SECTION_RETENTION;
    private String sectionExtensions = FALLBACK_SECTION_EXTENSIONS;

    @Override
    public String getEntryTitle() {
        return entryTitle;
    }

    @Override
    public String getEntrySummary() {
        return entrySummary;
    }

    @Override
    public int getEntryIcon() {
        return GboardPatchesIcons.CLIPBOARD;
    }

    @Override
    public String getEntryTone() {
        return "amber";
    }

    public GboardPatchesSettingsContract.Feature getWebClipboardFeature() {
        return webClipboardFeature;
    }

    @Override
    public List<GboardPatchesSettingsContract.Feature> getNavigationChildren() {
        return Collections.singletonList(webClipboardFeature);
    }

    @Override
    public boolean isAvailable(Context context) {
        initializeText(context);
        return GboardPatchesFeatureAvailability.hasAnyFeature(
                context,
                GboardPatchesFeatureAvailability.FEATURE_CLIPBOARD_ENHANCEMENTS,
                GboardPatchesFeatureAvailability.FEATURE_CLIPBOARD_CONTENT_LIMIT,
                GboardPatchesFeatureAvailability.FEATURE_WEB_CLIPBOARD);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            Context context = host.getContext();
            initializeText(context);
            SharedPreferences preferences = GboardClipboardSettings.preferences(context);
            GboardClipboardSettings.ensureDefaults(preferences);

            boolean canQueryAvailability = canQueryFeatureAvailability(context);
            boolean hasClipboardEnhancements = canQueryAvailability
                    ? GboardPatchesFeatureAvailability.hasFeature(
                            context,
                            GboardPatchesFeatureAvailability.FEATURE_CLIPBOARD_ENHANCEMENTS)
                    : true;
            boolean hasClipboardContentLimit = canQueryAvailability
                    ? GboardPatchesFeatureAvailability.hasFeature(
                            context,
                            GboardPatchesFeatureAvailability.FEATURE_CLIPBOARD_CONTENT_LIMIT)
                    : true;
            boolean hasWebClipboard = canQueryAvailability
                    ? webClipboardFeature.isAvailable(context)
                    : true;
            List<GboardPatchesSettingsContract.Section> sections =
                    new ArrayList<GboardPatchesSettingsContract.Section>();

            if (hasClipboardEnhancements) {
                boolean clipboardEnabled = GboardClipboardSettings.readClipboardEnabled(
                        preferences);
                List<GboardPatchesSettingsContract.Row> generalRows =
                        new ArrayList<GboardPatchesSettingsContract.Row>();
                generalRows.add(new GboardPatchesSettingsContract.ToggleRow(
                        entryTitle,
                        entrySummary,
                        true,
                        clipboardEnabled,
                        value -> preferences.edit()
                                .putBoolean(
                                        GboardClipboardSettings.PREF_KEY_CLIPBOARD_ENABLED,
                                        value)
                                .apply()));
                generalRows.add(new GboardPatchesSettingsContract.ToggleRow(
                        GboardSettingsText.get(
                                context,
                                R.string.gboard_patches_clipboard_show_sensitive_content_title),
                        GboardSettingsText.get(
                                context,
                                R.string.gboard_patches_clipboard_show_sensitive_content_summary),
                        clipboardEnabled,
                        GboardClipboardSettings.readClipboardShowSensitiveContent(preferences),
                        value -> preferences.edit()
                                .putBoolean(
                                        GboardClipboardSettings
                                                .PREF_KEY_CLIPBOARD_SHOW_SENSITIVE_CONTENT,
                                        value)
                                .apply(),
                        buildSensitiveContentPreview(context)));
                generalRows.add(new GboardPatchesSettingsContract.ToggleRow(
                        GboardSettingsText.get(context,
                                R.string.gboard_patches_clipboard_card_preview_limit_title),
                        GboardSettingsText.get(context,
                                R.string.gboard_patches_clipboard_card_preview_limit_summary),
                        clipboardEnabled,
                        GboardClipboardSettings.readClipboardCardPreviewLimitEnabled(
                                preferences),
                        value -> preferences.edit()
                                .putBoolean(
                                        GboardClipboardSettings
                                                .PREF_KEY_CLIPBOARD_CARD_PREVIEW_LIMIT_ENABLED,
                                        value)
                                .apply()));

                List<GboardPatchesSettingsContract.Row> metadataRows =
                        new ArrayList<GboardPatchesSettingsContract.Row>();
                metadataSection.appendRows(metadataRows, host, preferences, clipboardEnabled);

                List<GboardPatchesSettingsContract.Row> layoutRows =
                        new ArrayList<GboardPatchesSettingsContract.Row>();
                layoutSection.appendRows(layoutRows, host, preferences, clipboardEnabled);

                List<GboardPatchesSettingsContract.Row> retentionRows =
                        new ArrayList<GboardPatchesSettingsContract.Row>();
                retentionSection.appendRows(retentionRows, host, preferences, clipboardEnabled);

                sections.add(new GboardPatchesSettingsContract.Section(sectionGeneral, generalRows));
                sections.add(new GboardPatchesSettingsContract.Section(sectionMetadata, metadataRows));
                sections.add(new GboardPatchesSettingsContract.Section(sectionLayout, layoutRows));
                if (hasClipboardContentLimit) {
                    contentLimitSettingsRow.appendRow(retentionRows, host);
                }
                sections.add(new GboardPatchesSettingsContract.Section(
                        sectionRetention, retentionRows));
            } else if (hasClipboardContentLimit) {
                List<GboardPatchesSettingsContract.Row> retentionRows =
                        new ArrayList<GboardPatchesSettingsContract.Row>();
                contentLimitSettingsRow.appendRow(retentionRows, host);
                sections.add(new GboardPatchesSettingsContract.Section(
                        sectionRetention, retentionRows));
            }

            if (hasWebClipboard) {
                List<GboardPatchesSettingsContract.Row> extensionRows =
                        new ArrayList<GboardPatchesSettingsContract.Row>();
                extensionRows.add(new GboardPatchesSettingsContract.NavigationRow(
                        webClipboardFeature.getEntryTitle(),
                        webClipboardFeature.getHostedEntrySummary(context, preferences),
                        true,
                        () -> GboardPatchesSettingsContract.openFeature(host, webClipboardFeature)));
                sections.add(new GboardPatchesSettingsContract.Section(
                        sectionExtensions,
                        extensionRows));
            }

            if (sections.isEmpty()) {
                return buildErrorScreen();
            }
            return new GboardPatchesSettingsContract.Screen(
                    entryTitle,
                    headerBadge,
                    entryTitle,
                    entrySummary,
                    Collections.emptyList(),
                    sections,
                    GboardPatchesSettingsContract.RefreshPolicy.none(),
                    GboardPatchesSettingsContract.PanelStyle.FLAT);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to render clipboard settings screen", throwable);
            return buildErrorScreen();
        }
    }

    private GboardPatchesSettingsContract.Screen buildErrorScreen() {
        List<GboardPatchesSettingsContract.StatusBlock> statusBlocks =
                new ArrayList<GboardPatchesSettingsContract.StatusBlock>();
        statusBlocks.add(new GboardPatchesSettingsContract.StatusBlock(
                errorTitle,
                errorSummary,
                GboardPatchesSettingsContract.StatusTone.WARNING));
        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                headerBadge,
                entryTitle,
                entrySummary,
                statusBlocks,
                Collections.emptyList());
    }

    private GboardPatchesSettingsContract.PreviewSpec buildSensitiveContentPreview(
            Context context) {
        String title = GboardSettingsText.get(context,
                R.string.gboard_patches_clipboard_show_sensitive_content_title);
        String enabledCaption = GboardSettingsText.get(context,
                R.string.gboard_patches_preview_sensitive_content_enabled_caption);
        String disabledCaption = GboardSettingsText.get(context,
                R.string.gboard_patches_preview_sensitive_content_disabled_caption);
        return new GboardPatchesSettingsContract.PreviewSpec(
                title,
                EMPTY_PREVIEW_MESSAGE,
                GboardPatchesSettingsContract.PreviewLayout.STACKED,
                new GboardPatchesSettingsContract.PreviewImage(
                        "settings-previews/clipboard/show_sensitive_content_enabled.png",
                        enabledCaption),
                new GboardPatchesSettingsContract.PreviewImage(
                        "settings-previews/clipboard/show_sensitive_content_disabled.png",
                        disabledCaption));
    }

    private void initializeText(Context context) {
        headerBadge = GboardSettingsText.get(
                context,
                R.string.gboard_patches_header_badge);
        entryTitle = GboardSettingsText.get(
                context,
                R.string.gboard_patches_clipboard_title);
        entrySummary = GboardSettingsText.get(
                context,
                R.string.gboard_patches_clipboard_summary);
        errorTitle = GboardSettingsText.get(
                context,
                R.string.gboard_patches_clipboard_error_title);
        errorSummary = GboardSettingsText.get(
                context,
                R.string.gboard_patches_clipboard_error_summary);
        sectionGeneral = GboardSettingsText.get(
                context,
                R.string.gboard_patches_clipboard_section_general);
        sectionMetadata = GboardSettingsText.get(
                context,
                R.string.gboard_patches_clipboard_section_metadata);
        sectionLayout = GboardSettingsText.get(
                context,
                R.string.gboard_patches_clipboard_section_layout);
        sectionRetention = GboardSettingsText.get(
                context,
                R.string.gboard_patches_clipboard_section_retention);
        sectionExtensions = GboardSettingsText.get(
                context,
                R.string.gboard_patches_clipboard_section_extensions);
    }

    private boolean canQueryFeatureAvailability(Context context) {
        if (context == null) {
            return false;
        }
        Context applicationContext = context.getApplicationContext();
        Context lookupContext = applicationContext != null ? applicationContext : context;
        try {
            return lookupContext.getPackageManager() != null
                    && lookupContext.getPackageName() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
