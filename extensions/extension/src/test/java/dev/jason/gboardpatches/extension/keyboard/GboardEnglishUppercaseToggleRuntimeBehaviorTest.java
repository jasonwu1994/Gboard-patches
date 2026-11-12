package dev.jason.gboardpatches.extension.keyboard;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class GboardEnglishUppercaseToggleRuntimeBehaviorTest {
    @After
    public void tearDown() throws Exception {
        setApplicationContext(null);
    }

    @Test
    public void isEnabledReadsInjectedApplicationPreferences() throws Exception {
        ThrowingSharedPreferences preferences = new ThrowingSharedPreferences();
        GboardEnglishUppercaseToggleSettings.ensureDefaults(preferences);
        Assert.assertTrue(GboardEnglishUppercaseToggleSettings.writeEnabled(preferences, false));
        setApplicationContext(new SharedPreferencesContext(preferences));

        Assert.assertFalse(GboardEnglishUppercaseToggleRuntime.isEnabled());
    }

    @Test
    public void isEnabledFallsBackToDefaultWhenStoredTypeIsInvalid() throws Exception {
        ThrowingSharedPreferences preferences = new ThrowingSharedPreferences();
        preferences.values.put(
                GboardEnglishUppercaseToggleSettings.PREF_KEY_ENABLED,
                "true");
        setApplicationContext(new SharedPreferencesContext(preferences));

        Assert.assertFalse(GboardEnglishUppercaseToggleRuntime.isEnabled());
    }

    @Test
    public void patchedMetadataMarkersAreIdentityScoped() throws Exception {
        Method isPatched = GboardEnglishUppercaseToggleRuntime.class.getMethod(
                "isPatchedMetadata", Object.class);
        Method markPatched = GboardEnglishUppercaseToggleRuntime.class.getMethod(
                "markPatchedMetadata", Object.class);
        Object patched = new EqualValue("same");
        Object equalButDistinctStock = new EqualValue("same");

        Assert.assertFalse((Boolean) isPatched.invoke(null, patched));
        markPatched.invoke(null, patched);
        Assert.assertTrue((Boolean) isPatched.invoke(null, patched));
        Assert.assertFalse((Boolean) isPatched.invoke(null, equalButDistinctStock));
        Assert.assertFalse((Boolean) isPatched.invoke(null, new Object[] {null}));
    }

    @Test
    public void patchedMetadataCacheUsesOriginalIdentityAndReturnsCanonicalInstance()
            throws Exception {
        Method getCached = GboardEnglishUppercaseToggleRuntime.class.getMethod(
                "getCachedPatchedMetadata", Object.class);
        Method cachePatched = GboardEnglishUppercaseToggleRuntime.class.getMethod(
                "cachePatchedMetadata", Object.class, Object.class);
        Method isPatched = GboardEnglishUppercaseToggleRuntime.class.getMethod(
                "isPatchedMetadata", Object.class);
        Object original = new EqualValue("original");
        Object equalButDistinctOriginal = new EqualValue("original");
        Object patched = new EqualValue("patched");
        Object losingRacePatched = new EqualValue("patched");

        Assert.assertNull(getCached.invoke(null, original));
        Assert.assertSame(patched, cachePatched.invoke(null, original, patched));
        Assert.assertSame(patched, getCached.invoke(null, original));
        Assert.assertSame(patched, cachePatched.invoke(null, original, losingRacePatched));
        Assert.assertTrue((Boolean) isPatched.invoke(null, patched));
        Assert.assertFalse((Boolean) isPatched.invoke(null, losingRacePatched));

        Assert.assertNull(getCached.invoke(null, equalButDistinctOriginal));
        Object distinctPatched = new EqualValue("patched");
        Assert.assertSame(
                distinctPatched,
                cachePatched.invoke(null, equalButDistinctOriginal, distinctPatched));
        Assert.assertSame(distinctPatched, getCached.invoke(null, equalButDistinctOriginal));
    }

    @Test
    public void cacheNullInputsDoNotCreateEntriesOrMarkers() throws Exception {
        Method getCached = GboardEnglishUppercaseToggleRuntime.class.getMethod(
                "getCachedPatchedMetadata", Object.class);
        Method cachePatched = GboardEnglishUppercaseToggleRuntime.class.getMethod(
                "cachePatchedMetadata", Object.class, Object.class);
        Method isPatched = GboardEnglishUppercaseToggleRuntime.class.getMethod(
                "isPatchedMetadata", Object.class);
        Object patchedWithoutOriginal = new Object();
        Object originalWithoutPatched = new Object();

        Assert.assertNull(getCached.invoke(null, new Object[] {null}));
        Assert.assertSame(
                patchedWithoutOriginal,
                cachePatched.invoke(null, new Object[] {null, patchedWithoutOriginal}));
        Assert.assertFalse((Boolean) isPatched.invoke(null, patchedWithoutOriginal));
        Assert.assertNull(
                cachePatched.invoke(null, new Object[] {originalWithoutPatched, null}));
        Assert.assertNull(getCached.invoke(null, originalWithoutPatched));
    }

    @Test
    public void queuedOriginalIdentityRemovesItsCacheEntryAfterReferentClears()
            throws Exception {
        Method getCached = GboardEnglishUppercaseToggleRuntime.class.getMethod(
                "getCachedPatchedMetadata", Object.class);
        Method cachePatched = GboardEnglishUppercaseToggleRuntime.class.getMethod(
                "cachePatchedMetadata", Object.class, Object.class);
        Object original = new Object();
        Object patched = new Object();
        cachePatched.invoke(null, original, patched);

        Field stateField = GboardEnglishUppercaseToggleRuntime.class.getDeclaredField(
                "METADATA_IDENTITY_STATE");
        stateField.setAccessible(true);
        Object state = stateField.get(null);
        Field cacheField = state.getClass().getDeclaredField("patchedMetadataByOriginal");
        cacheField.setAccessible(true);
        Object weakIdentityMap = cacheField.get(state);
        Field entriesField = weakIdentityMap.getClass().getDeclaredField("entries");
        entriesField.setAccessible(true);
        Map<?, ?> entries = (Map<?, ?>) entriesField.get(weakIdentityMap);

        Reference<?> storedKey = null;
        for (Object candidate : entries.keySet()) {
            Reference<?> reference = (Reference<?>) candidate;
            if (reference.get() == original) {
                storedKey = reference;
                break;
            }
        }
        Assert.assertNotNull(storedKey);
        Assert.assertSame(patched, entries.get(storedKey));

        storedKey.clear();
        Assert.assertTrue(storedKey.equals(storedKey));
        Assert.assertTrue(storedKey.enqueue());
        getCached.invoke(null, new Object());

        Assert.assertFalse(entries.containsKey(storedKey));
    }

    private static void setApplicationContext(Context context) throws Exception {
        Field field = GboardEnglishUppercaseToggleRuntime.class
                .getDeclaredField("applicationContext");
        field.setAccessible(true);
        field.set(null, context);
    }

    private static final class EqualValue {
        private final String value;

        private EqualValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualValue equalValue && value.equals(equalValue.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    private static final class SharedPreferencesContext extends ContextWrapper {
        private final SharedPreferences preferences;

        private SharedPreferencesContext(SharedPreferences preferences) {
            super(null);
            this.preferences = preferences;
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return preferences;
        }
    }

    private static final class ThrowingSharedPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<String, Object>();

        @Override
        public Map<String, ?> getAll() {
            return Collections.unmodifiableMap(new HashMap<String, Object>(values));
        }

        @Override
        public String getString(String key, String defValue) {
            Object value = values.get(key);
            if (value == null) {
                return defValue;
            }
            if (!(value instanceof String)) {
                throw new ClassCastException(value.getClass().getName());
            }
            return (String) value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Set<String> getStringSet(String key, Set<String> defValues) {
            Object value = values.get(key);
            if (value == null) {
                return defValues;
            }
            if (!(value instanceof Set)) {
                throw new ClassCastException(value.getClass().getName());
            }
            return (Set<String>) value;
        }

        @Override
        public int getInt(String key, int defValue) {
            Object value = values.get(key);
            if (value == null) {
                return defValue;
            }
            if (!(value instanceof Number)) {
                throw new ClassCastException(value.getClass().getName());
            }
            return ((Number) value).intValue();
        }

        @Override
        public long getLong(String key, long defValue) {
            Object value = values.get(key);
            if (value == null) {
                return defValue;
            }
            if (!(value instanceof Number)) {
                throw new ClassCastException(value.getClass().getName());
            }
            return ((Number) value).longValue();
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object value = values.get(key);
            if (value == null) {
                return defValue;
            }
            if (!(value instanceof Number)) {
                throw new ClassCastException(value.getClass().getName());
            }
            return ((Number) value).floatValue();
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object value = values.get(key);
            if (value == null) {
                return defValue;
            }
            if (!(value instanceof Boolean)) {
                throw new ClassCastException(value.getClass().getName());
            }
            return ((Boolean) value).booleanValue();
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new Editor() {
                private final Map<String, Object> pending = new HashMap<String, Object>();

                @Override
                public Editor putString(String key, String value) {
                    pending.put(key, value);
                    return this;
                }

                @Override
                public Editor putStringSet(String key, Set<String> values) {
                    pending.put(key, values);
                    return this;
                }

                @Override
                public Editor putInt(String key, int value) {
                    pending.put(key, Integer.valueOf(value));
                    return this;
                }

                @Override
                public Editor putLong(String key, long value) {
                    pending.put(key, Long.valueOf(value));
                    return this;
                }

                @Override
                public Editor putFloat(String key, float value) {
                    pending.put(key, Float.valueOf(value));
                    return this;
                }

                @Override
                public Editor putBoolean(String key, boolean value) {
                    pending.put(key, Boolean.valueOf(value));
                    return this;
                }

                @Override
                public Editor remove(String key) {
                    pending.put(key, null);
                    return this;
                }

                @Override
                public Editor clear() {
                    values.clear();
                    pending.clear();
                    return this;
                }

                @Override
                public boolean commit() {
                    apply();
                    return true;
                }

                @Override
                public void apply() {
                    for (Map.Entry<String, Object> entry : pending.entrySet()) {
                        if (entry.getValue() == null) {
                            values.remove(entry.getKey());
                        } else {
                            values.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            };
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }
    }
}
