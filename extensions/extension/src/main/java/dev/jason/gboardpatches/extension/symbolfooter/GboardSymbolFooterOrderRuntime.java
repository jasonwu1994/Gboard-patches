package dev.jason.gboardpatches.extension.symbolfooter;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsProvider;

public final class GboardSymbolFooterOrderRuntime {
    private static final String TAG = "GboardPatches";
    private static final String LOG_LABEL = "expression footer tab order";
    private static final String CORPUS_MANAGER_CLASS = "gyo";
    private static final String CORPUS_MANAGER_METHOD = "a";
    private static final String CORPUS_MANAGER_CONTEXT_FIELD = "c";
    private static final String CORPUS_ITEM_CLASS = "ohn";
    private static final String CORPUS_ITEM_KEYBOARD_FIELD = "c";
    private static final String KEYBOARD_TYPE_CLASS = "qch";
    private static final String KEYBOARD_TYPE_NAME_FIELD = "m";
    private static final String IMMUTABLE_LIST_CLASS = "wns";
    private static final String IMMUTABLE_LIST_COPY_METHOD = "o";
    private static final long SETTINGS_CACHE_WINDOW_MS = 1_000L;
    private static final Map<ClassLoader, Handles> HANDLES_BY_LOADER =
            Collections.synchronizedMap(new WeakHashMap<ClassLoader, Handles>());
    private static final Object SETTINGS_LOCK = new Object();

    private static volatile CachedSettings cachedSettings;

    private GboardSymbolFooterOrderRuntime() {
    }

    public static Object reorderExpressionCorpusList(Object receiver, Object corpusList) {
        if (receiver == null || !(corpusList instanceof List<?>)) {
            return corpusList;
        }
        try {
            ClassLoader classLoader = receiver.getClass().getClassLoader();
            if (classLoader == null) {
                return corpusList;
            }
            Handles handles = handles(classLoader);
            if (!handles.expressionCorpusManagerClass.isInstance(receiver)) {
                return corpusList;
            }
            List<String> configuredOrder = resolveConfiguredOrder(
                    handles.expressionCorpusManagerContext(receiver));
            return reorderExpressionCorpusList(handles, corpusList, configuredOrder);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to reorder " + LOG_LABEL + " corpus list", throwable);
            return corpusList;
        }
    }

    public static void invalidateCachedSettings() {
        synchronized (SETTINGS_LOCK) {
            cachedSettings = null;
        }
    }

    private static Object reorderExpressionCorpusList(Handles handles,
            Object corpusList,
            List<String> configuredOrder) throws Throwable {
        List<?> source = (List<?>) corpusList;
        if (source.size() < 2) {
            return corpusList;
        }

        List<OrderEntry> entries = new ArrayList<OrderEntry>(source.size());
        for (int index = 0; index < source.size(); index++) {
            Object corpusItem = source.get(index);
            entries.add(new OrderEntry(
                    corpusItem,
                    handles.expressionCorpusTypeName(corpusItem),
                    index));
        }
        List<OrderEntry> reorderedEntries = new ArrayList<OrderEntry>(entries);
        Collections.sort(reorderedEntries, new java.util.Comparator<OrderEntry>() {
            @Override
            public int compare(OrderEntry left, OrderEntry right) {
                int priorityCompare = Integer.compare(
                        orderPriority(left.keyboardTypeName, configuredOrder),
                        orderPriority(right.keyboardTypeName, configuredOrder));
                return priorityCompare != 0
                        ? priorityCompare
                        : Integer.compare(left.originalIndex, right.originalIndex);
            }
        });

        if (isSameOrder(entries, reorderedEntries)) {
            Log.i(TAG, "Expression footer order unchanged: configured="
                    + describeOrder(configuredOrder)
                    + ", current=" + describeEntries(entries));
            return corpusList;
        }

        List<Object> reorderedCorpus = new ArrayList<Object>(reorderedEntries.size());
        for (OrderEntry entry : reorderedEntries) {
            reorderedCorpus.add(entry.corpusItem);
        }
        Object rebuilt = handles.immutableListCopyMethod.invoke(null, reorderedCorpus);
        Log.i(TAG, "Reordered " + LOG_LABEL + " corpus list: configured="
                + describeOrder(configuredOrder)
                + ", before=" + describeEntries(entries)
                + ", after=" + describeEntries(reorderedEntries));
        return rebuilt != null ? rebuilt : corpusList;
    }

