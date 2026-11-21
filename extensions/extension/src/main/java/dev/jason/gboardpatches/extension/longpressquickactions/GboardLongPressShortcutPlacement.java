package dev.jason.gboardpatches.extension.longpressquickactions;

import java.lang.reflect.Array;

/**
 * Places the editing shortcut inside a key's long-press popup.
 *
 * <p>The keyboard highlights the first popup entry and puts it under the finger, so releasing
 * without sliding selects entry 0. {@link #FIRST} makes the shortcut that default while the
 * key's own entries stay reachable after it; {@link #LAST} keeps the key's own first entry as
 * the default and adds the shortcut at the end.
 *
 * <p>Entries, labels and icons are parallel arrays. The key's own items keep their relative
 * order, and a single shared label or icon is repeated for every original entry, as the stock
 * metadata does.
 */
public final class GboardLongPressShortcutPlacement {
    public static final String FIRST = "first";
    public static final String LAST = "last";
    public static final String DEFAULT = FIRST;

    private GboardLongPressShortcutPlacement() {
    }

    /** Returns {@link #FIRST} or {@link #LAST}; anything else falls back to {@link #DEFAULT}. */
    public static String normalize(Object value) {
        if (value instanceof String text) {
            if (FIRST.equalsIgnoreCase(text.trim())) {
                return FIRST;
            }
            if (LAST.equalsIgnoreCase(text.trim())) {
                return LAST;
            }
        }
        return DEFAULT;
    }

    public static boolean isFirst(Object value) {
        return FIRST.equals(normalize(value));
    }

    /** Index of the shortcut in a popup that already holds {@code existingCount} entries. */
    public static int shortcutIndex(int existingCount, boolean first) {
        return first ? 0 : Math.max(existingCount, 0);
    }

    /** Returns a new {@code entryType[]} with the shortcut entry at its placement. */
    public static Object entries(Class<?> entryType, Object[] existing, Object shortcut,
            boolean first) {
        Object[] source = existing == null ? new Object[0] : existing;
        Object result = Array.newInstance(entryType, source.length + 1);
        int shortcutIndex = shortcutIndex(source.length, first);
        int offset = first ? 1 : 0;
        for (int index = 0; index < source.length; index++) {
            Array.set(result, index + offset, source[index]);
        }
        Array.set(result, shortcutIndex, shortcut);
        return result;
    }

    /** Returns labels for {@code existingCount + 1} entries; the shortcut slot is null. */
    public static String[] labels(String[] existing, int existingCount, boolean first) {
        int count = Math.max(existingCount, 0);
        String[] result = new String[count + 1];
        int offset = first ? 1 : 0;
        if (existing != null && existing.length == 1 && count > 1) {
            for (int index = 0; index < count; index++) {
                result[index + offset] = existing[0];
            }
        } else if (existing != null) {
            System.arraycopy(existing, 0, result, offset, Math.min(existing.length, count));
        }
        result[shortcutIndex(count, first)] = null;
        return result;
    }

    /** Returns icons for {@code existingCount + 1} entries with the shortcut icon placed. */
    public static int[] icons(int[] existing, int existingCount, int shortcutIcon,
            boolean first) {
        int count = Math.max(existingCount, 0);
        int[] result = new int[count + 1];
        int offset = first ? 1 : 0;
        if (existing != null && existing.length == 1 && count > 1) {
            for (int index = 0; index < count; index++) {
                result[index + offset] = existing[0];
            }
        } else if (existing != null) {
            System.arraycopy(existing, 0, result, offset, Math.min(existing.length, count));
        }
        result[shortcutIndex(count, first)] = shortcutIcon;
        return result;
    }
}
