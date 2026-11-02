package dev.jason.gboardpatches.extension.settings;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.search.GboardSettingsSearchEngine;

/**
 * The search page: lists the settings matching the toolbar query, each opening the page that
 * holds it. Its engine is prepared on the screen-build thread the first time a query is shown.
 */
final class GboardSettingsSearchFeature implements GboardPatchesSettingsContract.Feature {
    static final int RESULT_LIMIT = 50;

    private final GboardPatchesSettingsActivity activity;
    private volatile String query = "";
    private GboardSettingsSearchEngine engine;

    GboardSettingsSearchFeature(GboardPatchesSettingsActivity activity) {
        this.activity = activity;
    }

    String getQuery() {
        return query;
    }

    void setQuery(String query) {
        this.query = query == null ? "" : query;
    }

    /** Drops the prepared engine so the next search sees current feature availability. */
    synchronized void resetEngine() {
        engine = null;
    }

    @Override
    public String getEntryTitle() {
        return GboardSettingsText.get(activity, R.string.gboard_patches_search_title);
    }

    @Override
    public String getEntrySummary() {
        return "";
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        Context context = host.getContext();
        String currentQuery = query;
        String title = getEntryTitle();
        if (currentQuery.trim().isEmpty()) {
            return new GboardPatchesSettingsContract.Screen(
                    title, "", title, "",
                    Collections.<GboardPatchesSettingsContract.StatusBlock>emptyList(),
                    Collections.singletonList(new GboardPatchesSettingsContract.Section(
                            null,
                            GboardSettingsText.get(context,
                                    R.string.gboard_patches_search_empty_summary),
                            GboardPatchesSettingsContract.SectionStyle.DEFAULT,
                            Collections.<GboardPatchesSettingsContract.Row>emptyList())));
        }

        List<GboardSettingsSearchEngine.Result> results =
                engine().search(currentQuery, RESULT_LIMIT);
        if (results.isEmpty()) {
            return new GboardPatchesSettingsContract.Screen(
                    title, "", title, "",
                    Collections.singletonList(new GboardPatchesSettingsContract.StatusBlock(
                            GboardSettingsText.get(context,
                                    R.string.gboard_patches_search_no_results_title),
                            GboardSettingsText.get(context,
                                    R.string.gboard_patches_search_no_results_summary))),
                    Collections.<GboardPatchesSettingsContract.Section>emptyList());
        }

        List<GboardPatchesSettingsContract.Row> rows =
                new ArrayList<GboardPatchesSettingsContract.Row>(results.size());
        for (GboardSettingsSearchEngine.Result result : results) {
            String summary = summary(context, result);
            ResultRow row = new ResultRow(
                    result.getTitle(),
                    summary,
                    () -> activity.openSearchResult(result),
                    String.join(">", result.getEntry().getPath())
                            + '#' + result.getEntry().getTitleRes()
                            + '#' + (summary == null ? "" : summary));
            GboardPatchesSettingsContract.Feature root = result.getFeaturePath().get(0);
            if (root.getEntryIcon() != GboardPatchesIcons.NONE) {
                row.setDecoration(new GboardPatchesSettingsContract.RowDecoration()
                        .leadingIcon(root.getEntryIcon(), root.getEntryTone()));
            }
            rows.add(row);
        }
        return new GboardPatchesSettingsContract.Screen(
                title, "", title, "",
                Collections.<GboardPatchesSettingsContract.StatusBlock>emptyList(),
                Collections.singletonList(new GboardPatchesSettingsContract.Section(
                        GboardSettingsText.quantity(context,
                                R.plurals.gboard_patches_search_results, results.size()),
                        rows)));
    }

    /**
     * A result row with a key that stays the same while the query changes, so the renderer can
     * keep its view and fold only the rows that come and go.
     */
    static final class ResultRow extends GboardPatchesSettingsContract.NavigationRow {
        private final String key;

        ResultRow(CharSequence title, String summary, Runnable action, String key) {
            super(title, summary, true, action);
            this.key = key;
        }

        String getKey() {
            return key;
        }
    }

    private synchronized GboardSettingsSearchEngine engine() {
        if (engine == null) {
            engine = GboardSettingsSearchEngine.create(activity, activity.searchRoots());
        }
        return engine;
    }

    private static String summary(Context context, GboardSettingsSearchEngine.Result result) {
        StringBuilder summary = new StringBuilder(result.getBreadcrumb());
        if (result.getEnglishMatch() != null) {
            if (summary.length() > 0) {
                summary.append('\n');
            }
            summary.append(GboardSettingsText.format(context,
                    R.string.gboard_patches_search_english_match, result.getEnglishMatch()));
        }
        return summary.length() == 0 ? null : summary.toString();
    }
}
