package dev.jason.gboardpatches.extension.settings.search;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/**
 * Matches a query against the packaged index in the current language and, when that is not
 * English, in English too. Text is folded once per engine; each query only scores strings.
 */
public final class GboardSettingsSearchEngine {
    private static final String ENGLISH = "en";
    private static final String BREADCRUMB_SEPARATOR = " › ";
    private static final double ENGLISH_WEIGHT = 0.9;
    private static final int PAGE_BONUS = 20;
    /** A title substring or better, in either language, for every term. */
    private static final int STRONG_TERM_SCORE = (int) Math.round(
            GboardSettingsSearchMatcher.SUBSTRING * ENGLISH_WEIGHT);

    private final List<Candidate> candidates;

    private GboardSettingsSearchEngine(List<Candidate> candidates) {
        this.candidates = candidates;
    }

    public static final class Result {
        private final GboardSettingsSearchIndex.Entry entry;
        private final List<GboardPatchesSettingsContract.Feature> featurePath;
        private final String title;
        private final String breadcrumb;
        private final String englishMatch;

        Result(Candidate candidate, String englishMatch) {
            this.entry = candidate.entry;
            this.featurePath = candidate.featurePath;
            this.title = candidate.title;
            this.breadcrumb = candidate.breadcrumb;
            this.englishMatch = englishMatch;
        }

        public GboardSettingsSearchIndex.Entry getEntry() {
            return entry;
        }

        /** The features to open, root first; the last one is the page holding the result. */
        public List<GboardPatchesSettingsContract.Feature> getFeaturePath() {
            return featurePath;
        }

        public String getTitle() {
            return title;
        }

        /** Where the result lives, such as "Keyboard tools › Frosted Glass"; may be empty. */
        public String getBreadcrumb() {
            return breadcrumb;
        }

        /** The English text that matched when the current language's did not, else null. */
        public String getEnglishMatch() {
            return englishMatch;
        }
    }

    static final class Candidate {
        final GboardSettingsSearchIndex.Entry entry;
        final List<GboardPatchesSettingsContract.Feature> featurePath;
        final String title;
        final String breadcrumb;
        final int order;
        final GboardSettingsSearchMatcher.Text titleText;
        final GboardSettingsSearchMatcher.Text summaryText;
        final String englishTitle;
        final String englishSummary;
        final GboardSettingsSearchMatcher.Text englishTitleText;
        final GboardSettingsSearchMatcher.Text englishSummaryText;

        Candidate(GboardSettingsSearchIndex.Entry entry,
                List<GboardPatchesSettingsContract.Feature> featurePath,
                String title, String summary, String englishTitle, String englishSummary,
                String breadcrumb, int order) {
            this.entry = entry;
            this.featurePath = featurePath;
            this.title = title;
            this.breadcrumb = breadcrumb;
            this.order = order;
            this.titleText = GboardSettingsSearchMatcher.prepare(title);
            this.summaryText = GboardSettingsSearchMatcher.prepare(summary);
            this.englishTitle = englishTitle;
            this.englishSummary = englishSummary;
            this.englishTitleText = englishTitle == null
                    ? null
                    : GboardSettingsSearchMatcher.prepare(englishTitle);
            this.englishSummaryText = englishSummary == null
                    ? null
                    : GboardSettingsSearchMatcher.prepare(englishSummary);
        }
    }

