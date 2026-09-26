package dev.jason.gboardpatches.extension.hideaccentpopups;

/**
 * Decides which long-press popup entries of a Latin letter key are accented/alternate letters.
 *
 * <p>A key qualifies when it commits exactly one Latin-script letter on press (for example
 * {@code e} or {@code N}). On such a key every long-press entry that commits other Latin letters
 * (for example {@code é}, {@code ñ}, {@code ß} or {@code æ}) is hidden, while digits, symbols and
 * non-text actions such as the Long-Press Editing Shortcuts stay in place.
 */
public final class GboardHideAccentPopupsPolicy {
    private GboardHideAccentPopupsPolicy() {
    }

    public static boolean isLatinLetterKey(String pressText) {
        if (pressText == null || pressText.isEmpty()) {
            return false;
        }
        int codePoint = pressText.codePointAt(0);
        return Character.charCount(codePoint) == pressText.length()
                && isLatinLetter(codePoint);
    }

    /**
     * Returns a keep-mask for {@code entries}, or {@code null} when nothing should be hidden.
     * When every entry would be hidden the returned mask is all {@code false}.
     */
    public static boolean[] planKeepMask(String pressText, int[] keycodes, Object[] payloads) {
        if (!isLatinLetterKey(pressText) || keycodes == null || payloads == null
                || keycodes.length != payloads.length || keycodes.length == 0) {
            return null;
        }
        boolean[] keep = new boolean[keycodes.length];
        boolean changed = false;
        for (int index = 0; index < keycodes.length; index++) {
            boolean hide = isAlternateLetter(pressText, keycodes[index], payloads[index]);
            keep[index] = !hide;
            changed |= hide;
        }
        return changed ? keep : null;
    }

    static boolean isAlternateLetter(String pressText, int keycode, Object payload) {
        String text;
        if (payload instanceof CharSequence) {
            text = payload.toString();
        } else if (payload == null && keycode > 0 && Character.isValidCodePoint(keycode)) {
            text = new String(Character.toChars(keycode));
        } else {
            return false;
        }
        if (text.isEmpty() || text.equals(pressText)) {
            return false;
        }
        boolean hasLatinLetter = false;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isLatinLetter(codePoint)) {
                hasLatinLetter = true;
            } else if (!isCombiningMark(codePoint)) {
                return false;
            }
        }
        return hasLatinLetter;
    }

    private static boolean isLatinLetter(int codePoint) {
        return Character.isLetter(codePoint)
                && Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN;
    }

    private static boolean isCombiningMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }
}
