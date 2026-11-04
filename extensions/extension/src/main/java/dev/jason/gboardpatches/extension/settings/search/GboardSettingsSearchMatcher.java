package dev.jason.gboardpatches.extension.settings.search;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Fuzzy matching for setting names, tiered after fzf and VS Code: an exact, prefix or substring
 * hit outranks letters found in order, which outrank an English typo. Titles take every tier;
 * summaries only take substrings, since loose matching over long text finds nearly anything.
 */
public final class GboardSettingsSearchMatcher {
    static final int EXACT = 1000;
    static final int PREFIX = 900;
    static final int SUBSTRING = 800;
    static final int WORD_START_BONUS = 50;
    static final int SUBSEQUENCE = 500;
    static final int TYPO = 400;
    static final int SUMMARY = 300;
    private static final int SUBSEQUENCE_FLOOR = TYPO + 1;
    private static final int CONSECUTIVE_BONUS = 20;
    private static final int MAX_CONSECUTIVE_BONUS = 150;
    private static final int GAP_PENALTY = 10;

    private GboardSettingsSearchMatcher() {
    }

    /** Text folded for matching, remembering where each Latin word starts. */
    public static final class Text {
        final String normalized;
        final boolean[] wordStarts;
        final List<String> latinWords;

        private Text(String normalized, boolean[] wordStarts, List<String> latinWords) {
            this.normalized = normalized;
            this.wordStarts = wordStarts;
            this.latinWords = latinWords;
        }

        public boolean isEmpty() {
            return normalized.isEmpty();
        }
    }

    public static Text prepare(String raw) {
        String folded = fold(raw);
        StringBuilder normalized = new StringBuilder(folded.length());
        List<Boolean> starts = new ArrayList<Boolean>(folded.length());
        List<String> latinWords = new ArrayList<String>();
        StringBuilder word = new StringBuilder();
        boolean previousKeptLatin = false;
        boolean previousSkipped = true;
        for (int index = 0; index < folded.length(); index++) {
            char character = folded.charAt(index);
            if (!Character.isLetterOrDigit(character)) {
                previousSkipped = true;
                flushWord(word, latinWords);
                continue;
            }
            boolean latin = !isIdeographic(character);
            boolean start = normalized.length() == 0
                    || (latin && (previousSkipped || !previousKeptLatin));
            if (!latin || start) {
                flushWord(word, latinWords);
            }
            if (latin) {
                word.append(character);
            }
            normalized.append(character);
            starts.add(start);
            previousKeptLatin = latin;
            previousSkipped = false;
        }
        flushWord(word, latinWords);
        boolean[] wordStarts = new boolean[starts.size()];
        for (int index = 0; index < wordStarts.length; index++) {
            wordStarts[index] = starts.get(index);
        }
        return new Text(normalized.toString(), wordStarts,
                Collections.unmodifiableList(latinWords));
    }

    /** The query split into folded terms; every term has to match for an entry to show. */
    public static List<String> terms(String query) {
        List<String> terms = new ArrayList<String>();
        if (query == null) {
            return terms;
        }
        // Fold first so a full-width space separates terms too.
        for (String part : fold(query).trim().split("\\s+")) {
            String term = prepare(part).normalized;
            if (!term.isEmpty()) {
                terms.add(term);
            }
        }
        return terms;
    }

    public static int scoreTitle(String term, Text title) {
        if (term.isEmpty() || title == null || title.isEmpty()) {
            return 0;
        }
        String text = title.normalized;
        if (text.equals(term)) {
            return EXACT;
        }
        if (text.startsWith(term)) {
            return PREFIX;
        }
        int substring = substringScore(term, title);
        if (substring > 0) {
            return substring;
        }
        if (term.length() < 2) {
            return 0;
        }
        int subsequence = subsequenceScore(term, title);
        if (subsequence > 0) {
            return subsequence;
        }
        return typoScore(term, title);
    }

    public static int scoreSummary(String term, Text summary) {
        if (term.length() < 2 || summary == null || summary.isEmpty()) {
            return 0;
        }
        return summary.normalized.contains(term) ? SUMMARY : 0;
    }