    /**
     * Prepares the index for {@code context}'s language. {@code roots} are the home-page features;
     * results under an unavailable feature are left out. Evaluates feature availability, so call
     * it off the UI thread.
     */
    public static GboardSettingsSearchEngine create(Context context,
            List<GboardPatchesSettingsContract.Feature> roots) {
        boolean matchEnglish = !ENGLISH.equals(GboardSettingsText.languageTag(context));
        GboardSettingsSearchIndex.PathResolver resolver =
                new GboardSettingsSearchIndex.PathResolver(context, roots);
        List<GboardSettingsSearchIndex.Entry> entries = GboardSettingsSearchIndex.entries();
        List<Candidate> candidates = new ArrayList<Candidate>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            GboardSettingsSearchIndex.Entry entry = entries.get(index);
            List<GboardPatchesSettingsContract.Feature> featurePath =
                    resolver.resolve(entry.getPath());
            if (featurePath == null) {
                continue;
            }
            String title = string(context, entry.getTitleRes());
            if (title.isEmpty()) {
                continue;
            }
            String englishTitle = matchEnglish ? englishText(entry.getTitleRes()) : null;
            String englishSummary = matchEnglish && entry.getSummaryRes() != 0
                    ? englishText(entry.getSummaryRes())
                    : null;
            candidates.add(new Candidate(
                    entry,
                    featurePath,
                    title,
                    entry.getSummaryRes() == 0 ? "" : string(context, entry.getSummaryRes()),
                    englishTitle,
                    englishSummary,
                    breadcrumb(featurePath, entry.isPage()),
                    index));
        }
        return new GboardSettingsSearchEngine(Collections.unmodifiableList(candidates));
    }

    public List<Result> search(String query, int limit) {
        List<String> terms = GboardSettingsSearchMatcher.terms(query);
        if (terms.isEmpty()) {
            return Collections.emptyList();
        }
        List<Scored> scored = new ArrayList<Scored>();
        boolean anyStrong = false;
        for (Candidate candidate : candidates) {
            Scored match = score(candidate, terms);
            if (match != null) {
                scored.add(match);
                anyStrong |= match.strong;
            }
        }
        if (anyStrong) {
            // Loose matches (letters in order, typos, summaries) only stand in for missing hits.
            List<Scored> strong = new ArrayList<Scored>(scored.size());
            for (Scored match : scored) {
                if (match.strong) {
                    strong.add(match);
                }
            }
            scored = strong;
        }
        Collections.sort(scored, (first, second) -> first.score != second.score
                ? Integer.compare(second.score, first.score)
                : Integer.compare(first.candidate.order, second.candidate.order));
        List<Scored> kept =
                new ArrayList<Scored>(scored.subList(0, Math.min(limit, scored.size())));
        // Shown in settings-page order, as a filtered page is, so narrowing a query only folds
        // rows away and never reshuffles the ones that stay.
        Collections.sort(kept, (first, second) ->
                Integer.compare(first.candidate.order, second.candidate.order));
        List<Result> results = new ArrayList<Result>(kept.size());
        for (Scored match : kept) {
            results.add(new Result(match.candidate, match.englishMatch));
        }
        return Collections.unmodifiableList(results);
    }

    private static final class Scored {
        final Candidate candidate;
        final int score;
        final String englishMatch;
        final boolean strong;

        Scored(Candidate candidate, int score, String englishMatch, boolean strong) {
            this.candidate = candidate;
            this.score = score;
            this.englishMatch = englishMatch;
            this.strong = strong;
        }
    }

    private static Scored score(Candidate candidate, List<String> terms) {
        int total = 0;
        String englishMatch = null;
        boolean strong = true;
        for (String term : terms) {
            int current = Math.max(
                    GboardSettingsSearchMatcher.scoreTitle(term, candidate.titleText),
                    GboardSettingsSearchMatcher.scoreSummary(term, candidate.summaryText));
            int englishTitle = english(
                    GboardSettingsSearchMatcher.scoreTitle(term, candidate.englishTitleText));
            int englishSummary = english(
                    GboardSettingsSearchMatcher.scoreSummary(term, candidate.englishSummaryText));
            int best = Math.max(current, Math.max(englishTitle, englishSummary));
            if (best == 0) {
                return null;
            }
            if (best > current && englishMatch == null) {
                englishMatch = englishTitle >= englishSummary
                        ? candidate.englishTitle
                        : candidate.englishSummary;
            }
            strong &= best >= STRONG_TERM_SCORE;
            total += best;
        }
        if (candidate.entry.isPage()) {
            total += PAGE_BONUS;
        }
        return new Scored(candidate, total, englishMatch, strong);
    }

    private static int english(int score) {
        return (int) Math.round(score * ENGLISH_WEIGHT);
    }

    private static String breadcrumb(List<GboardPatchesSettingsContract.Feature> featurePath,
            boolean page) {
        int count = page ? featurePath.size() - 1 : featurePath.size();
        List<String> titles = new ArrayList<String>(count);
        for (int index = 0; index < count; index++) {
            String title = featurePath.get(index).getEntryTitle();
            if (title != null && !title.isEmpty()) {
                titles.add(title);
            }
        }
        return String.join(BREADCRUMB_SEPARATOR, titles);
    }

    private static String string(Context context, int resId) {
        try {
            String value = GboardSettingsText.get(context, resId);
            return value == null ? "" : value;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String englishText(int resId) {
        return GboardSettingsText.getInLanguage(resId, ENGLISH);
    }
}
