package dev.jason.gboardpatches.extension.zhuyindictionary;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Bridges explicit Zhuyin shortcuts to the stock candidate presentation and commit path. */
public final class GboardZhuyinShortcutCandidates {
    private static final String PROCESSOR =
            "com.google.android.apps.inputmethod.libs.zhuyin.ZhuyinHmmDecodeProcessor";
    private static final ZhuyinShortcutStore STORE = new ZhuyinShortcutStore();
    private static boolean reportedFailure;
    private static int lastCandidateStatus = -1;
    private static long lastDbStamp = -1;
    private static long lastCheckMillis = 0;

    private GboardZhuyinShortcutCandidates() {}

    public static void beginImport(Object listener) {
        boolean zhuyin = isZhuyinImport(listener);
        Log.i("GboardZhuyinShortcuts", "beginImport; listener="
                + (listener == null ? "null" : listener.getClass().getName())
                + "; zhuyin=" + zhuyin);
        if (zhuyin) {
            STORE.begin();
            lastDbStamp = -1;
        }
    }

    public static void recordRow(Object listener, Object[] row) {
        if (row == null) return;
        String word = (row.length > 0 && row[0] instanceof String) ? (String) row[0] : null;
        String locale = (row.length > 2 && row[2] instanceof String) ? (String) row[2] : null;
        String shortcut = (row.length > 3 && row[3] instanceof String) ? (String) row[3] : null;
        boolean zhuyinShortcut = ZhuyinShortcutStore.isZhuyin(shortcut);
        boolean zhuyin = isZhuyinImport(listener) || zhuyinShortcut;
        if (zhuyinShortcut || zhuyin) {
            Log.i("GboardZhuyinShortcuts", "recordRow; zhuyin=" + zhuyin
                    + "; zhuyinShortcut=" + zhuyinShortcut
                    + "; shortcutLen=" + (shortcut == null ? 0 : shortcut.length())
                    + "; wordLen=" + (word == null ? 0 : word.length())
                    + "; locale=" + locale);
        }
        if (zhuyinShortcut && word != null) {
            STORE.record(shortcut, word);
        }
    }

    public static void recordEntry(int mode, String locale, String shortcut, String word) {
        boolean zhuyinShortcut = ZhuyinShortcutStore.isZhuyin(shortcut);
        if (zhuyinShortcut || mode == 1) {
            Log.i("GboardZhuyinShortcuts", "recordEntry; mode=" + mode
                    + "; zhuyinShortcut=" + zhuyinShortcut
                    + "; shortcutLen=" + (shortcut == null ? 0 : shortcut.length())
                    + "; wordLen=" + (word == null ? 0 : word.length())
                    + "; locale=" + locale);
        }
        if (zhuyinShortcut && word != null) {
            STORE.record(shortcut, word);
        }
    }

    public static void finishImport(Object listener) {
        boolean zhuyin = (listener != null && "kkb".equals(listener.getClass().getName()))
                || isZhuyinImport(listener);
        Log.i("GboardZhuyinShortcuts", "finishImport; listener="
                + (listener == null ? "null" : listener.getClass().getName())
                + "; zhuyin=" + zhuyin);
        STORE.finish();
        lastCandidateStatus = -1;
        Log.i("GboardZhuyinShortcuts", "Dictionary import completed; shortcuts=" + STORE.size());
    }

    public static Iterator<?> candidates(Object processor) {
        if (!isZhuyinProcessor(processor)) return null;
        ensureLoaded(processor);
        try {
            Object model = field(processor.getClass(), "m").get(processor);
            if (model == null) return null;
            String composing = (String) method(model.getClass(), "e").invoke(model);
            List<String> words = STORE.find(composing);
            int status = words.size() + (ZhuyinShortcutStore.isZhuyin(composing) ? 16 : 0);
            if (status != lastCandidateStatus) {
                lastCandidateStatus = status;
                Log.i("GboardZhuyinShortcuts", "Candidate lookup; zhuyin="
                        + ZhuyinShortcutStore.isZhuyin(composing)
                        + "; composingLen=" + (composing == null ? 0 : composing.length())
                        + "; matches=" + words.size());
            }
            if (words.isEmpty()) return null;
            ClassLoader loader = processor.getClass().getClassLoader();
            Class<?> builderType = Class.forName("ojq", false, loader);
            Class<?> categoryType = Class.forName("ojs", false, loader);
            Object category;
            try {
                category = field(categoryType, "d").get(null);
            } catch (NoSuchFieldException ignored) {
                category = field(categoryType, "a").get(null);
            }
            List<Object> additions = new ArrayList<>();
            for (String word : words) {
                Object builder = builderType.getDeclaredConstructor().newInstance();
                field(builderType, "a").set(builder, word);
                field(builderType, "e").set(builder, category);
                field(builderType, "m").set(builder, new ShortcutCandidate(composing, word));
                additions.add(method(builderType, "a").invoke(builder));
            }
            Iterator<?> stock = (Iterator<?>) method(model.getClass(), "f").invoke(model);
            return prepend(additions, stock);
        } catch (ReflectiveOperationException | RuntimeException error) {
            reportFailure(error);
            return null;
        }
    }

    public static boolean commit(Object processor, Object candidate) {
        if (!isZhuyinProcessor(processor) || candidate == null) return false;
        ShortcutCandidate data;
        try {
            Object marker = field(candidate.getClass(), "m").get(candidate);
            if (!(marker instanceof ShortcutCandidate)) return false;
            data = (ShortcutCandidate) marker;
        } catch (ReflectiveOperationException | RuntimeException error) {
            return false;
        }
        // Consume our own stale candidates instead of handing non-native data to the decoder.
        try {
            Object model = field(processor.getClass(), "m").get(processor);
            if (model == null || !data.shortcut.equals(method(model.getClass(), "e").invoke(model))
                    || !STORE.find(data.shortcut).contains(data.word)) return true;
            method(processor.getClass(), "aC", String.class, int.class, boolean.class)
                    .invoke(processor, data.word, 1, true);
        } catch (ReflectiveOperationException | RuntimeException error) {
            reportFailure(error);
        }
        return true;
    }

