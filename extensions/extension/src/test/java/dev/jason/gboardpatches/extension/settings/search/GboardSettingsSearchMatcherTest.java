package dev.jason.gboardpatches.extension.settings.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public final class GboardSettingsSearchMatcherTest {
    private static int title(String query, String title) {
        int total = 0;
        for (String term : GboardSettingsSearchMatcher.terms(query)) {
            int score = GboardSettingsSearchMatcher.scoreTitle(
                    term, GboardSettingsSearchMatcher.prepare(title));
            if (score == 0) {
                return 0;
            }
            total += score;
        }
        return total;
    }

    @Test
    public void foldsWidthCaseAndPunctuation() {
        assertEquals(Arrays.asList("ocr", "引擎"),
                GboardSettingsSearchMatcher.terms("  ＯＣＲ　引擎 "));
        assertEquals("wificlipboard",
                GboardSettingsSearchMatcher.prepare("Wi-Fi Clipboard").normalized);
    }

    @Test
    public void exactPrefixAndSubstringRankInOrder() {
        int exact = title("磨砂玻璃", "磨砂玻璃");
        int prefix = title("磨砂", "磨砂玻璃");
        int wordStart = title("glass", "Frosted Glass");
        int substring = title("玻璃", "磨砂玻璃");
        assertEquals(GboardSettingsSearchMatcher.EXACT, exact);
        assertEquals(GboardSettingsSearchMatcher.PREFIX, prefix);
        assertEquals(GboardSettingsSearchMatcher.SUBSTRING
                + GboardSettingsSearchMatcher.WORD_START_BONUS, wordStart);
        assertEquals(GboardSettingsSearchMatcher.SUBSTRING, substring);
    }

    @Test
    public void lettersInOrderMatchAcrossWordsAndNearbyHan() {
        assertTrue(title("frgl", "Frosted Glass") > GboardSettingsSearchMatcher.TYPO);
        assertTrue(title("磨玻", "磨砂玻璃") > GboardSettingsSearchMatcher.TYPO);
        assertTrue(title("磨玻", "磨砂玻璃") < GboardSettingsSearchMatcher.SUBSTRING);
    }

    @Test
    public void scatteredHanCharactersDoNotMatch() {
        assertEquals(0, title("鍵模", "長按鍵盤上的地球鍵切換到其他輸入法模式"));
    }

    @Test
    public void missingLettersMatchAsLettersInOrder() {
        assertTrue(title("trasparency", "Key transparency") > GboardSettingsSearchMatcher.TYPO);
        assertTrue(title("blr", "Enable live blur keys") > GboardSettingsSearchMatcher.TYPO);
        assertEquals(0, title("bzr", "Enable live blur keys"));
    }

    @Test
    public void englishTyposMatchWholeAndPartialWords() {
        assertEquals(GboardSettingsSearchMatcher.TYPO, title("blurr", "Enable live blur"));
        assertEquals(GboardSettingsSearchMatcher.TYPO, title("trensparency", "Key transparency"));
        assertEquals(GboardSettingsSearchMatcher.TYPO, title("trens", "Key transparency"));
        assertEquals(0, title("blxyz", "Enable live blur"));
    }

    @Test
    public void singleCharacterOnlyMatchesAsSubstring() {
        assertEquals(GboardSettingsSearchMatcher.SUBSTRING, title("砂", "磨砂玻璃"));
        assertEquals(0, title("z", "Frosted Glass"));
    }

    @Test
    public void everyTermHasToMatch() {
        assertTrue(title("key trans", "Key transparency") > 0);
        assertEquals(0, title("key ftp", "Key transparency"));
    }

    @Test
    public void summariesOnlyMatchSubstrings() {
        GboardSettingsSearchMatcher.Text summary =
                GboardSettingsSearchMatcher.prepare("為鍵盤面板加入即時背景模糊。");
        assertEquals(GboardSettingsSearchMatcher.SUMMARY,
                GboardSettingsSearchMatcher.scoreSummary("模糊", summary));
        assertEquals(0, GboardSettingsSearchMatcher.scoreSummary("鍵糊", summary));
        assertEquals(0, GboardSettingsSearchMatcher.scoreSummary("糊", summary));
    }

    @Test
    public void editDistanceCountsSwapsAsOneSlip() {
        assertEquals(1, GboardSettingsSearchMatcher.editDistance("bulr", "blur", 2));
        assertEquals(2, GboardSettingsSearchMatcher.editDistance("abcd", "abef", 2));
    }
}
