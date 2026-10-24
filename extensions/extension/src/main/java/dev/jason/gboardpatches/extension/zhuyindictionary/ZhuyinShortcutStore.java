package dev.jason.gboardpatches.extension.zhuyindictionary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Publishes complete dictionary imports; never exposes a partially rebuilt dictionary. */
final class ZhuyinShortcutStore {
    private static final int MAX_ENTRIES = 4096;
    private final Object lock = new Object();
    private Map<String, List<String>> staging;
    private volatile Map<String, List<String>> entries = Collections.emptyMap();

    void begin() {
        synchronized (lock) {
            staging = new LinkedHashMap<>();
        }
    }

    void record(int mode, String locale, String shortcut, String word) {
        if (mode != 1) return;
        if (locale != null && !locale.isEmpty()
                && !locale.equals("zh")
                && !locale.startsWith("zh_")
                && !locale.startsWith("zh-")) {
            return;
        }
        record(shortcut, word);
    }

    void record(String shortcut, String word) {
        if (shortcut == null || word == null) return;
        shortcut = shortcut.trim();
        word = word.trim();
        if (word.isEmpty() || word.length() > 256 || !isZhuyin(shortcut)) return;

        synchronized (lock) {
            boolean implicit = (staging == null);
            if (implicit) {
                staging = new LinkedHashMap<>(entries);
            }
            if (!staging.containsKey(shortcut) && staging.size() >= MAX_ENTRIES) return;
            List<String> words = staging.get(shortcut);
            if (words == null) {
                words = new ArrayList<>();
                staging.put(shortcut, words);
            }
            if (words.size() < 8 && !words.contains(word)) {
                words.add(word);
            }
            if (implicit) {
                finish();
            }
        }
    }

    void finish() {
        synchronized (lock) {
            if (staging == null) return;
            Map<String, List<String>> next = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : staging.entrySet()) {
                next.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            }
            entries = Collections.unmodifiableMap(next);
            staging = null;
        }
    }

    void replaceEntries(Map<String, List<String>> newEntries) {
        synchronized (lock) {
            Map<String, List<String>> next = new LinkedHashMap<>();
            if (newEntries != null) {
                for (Map.Entry<String, List<String>> entry : newEntries.entrySet()) {
                    next.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
                }
            }
            entries = Collections.unmodifiableMap(next);
            staging = null;
        }
    }

    List<String> find(String composing) {
        if (composing == null) return Collections.emptyList();
        composing = composing.trim();
        List<String> result = entries.get(composing);
        return result == null ? Collections.emptyList() : result;
    }

    int size() {
        return entries.size();
    }

    static boolean isZhuyin(String text) {
        if (text == null || text.isEmpty() || text.length() > 64) return false;
        boolean letter = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= '\u3105' && ch <= '\u3129') letter = true;
            else if (ch != '\u02c9' && ch != '\u02ca' && ch != '\u02c7'
                    && ch != '\u02cb' && ch != '\u02d9') return false;
        }
        return letter;
    }
}