    private static int substringScore(String term, Text title) {
        String text = title.normalized;
        int index = text.indexOf(term);
        if (index < 0) {
            return 0;
        }
        while (index >= 0) {
            if (title.wordStarts[index]) {
                return SUBSTRING + WORD_START_BONUS;
            }
            index = text.indexOf(term, index + 1);
        }
        return SUBSTRING;
    }

    /**
     * Letters of the term in order. Skipping ahead to a Latin word start is free ("frgl" finds
     * Frosted Glass); every other skipped character costs, up to the term's own length.
     */
    private static int subsequenceScore(String term, Text title) {
        String text = title.normalized;
        int best = 0;
        for (int start = text.indexOf(term.charAt(0)); start >= 0;
                start = text.indexOf(term.charAt(0), start + 1)) {
            int matched = 1;
            int last = start;
            int penalty = 0;
            int consecutive = 0;
            for (int index = start + 1; index < text.length() && matched < term.length();
                    index++) {
                if (text.charAt(index) != term.charAt(matched)) {
                    continue;
                }
                if (index == last + 1) {
                    consecutive++;
                } else if (!title.wordStarts[index]) {
                    penalty += index - last - 1;
                }
                last = index;
                matched++;
            }
            if (matched < term.length() || penalty > term.length()) {
                continue;
            }
            int score = SUBSEQUENCE
                    + Math.min(MAX_CONSECUTIVE_BONUS, consecutive * CONSECUTIVE_BONUS)
                    + (title.wordStarts[start] ? WORD_START_BONUS : 0)
                    - penalty * GAP_PENALTY;
            best = Math.max(best, Math.max(SUBSEQUENCE_FLOOR, score));
        }
        return best;
    }

    /** One slip in a 4-7 letter English word, two from 8 letters, also while still typing it. */
    private static int typoScore(String term, Text title) {
        if (term.length() < 4 || !isLatin(term)) {
            return 0;
        }
        int allowed = term.length() >= 8 ? 2 : 1;
        for (String word : title.latinWords) {
            if (Math.abs(word.length() - term.length()) <= allowed
                    && editDistance(term, word, allowed) <= allowed) {
                return TYPO;
            }
            // A word still being typed: compare against the word's beginning of similar length.
            for (int length = term.length() - allowed; length <= term.length() + allowed;
                    length++) {
                if (length > 0 && length < word.length()
                        && editDistance(term, word.substring(0, length), allowed) <= allowed) {
                    return TYPO;
                }
            }
        }
        return 0;
    }

    /** Optimal string alignment distance, so a swapped pair counts as one slip. */
    static int editDistance(String first, String second, int limit) {
        int rows = first.length() + 1;
        int columns = second.length() + 1;
        int[][] distance = new int[rows][columns];
        for (int row = 0; row < rows; row++) {
            distance[row][0] = row;
        }
        for (int column = 0; column < columns; column++) {
            distance[0][column] = column;
        }
        for (int row = 1; row < rows; row++) {
            int rowMinimum = Integer.MAX_VALUE;
            for (int column = 1; column < columns; column++) {
                int cost = first.charAt(row - 1) == second.charAt(column - 1) ? 0 : 1;
                int value = Math.min(
                        Math.min(distance[row - 1][column] + 1, distance[row][column - 1] + 1),
                        distance[row - 1][column - 1] + cost);
                if (row > 1 && column > 1
                        && first.charAt(row - 1) == second.charAt(column - 2)
                        && first.charAt(row - 2) == second.charAt(column - 1)) {
                    value = Math.min(value, distance[row - 2][column - 2] + 1);
                }
                distance[row][column] = value;
                rowMinimum = Math.min(rowMinimum, value);
            }
            if (rowMinimum > limit) {
                return rowMinimum;
            }
        }
        return distance[rows - 1][columns - 1];
    }

    private static String fold(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private static void flushWord(StringBuilder word, List<String> words) {
        if (word.length() > 0) {
            words.add(word.toString());
            word.setLength(0);
        }
    }

    private static boolean isLatin(String term) {
        for (int index = 0; index < term.length(); index++) {
            if (isIdeographic(term.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIdeographic(char character) {
        Character.UnicodeScript script;
        try {
            script = Character.UnicodeScript.of(character);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL
                || script == Character.UnicodeScript.BOPOMOFO;
    }
}
