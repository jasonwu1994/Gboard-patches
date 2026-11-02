package dev.jason.gboardpatches.extension.keyboard;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public final class GboardLatinGlobeKeyIgnoreIntervalRuntimeTest {
    private Context application;
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        application = RuntimeEnvironment.getApplication();
        preferences = GboardLatinGlobeKeyIgnoreIntervalSettings.preferences(application);
        Assert.assertNotNull(preferences);
        preferences.edit().clear().commit();
        GboardLatinGlobeKeyIgnoreIntervalSettings.ensureDefaults(preferences);
    }

    @Test
    public void enabledOverrideSupportsAllBoundaryValuesAndOnlyTouchesExactH() throws Exception {
        try (CompiledTarget target = compileTarget(TargetShape.VALID)) {
            TargetContext context = new TargetContext(application, target.loader, null, false);
            for (int interval : new int[] { 0, 250, 500, 1000 }) {
                setSettings(true, interval);
                Object params = target.newInstance();
                writeFloat(params, "h", 777f);

                Object result = applyWithContext(params, context);

                Assert.assertSame(params, result);
                Assert.assertEquals((float) interval, readFloat(params, "h"), 0f);
                Assert.assertEquals(321f, readFloat(params, "o"), 0f);
                Assert.assertEquals(-1f, readFloat(params, "q"), 0f);
                Assert.assertEquals(-2f, readFloat(params, "r"), 0f);
            }
        }
    }

    @Test
    public void disabledPersistsIntervalThenReenableReusesItAndFiveHundredResets() throws Exception {
        try (CompiledTarget target = compileTarget(TargetShape.VALID)) {
            TargetContext context = new TargetContext(application, target.loader, null, false);
            Assert.assertTrue(
                    GboardLatinGlobeKeyIgnoreIntervalSettings.writeIntervalMs(preferences, 250));
            Assert.assertTrue(
                    GboardLatinGlobeKeyIgnoreIntervalSettings.writeEnabled(preferences, false));
            Object disabled = target.newInstance();
            writeFloat(disabled, "h", 777f);

            Assert.assertSame(disabled, applyWithContext(disabled, context));
            Assert.assertEquals(777f, readFloat(disabled, "h"), 0f);
            Assert.assertEquals(
                    250,
                    GboardLatinGlobeKeyIgnoreIntervalSettings.readIntervalMs(preferences));

            Assert.assertTrue(
                    GboardLatinGlobeKeyIgnoreIntervalSettings.writeEnabled(preferences, true));
            Object reenabled = target.newInstance();
            Assert.assertSame(reenabled, applyWithContext(reenabled, context));
            Assert.assertEquals(250f, readFloat(reenabled, "h"), 0f);
            Assert.assertSame(reenabled, applyWithContext(reenabled, context));
            Assert.assertEquals(250f, readFloat(reenabled, "h"), 0f);

            Assert.assertTrue(
                    GboardLatinGlobeKeyIgnoreIntervalSettings.writeIntervalMs(preferences, 500));
            Assert.assertSame(reenabled, applyWithContext(reenabled, context));
            Assert.assertEquals(500f, readFloat(reenabled, "h"), 0f);
        }
    }

    @Test
    public void exactBindingsAreIsolatedPerTargetClassLoader() throws Exception {
        try (CompiledTarget first = compileTarget(TargetShape.VALID);
                CompiledTarget second = compileTarget(TargetShape.VALID)) {
            TargetContext firstContext =
                    new TargetContext(application, first.loader, null, false);
            TargetContext secondContext =
                    new TargetContext(application, second.loader, null, false);
            Object firstParams = first.newInstance();
            Object secondParams = second.newInstance();

            setSettings(true, 250);
            Assert.assertSame(secondParams, applyWithContext(secondParams, firstContext));
            Assert.assertEquals(500f, readFloat(secondParams, "h"), 0f);
            Assert.assertSame(firstParams, applyWithContext(firstParams, firstContext));
            Assert.assertEquals(250f, readFloat(firstParams, "h"), 0f);

            setSettings(true, 750);
            Assert.assertSame(secondParams, applyWithContext(secondParams, secondContext));
            Assert.assertEquals(750f, readFloat(secondParams, "h"), 0f);
            Assert.assertEquals(250f, readFloat(firstParams, "h"), 0f);
        }
    }

    @Test
    public void wrongClassAndEveryWrongHShapeFailClosed() throws Exception {
        assertRejectedShape(TargetShape.BOXED_FLOAT, 123f);
        assertRejectedShape(TargetShape.DOUBLE, 123d);
        assertRejectedShape(TargetShape.PRIVATE, 123f);
        assertRejectedShape(TargetShape.STATIC, 123f);
        assertRejectedShape(TargetShape.FINAL_FIELD, 123f);
        assertRejectedShape(TargetShape.INHERITED, 123f);
        assertRejectedShape(TargetShape.NON_FINAL_CLASS, 123f);

        try (CompiledTarget target = compileTarget(TargetShape.VALID)) {
            TargetContext context = new TargetContext(application, target.loader, null, false);
            Object ordinary = new Object();
            setSettings(true, 250);
            Assert.assertSame(ordinary, applyWithContext(ordinary, context));
        }
    }

    @Test
    public void nullMissingAndThrowingSettingsFailClosedWithoutMutation() throws Exception {
        try (CompiledTarget target = compileTarget(TargetShape.VALID)) {
            Object params = target.newInstance();
            writeFloat(params, "h", 777f);
            TargetContext valid = new TargetContext(application, target.loader, null, false);

            Assert.assertNull(applyWithContext(null, valid));
            Assert.assertSame(params, applyWithContext(params, null));
            Assert.assertEquals(777f, readFloat(params, "h"), 0f);

            TargetContext throwingLookup =
                    new TargetContext(application, target.loader, null, true);
            Assert.assertSame(params, applyWithContext(params, throwingLookup));
            Assert.assertEquals(777f, readFloat(params, "h"), 0f);

            TargetContext throwingReads =
                    new TargetContext(application, target.loader, new ThrowingPreferences(), false);
            Assert.assertSame(params, applyWithContext(params, throwingReads));
            Assert.assertEquals(777f, readFloat(params, "h"), 0f);

            TargetContext missingSettings =
                    new TargetContext(application, target.loader, null, false, true);
            Assert.assertSame(params, applyWithContext(params, missingSettings));
            Assert.assertEquals(777f, readFloat(params, "h"), 0f);

            ClassLoader missingTargetLoader =
                    new ClassLoader(GboardLatinGlobeKeyIgnoreIntervalRuntimeTest.class
                            .getClassLoader()) { };
            TargetContext missingTarget =
                    new TargetContext(application, missingTargetLoader, null, false);
            setSettings(true, 250);
            Assert.assertSame(params, applyWithContext(params, missingTarget));
            Assert.assertEquals(777f, readFloat(params, "h"), 0f);
        }
    }

    @Test
    public void malformedStoredTypesDefaultToDisabledAndNeverApplyTrustedLookingValues()
            throws Exception {
        try (CompiledTarget target = compileTarget(TargetShape.VALID)) {
            TargetContext context = new TargetContext(application, target.loader, null, false);
            Object params = target.newInstance();
            writeFloat(params, "h", 777f);

            preferences.edit()
                    .clear()
                    .putString(GboardLatinGlobeKeyIgnoreIntervalSettings.PREF_KEY_ENABLED, "true")
                    .putInt(GboardLatinGlobeKeyIgnoreIntervalSettings.PREF_KEY_INTERVAL_MS, 250)
                    .commit();
            Assert.assertSame(params, applyWithContext(params, context));
            Assert.assertEquals(777f, readFloat(params, "h"), 0f);

            preferences.edit()
                    .clear()
                    .putBoolean(GboardLatinGlobeKeyIgnoreIntervalSettings.PREF_KEY_ENABLED, true)
                    .putString(
                            GboardLatinGlobeKeyIgnoreIntervalSettings.PREF_KEY_INTERVAL_MS,
                            "250")
                    .commit();
            Assert.assertSame(params, applyWithContext(params, context));
            Assert.assertEquals(777f, readFloat(params, "h"), 0f);
        }
    }

    private void assertRejectedShape(TargetShape shape, double initialValue) throws Exception {
        try (CompiledTarget target = compileTarget(shape)) {
            TargetContext context = new TargetContext(application, target.loader, null, false);
            Object params = target.newInstance();
            setSettings(true, 250);

            Assert.assertSame(params, applyWithContext(params, context));
            Assert.assertEquals(initialValue, readNumber(params, "h"), 0d);
        }
    }

    private Object applyWithContext(Object params, Context context) throws Exception {
        Method method = GboardLatinGlobeKeyIgnoreIntervalRuntime.class.getDeclaredMethod(
                "applyOverride",
                Object.class,
                Context.class);
        method.setAccessible(true);
        return method.invoke(null, params, context);
    }

    private void setSettings(boolean enabled, int interval) {
        Assert.assertTrue(
                GboardLatinGlobeKeyIgnoreIntervalSettings.writeIntervalMs(preferences, interval));
        Assert.assertTrue(
                GboardLatinGlobeKeyIgnoreIntervalSettings.writeEnabled(preferences, enabled));
    }

    private static float readFloat(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getFloat(target);
    }

    private static double readNumber(Object target, String name) throws Exception {
        Field field;
        Class<?> current = target.getClass();
        while (true) {
            try {
                field = current.getDeclaredField(name);
                break;
            } catch (NoSuchFieldException missing) {
                current = current.getSuperclass();
                if (current == null) {
                    throw missing;
                }
            }
        }
        field.setAccessible(true);
        Object value = field.get(target);
        return value instanceof Number ? ((Number) value).doubleValue() : Double.NaN;
    }

    private static void writeFloat(Object target, String name, float value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setFloat(target, value);
    }

    private static CompiledTarget compileTarget(TargetShape shape) {
        Map<String, byte[]> definitions = new HashMap<String, byte[]>();
        if (shape == TargetShape.INHERITED) {
            definitions.put("latin.globe.parent", parentClassBytes());
        }
        definitions.put("yrt", targetClassBytes(shape));
        return new CompiledTarget(new TargetClassLoader(
                GboardLatinGlobeKeyIgnoreIntervalRuntimeTest.class.getClassLoader(),
                definitions));
    }

    private static byte[] targetClassBytes(TargetShape shape) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        int classAccess = Opcodes.ACC_PUBLIC;
        if (shape != TargetShape.NON_FINAL_CLASS) {
            classAccess |= Opcodes.ACC_FINAL;
        }
        String superName = shape == TargetShape.INHERITED
                ? "latin/globe/parent"
                : "java/lang/Object";
        writer.visit(Opcodes.V1_8, classAccess, "yrt", null, superName, null);

        if (shape != TargetShape.INHERITED) {
            int fieldAccess = Opcodes.ACC_PUBLIC;
            String descriptor = "F";
            if (shape == TargetShape.PRIVATE) {
                fieldAccess = Opcodes.ACC_PRIVATE;
            } else if (shape == TargetShape.STATIC) {
                fieldAccess |= Opcodes.ACC_STATIC;
            } else if (shape == TargetShape.FINAL_FIELD) {
                fieldAccess |= Opcodes.ACC_FINAL;
            } else if (shape == TargetShape.BOXED_FLOAT) {
                descriptor = "Ljava/lang/Float;";
            } else if (shape == TargetShape.DOUBLE) {
                descriptor = "D";
            }
            writer.visitField(fieldAccess, "h", descriptor, null, null).visitEnd();
        }
        if (shape == TargetShape.VALID) {
            writer.visitField(Opcodes.ACC_PUBLIC, "o", "F", null, null).visitEnd();
            writer.visitField(Opcodes.ACC_PUBLIC, "q", "F", null, null).visitEnd();
            writer.visitField(Opcodes.ACC_PUBLIC, "r", "F", null, null).visitEnd();
        }

        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "<init>",
                "()V",
                null,
                null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                superName,
                "<init>",
                "()V",
                false);
        if (shape != TargetShape.INHERITED && shape != TargetShape.STATIC) {
            emitInstanceValue(constructor, shape, "h",
                    shape == TargetShape.VALID ? 500d : 123d);
        }
        if (shape == TargetShape.VALID) {
            emitFloatField(constructor, "o", 321f);
            emitFloatField(constructor, "q", -1f);
            emitFloatField(constructor, "r", -2f);
        }
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();

        if (shape == TargetShape.STATIC) {
            MethodVisitor initializer = writer.visitMethod(
                    Opcodes.ACC_STATIC,
                    "<clinit>",
                    "()V",
                    null,
                    null);
            initializer.visitCode();
            initializer.visitLdcInsn(Float.valueOf(123f));
            initializer.visitFieldInsn(Opcodes.PUTSTATIC, "yrt", "h", "F");
            initializer.visitInsn(Opcodes.RETURN);
            initializer.visitMaxs(0, 0);
            initializer.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] parentClassBytes() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                "latin/globe/parent",
                null,
                "java/lang/Object",
                null);
        writer.visitField(Opcodes.ACC_PUBLIC, "h", "F", null, null).visitEnd();
        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "<init>",
                "()V",
                null,
                null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/Object",
                "<init>",
                "()V",
                false);
        emitFloatField(constructor, "latin/globe/parent", "h", 123f);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void emitInstanceValue(MethodVisitor visitor,
            TargetShape shape,
            String fieldName,
            double value) {
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        if (shape == TargetShape.BOXED_FLOAT) {
            visitor.visitLdcInsn(Float.valueOf((float) value));
            visitor.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "java/lang/Float",
                    "valueOf",
                    "(F)Ljava/lang/Float;",
                    false);
            visitor.visitFieldInsn(
                    Opcodes.PUTFIELD,
                    "yrt",
                    fieldName,
                    "Ljava/lang/Float;");
        } else if (shape == TargetShape.DOUBLE) {
            visitor.visitLdcInsn(Double.valueOf(value));
            visitor.visitFieldInsn(Opcodes.PUTFIELD, "yrt", fieldName, "D");
        } else {
            visitor.visitLdcInsn(Float.valueOf((float) value));
            visitor.visitFieldInsn(Opcodes.PUTFIELD, "yrt", fieldName, "F");
        }
    }

    private static void emitFloatField(MethodVisitor visitor, String fieldName, float value) {
        emitFloatField(visitor, "yrt", fieldName, value);
    }

    private static void emitFloatField(MethodVisitor visitor,
            String owner,
            String fieldName,
            float value) {
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitLdcInsn(Float.valueOf(value));
        visitor.visitFieldInsn(Opcodes.PUTFIELD, owner, fieldName, "F");
    }

    private enum TargetShape {
        VALID,
        BOXED_FLOAT,
        DOUBLE,
        PRIVATE,
        STATIC,
        FINAL_FIELD,
        INHERITED,
        NON_FINAL_CLASS
    }

    private static final class CompiledTarget implements AutoCloseable {
        final TargetClassLoader loader;

        CompiledTarget(TargetClassLoader loader) {
            this.loader = loader;
        }

        Object newInstance() throws Exception {
            return loader.loadClass("yrt").getDeclaredConstructor().newInstance();
        }

        @Override
        public void close() {
        }
    }

    private static final class TargetClassLoader extends ClassLoader {
        private final Map<String, byte[]> definitions;

        TargetClassLoader(ClassLoader parent, Map<String, byte[]> definitions) {
            super(parent);
            this.definitions = definitions;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (this) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    byte[] definition = definitions.get(name);
                    loaded = definition != null
                            ? defineClass(name, definition, 0, definition.length)
                            : super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }

    private static final class TargetContext extends ContextWrapper {
        private final ClassLoader targetLoader;
        private final SharedPreferences overridePreferences;
        private final boolean throwOnLookup;
        private final boolean returnNullOnLookup;

        TargetContext(Context base,
                ClassLoader targetLoader,
                SharedPreferences overridePreferences,
                boolean throwOnLookup) {
            this(base, targetLoader, overridePreferences, throwOnLookup, false);
        }

        TargetContext(Context base,
                ClassLoader targetLoader,
                SharedPreferences overridePreferences,
                boolean throwOnLookup,
                boolean returnNullOnLookup) {
            super(base);
            this.targetLoader = targetLoader;
            this.overridePreferences = overridePreferences;
            this.throwOnLookup = throwOnLookup;
            this.returnNullOnLookup = returnNullOnLookup;
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public ClassLoader getClassLoader() {
            return targetLoader;
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            if (throwOnLookup) {
                throw new IllegalStateException("settings lookup failure");
            }
            if (returnNullOnLookup) {
                return null;
            }
            return overridePreferences != null
                    ? overridePreferences
                    : super.getSharedPreferences(name, mode);
        }
    }

    private static final class ThrowingPreferences implements SharedPreferences {
        private RuntimeException failure() {
            return new IllegalStateException("settings read failure");
        }

        @Override public Map<String, ?> getAll() { throw failure(); }
        @Override public String getString(String key, String defValue) { throw failure(); }
        @Override public Set<String> getStringSet(String key, Set<String> defValues) {
            throw failure();
        }
        @Override public int getInt(String key, int defValue) { throw failure(); }
        @Override public long getLong(String key, long defValue) { throw failure(); }
        @Override public float getFloat(String key, float defValue) { throw failure(); }
        @Override public boolean getBoolean(String key, boolean defValue) { throw failure(); }
        @Override public boolean contains(String key) { throw failure(); }
        @Override public Editor edit() { throw failure(); }
        @Override public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) { }
    }
}
