package dev.jason.gboardpatches.extension.zhuyinslide;

import android.content.Context;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GboardZhuyinSlideTargetFixture {
    private static final String SOFT_KEY_VIEW_NAME =
            "com.google.android.libraries.inputmethod.widgets.SoftKeyView";
    private static final String SOFT_KEY_VIEW_INTERNAL_NAME =
            "com/google/android/libraries/inputmethod/widgets/SoftKeyView";
    private static final String METADATA_NAME =
            "com.google.android.libraries.inputmethod.metadata.SoftKeyDef";
    private static final String METADATA_INTERNAL_NAME =
            "com/google/android/libraries/inputmethod/metadata/SoftKeyDef";
    private static final String POINTER_TRACKER_NAME = "qin";
    private static final Map<String, String> TARGET_CLASS_NAMES = targetClassNames();
    private static final FixtureClassLoader CLASS_LOADER = new FixtureClassLoader(
            GboardZhuyinSlideTargetFixture.class.getClassLoader(),
            buildSoftKeyViewClass(), buildPointerTrackerClass());
    private static final Class<?> SOFT_KEY_VIEW_CLASS = loadClass(SOFT_KEY_VIEW_NAME);
    private static final Class<?> METADATA_CLASS = loadClass(METADATA_NAME);
    private static final Class<?> POINTER_TRACKER_CLASS = loadClass(POINTER_TRACKER_NAME);

    private GboardZhuyinSlideTargetFixture() {
    }

    public static ClassLoader classLoader() {
        return CLASS_LOADER;
    }

    public static Object newSoftKeyView(Context context) throws Exception {
        return SOFT_KEY_VIEW_CLASS.getConstructor(Context.class).newInstance(context);
    }

    public static Object newPointerTracker() throws Exception {
        return POINTER_TRACKER_CLASS.getConstructor().newInstance();
    }

    public static Object metadata(int keyId, String primaryLabel, String pressText,
            String[] longPressTokens, String nativeSlideUp, String nativeSlideDown)
            throws Exception {
        return METADATA_CLASS.getMethod(
                "key", int.class, String.class, String.class, String[].class,
                String.class, String.class)
                .invoke(null, Integer.valueOf(keyId), primaryLabel, pressText,
                        longPressTokens, nativeSlideUp, nativeSlideDown);
    }

    public static void bind(Object view, Object metadata, long token) throws Exception {
        SOFT_KEY_VIEW_CLASS.getMethod("s", METADATA_CLASS, long.class)
                .invoke(view, metadata, Long.valueOf(token));
    }

    public static Object boundMetadata(Object view) throws Exception {
        return SOFT_KEY_VIEW_CLASS.getField("e").get(view);
    }

    public static long bindToken(Object view) throws Exception {
        return SOFT_KEY_VIEW_CLASS.getField("f").getLong(view);
    }

    public static int bindCount(Object view) throws Exception {
        return SOFT_KEY_VIEW_CLASS.getField("bindCount").getInt(view);
    }

    public static void setThrowOnBind(Object view, boolean shouldThrow) throws Exception {
        SOFT_KEY_VIEW_CLASS.getField("throwOnBind").setBoolean(view, shouldThrow);
    }

    public static void setResourceEntryName(Object view, int viewId, String resourceEntryName)
            throws Exception {
        ((android.view.View) view).setId(viewId);
        GboardZhuyinSlideFixtureResources.register(viewId, resourceEntryName);
    }

    static Object newDispatcher(Context context) throws Exception {
        Class<?> preferencesClass = loadClass("qvt");
        Object preferences = preferencesClass.getMethod("instance").invoke(null);
        return loadClass("qil").getConstructor(Context.class, preferencesClass)
                .newInstance(context, preferences);
    }

    static void resetPreferences() throws Exception {
        loadClass("qvt").getMethod("reset").invoke(null);
    }

    static Object action(String actionName) {
        return enumValue(loadClass("qae"), actionName);
    }

    public static void setCurrentOwner(Object tracker, Object view) throws Exception {
        pointerField("m").set(tracker, view);
    }

    public static void setActions(Object tracker, String currentAction, String resolvedAction)
            throws Exception {
        Class<?> actionType = loadClass("qae");
        pointerField("currentAction").set(tracker, enumValue(actionType, currentAction));
        pointerField("resolvedAction").set(tracker, enumValue(actionType, resolvedAction));
    }

    static void setThrowOnResolve(Object tracker, boolean shouldThrow) throws Exception {
        pointerField("throwOnResolve").setBoolean(tracker, shouldThrow);
    }

    static Object exactAction(Object metadata, String actionName) throws Exception {
        Class<?> actionType = loadClass("qae");
        Method lookup = METADATA_CLASS.getMethod("g", actionType);
        return lookup.invoke(metadata, enumValue(actionType, actionName));
    }

    static Object actionPayload(Object action) throws Exception {
        java.util.List<?> entries = (java.util.List<?>) action.getClass().getField("f").get(action);
        return entries.get(0).getClass().getField("e").get(entries.get(0));
    }

    private static Field pointerField(String name) throws Exception {
        return POINTER_TRACKER_CLASS.getField(name);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name, true, CLASS_LOADER);
        } catch (ClassNotFoundException exception) {
            throw new AssertionError(exception);
        }
    }

    private static byte[] buildSoftKeyViewClass() {
        ClassWriter writer = classWriter();
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                SOFT_KEY_VIEW_INTERNAL_NAME, null, "android/view/View", null);
        writer.visitField(Opcodes.ACC_PUBLIC, "e", "L" + METADATA_INTERNAL_NAME + ";",
                null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC, "f", "J", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC, "throwOnBind", "Z", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC, "bindCount", "I", null, null).visitEnd();

        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "(Landroid/content/Context;)V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/view/View", "<init>",
                "(Landroid/content/Context;)V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();

        MethodVisitor bind = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "s",
                "(L" + METADATA_INTERNAL_NAME + ";J)Z", null, null);
        bind.visitCode();
        Label bindNormally = new Label();
        bind.visitVarInsn(Opcodes.ALOAD, 0);
        bind.visitFieldInsn(Opcodes.GETFIELD, SOFT_KEY_VIEW_INTERNAL_NAME, "throwOnBind", "Z");
        bind.visitJumpInsn(Opcodes.IFEQ, bindNormally);
        bind.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
        bind.visitInsn(Opcodes.DUP);
        bind.visitLdcInsn("fixture bind failure");
        bind.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException",
                "<init>", "(Ljava/lang/String;)V", false);
        bind.visitInsn(Opcodes.ATHROW);
        bind.visitLabel(bindNormally);
        bind.visitVarInsn(Opcodes.ALOAD, 0);
        bind.visitVarInsn(Opcodes.ALOAD, 1);
        bind.visitMethodInsn(Opcodes.INVOKESTATIC,
                "dev/jason/gboardpatches/extension/toprowswipe/GboardTopRowSwipeRuntime",
                "patchIncomingSoftKeyMetadata",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false);
        bind.visitTypeInsn(Opcodes.CHECKCAST, METADATA_INTERNAL_NAME);
        bind.visitVarInsn(Opcodes.ASTORE, 1);
        bind.visitVarInsn(Opcodes.ALOAD, 0);
        bind.visitVarInsn(Opcodes.ALOAD, 1);
        bind.visitMethodInsn(Opcodes.INVOKESTATIC,
                "dev/jason/gboardpatches/extension/zhuyintoggle/"
                        + "GboardZhuyinTraditionalSimplifiedToggleRuntime",
                "patchIncomingSoftKeyMetadata",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false);
        bind.visitTypeInsn(Opcodes.CHECKCAST, METADATA_INTERNAL_NAME);
        bind.visitVarInsn(Opcodes.ASTORE, 1);
        bind.visitVarInsn(Opcodes.ALOAD, 0);
        bind.visitVarInsn(Opcodes.ALOAD, 1);
        bind.visitMethodInsn(Opcodes.INVOKESTATIC,
                "dev/jason/gboardpatches/extension/zhuyinslide/GboardZhuyinSlideRuntime",
                "patchIncomingSoftKeyMetadata",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false);
        bind.visitTypeInsn(Opcodes.CHECKCAST, METADATA_INTERNAL_NAME);
        bind.visitVarInsn(Opcodes.ASTORE, 1);
        bind.visitVarInsn(Opcodes.ALOAD, 0);
        bind.visitVarInsn(Opcodes.ALOAD, 1);
        bind.visitFieldInsn(Opcodes.PUTFIELD, SOFT_KEY_VIEW_INTERNAL_NAME, "e",
                "L" + METADATA_INTERNAL_NAME + ";");
        bind.visitVarInsn(Opcodes.ALOAD, 0);
        bind.visitVarInsn(Opcodes.LLOAD, 2);
        bind.visitFieldInsn(Opcodes.PUTFIELD, SOFT_KEY_VIEW_INTERNAL_NAME, "f", "J");
        bind.visitVarInsn(Opcodes.ALOAD, 0);
        bind.visitInsn(Opcodes.DUP);
        bind.visitFieldInsn(Opcodes.GETFIELD, SOFT_KEY_VIEW_INTERNAL_NAME, "bindCount", "I");
        bind.visitInsn(Opcodes.ICONST_1);
        bind.visitInsn(Opcodes.IADD);
        bind.visitFieldInsn(Opcodes.PUTFIELD, SOFT_KEY_VIEW_INTERNAL_NAME, "bindCount", "I");
        bind.visitInsn(Opcodes.ICONST_1);
        bind.visitInsn(Opcodes.IRETURN);
        bind.visitMaxs(0, 0);
        bind.visitEnd();

        MethodVisitor resources = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "getResources", "()Landroid/content/res/Resources;",
                null, null);
        resources.visitCode();
        resources.visitMethodInsn(Opcodes.INVOKESTATIC,
                "dev/jason/gboardpatches/extension/zhuyinslide/"
                        + "GboardZhuyinSlideFixtureResources",
                "instance", "()Landroid/content/res/Resources;", false);
        resources.visitInsn(Opcodes.ARETURN);
        resources.visitMaxs(0, 0);
        resources.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] buildPointerTrackerClass() {
        ClassWriter writer = classWriter();
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                POINTER_TRACKER_NAME, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, "m", "L" + SOFT_KEY_VIEW_INTERNAL_NAME + ";",
                null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC, "currentAction", "Lqae;", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC, "resolvedAction", "Lqae;", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC, "throwOnResolve", "Z", null, null).visitEnd();

        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>",
                "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();

        MethodVisitor owner = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "B",
                "(L" + SOFT_KEY_VIEW_INTERNAL_NAME + ";FFJI)V", null, null);
        owner.visitCode();
        owner.visitInsn(Opcodes.RETURN);
        owner.visitMaxs(0, 0);
        owner.visitEnd();

        MethodVisitor cancel = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "s", "(J)V", null, null);
        cancel.visitCode();
        cancel.visitInsn(Opcodes.RETURN);
        cancel.visitMaxs(0, 0);
        cancel.visitEnd();

        MethodVisitor reset = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "C", "()V", null, null);
        reset.visitCode();
        reset.visitInsn(Opcodes.RETURN);
        reset.visitMaxs(0, 0);
        reset.visitEnd();

        MethodVisitor current = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "i", "()Lqae;", null, null);
        current.visitCode();
        current.visitVarInsn(Opcodes.ALOAD, 0);
        current.visitFieldInsn(Opcodes.GETFIELD, POINTER_TRACKER_NAME, "currentAction", "Lqae;");
        current.visitInsn(Opcodes.ARETURN);
        current.visitMaxs(0, 0);
        current.visitEnd();

        MethodVisitor resolve = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "h", "(FFLqae;)Lqae;", null, null);
        resolve.visitCode();
        Label returnConfigured = new Label();
        resolve.visitVarInsn(Opcodes.ALOAD, 0);
        resolve.visitFieldInsn(Opcodes.GETFIELD, POINTER_TRACKER_NAME, "throwOnResolve", "Z");
        resolve.visitJumpInsn(Opcodes.IFEQ, returnConfigured);
        resolve.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
        resolve.visitInsn(Opcodes.DUP);
        resolve.visitLdcInsn("fixture resolve failure");
        resolve.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException",
                "<init>", "(Ljava/lang/String;)V", false);
        resolve.visitInsn(Opcodes.ATHROW);
        resolve.visitLabel(returnConfigured);
        resolve.visitVarInsn(Opcodes.ALOAD, 0);
        resolve.visitFieldInsn(Opcodes.GETFIELD, POINTER_TRACKER_NAME, "resolvedAction", "Lqae;");
        Label returnResolved = new Label();
        resolve.visitInsn(Opcodes.DUP);
        resolve.visitJumpInsn(Opcodes.IFNONNULL, returnResolved);
        resolve.visitInsn(Opcodes.POP);
        resolve.visitVarInsn(Opcodes.ALOAD, 3);
        resolve.visitLabel(returnResolved);
        resolve.visitInsn(Opcodes.ARETURN);
        resolve.visitMaxs(0, 0);
        resolve.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter classWriter() {
        return new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String left, String right) {
                return "java/lang/Object";
            }
        };
    }

    private static Map<String, String> targetClassNames() {
        Map<String,String> names = new LinkedHashMap<>();
        String model="dev.jason.gboardpatches.extension.zhuyinslide.GboardInputModel$";
        names.put(METADATA_NAME,model+"Metadata");
        names.put("qae",model+"ActionType");
        names.put("com.google.android.libraries.inputmethod.metadata.ActionDef",model+"Action");
        names.put("qbc",model+"Entry");names.put("qaf",model+"ActionBuilder");
        names.put("qcv",model+"MetadataBuilder");names.put("qbb",model+"Intention");
        names.put("wns",model+"Wns");names.put("qil",model+"Dispatcher");
        names.put("qvt",model+"Preferences");names.put("cht",model+"BasePreferences");
        return names;
    }

    private static byte[] remapTargetClass(byte[] sourceBytes) {
        ClassReader reader = new ClassReader(sourceBytes);
        ClassWriter writer = classWriter();
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            private String sourceOwner;

            @Override
            public void visit(int version, int access, String name, String signature,
                    String superName, String[] interfaces) {
                sourceOwner = name;
                super.visit(version, access, mapInternalName(name), mapSignature(signature),
                        mapInternalName(superName), mapInternalNames(interfaces));
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                    String signature, Object value) {
                return super.visitField(access, mapFieldName(sourceOwner, name),
                        mapDescriptor(descriptor), mapSignature(signature), value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(
                        access,
                        mapMethodName(sourceOwner, name, descriptor),
                        mapDescriptor(descriptor),
                        mapSignature(signature),
                        mapInternalNames(exceptions));
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        super.visitTypeInsn(opcode, mapInternalName(type));
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fieldName,
                            String fieldDescriptor) {
                        super.visitFieldInsn(opcode, mapInternalName(owner),
                                mapFieldName(owner, fieldName), mapDescriptor(fieldDescriptor));
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                            String methodDescriptor, boolean isInterface) {
                        super.visitMethodInsn(opcode, mapInternalName(owner),
                                mapMethodName(owner, methodName, methodDescriptor),
                                mapDescriptor(methodDescriptor), isInterface);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof Type) {
                            super.visitLdcInsn(Type.getType(mapDescriptor(
                                    ((Type) value).getDescriptor())));
                        } else {
                            super.visitLdcInsn(value);
                        }
                    }

                    @Override
                    public void visitMultiANewArrayInsn(String descriptor, int dimensions) {
                        super.visitMultiANewArrayInsn(mapDescriptor(descriptor), dimensions);
                    }

                    @Override
                    public void visitTryCatchBlock(Label start, Label end, Label handler,
                            String type) {
                        super.visitTryCatchBlock(start, end, handler, mapInternalName(type));
                    }

                    @Override
                    public void visitLocalVariable(String variableName, String descriptor,
                            String signature, Label start, Label end, int index) {
                        super.visitLocalVariable(variableName, mapDescriptor(descriptor),
                                mapSignature(signature), start, end, index);
                    }

                    @Override
                    public void visitFrame(int type, int numLocal, Object[] local,
                            int numStack, Object[] stack) {
                        super.visitFrame(type, numLocal, mapFrame(local),
                                numStack, mapFrame(stack));
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    private static String mapMethodName(String owner, String name, String descriptor) {
        return name;
    }

    private static String mapFieldName(String owner, String name) { return name; }

    private static String mapDescriptor(String value) {
        if (value == null) {
            return null;
        }
        String mapped = value;
        for (Map.Entry<String, String> entry : TARGET_CLASS_NAMES.entrySet()) {
            String source = entry.getValue().replace('.', '/');
            String target = entry.getKey().replace('.', '/');
            mapped = mapped.replace("L" + source + ";", "L" + target + ";");
        }
        return mapped;
    }

    private static String mapSignature(String value) {
        return mapDescriptor(value);
    }

    private static String mapInternalName(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("[")) {
            return mapDescriptor(value);
        }
        for (Map.Entry<String, String> entry : TARGET_CLASS_NAMES.entrySet()) {
            if (entry.getValue().replace('.', '/').equals(value)) {
                return entry.getKey().replace('.', '/');
            }
        }
        return value;
    }

    private static String[] mapInternalNames(String[] values) {
        if (values == null) {
            return null;
        }
        String[] mapped = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            mapped[index] = mapInternalName(values[index]);
        }
        return mapped;
    }

    private static Object[] mapFrame(Object[] values) {
        if (values == null) {
            return null;
        }
        Object[] mapped = values.clone();
        for (int index = 0; index < mapped.length; index++) {
            if (mapped[index] instanceof String) {
                mapped[index] = mapInternalName((String) mapped[index]);
            }
        }
        return mapped;
    }

    private static final class FixtureClassLoader extends ClassLoader {
        private final byte[] softKeyViewClass;
        private final byte[] pointerTrackerClass;

        FixtureClassLoader(ClassLoader parent, byte[] softKeyViewClass,
                byte[] pointerTrackerClass) {
            super(parent);
            this.softKeyViewClass = softKeyViewClass;
            this.pointerTrackerClass = pointerTrackerClass;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (SOFT_KEY_VIEW_NAME.equals(name)) {
                return defineClass(name, softKeyViewClass, 0, softKeyViewClass.length);
            }
            if (POINTER_TRACKER_NAME.equals(name)) {
                return defineClass(name, pointerTrackerClass, 0, pointerTrackerClass.length);
            }
            if (isTargetClass(name)) {
                byte[] classBytes = remapTargetClass(readParentClassBytes(
                        TARGET_CLASS_NAMES.get(name)));
                return defineClass(name, classBytes, 0, classBytes.length);
            }
            throw new ClassNotFoundException(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!SOFT_KEY_VIEW_NAME.equals(name)
                    && !POINTER_TRACKER_NAME.equals(name)
                    && !isTargetClass(name)) {
                return super.loadClass(name, resolve);
            }
            synchronized (this) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = findClass(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        private byte[] readParentClassBytes(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            InputStream input = getParent().getResourceAsStream(resourceName);
            if (input == null) {
                throw new ClassNotFoundException(name);
            }
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            } finally {
                try {
                    input.close();
                } catch (IOException ignored) {
                    // Fixture cleanup only.
                }
            }
        }

        private boolean isTargetClass(String name) {
            return TARGET_CLASS_NAMES.containsKey(name);
        }
    }
}
