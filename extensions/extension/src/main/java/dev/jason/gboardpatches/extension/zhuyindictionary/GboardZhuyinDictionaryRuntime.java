package dev.jason.gboardpatches.extension.zhuyindictionary;

import java.util.ArrayList;
import java.util.List;

/** Tokenizes shortcuts for the Traditional Chinese dictionary importer. */
public final class GboardZhuyinDictionaryRuntime {
    private GboardZhuyinDictionaryRuntime() {}

    public static String[] tokenizeShortcut(String shortcut) {
        List<String> tokens = new ArrayList<>();
        if (shortcut == null) {
            return new String[0];
        }
        for (int offset = 0; offset < shortcut.length();) {
            int codePoint = shortcut.codePointAt(offset);
            // Retain stock Latin tokens and add standard Bopomofo and tone marks.
            if ((codePoint >= 'a' && codePoint <= 'z')
                    || (codePoint >= 0x3105 && codePoint <= 0x3129)
                    || codePoint == 0x02C9 || codePoint == 0x02CA
                    || codePoint == 0x02C7 || codePoint == 0x02CB
                    || codePoint == 0x02D9) {
                tokens.add(new String(Character.toChars(codePoint)));
            }
            offset += Character.charCount(codePoint);
        }
        return tokens.toArray(new String[0]);
    }
}
