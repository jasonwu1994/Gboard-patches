package dev.jason.gboardpatches.extension.settingshomepage;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class GboardSettingsHomepageRuntimeTest {
    @Test
    public void autoResetsPreviouslyForcedNewAndLegacyStatesToOfficialUninitializedState()
            throws Exception {
        try (TargetEnvironment target = TargetEnvironment.exact()) {
            target.preferences.setMode(GboardSettingsHomepageSettings.MODE_AUTO);

            target.setState(2);
            apply(target.context);
            Assert.assertEquals(1, target.state());

            target.setState(3);
            apply(target.context);
            Assert.assertEquals(1, target.state());
        }
    }

    @Test
    public void legacyWritesThreeAndRepeatedLaunchIsAnIdempotentNoWrite() throws Exception {
        try (TargetEnvironment target = TargetEnvironment.exact()) {
            target.preferences.setMode(GboardSettingsHomepageSettings.MODE_FORCE_LEGACY);
            target.setState(1);

            apply(target.context);
            Assert.assertEquals(3, target.state());

            Method writer = GboardSettingsHomepageRuntime.class.getDeclaredMethod(
                    "writePolicyStateIfNeeded", Field.class, Integer.TYPE);
            writer.setAccessible(true);
            Assert.assertEquals(Boolean.FALSE, writer.invoke(null, target.stateField, 3));
            Assert.assertEquals(3, target.state());
            Assert.assertEquals(Boolean.TRUE, writer.invoke(null, target.stateField, 2));
            Assert.assertEquals(2, target.state());
        }
    }

    @Test
    public void safeguardedNewWritesTwoOnlyForAHealthyLaunch() throws Exception {
        try (TargetEnvironment target = TargetEnvironment.exact()) {
            target.preferences.setHealthyForceNew();
            target.setState(3);

            apply(target.context);

            Assert.assertEquals(2, target.state());
            Assert.assertTrue(target.preferences.booleanValue(
                    GboardSettingsHomepageSettings.PREF_KEY_FORCE_NEW_GUARD_PENDING));
        }
    }

    @Test
    public void safeguardedNewRecoveryAndTimeoutWriteLegacy() throws Exception {
        try (TargetEnvironment recovery = TargetEnvironment.exact();
                TargetEnvironment timeout = TargetEnvironment.exact()) {
            recovery.preferences.setHealthyForceNew();
            recovery.preferences.edit()
                    .putBoolean(
                            GboardSettingsHomepageSettings.PREF_KEY_FORCE_NEW_GUARD_RECOVERED,
                            true)
                    .apply();
            recovery.setState(2);

            apply(recovery.context);

            Assert.assertEquals(3, recovery.state());

            timeout.preferences.setHealthyForceNew();
            timeout.preferences.edit()
                    .putLong(
                            GboardSettingsHomepageSettings.PREF_KEY_FORCE_NEW_TRIAL_EXPIRES_AT,
                            System.currentTimeMillis() - 1L)
                    .apply();
            timeout.setState(2);

            apply(timeout.context);

            Assert.assertEquals(3, timeout.state());
            Assert.assertTrue(timeout.preferences.booleanValue(
                    GboardSettingsHomepageSettings.PREF_KEY_FORCE_NEW_TRIAL_EXPIRED));
        }
    }

    @Test
    public void settingsAndResolverExceptionsRecoverAndWriteLegacyWhenBindingExists()
            throws Exception {
        try (TargetEnvironment settingsFailure = TargetEnvironment.exact();
                TargetEnvironment resolverFailure = TargetEnvironment.exact()) {
            settingsFailure.preferences.setMode(GboardSettingsHomepageSettings.MODE_FORCE_NEW);
            settingsFailure.preferences.throwOnGetString = true;
            settingsFailure.setState(2);

            apply(settingsFailure.context);

            Assert.assertEquals(3, settingsFailure.state());

            resolverFailure.preferences.setHealthyForceNew();
            resolverFailure.preferences.throwOnGetAll = true;
            resolverFailure.setState(2);

            apply(resolverFailure.context);

            Assert.assertEquals(3, resolverFailure.state());
        }
    }

    @Test
    public void modeReadFailureLatchesRecoveryUntilAnExplicitNewSelectionRetries()
            throws Exception {
        try (TargetEnvironment target = TargetEnvironment.exact()) {
            target.preferences.setHealthyForceNew();
            target.preferences.throwOnGetString = true;
            target.setState(2);

            apply(target.context);

            Assert.assertEquals(3, target.state());
            Assert.assertTrue(target.preferences.booleanValue(
                    GboardSettingsHomepageSettings.PREF_KEY_FORCE_NEW_GUARD_RECOVERED));

            target.preferences.throwOnGetString = false;
            apply(target.context);
            Assert.assertEquals(3, target.state());

            GboardSettingsHomepageSettings.writeMode(
                    target.context,
                    GboardSettingsHomepageSettings.MODE_FORCE_NEW);
            Assert.assertFalse(target.preferences.booleanValue(
                    GboardSettingsHomepageSettings.PREF_KEY_FORCE_NEW_GUARD_RECOVERED));
            apply(target.context);
            Assert.assertEquals(2, target.state());
        }
    }

    @Test
    public void applicationContextAcquisitionFailureLatchesRecoveryUntilExplicitRetry()
            throws Exception {
        try (TargetEnvironment target = TargetEnvironment.exact()) {
            target.context.throwOnGetApplicationContextOnce = true;
            assertAcquisitionFailureLatchesRecoveryUntilExplicitRetry(target);
        }
    }

    @Test
    public void sharedPreferencesAcquisitionFailureLatchesRecoveryUntilExplicitRetry()
            throws Exception {
        try (TargetEnvironment target = TargetEnvironment.exact()) {
            target.context.throwOnGetSharedPreferencesOnce = true;
            assertAcquisitionFailureLatchesRecoveryUntilExplicitRetry(target);
        }
    }

    @Test
    public void invalidReceiverAndMissingBindingAreNoOps() throws Exception {
        try (TargetEnvironment target = TargetEnvironment.exact();
                TargetEnvironment missing = TargetEnvironment.missing();
                TargetEnvironment wrongLoader = TargetEnvironment.parentLoaded()) {
            target.setState(2);
            wrongLoader.setState(2);

            apply(null);
            apply(new Object());
            apply(missing.context);
            apply(wrongLoader.context);

            Assert.assertEquals(2, target.state());
            Assert.assertEquals(2, wrongLoader.state());
        }
    }

    @Test
    public void exactReflectionShapeRejectsWrongClassFieldModifiersTypesAndInheritance()
            throws Exception {
        assertRejected("dga");
        assertRejected("dgb");
        assertRejected("dgc");
        assertRejected("dgd");
        assertRejected("dge");
        assertRejected("dgf");
    }

    @Test
    public void childLoaderBindingsStayIndependentAndNeverReuseOneGlobalField() throws Exception {
        try (TargetEnvironment first = TargetEnvironment.exact();
                TargetEnvironment second = TargetEnvironment.exact()) {
            Assert.assertNotSame(first.loader, second.loader);
            Assert.assertNotSame(first.stateField, second.stateField);
            first.preferences.setMode(GboardSettingsHomepageSettings.MODE_AUTO);
            second.preferences.setMode(GboardSettingsHomepageSettings.MODE_FORCE_LEGACY);
            first.setState(2);
            second.setState(1);

            apply(first.context);
            apply(second.context);

            Assert.assertEquals(1, first.state());
            Assert.assertEquals(3, second.state());
        }
    }

    @Test
    public void modeIsResolvedOncePerEntryCallRatherThanPerGlobalSelectorConsumer()
            throws Exception {
        try (TargetEnvironment target = TargetEnvironment.exact()) {
            target.preferences.setMode(GboardSettingsHomepageSettings.MODE_FORCE_LEGACY);
            target.preferences.modeReadCount = 0;

            apply(target.context);

            Assert.assertEquals(1, target.preferences.modeReadCount);
            for (Method method : GboardSettingsHomepageRuntime.class.getDeclaredMethods()) {
                Assert.assertNotEquals("shouldUseNewSettingsStyle", method.getName());
            }
        }
    }

    @Test
    public void cacheUsesWeakKeysAndWeakFieldValues() throws Exception {
        Field cacheField = GboardSettingsHomepageRuntime.class.getDeclaredField(
                "POLICY_FIELDS_BY_CLASS_LOADER");
        cacheField.setAccessible(true);
        Object cache = cacheField.get(null);
        Assert.assertTrue(cache instanceof java.util.WeakHashMap);

        try (TargetEnvironment target = TargetEnvironment.exact()) {
            apply(target.context);
            @SuppressWarnings("unchecked")
            Map<ClassLoader, ?> typedCache = (Map<ClassLoader, ?>) cache;
            Object cached = typedCache.get(target.loader);
            Assert.assertTrue(cached instanceof WeakReference<?>);
            Assert.assertEquals(target.stateField, ((WeakReference<?>) cached).get());
        }
    }

    private static void apply(Object receiver) throws Exception {
        Method entry = GboardSettingsHomepageRuntime.class.getDeclaredMethod(
                "applySettingsHomepagePolicy", Object.class);
        entry.invoke(null, receiver);
    }

    private static void assertAcquisitionFailureLatchesRecoveryUntilExplicitRetry(
            TargetEnvironment target) throws Exception {
        target.preferences.setHealthyForceNew();
        target.setState(2);

        apply(target.context);

        Assert.assertEquals(3, target.state());
        Assert.assertTrue(target.preferences.booleanValue(
                GboardSettingsHomepageSettings.PREF_KEY_FORCE_NEW_GUARD_RECOVERED));

        apply(target.context);
        Assert.assertEquals(3, target.state());

        GboardSettingsHomepageSettings.writeMode(
                target.context,
                GboardSettingsHomepageSettings.MODE_FORCE_NEW);
        Assert.assertFalse(target.preferences.booleanValue(
                GboardSettingsHomepageSettings.PREF_KEY_FORCE_NEW_GUARD_RECOVERED));
        apply(target.context);
        Assert.assertEquals(2, target.state());
    }

    private static void assertRejected(String fixtureClassName) throws Exception {
        try (TargetEnvironment target = TargetEnvironment.fixture(fixtureClassName)) {
            target.preferences.setMode(GboardSettingsHomepageSettings.MODE_FORCE_LEGACY);
            Object before = target.declaredOrInheritedState();

            apply(target.context);

            Assert.assertEquals(before, target.declaredOrInheritedState());
        }
    }

    private static final class TargetEnvironment implements AutoCloseable {
        private final ClassLoader loader;
        private final Class<?> policyClass;
        private final Field stateField;
        private final InMemorySharedPreferences preferences;
        private final LoaderContext context;

        private TargetEnvironment(ClassLoader loader, Class<?> policyClass,
                Field stateField) {
            this.loader = loader;
            this.policyClass = policyClass;
            this.stateField = stateField;
            this.preferences = new InMemorySharedPreferences();
            this.context = new LoaderContext(loader, preferences);
        }

        static TargetEnvironment exact() throws Exception {
            return fixture("dto");
        }

        static TargetEnvironment missing() throws Exception {
            return new TargetEnvironment(new FixtureClassLoader(null), null, null);
        }

        static TargetEnvironment parentLoaded() throws Exception {
            ClassLoader loader = new ClassLoader(
                    GboardSettingsHomepageRuntimeTest.class.getClassLoader()) {
            };
            Class<?> policyClass = Class.forName("dto", true, loader);
            Field stateField = policyClass.getDeclaredField("a");
            stateField.setAccessible(true);
            return new TargetEnvironment(loader, policyClass, stateField);
        }

        static TargetEnvironment fixture(String fixtureClassName) throws Exception {
            FixtureClassLoader loader = new FixtureClassLoader(fixtureClassName);
            Class<?> policyClass = Class.forName("dto", true, loader);
            Field stateField = findStateField(policyClass);
            if (stateField != null) {
                stateField.setAccessible(true);
            }
            return new TargetEnvironment(loader, policyClass, stateField);
        }

        private static Field findStateField(Class<?> policyClass) {
            try {
                return policyClass.getDeclaredField("a");
            } catch (NoSuchFieldException ignored) {
                Class<?> parent = policyClass.getSuperclass();
                if (parent == null || parent == Object.class) {
                    return null;
                }
                try {
                    return parent.getDeclaredField("a");
                } catch (NoSuchFieldException secondIgnored) {
                    return null;
                }
            }
        }

        void setState(int state) throws Exception {
            Assert.assertNotNull(stateField);
            Assert.assertTrue(Modifier.isStatic(stateField.getModifiers()));
            stateField.setInt(null, state);
        }

        int state() throws Exception {
            Assert.assertNotNull(stateField);
            return stateField.getInt(null);
        }

        Object declaredOrInheritedState() throws Exception {
            if (stateField == null) {
                return null;
            }
            if (!Modifier.isStatic(stateField.getModifiers())) {
                Object receiver = policyClass.getDeclaredConstructor().newInstance();
                return stateField.get(receiver);
            }
            return stateField.get(null);
        }

        @Override
        public void close() {
        }
    }

    private static final class FixtureClassLoader extends ClassLoader {
        private final String fixtureClassName;

        private FixtureClassLoader(String fixtureClassName) {
            super(GboardSettingsHomepageRuntimeTest.class.getClassLoader());
            this.fixtureClassName = fixtureClassName;
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!"dto".equals(name)) {
                return super.loadClass(name, resolve);
            }
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) {
                return loaded;
            }
            if (fixtureClassName == null) {
                throw new ClassNotFoundException(name);
            }
            byte[] bytes;
            try {
                bytes = readFixtureClass(fixtureClassName);
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
            // Fixture names are three bytes so rewriting to dog preserves class-file layout.
            rewriteAscii(bytes, fixtureClassName, "dto");
            Class<?> defined = defineClass(name, bytes, 0, bytes.length);
            if (resolve) {
                resolveClass(defined);
            }
            return defined;
        }

        private static byte[] readFixtureClass(String name) throws IOException {
            String resourceName = name + ".class";
            InputStream input = GboardSettingsHomepageRuntimeTest.class
                    .getClassLoader()
                    .getResourceAsStream(resourceName);
            if (input == null) {
                throw new IOException("Missing compiled fixture " + resourceName);
            }
            try (InputStream stream = input; ByteArrayOutputStream output =
                    new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        }

        private static void rewriteAscii(byte[] bytes, String from, String to) {
            Assert.assertEquals(from.length(), to.length());
            byte[] fromBytes = from.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            byte[] toBytes = to.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            int replacements = 0;
            for (int index = 0; index <= bytes.length - fromBytes.length; index++) {
                boolean matches = true;
                for (int offset = 0; offset < fromBytes.length; offset++) {
                    if (bytes[index + offset] != fromBytes[offset]) {
                        matches = false;
                        break;
                    }
                }
                if (!matches) {
                    continue;
                }
                System.arraycopy(toBytes, 0, bytes, index, toBytes.length);
                replacements++;
                index += fromBytes.length - 1;
            }
            Assert.assertTrue("No class-name bytes found for " + from, replacements > 0);
        }
    }

    private static final class LoaderContext extends ContextWrapper {
        private final ClassLoader loader;
        private final SharedPreferences preferences;
        private boolean throwOnGetApplicationContextOnce;
        private boolean throwOnGetSharedPreferencesOnce;

        private LoaderContext(ClassLoader loader, SharedPreferences preferences) {
            super(null);
            this.loader = loader;
            this.preferences = preferences;
        }

        @Override
        public Context getApplicationContext() {
            if (throwOnGetApplicationContextOnce) {
                throwOnGetApplicationContextOnce = false;
                throw new IllegalStateException("application context acquisition failure");
            }
            return this;
        }

        @Override
        public ClassLoader getClassLoader() {
            return loader;
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            if (throwOnGetSharedPreferencesOnce) {
                throwOnGetSharedPreferencesOnce = false;
                throw new IllegalStateException("SharedPreferences acquisition failure");
            }
            Assert.assertEquals(GboardSettingsHomepageSettings.PREF_FILE, name);
            Assert.assertEquals(Context.MODE_PRIVATE, mode);
            return preferences;
        }
    }

    private static final class InMemorySharedPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();
        private int modeReadCount;
        private boolean throwOnGetString;
        private boolean throwOnGetAll;

        void setMode(String mode) {
            edit().putString(GboardSettingsHomepageSettings.PREF_KEY_MODE, mode).apply();
        }

        void setHealthyForceNew() {
            edit()
                    .putString(
                            GboardSettingsHomepageSettings.PREF_KEY_MODE,
                            GboardSettingsHomepageSettings.MODE_FORCE_NEW)
                    .putBoolean(
                            GboardSettingsHomepageSettings.PREF_KEY_FORCE_NEW_GUARD_PENDING,
                            false)
                    .putLong(
                            GboardSettingsHomepageSettings.PREF_KEY_FORCE_NEW_GUARD_PENDING_AT,
                            0L)
                    .putBoolean(
                            GboardSettingsHomepageSettings.PREF_KEY_FORCE_NEW_GUARD_RECOVERED,
                            false)
                    .putBoolean(
                            GboardSettingsHomepageSettings.PREF_KEY_FORCE_NEW_TRIAL_ARMED,
                            true)
                    .putLong(
                            GboardSettingsHomepageSettings.PREF_KEY_FORCE_NEW_TRIAL_EXPIRES_AT,
                            System.currentTimeMillis() + 10_000L)
                    .putBoolean(
                            GboardSettingsHomepageSettings.PREF_KEY_FORCE_NEW_TRIAL_EXPIRED,
                            false)
                    .apply();
        }

        boolean booleanValue(String key) {
            Object value = values.get(key);
            return value instanceof Boolean && (Boolean) value;
        }

        @Override
        public Map<String, ?> getAll() {
            if (throwOnGetAll) {
                throw new IllegalStateException("resolver failure");
            }
            return Collections.unmodifiableMap(new HashMap<>(values));
        }

        @Override
        public String getString(String key, String defValue) {
            if (throwOnGetString) {
                throw new IllegalStateException("settings failure");
            }
            if (GboardSettingsHomepageSettings.PREF_KEY_MODE.equals(key)) {
                modeReadCount++;
            }
            Object value = values.get(key);
            return value instanceof String ? (String) value : defValue;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Set<String> getStringSet(String key, Set<String> defValues) {
            Object value = values.get(key);
            return value instanceof Set ? (Set<String>) value : defValues;
        }

        @Override
        public int getInt(String key, int defValue) {
            Object value = values.get(key);
            return value instanceof Number ? ((Number) value).intValue() : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object value = values.get(key);
            return value instanceof Number ? ((Number) value).longValue() : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object value = values.get(key);
            return value instanceof Number ? ((Number) value).floatValue() : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defValue;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new Editor() {
                private final Map<String, Object> pending = new HashMap<>();
                private boolean clearRequested;

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
                    pending.put(key, value);
                    return this;
                }

                @Override
                public Editor putLong(String key, long value) {
                    pending.put(key, value);
                    return this;
                }

                @Override
                public Editor putFloat(String key, float value) {
                    pending.put(key, value);
                    return this;
                }

                @Override
                public Editor putBoolean(String key, boolean value) {
                    pending.put(key, value);
                    return this;
                }

                @Override
                public Editor remove(String key) {
                    pending.put(key, null);
                    return this;
                }

                @Override
                public Editor clear() {
                    clearRequested = true;
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
                    if (clearRequested) {
                        values.clear();
                    }
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
