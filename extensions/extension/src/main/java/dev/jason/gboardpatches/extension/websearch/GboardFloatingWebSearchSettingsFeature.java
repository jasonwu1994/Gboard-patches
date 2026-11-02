package dev.jason.gboardpatches.extension.websearch;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardFloatingWebSearchSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";
    private static final String PREVIEW_IMAGE_BROWSER =
            "settings-previews/websearch/01_floating_web_search_browser.png";
    private static final String PREVIEW_IMAGE_ACCESS_POINT =
            "settings-previews/websearch/02_floating_web_search_access_point.png";

    private final Context textContext;

    public GboardFloatingWebSearchSettingsFeature(Context context) {
        textContext = context;
    }

    @Override
    public String getEntryTitle() {
        return text(R.string.gboard_patches_floating_web_search_title);
    }

    @Override
    public String getEntrySummary() {
        return text(R.string.gboard_patches_floating_web_search_summary);
    }

    @Override
    public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(
                context, GboardPatchesFeatureAvailability.FEATURE_FLOATING_WEB_SEARCH);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            if (host == null || host.getContext() == null) {
                return errorScreen();
            }
            SharedPreferences preferences =
                    GboardFloatingWebSearchSettingsStore.preferences(host.getContext());
            GboardFloatingWebSearchSettings.ensureDefaults(preferences);
            GboardFloatingWebSearchSettingsSnapshot settings =
                    GboardFloatingWebSearchSettings.readSnapshotOrNull(preferences);
            return settings == null ? errorScreen() : buildScreen(host, preferences, settings);
        } catch (Throwable failure) {
            logFailure("Failed to render Floating Web Search settings", failure);
            return errorScreen();
        }
    }

    private GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host, SharedPreferences preferences,
            GboardFloatingWebSearchSettingsSnapshot settings) {
        Context context = host.getContext();
        boolean enabled = settings.isEnabled();

        List<GboardPatchesSettingsContract.Row> featureRows = new ArrayList<>();
        featureRows.add(new GboardPatchesSettingsContract.ToggleRow(
                text(R.string.gboard_patches_floating_web_search_enable_title),
                text(R.string.gboard_patches_floating_web_search_enable_summary),
                true,
                enabled,
                value -> safeWrite(() ->
                        GboardFloatingWebSearchSettingsStore.writeEnabled(context, value)),
                buildEnabledPreview())
                .setRestartRequired(GboardFloatingWebSearchSettings.PREF_KEY_ENABLED));

        List<GboardPatchesSettingsContract.Row> launchRows = new ArrayList<>();
        launchRows.add(new GboardPatchesSettingsContract.SelectorRow(
                text(R.string.gboard_patches_floating_web_search_start_page_title),
                text(R.string.gboard_patches_floating_web_search_start_page_summary),
                startPageLabel(settings.getStartPageMode()),
                enabled,
                () -> showStartPageDialog(host, settings)));
        if (GboardFloatingWebSearchSettings.START_PAGE_CUSTOM.equals(
                settings.getStartPageMode())) {
            launchRows.add(new GboardPatchesSettingsContract.SelectorRow(
                    text(R.string.gboard_patches_floating_web_search_custom_url_title),
                    text(R.string.gboard_patches_floating_web_search_custom_url_summary),
                    settings.getCustomStartPageUrl(),
                    enabled,
                    () -> showCustomUrlDialog(host, settings.getCustomStartPageUrl())));
        }
        launchRows.add(new GboardPatchesSettingsContract.SelectorRow(
                text(R.string.gboard_patches_floating_web_search_height_title),
                text(R.string.gboard_patches_floating_web_search_height_summary),
                settings.getInitialHeightPercent() + "%",
                enabled,
                () -> showHeightDialog(host, settings.getInitialHeightPercent())));
        launchRows.add(new GboardPatchesSettingsContract.SelectorRow(
                text(R.string.gboard_patches_floating_web_search_browser_title),
                text(R.string.gboard_patches_floating_web_search_browser_summary),
                browserLabel(context, settings.getBrowserProvider()),
                enabled,
                () -> showBrowserDialog(host, settings.getBrowserProvider())));
        launchRows.add(new GboardPatchesSettingsContract.CommandRow(
                text(R.string.gboard_patches_floating_web_search_test_title),
                text(R.string.gboard_patches_floating_web_search_test_summary),
                enabled,
                () -> {
                    try {
                        GboardFloatingWebSearchSettingsSnapshot current =
                                GboardFloatingWebSearchSettings.readSnapshotOrNull(preferences);
                        if (current != null) {
                            GboardFloatingWebSearchRuntime.open(context, current);
                        }
                    } catch (Throwable failure) {
                        logFailure("Floating Web Search test launch failed", failure);
                        GboardFloatingWebSearchRuntime.showUnavailable(context);
                    }
                }));

        return new GboardPatchesSettingsContract.Screen(
                getEntryTitle(),
                text(R.string.gboard_patches_header_badge),
                getEntryTitle(),
                "",
                Collections.emptyList(),
                List.of(
                        new GboardPatchesSettingsContract.Section(
                                text(R.string.gboard_patches_floating_web_search_section_feature),
                                featureRows),
                        new GboardPatchesSettingsContract.Section(
                                text(R.string.gboard_patches_floating_web_search_section_launch),
                                launchRows)),
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT);
    }

    private void showStartPageDialog(GboardPatchesSettingsContract.FeatureHost host,
            GboardFloatingWebSearchSettingsSnapshot settings) {
        try {
            GboardPatchesSettingsContract.showChoiceDialog(
                    host,
                    text(R.string.gboard_patches_floating_web_search_start_page_title),
                    new String[]{"Google", "DuckDuckGo", "Bing",
                            text(R.string.gboard_patches_floating_web_search_start_page_custom)},
                    new String[]{GboardFloatingWebSearchSettings.START_PAGE_GOOGLE,
                            GboardFloatingWebSearchSettings.START_PAGE_DUCKDUCKGO,
                            GboardFloatingWebSearchSettings.START_PAGE_BING,
                            GboardFloatingWebSearchSettings.START_PAGE_CUSTOM},
                    settings.getStartPageMode(),
                    GboardFloatingWebSearchSettings.START_PAGE_CUSTOM,
                    () -> showCustomUrlDialog(host, settings.getCustomStartPageUrl()),
                    value -> safeWrite(() ->
                            GboardFloatingWebSearchSettingsStore.writeStartPageMode(
                                    host.getContext(), value)));
        } catch (Throwable failure) {
            logFailure("Unable to show start page choices", failure);
        }
    }

    private void showCustomUrlDialog(GboardPatchesSettingsContract.FeatureHost host,
            String currentUrl) {
        try {
            GboardPatchesSettingsContract.showTextInputDialog(
                    host,
                    text(R.string.gboard_patches_floating_web_search_custom_url_title),
                    "https://example.com/",
                    currentUrl,
                    value -> {
                        if (!GboardFloatingWebSearchSettings.isAllowedHttpsUrl(value)) {
                            throw new IllegalArgumentException(text(
                                    R.string.gboard_patches_floating_web_search_custom_url_error));
                        }
                        if (!GboardFloatingWebSearchSettingsStore.writeCustomStartPage(
                                host.getContext(), value)) {
                            throw new IllegalStateException("Unable to save custom start page");
                        }
                    });
        } catch (Throwable failure) {
            logFailure("Unable to show custom start page editor", failure);
        }
    }

    private void showHeightDialog(GboardPatchesSettingsContract.FeatureHost host,
            int currentHeight) {
        int[] allowed = GboardFloatingWebSearchSettings.ALLOWED_HEIGHT_PERCENTAGES;
        String[] labels = new String[allowed.length];
        String[] values = new String[allowed.length];
        for (int index = 0; index < allowed.length; index++) {
            labels[index] = allowed[index] + "%";
            values[index] = Integer.toString(allowed[index]);
        }
        try {
            GboardPatchesSettingsContract.showChoiceDialog(
                    host,
                    text(R.string.gboard_patches_floating_web_search_height_title),
                    labels,
                    values,
                    Integer.toString(currentHeight),
                    "",
                    () -> { },
                    value -> safeWrite(() ->
                            GboardFloatingWebSearchSettingsStore.writeInitialHeightPercent(
                                    host.getContext(), Integer.parseInt(value))));
        } catch (Throwable failure) {
            logFailure("Unable to show initial height choices", failure);
        }
    }

    private void showBrowserDialog(GboardPatchesSettingsContract.FeatureHost host,
            String currentProvider) {
        try {
            List<GboardCustomTabsProviderResolver.ProviderOption> providers =
                    GboardCustomTabsProviderResolver.installedProviders(host.getContext());
            List<String> labels = new ArrayList<>();
            List<String> values = new ArrayList<>();
            labels.add(text(
                    R.string.gboard_patches_floating_web_search_browser_system_default));
            values.add(GboardFloatingWebSearchSettings.BROWSER_SYSTEM_DEFAULT);
            for (GboardCustomTabsProviderResolver.ProviderOption provider : providers) {
                labels.add(provider.label);
                values.add(provider.packageName);
            }
            if (!GboardFloatingWebSearchSettings.BROWSER_SYSTEM_DEFAULT.equals(currentProvider)
                    && !values.contains(currentProvider)) {
                labels.add(currentProvider);
                values.add(currentProvider);
            }
            GboardPatchesSettingsContract.showChoiceDialog(
                    host,
                    text(R.string.gboard_patches_floating_web_search_browser_title),
                    labels.toArray(new String[0]),
                    values.toArray(new String[0]),
                    currentProvider,
                    "",
                    () -> { },
                    value -> safeWrite(() ->
                            GboardFloatingWebSearchSettingsStore.writeBrowserProvider(
                                    host.getContext(), value)));
        } catch (Throwable failure) {
            logFailure("Unable to show browser choices", failure);
        }
    }

    private String startPageLabel(String mode) {
        if (GboardFloatingWebSearchSettings.START_PAGE_DUCKDUCKGO.equals(mode)) {
            return "DuckDuckGo";
        }
        if (GboardFloatingWebSearchSettings.START_PAGE_BING.equals(mode)) {
            return "Bing";
        }
        if (GboardFloatingWebSearchSettings.START_PAGE_CUSTOM.equals(mode)) {
            return text(R.string.gboard_patches_floating_web_search_start_page_custom);
        }
        return "Google";
    }

    private String browserLabel(Context context, String providerValue) {
        if (GboardFloatingWebSearchSettings.BROWSER_SYSTEM_DEFAULT.equals(providerValue)) {
            return text(R.string.gboard_patches_floating_web_search_browser_system_default);
        }
        for (GboardCustomTabsProviderResolver.ProviderOption provider
                : GboardCustomTabsProviderResolver.installedProviders(context)) {
            if (provider.packageName.equals(providerValue)) {
                return provider.label;
            }
        }
        return providerValue;
    }

    private GboardPatchesSettingsContract.PreviewSpec buildEnabledPreview() {
        return new GboardPatchesSettingsContract.PreviewSpec(
                text(R.string.gboard_patches_floating_web_search_enable_title),
                "",
                GboardPatchesSettingsContract.PreviewLayout.STACKED,
                new GboardPatchesSettingsContract.PreviewImage(PREVIEW_IMAGE_BROWSER, ""),
                new GboardPatchesSettingsContract.PreviewImage(PREVIEW_IMAGE_ACCESS_POINT, ""));
    }

    private GboardPatchesSettingsContract.Screen errorScreen() {
        return new GboardPatchesSettingsContract.Screen(
                getEntryTitle(),
                text(R.string.gboard_patches_header_badge),
                getEntryTitle(),
                "",
                Collections.singletonList(new GboardPatchesSettingsContract.StatusBlock(
                        text(R.string.gboard_patches_floating_web_search_error_title),
                        text(R.string.gboard_patches_floating_web_search_error_summary),
                        GboardPatchesSettingsContract.StatusTone.WARNING)),
                Collections.emptyList());
    }

    private String text(int resourceId) {
        return GboardSettingsText.get(textContext, resourceId);
    }

    private static void safeWrite(BooleanOperation operation) {
        try {
            operation.run();
        } catch (Throwable failure) {
            logFailure("Floating Web Search setting callback failed", failure);
        }
    }

    private static void logFailure(String message, Throwable failure) {
        try {
            Log.w(TAG, message, failure);
        } catch (Throwable ignored) {
            // Settings diagnostics cannot affect the host activity.
        }
    }

    private interface BooleanOperation {
        boolean run();
    }
}
