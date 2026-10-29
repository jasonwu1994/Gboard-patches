package dev.jason.gboardpatches.extension.settings;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GboardFeatureGroup implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";

    private final String entryTitle;
    private final String entrySummary;
    private final String headerBadge;
    private final String headerSummary;
    private final String emptyTitle;
    private final String emptySummary;
    private final List<GboardPatchesSettingsContract.Feature> children;

    public GboardFeatureGroup(String entryTitle,
            String entrySummary,
            String headerBadge,
            String headerSummary,
            String emptyTitle,
            String emptySummary,
            List<GboardPatchesSettingsContract.Feature> children) {
        this.entryTitle = entryTitle;
        this.entrySummary = entrySummary;
        this.headerBadge = headerBadge;
        this.headerSummary = headerSummary;
        this.emptyTitle = emptyTitle;
        this.emptySummary = emptySummary;
        this.children = Collections.unmodifiableList(
                new ArrayList<GboardPatchesSettingsContract.Feature>(children));
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
        return !availableChildren(context).isEmpty();
    }

    @Override
    public List<GboardPatchesSettingsContract.Feature> getNavigationChildren() {
        return children;
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        List<GboardPatchesSettingsContract.Feature> visibleChildren =
                availableChildren(host.getContext());
        if (visibleChildren.isEmpty()) {
            List<GboardPatchesSettingsContract.StatusBlock> statusBlocks =
                    new ArrayList<GboardPatchesSettingsContract.StatusBlock>();
            statusBlocks.add(new GboardPatchesSettingsContract.StatusBlock(
                    emptyTitle,
                    emptySummary));
            return new GboardPatchesSettingsContract.Screen(
                    entryTitle,
                    headerBadge,
                    entryTitle,
                    headerSummary,
                    statusBlocks,
                    Collections.emptyList());
        } else {
            List<GboardPatchesSettingsContract.Row> rows =
                    new ArrayList<GboardPatchesSettingsContract.Row>();
            for (GboardPatchesSettingsContract.Feature child : visibleChildren) {
                GboardPatchesSettingsContract.NavigationRow row =
                        new GboardPatchesSettingsContract.NavigationRow(
                                child.getEntryTitle(),
                                child.getEntrySummary(),
                                true,
                                () -> child.openRootEntry(host));
                GboardFeatureEntryState state = GboardFeatureEntryState.inspect(child, host);
                if (state.getMasterToggle() != null) {
                    row.setDecoration(new GboardPatchesSettingsContract.RowDecoration()
                            .entryToggle(state.getMasterToggle()));
                }
                rows.add(row);
            }
            return new GboardPatchesSettingsContract.Screen(
                    entryTitle,
                    headerBadge,
                    entryTitle,
                    pageSummary(),
                    Collections.emptyList(),
                    Collections.singletonList(new GboardPatchesSettingsContract.Section(
                            null,
                            rows)));
        }
    }

    private String pageSummary() {
        return headerSummary == null || headerSummary.isEmpty() ? entrySummary : headerSummary;
    }

    private List<GboardPatchesSettingsContract.Feature> availableChildren(Context context) {
        List<GboardPatchesSettingsContract.Feature> visible =
                new ArrayList<GboardPatchesSettingsContract.Feature>();
        for (GboardPatchesSettingsContract.Feature child : children) {
            if (child == null) {
                continue;
            }
            try {
                if (child.isAvailable(context)) {
                    visible.add(child);
                }
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed to evaluate grouped feature availability: "
                        + child.getClass().getName(), throwable);
            }
        }
        return visible;
    }
}
