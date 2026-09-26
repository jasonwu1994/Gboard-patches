package dev.jason.gboardpatches.extension.hideaccentpopups;

import org.junit.Assert;
import org.junit.Test;

public final class GboardHideAccentPopupsPolicyTest {
    private static final int COMMIT_TEXT = -10000;
    private static final int SELECT_ALL = -0x2766;

    @Test
    public void onlySingleLatinLetterKeysQualify() {
        Assert.assertTrue(GboardHideAccentPopupsPolicy.isLatinLetterKey("e"));
        Assert.assertTrue(GboardHideAccentPopupsPolicy.isLatinLetterKey("N"));
        Assert.assertTrue(GboardHideAccentPopupsPolicy.isLatinLetterKey("ñ"));
        Assert.assertFalse(GboardHideAccentPopupsPolicy.isLatinLetterKey(null));
        Assert.assertFalse(GboardHideAccentPopupsPolicy.isLatinLetterKey(""));
        Assert.assertFalse(GboardHideAccentPopupsPolicy.isLatinLetterKey("1"));
        Assert.assertFalse(GboardHideAccentPopupsPolicy.isLatinLetterKey(","));
        Assert.assertFalse(GboardHideAccentPopupsPolicy.isLatinLetterKey("ab"));
        Assert.assertFalse(GboardHideAccentPopupsPolicy.isLatinLetterKey("ㄅ"));
        Assert.assertFalse(GboardHideAccentPopupsPolicy.isLatinLetterKey("е")); // Cyrillic
    }

    @Test
    public void hidesAccentedLettersButKeepsDigitsSymbolsAndActions() {
        int[] keycodes = {COMMIT_TEXT, COMMIT_TEXT, COMMIT_TEXT, COMMIT_TEXT, COMMIT_TEXT,
                SELECT_ALL};
        Object[] payloads = {"3", "é", "è", "€", "é", null};

        boolean[] keep = GboardHideAccentPopupsPolicy.planKeepMask("e", keycodes, payloads);

        Assert.assertArrayEquals(
                new boolean[] {true, false, false, true, false, true}, keep);
    }

    @Test
    public void hidesAlternateLettersWithoutDecomposition() {
        int[] keycodes = {COMMIT_TEXT, COMMIT_TEXT, COMMIT_TEXT};
        Object[] payloads = {"ß", "ś", "$"};

        Assert.assertArrayEquals(new boolean[] {false, false, true},
                GboardHideAccentPopupsPolicy.planKeepMask("s", keycodes, payloads));
    }

    @Test
    public void hidesCodepointOnlyEntries() {
        Assert.assertArrayEquals(new boolean[] {false, true},
                GboardHideAccentPopupsPolicy.planKeepMask(
                        "n", new int[] {'ñ', '1'}, new Object[] {null, null}));
    }

    @Test
    public void returnsAllFalseMaskWhenOnlyAccentsExist() {
        Assert.assertArrayEquals(new boolean[] {false, false},
                GboardHideAccentPopupsPolicy.planKeepMask(
                        "u", new int[] {COMMIT_TEXT, COMMIT_TEXT}, new Object[] {"ü", "Ü"}));
    }

    @Test
    public void leavesNonLatinAndUnchangedKeysAlone() {
        Assert.assertNull(GboardHideAccentPopupsPolicy.planKeepMask(
                "е", new int[] {COMMIT_TEXT}, new Object[] {"ё"}));
        Assert.assertNull(GboardHideAccentPopupsPolicy.planKeepMask(
                "q", new int[] {COMMIT_TEXT, COMMIT_TEXT}, new Object[] {"1", "q"}));
        Assert.assertNull(GboardHideAccentPopupsPolicy.planKeepMask(
                ".", new int[] {COMMIT_TEXT}, new Object[] {"é"}));
        Assert.assertNull(GboardHideAccentPopupsPolicy.planKeepMask(
                "a", new int[0], new Object[0]));
    }
}
