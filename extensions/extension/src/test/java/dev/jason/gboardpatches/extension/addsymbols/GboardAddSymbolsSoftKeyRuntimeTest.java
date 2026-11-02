package dev.jason.gboardpatches.extension.addsymbols;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class GboardAddSymbolsSoftKeyRuntimeTest {
    @Test
    public void usesOnlyGboard1853SoftKeyReflectionModel() throws Exception {
        String source = runtimeSource();
        List<String> targetMappings = List.of(
                "\"com.google.android.libraries.inputmethod.metadata.SoftKeyDef\";",
                "private static final String ACTION_TYPE_CLASS = \"qae\";",
                "\"com.google.android.libraries.inputmethod.metadata.ActionDef\";",
                "private static final String ACTION_ENTRY_CLASS = \"qbc\";",
                "private static final String ACTION_BUILDER_CLASS = \"qaf\";",
                "private static final String METADATA_BUILDER_CLASS = \"qcv\";",
                "private static final String INTENTION_CLASS = \"qbb\";",
                "private static final String KEYBOARD_TYPE_CLASS = \"qch\";");
        for (String mapping : targetMappings) {
            Assert.assertTrue("Missing Gboard 18.5.4 soft-key mapping: " + mapping,
                    source.contains(mapping));
        }

        List<String> baselineClasses = List.of(
                "owd", "oth", "otk", "oud", "oti", "ovv", "ouc", "ovf");
        for (String className : baselineClasses) {
            Assert.assertFalse("Stale Gboard 18.5.4 soft-key class remains: " + className,
                    source.contains("\"" + className + "\""));
        }

        Assert.assertTrue(source.contains("field(actionSetClass, \"h\")"));
        Assert.assertTrue(source.contains("field(actionSetClass, \"i\")"));
        Assert.assertTrue(source.contains("method(actionSetClass, \"g\", actionTypeClass)"));
        Assert.assertTrue(source.contains("field(actionDefClass, \"p\")"));
        Assert.assertTrue(source.contains("field(actionDefClass, \"q\")"));
        Assert.assertTrue(source.contains("method(resolve(classLoader, \"qaz\"), \"ag\", Object.class)"));
        Assert.assertTrue(source.contains("field(keyboardTypeClass, \"m\")"));
    }

    @Test
    public void removesResolvedLayoutAndInjectedDrawableDependencies() throws Exception {
        String source = runtimeSource();

        Assert.assertFalse(source.contains("0x7f0e05fd"));
        Assert.assertFalse(source.contains("0x7f080576"));
        Assert.assertTrue(source.contains("POPUP_LAYOUT_ATTRIBUTE_RES_ID = 0x7f04027f"));
        Assert.assertTrue(source.contains("handles.popupLayout(action) == 0"));
    }

    @Test
    public void plansReplaceAppendAndNoOpPaths() throws Exception {
        Method plan = requiredMethod(
                GboardAddSymbolsSoftKeyRuntime.class,
                "plan",
                boolean.class,
                boolean.class,
                int.class,
                boolean.class);

        Assert.assertEquals("REPLACE", plan.invoke(null, true, true, 2, false).toString());
        Assert.assertEquals("APPEND", plan.invoke(null, true, true, -1, false).toString());
        Assert.assertEquals("NONE", plan.invoke(null, false, true, 2, false).toString());
        Assert.assertEquals("NONE", plan.invoke(null, true, false, 2, false).toString());
        Assert.assertEquals("NONE", plan.invoke(null, true, true, 2, true).toString());
    }

    @Test
    public void clonesMetadataAndPreservesTemplateIcons() throws Exception {
        String source = runtimeSource();
        Assert.assertTrue(source.contains("copyActionMethod.invoke(builder, template)"));
        Assert.assertTrue(source.contains("copyMetadataMethod.invoke(builder, metadata)"));
        Assert.assertTrue(source.contains("replaceAction(builder, handles.longPressActionType"));
        Assert.assertTrue(source.contains("normalizeIcons(popupIcons(template), entries.length)"));
        Assert.assertTrue(source.contains(
                "normalizeIcons(popupIcons(template), entries.length + 1)"));

        Class<?> handlesClass = Arrays.stream(GboardAddSymbolsSoftKeyRuntime.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("ReflectionHandles"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing ReflectionHandles"));
        Method normalizeIcons = requiredMethod(
                handlesClass, "normalizeIcons", int[].class, int.class);

        Assert.assertArrayEquals(new int[] { 11, 11, 11 },
                (int[]) normalizeIcons.invoke(null, new int[] { 11 }, 3));
        Assert.assertArrayEquals(new int[] { 4, 5 },
                (int[]) normalizeIcons.invoke(null, new int[] { 4, 5 }, 2));
        Assert.assertArrayEquals(new int[] { 4, 5, 0 },
                (int[]) normalizeIcons.invoke(null, new int[] { 4, 5 }, 3));
    }

    @Test
    public void activeReflectionFailuresUseBoundedThrowableLogging() throws Exception {
        String source = runtimeSource();
        String patchMethod = methodSource(source, "patchSoftKeyMetadata");
        String failureLogger = methodSource(source, "logSoftKeyFailure");

        Assert.assertTrue(patchMethod.contains("catch (Throwable throwable)"));
        Assert.assertTrue(patchMethod.contains("logSoftKeyFailure(throwable)"));
        Assert.assertFalse(patchMethod.contains("catch (Throwable ignored)"));
        Assert.assertTrue(source.contains("AtomicInteger ERROR_LOG_COUNT"));
        Assert.assertTrue(failureLogger.contains("ERROR_LOG_COUNT.getAndIncrement() < 3"));
        Assert.assertTrue(failureLogger.contains(
                "Log.w(TAG, \"soft-key patch failed\", throwable)"));
    }

    private static Method requiredMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException exception) {
            Assert.fail("Missing method: " + owner.getName() + "#" + name);
            return null;
        }
    }

    private static String runtimeSource() throws Exception {
        Path path = Path.of(
                "src/main/java/dev/jason/gboardpatches/extension/addsymbols/"
                        + "GboardAddSymbolsSoftKeyRuntime.java");
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String methodSource(String source, String methodName) {
        int nameIndex = source.indexOf(methodName + "(");
        Assert.assertTrue("Missing method source: " + methodName, nameIndex >= 0);
        int bodyStart = source.indexOf('{', nameIndex);
        Assert.assertTrue("Missing method body: " + methodName, bodyStart >= 0);
        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, index + 1);
                }
            }
        }
        throw new AssertionError("Unterminated method body: " + methodName);
    }
}
