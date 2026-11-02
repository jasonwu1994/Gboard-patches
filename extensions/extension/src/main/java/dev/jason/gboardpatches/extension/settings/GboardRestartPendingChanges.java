package dev.jason.gboardpatches.extension.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class GboardRestartPendingChanges {
    public interface Listener {
        void onPendingCountChanged(int pendingCount);
    }

    private static final String UNCOMPARABLE_BASELINE = "\u0000uncomparable";
    private static final Object LOCK = new Object();
    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private static final class Entry {
        String baseline;
        String current;
        String localeTag;

        boolean isPending() {
            return !Objects.equals(baseline, current);
        }
    }

    private GboardRestartPendingChanges() {
    }

    public static void observe(String restartKey, String value, String localeTag) {
        if (restartKey == null || restartKey.isEmpty() || value == null) {
            return;
        }
        int before;
        int after;
        synchronized (LOCK) {
            before = countLocked();
            Entry entry = ENTRIES.get(restartKey);
            if (entry == null) {
                entry = new Entry();
                entry.baseline = value;
                entry.current = value;
                entry.localeTag = localeTag;
                ENTRIES.put(restartKey, entry);
            } else {
                if (!Objects.equals(entry.localeTag, localeTag)) {
                    entry.baseline = entry.isPending() ? UNCOMPARABLE_BASELINE : value;
                    entry.localeTag = localeTag;
                }
                entry.current = value;
            }
            after = countLocked();
        }
        if (before != after) {
            notifyListeners(after);
        }
    }

    public static int pendingCount() {
        synchronized (LOCK) {
            return countLocked();
        }
    }

    public static List<String> pendingKeys() {
        List<String> keys = new ArrayList<>();
        synchronized (LOCK) {
            for (Map.Entry<String, Entry> item : ENTRIES.entrySet()) {
                if (item.getValue().isPending()) {
                    keys.add(item.getKey());
                }
            }
        }
        Collections.sort(keys);
        return keys;
    }

    public static void onTargetRestarted() {
        int before;
        synchronized (LOCK) {
            before = countLocked();
            ENTRIES.clear();
        }
        if (before != 0) {
            notifyListeners(0);
        }
    }

    public static void addListener(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    static void resetForTest() {
        synchronized (LOCK) {
            ENTRIES.clear();
        }
        LISTENERS.clear();
    }

    private static int countLocked() {
        int count = 0;
        for (Entry entry : ENTRIES.values()) {
            if (entry.isPending()) {
                count++;
            }
        }
        return count;
    }

    private static void notifyListeners(int pendingCount) {
        for (Listener listener : LISTENERS) {
            try {
                listener.onPendingCountChanged(pendingCount);
            } catch (Throwable ignored) {
            }
        }
    }
}