    private static boolean isSameOrder(List<OrderEntry> left, List<OrderEntry> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            if (left.get(index).corpusItem != right.get(index).corpusItem) {
                return false;
            }
        }
        return true;
    }

    private static String describeOrder(List<String> configuredOrder) {
        return configuredOrder == null || configuredOrder.isEmpty()
                ? "<empty>"
                : configuredOrder.toString();
    }

    private static String describeEntries(List<OrderEntry> entries) {
        List<String> names = new ArrayList<String>(entries.size());
        for (OrderEntry entry : entries) {
            names.add(entry != null ? String.valueOf(entry.keyboardTypeName) : "null");
        }
        return names.toString();
    }

    private static int orderPriority(String keyboardTypeName, List<String> configuredOrder) {
        int index = configuredOrder.indexOf(keyboardTypeName);
        return index >= 0 ? index : configuredOrder.size();
    }

    private static List<String> resolveConfiguredOrder(Context context) {
        long now = SystemClock.elapsedRealtime();
        CachedSettings current = cachedSettings;
        if (current != null && isSettingsCacheFresh(current.loadedAtElapsedMs, now)) {
            return current.order;
        }
        synchronized (SETTINGS_LOCK) {
            current = cachedSettings;
            if (current != null && isSettingsCacheFresh(current.loadedAtElapsedMs, now)) {
                return current.order;
            }
            CachedSettings loaded = new CachedSettings(
                    now,
                    normalizeProviderOrder(loadConfiguredOrderFromProvider(context)));
            cachedSettings = loaded;
            return loaded.order;
        }
    }

    private static List<String> loadConfiguredOrderFromProvider(Context context) {
        if (context == null) {
            return null;
        }
        try {
            ContentResolver contentResolver = context.getContentResolver();
            if (contentResolver == null) {
                return null;
            }
            Bundle result = contentResolver.call(
                    buildSettingsProviderUri(context),
                    GboardPatchesSettingsProvider.METHOD_GET_SYMBOL_FOOTER_ORDER_SETTINGS,
                    null,
                    null);
            if (result == null) {
                return null;
            }
            return result.getStringArrayList(
                    GboardPatchesSettingsProvider.BUNDLE_KEY_SYMBOL_FOOTER_ORDER);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to load " + LOG_LABEL + " from provider; using defaults",
                    throwable);
            return null;
        }
    }

    private static Uri buildSettingsProviderUri(Context context) {
        return Uri.parse("content://"
                + context.getPackageName()
                + GboardPatchesSettingsProvider.AUTHORITY_SUFFIX);
    }

    private static List<String> normalizeProviderOrder(List<String> providerOrder) {
        if (providerOrder == null || providerOrder.isEmpty()) {
            return defaultOrderCopy();
        }
        for (String value : providerOrder) {
            if (value == null
                    || !GboardSymbolFooterOrderSettings.DEFAULT_SYMBOL_FOOTER_ORDER
                            .contains(value.trim())) {
                return defaultOrderCopy();
            }
        }
        return normalizeConfiguredOrder(providerOrder);
    }

    private static List<String> normalizeConfiguredOrder(List<String> configuredOrder) {
        LinkedHashSet<String> normalized = new LinkedHashSet<String>();
        if (configuredOrder != null) {
            for (String value : configuredOrder) {
                if (value == null) {
                    continue;
                }
                String trimmed = value.trim();
                if (GboardSymbolFooterOrderSettings.DEFAULT_SYMBOL_FOOTER_ORDER
                        .contains(trimmed)) {
                    normalized.add(trimmed);
                }
            }
        }
        normalized.addAll(GboardSymbolFooterOrderSettings.DEFAULT_SYMBOL_FOOTER_ORDER);
        return Collections.unmodifiableList(new ArrayList<String>(normalized));
    }

    private static boolean isSettingsCacheFresh(long loadedAtElapsedMs, long nowElapsedMs) {
        return nowElapsedMs - loadedAtElapsedMs <= SETTINGS_CACHE_WINDOW_MS;
    }

    private static Handles handles(ClassLoader classLoader) throws Throwable {
        synchronized (HANDLES_BY_LOADER) {
            Handles cached = HANDLES_BY_LOADER.get(classLoader);
            if (cached != null) {
                return cached;
            }
            Handles created = new Handles(classLoader);
            HANDLES_BY_LOADER.put(classLoader, created);
            return created;
        }
    }

    private static final class Handles {
        final Class<?> expressionCorpusManagerClass;
        final Method expressionCorpusManagerMethod;
        final Field expressionCorpusManagerContextField;
        final Class<?> expressionCorpusItemClass;
        final Field expressionCorpusItemKeyboardTypeField;
        final Class<?> keyboardTypeClass;
        final Field keyboardTypeNameField;
        final Method immutableListCopyMethod;

        Handles(ClassLoader classLoader) throws Throwable {
            expressionCorpusManagerClass = resolve(classLoader, CORPUS_MANAGER_CLASS);
            expressionCorpusItemClass = resolve(classLoader, CORPUS_ITEM_CLASS);
            keyboardTypeClass = resolve(classLoader, KEYBOARD_TYPE_CLASS);
            Class<?> immutableListClass = resolve(classLoader, IMMUTABLE_LIST_CLASS);

            expressionCorpusManagerMethod = method(
                    expressionCorpusManagerClass,
                    CORPUS_MANAGER_METHOD,
                    EditorInfo.class,
                    boolean.class);
            if (expressionCorpusManagerMethod.getReturnType() != immutableListClass) {
                throw new NoSuchMethodException("gri#a return type is "
                        + expressionCorpusManagerMethod.getReturnType().getName());
            }
            expressionCorpusManagerContextField = field(
                    expressionCorpusManagerClass, CORPUS_MANAGER_CONTEXT_FIELD);
            expressionCorpusItemKeyboardTypeField = field(
                    expressionCorpusItemClass, CORPUS_ITEM_KEYBOARD_FIELD);
            keyboardTypeNameField = field(keyboardTypeClass, KEYBOARD_TYPE_NAME_FIELD);
            immutableListCopyMethod = method(
                    immutableListClass, IMMUTABLE_LIST_COPY_METHOD, Collection.class);
        }

        Context expressionCorpusManagerContext(Object receiver) throws IllegalAccessException {
            Object value = expressionCorpusManagerContextField.get(receiver);
            return value instanceof Context ? (Context) value : null;
        }

        String expressionCorpusTypeName(Object corpusItem) throws IllegalAccessException {
            if (corpusItem == null || !expressionCorpusItemClass.isInstance(corpusItem)) {
                return null;
            }
            Object keyboardType = expressionCorpusItemKeyboardTypeField.get(corpusItem);
            if (keyboardType == null || !keyboardTypeClass.isInstance(keyboardType)) {
                return null;
            }
            Object name = keyboardTypeNameField.get(keyboardType);
            return name instanceof String ? (String) name : null;
        }
    }

    private static final class OrderEntry {
        final Object corpusItem;
        final String keyboardTypeName;
        final int originalIndex;

        OrderEntry(Object corpusItem, String keyboardTypeName, int originalIndex) {
            this.corpusItem = corpusItem;
            this.keyboardTypeName = keyboardTypeName;
            this.originalIndex = originalIndex;
        }
    }

    private static final class CachedSettings {
        final long loadedAtElapsedMs;
        final List<String> order;

        CachedSettings(long loadedAtElapsedMs, List<String> order) {
            this.loadedAtElapsedMs = loadedAtElapsedMs;
            this.order = order;
        }
    }

    private static List<String> defaultOrderCopy() {
        return Collections.unmodifiableList(new ArrayList<String>(
                GboardSymbolFooterOrderSettings.DEFAULT_SYMBOL_FOOTER_ORDER));
    }

    private static Class<?> resolve(ClassLoader classLoader, String className)
            throws ClassNotFoundException {
        return Class.forName(className, false, classLoader);
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }
}
