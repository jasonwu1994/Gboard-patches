package dev.jason.gboardpatches.extension.longpressquickactions;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class GboardLongPressQuickActions1803PolicyTest {
    private static final String POLICY_CLASS =
            "dev.jason.gboardpatches.extension.longpressquickactions."
                    + "GboardLongPressQuickActions1803Policy";

    @Test
    public void mapsAllEnglishLowerAndUppercaseTargetKeysToExactActions() throws Exception {
        assertPlan(0x7f0b1990, "a", -0x2766, 0x7f0804e3,
                "Select all", 0x102001f);
        assertPlan(0x7f0b18b2, "A", -0x2766, 0x7f0804e3,
                "Select all", 0x102001f);
        assertPlan(0x7f0b1ac9, "z", -0x273d, 0x7f08051f,
                "Undo", 0);
        assertPlan(0x7f0b1969, "Z", -0x273d, 0x7f08051f,
                "Undo", 0);
        assertPlan(0x7f0b19a5, "c", -0x2767, 0x7f08044f,
                "Copy", 0x1020021);
        assertPlan(0x7f0b18c3, "C", -0x2767, 0x7f08044f,
                "Copy", 0x1020021);
        assertPlan(0x7f0b1abf, "x", -0x2769, 0x7f080450,
                "Cut", 0x1020020);
        assertPlan(0x7f0b1964, "X", -0x2769, 0x7f080450,
                "Cut", 0x1020020);
        assertPlan(0x7f0b1ab7, "v", -0x2768, 0x7f080452,
                "Paste", 0x1020022);
        assertPlan(0x7f0b195f, "V", -0x2768, 0x7f080452,
                "Paste", 0x1020022);
        assertPlan(0x7f0b1ac2, "y", -0x27a3, 0x7f0804df,
                "Redo", 0);
        assertPlan(0x7f0b1965, "Y", -0x27a3, 0x7f0804df,
                "Redo", 0);
        assertPlan(0x7f0b19db, "g", -0x27f1, 0x7f0803c0,
                "Toggle case", 0);
        assertPlan(0x7f0b18e4, "G", -0x27f1, 0x7f0803c0,
                "Toggle case", 0);
        assertPlan(0x7f0b1aba, null, -0x27f1, 0x7f0803c0,
                "Toggle case", 0);
    }

    @Test
    public void mapsExactZhuyinTargetKeysToActions() throws Exception {
        assertPlan(0x7f0b2976, "a", -0x2766, 0x7f0804e3,
                "Select all", 0x102001f);
        assertPlan(0x7f0b296d, "z", -0x273d, 0x7f08051f,
                "Undo", 0);
        assertPlan(0x7f0b2970, "c", -0x2767, 0x7f08044f,
                "Copy", 0x1020021);
        assertPlan(0x7f0b2975, "x", -0x2769, 0x7f080450,
                "Cut", 0x1020020);
        assertPlan(0x7f0b2987, "v", -0x2768, 0x7f080452,
                "Paste", 0x1020022);
        assertPlan(0x7f0b2989, "y", -0x27a3, 0x7f0804df,
                "Redo", 0);
    }

    @Test
    public void rejectsMismatchedPayloadsUnknownKeysAndDuplicateActions() throws Exception {
        Assert.assertNull(invokePlan(0x7f0b1990, "A", new int[0]));
        Assert.assertNull(invokePlan(0x7f0b18b2, "a", new int[0]));
        Assert.assertNull(invokePlan(0x7f0b2976, "A", new int[0]));
        Assert.assertNull(invokePlan(0x7f0b2970, "x", new int[0]));
        Assert.assertNull(invokePlan(0x7f0b19f6, "q", new int[0]));
        Assert.assertNull(invokePlan(0x7f0b1990, null, new int[0]));
        Assert.assertNull(invokePlan(0x7f0b1990, "a", new int[] {-0x2766}));
        Assert.assertNull(invokePlan(0x7f0b2976, "a", new int[] {-0x2766}));
        Assert.assertNotNull(invokePlan(0x7f0b2976, "a", new int[] {-0x2767}));
    }

    @Test
    public void exposesContextMenuActionsOnlyForExactEligibleLongPressEvents()
            throws Exception {
        Assert.assertEquals(Integer.valueOf(0x102001f),
                contextMenuActionFor(0x7f0b1990, "a", "LONG_PRESS", -0x2766));
        Assert.assertEquals(Integer.valueOf(0x1020021),
                contextMenuActionFor(0x7f0b18c3, "C", "LONG_PRESS", -0x2767));
        Assert.assertEquals(Integer.valueOf(0x1020020),
                contextMenuActionFor(0x7f0b1abf, "x", "LONG_PRESS", -0x2769));
        Assert.assertEquals(Integer.valueOf(0x1020022),
                contextMenuActionFor(0x7f0b195f, "V", "LONG_PRESS", -0x2768));
        Assert.assertEquals(Integer.valueOf(0x102001f),
                contextMenuActionFor(0x7f0b2976, "a", "LONG_PRESS", -0x2766));
        Assert.assertEquals(Integer.valueOf(0x1020021),
                contextMenuActionFor(0x7f0b2970, "c", "LONG_PRESS", -0x2767));
        Assert.assertEquals(Integer.valueOf(0x1020020),
                contextMenuActionFor(0x7f0b2975, "x", "LONG_PRESS", -0x2769));
        Assert.assertEquals(Integer.valueOf(0x1020022),
                contextMenuActionFor(0x7f0b2987, "v", "LONG_PRESS", -0x2768));

        Assert.assertNull(contextMenuActionFor(
                0x7f0b1ac9, "z", "LONG_PRESS", -0x273d));
        Assert.assertNull(contextMenuActionFor(
                0x7f0b1965, "Y", "LONG_PRESS", -0x27a3));
        Assert.assertNull(contextMenuActionFor(
                0x7f0b296d, "z", "LONG_PRESS", -0x273d));
        Assert.assertNull(contextMenuActionFor(
                0x7f0b2989, "y", "LONG_PRESS", -0x27a3));
        Assert.assertNull(contextMenuActionFor(
                0x7f0b1990, "a", "PRESS", -0x2766));
        Assert.assertNull(contextMenuActionFor(
                0x7f0b2976, "a", "LONG_PRESS", -0x2767));
        Assert.assertNull(contextMenuActionFor(
                0x7f0b19f6, "q", "LONG_PRESS", -0x2766));
    }

    @Test
    public void recognizesAllSixExactInjectedActionEvents() throws Exception {
        Assert.assertTrue(isQuickActionEvent(
                0x7f0b1990, "a", "LONG_PRESS", -0x2766));
        Assert.assertTrue(isQuickActionEvent(
                0x7f0b296d, "z", "LONG_PRESS", -0x273d));
        Assert.assertTrue(isQuickActionEvent(
                0x7f0b2970, "c", "LONG_PRESS", -0x2767));
        Assert.assertTrue(isQuickActionEvent(
                0x7f0b1abf, "x", "LONG_PRESS", -0x2769));
        Assert.assertTrue(isQuickActionEvent(
                0x7f0b2987, "v", "LONG_PRESS", -0x2768));
        Assert.assertTrue(isQuickActionEvent(
                0x7f0b1965, "Y", "LONG_PRESS", -0x27a3));

        Assert.assertFalse(isQuickActionEvent(
                0x7f0b1990, "a", "PRESS", -0x2766));
        Assert.assertFalse(isQuickActionEvent(
                0x7f0b1990, "a", "LONG_PRESS", -0x2767));
        Assert.assertFalse(isQuickActionEvent(
                0x7f0b2976, "A", "LONG_PRESS", -0x2766));
    }

    @Test
    public void recognizesOnlyTheAssignedActionInsidePatchedMetadata() throws Exception {
        Assert.assertTrue(containsAssignedAction(
                0x7f0b1abf, "x", new int[] {-0x2769}));
        Assert.assertTrue(containsAssignedAction(
                0x7f0b2987, "v", new int[] {-0x2767, -0x2768}));
        Assert.assertFalse(containsAssignedAction(
                0x7f0b1abf, "x", new int[] {-0x2768}));
        Assert.assertFalse(containsAssignedAction(
                0x7f0b19f6, "q", new int[] {-0x2769}));
    }

    @Test
    public void globeChordUsesTheSameEnglishAndZhuyinMappings() {
        Assert.assertSame(GboardEditingShortcutPolicy.Shortcut.SELECT_ALL,
                GboardLongPressQuickActions1803Policy.shortcutForChord(
                        0x7f0b1990, "a", 'a', -10043, -10009));
        Assert.assertSame(GboardEditingShortcutPolicy.Shortcut.CUT,
                GboardLongPressQuickActions1803Policy.shortcutForChord(
                        0x7f0b2975, "x", -10009, -10043, -10009));
        Assert.assertSame(GboardEditingShortcutPolicy.Shortcut.REDO,
                GboardLongPressQuickActions1803Policy.shortcutForPointerTarget(
                        0x7f0b2989, "y", -10043, -10043));
        Assert.assertNull(GboardLongPressQuickActions1803Policy.shortcutForChord(
                0x7f0b19f6, "q", 'q', -10043, -10009));
    }

    @Test
    public void zhuyinRangeAdmissionIsVersionOwnedAndBounded() {
        Assert.assertFalse(GboardLongPressQuickActions1803Policy.isZhuyinKeyId(0x7f0b295d));
        Assert.assertTrue(GboardLongPressQuickActions1803Policy.isZhuyinKeyId(0x7f0b295e));
        Assert.assertTrue(GboardLongPressQuickActions1803Policy.isZhuyinKeyId(0x7f0b298a));
        Assert.assertFalse(GboardLongPressQuickActions1803Policy.isZhuyinKeyId(0x7f0b298b));
    }

    private static void assertPlan(int keyId, String pressText, int actionCode,
            int iconResId, String debugName, int contextMenuActionId) throws Exception {
        Object plan = invokePlan(keyId, pressText, new int[0]);
        Assert.assertNotNull("missing plan for key id 0x" + Integer.toHexString(keyId), plan);
        Assert.assertEquals(actionCode, intField(plan, "actionCode"));
        Assert.assertEquals(iconResId, intField(plan, "iconResId"));
        Assert.assertEquals(debugName, objectField(plan, "debugName"));
        Assert.assertEquals(contextMenuActionId, intField(plan, "contextMenuActionId"));
        assertMissingField(plan, "labelResId");
        assertMissingField(plan, "fallbackLabel");
    }

    private static Object invokePlan(int keyId, String pressText, int[] existingCodes)
            throws Exception {
        try {
            Class<?> policy = Class.forName(POLICY_CLASS);
            Method method = policy.getMethod(
                    "plan", int.class, String.class, int[].class);
            return method.invoke(null, Integer.valueOf(keyId), pressText, existingCodes);
        } catch (ClassNotFoundException missing) {
            Assert.fail("missing GboardLongPressQuickActions1803Policy");
            throw missing;
        } catch (InvocationTargetException invocation) {
            throw rethrow(invocation);
        }
    }

    private static Integer contextMenuActionFor(int keyId, String pressText,
            String actionTypeName, int selectedCode) throws Exception {
        try {
            Class<?> policy = Class.forName(POLICY_CLASS);
            Method method = policy.getMethod(
                    "contextMenuActionFor",
                    int.class, String.class, String.class, int.class);
            return (Integer) method.invoke(null, Integer.valueOf(keyId), pressText,
                    actionTypeName, Integer.valueOf(selectedCode));
        } catch (ClassNotFoundException missing) {
            Assert.fail("missing GboardLongPressQuickActions1803Policy");
            throw missing;
        } catch (InvocationTargetException invocation) {
            throw rethrow(invocation);
        }
    }

    private static boolean isQuickActionEvent(int keyId, String pressText,
            String actionTypeName, int selectedCode) throws Exception {
        try {
            Class<?> policy = Class.forName(POLICY_CLASS);
            Method method = policy.getMethod(
                    "isQuickActionEvent",
                    int.class, String.class, String.class, int.class);
            return ((Boolean) method.invoke(null, Integer.valueOf(keyId), pressText,
                    actionTypeName, Integer.valueOf(selectedCode))).booleanValue();
        } catch (ClassNotFoundException missing) {
            Assert.fail("missing GboardLongPressQuickActions1803Policy");
            throw missing;
        } catch (InvocationTargetException invocation) {
            throw rethrow(invocation);
        }
    }

    private static boolean containsAssignedAction(int keyId, String pressText,
            int[] existingCodes) throws Exception {
        try {
            Class<?> policy = Class.forName(POLICY_CLASS);
            Method method = policy.getMethod(
                    "containsAssignedAction", int.class, String.class, int[].class);
            return ((Boolean) method.invoke(null, Integer.valueOf(keyId), pressText,
                    existingCodes)).booleanValue();
        } catch (ClassNotFoundException missing) {
            Assert.fail("missing GboardLongPressQuickActions1803Policy");
            throw missing;
        } catch (InvocationTargetException invocation) {
            throw rethrow(invocation);
        }
    }

    private static int intField(Object instance, String name) throws Exception {
        return ((Integer) objectField(instance, name)).intValue();
    }

    private static Object objectField(Object instance, String name) throws Exception {
        Field field = instance.getClass().getField(name);
        return field.get(instance);
    }

    private static void assertMissingField(Object instance, String name) {
        try {
            instance.getClass().getField(name);
            Assert.fail("unexpected visible-label field: " + name);
        } catch (NoSuchFieldException expected) {
            // Expected: icon-only actions do not carry visible label resources.
        }
    }

    private static Exception rethrow(InvocationTargetException invocation)
            throws Exception {
        Throwable cause = invocation.getCause();
        if (cause instanceof Exception exception) {
            return exception;
        }
        throw invocation;
    }
}