    private static boolean isZhuyinProcessor(Object processor) {
        if (processor == null) return false;
        for (Class<?> type = processor.getClass(); type != null; type = type.getSuperclass()) {
            if (PROCESSOR.equals(type.getName())) return true;
        }
        return false;
    }

    private static Context getContext(Object processor) {
        if (processor == null) return null;
        try {
            Field uField = field(processor.getClass(), "u");
            Object candidate = uField.get(processor);
            if (candidate instanceof Context) {
                return (Context) candidate;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static synchronized void ensureLoaded(Object processor) {
        Context context = getContext(processor);
        if (context == null) return;

        long now = System.currentTimeMillis();
        if (STORE.size() > 0 && (now - lastCheckMillis < 1500L)) {
            return;
        }
        lastCheckMillis = now;

        try {
            File dbFile = context.getDatabasePath("PersonalDictionary.db");
            if (dbFile == null || !dbFile.exists()) {
                return;
            }

            File walFile = new File(dbFile.getPath() + "-wal");
            long dbMod = dbFile.lastModified();
            long walMod = walFile.exists() ? walFile.lastModified() : 0L;
            long stamp = dbMod ^ (walMod * 31);

            if (stamp == lastDbStamp && STORE.size() > 0) {
                return;
            }

            loadFromDatabase(dbFile, stamp);
        } catch (Throwable error) {
            Log.w("GboardZhuyinShortcuts", "Failed to check PersonalDictionary.db", error);
        }
    }

    private static void loadFromDatabase(File dbFile, long stamp) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = SQLiteDatabase.openDatabase(
                    dbFile.getPath(),
                    null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            cursor = db.rawQuery(
                    "SELECT shortcut, word FROM entry WHERE shortcut IS NOT NULL AND shortcut != ''",
                    null);
            Map<String, List<String>> newEntries = new LinkedHashMap<>();
            int count = 0;
            if (cursor != null) {
                int shortcutIdx = cursor.getColumnIndex("shortcut");
                int wordIdx = cursor.getColumnIndex("word");
                while (cursor.moveToNext()) {
                    String shortcut = shortcutIdx >= 0 ? cursor.getString(shortcutIdx) : cursor.getString(0);
                    String word = wordIdx >= 0 ? cursor.getString(wordIdx) : cursor.getString(1);
                    if (shortcut == null || word == null) continue;
                    shortcut = shortcut.trim();
                    word = word.trim();
                    if (word.isEmpty() || word.length() > 256 || !ZhuyinShortcutStore.isZhuyin(shortcut)) {
                        continue;
                    }
                    List<String> words = newEntries.get(shortcut);
                    if (words == null) {
                        words = new ArrayList<>();
                        newEntries.put(shortcut, words);
                    }
                    if (words.size() < 8 && !words.contains(word)) {
                        words.add(word);
                        count++;
                    }
                }
            }
            STORE.replaceEntries(newEntries);
            lastDbStamp = stamp;
            lastCandidateStatus = -1;
            Log.i("GboardZhuyinShortcuts", "Loaded shortcuts from PersonalDictionary.db; distinct="
                    + newEntries.size() + "; totalWords=" + count);
        } catch (Throwable error) {
            Log.w("GboardZhuyinShortcuts", "Failed to query PersonalDictionary.db", error);
        } finally {
            if (cursor != null) {
                try { cursor.close(); } catch (Throwable ignored) {}
            }
            if (db != null) {
                try { db.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static boolean isZhuyinImport(Object listener) {
        if (listener == null) return false;
        String name = listener.getClass().getName();
        if ("kkb".equals(name) || name.endsWith(".ZhuyinHmmEngineFactory")) return true;
        try {
            Field fieldA = field(listener.getClass(), "a");
            Object owner = fieldA.get(listener);
            if (owner != null) {
                String ownerName = owner.getClass().getName();
                if ("kkb".equals(ownerName) || ownerName.endsWith(".ZhuyinHmmEngineFactory")) return true;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        try {
            Field fieldC = field(listener.getClass(), "c");
            Object accessor = fieldC.get(listener);
            if (accessor != null) {
                Field fieldMode = field(accessor.getClass(), "a");
                int mode = fieldMode.getInt(accessor);
                if (mode == 1) return true;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return false;
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field result = current.getDeclaredField(name); result.setAccessible(true); return result; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Method result = current.getDeclaredMethod(name, parameters); result.setAccessible(true); return result; }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }

    static Iterator<?> prepend(List<?> additions, Iterator<?> stock) {
        Iterator<?> first = additions.iterator();
        Iterator<?> rest = stock == null ? Collections.emptyList().iterator() : stock;
        return new Iterator<Object>() {
            @Override public boolean hasNext() { return first.hasNext() || rest.hasNext(); }
            @Override public Object next() {
                if (first.hasNext()) return first.next();
                if (rest.hasNext()) return rest.next();
                throw new NoSuchElementException();
            }
        };
    }

    private static void reportFailure(Exception error) {
        if (!reportedFailure) {
            reportedFailure = true;
            Log.w("GboardZhuyinShortcuts", "Shortcut bridge unavailable; error="
                    + error.getClass().getSimpleName(), error);
        }
    }

    private static final class ShortcutCandidate {
        final String shortcut;
        final String word;
        ShortcutCandidate(String shortcut, String word) { this.shortcut = shortcut; this.word = word; }
    }
}
