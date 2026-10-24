package dev.jason.gboardpatches.extension.zhuyindictionary;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

public class GboardZhuyinDictionaryRuntimeTest {
    @Test
    public void retainsSingleSymbolShortcut() {
        assertArrayEquals(new String[]{"ㄉ"},
                GboardZhuyinDictionaryRuntime.tokenizeShortcut("ㄉ"));
    }

    @Test
    public void retainsReadingAndToneMarksInOrder() {
        assertArrayEquals(new String[]{"ㄉ", "ㄧ", "ㄥ", "ˋ"},
                GboardZhuyinDictionaryRuntime.tokenizeShortcut("ㄉㄧㄥˋ"));
        assertArrayEquals(new String[]{"ˉ", "ˊ", "ˇ", "ˋ", "˙"},
                GboardZhuyinDictionaryRuntime.tokenizeShortcut("ˉˊˇˋ˙"));
    }

    @Test
    public void preservesStockLatinFilteringAndHandlesSupplementaryCharacters() {
        assertArrayEquals(new String[]{"r", "s", "p", "s"},
                GboardZhuyinDictionaryRuntime.tokenizeShortcut("rsps1008"));
        assertArrayEquals(new String[]{"a", "ㄉ", "z"},
                GboardZhuyinDictionaryRuntime.tokenizeShortcut("aㄉ😀z"));
        assertArrayEquals(new String[0], GboardZhuyinDictionaryRuntime.tokenizeShortcut(null));
        assertArrayEquals(new String[0], GboardZhuyinDictionaryRuntime.tokenizeShortcut(""));
    }
}
