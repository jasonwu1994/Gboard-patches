package dev.jason.gboardpatches.extension.longpressquickactions;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class GboardLongPressQuickActionsPolicyTest {
    private static final String POLICY_CLASS =
            "dev.jason.gboardpatches.extension.longpressquickactions."
                    + "GboardLongPressQuickActionsPolicy";

    @Test
    public void mapsAllEnglishLowerAndUppercaseTargetKeysToExactActions() throws Exception {
        assertPlan(0x7f0b18d4, "a", -0x2766, 0x7f0804c1,
                "Select all", 0x102001f);
        assertPlan(0x7f0b17f7, "A", -0x2766, 0x7f0804c1,
                "Select all", 0x102001f);
        assertPlan(0x7f0b1a0d, "z", -0x273d, 0x7f0804fd,
                "Undo", 0);
        assertPlan(0x7f0b18ad, "Z", -0x273d, 0x7f0804fd,
                "Undo", 0);
        assertPlan(0x7f0b18e9, "c", -0x2767, 0x7f08042b,
                "Copy", 0x1020021);
        assertPlan(0x7f0b1808, "C", -0x2767, 0x7f08042b,
                "Copy", 0x1020021);
        assertPlan(0x7f0b1a03, "x", -0x2769, 0x7f08042c,
                "Cut", 0x1020020);
        assertPlan(0x7f0b18a8, "X", -0x2769, 0x7f08042c,
                "Cut", 0x1020020);
        assertPlan(0x7f0b19fb, "v", -0x2768, 0x7f08042e,
                "Paste", 0x1020022);
        assertPlan(0x7f0b18a3, "V", -0x2768, 0x7f08042e,
                "Paste", 0x1020022);
        assertPlan(0x7f0b1a06, "y", -0x27a3, 0x7f0804bd,
                "Redo", 0);
        assertPlan(0x7f0b18a9, "Y", -0x27a3, 0x7f0804bd,
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
        assertPlan(0x7f0b28d1, "a", -0x2766, 0x7f0804c1,
                "Select all", 0x102001f);
        assertPlan(0x7f0b28c8, "z", -0x273d, 0x7f0804fd,
                "Undo", 0);
        assertPlan(0x7f0b28cb, "c", -0x2767, 0x7f08042b,
                "Copy", 0x1020021);
        assertPlan(0x7f0b28d0, "x", -0x2769, 0x7f08042c,
                "Cut", 0x1020020);
        assertPlan(0x7f0b28e2, "v", -0x2768, 0x7f08042e,
                "Paste", 0x1020022);
        assertPlan(0x7f0b28e4, "y", -0x27a3, 0x7f0804bd,
                "Redo", 0);
    }

    @Test
    public void rejectsMismatchedPayloadsUnknownKeysAndDuplicateActions() throws Exception {
        Assert.assertNull(invokePlan(0x7f0b18d4, "A", new int[0]));
        Assert.assertNull(invokePlan(0x7f0b17f7, "a", new int[0]));
        Assert.assertNull(invokePlan(0x7f0b28d1, "A", new int[0]));
        Assert.assertNull(invokePlan(0x7f0b28cb, "x", new int[0]));
        Assert.assertNull(invokePlan(0x7f0b19f6, "q", new int[0]));
        Assert.assertNull(invokePlan(0x7f0b18d4, null, new int[0]));
        Assert.assertNull(invokePlan(0x7f0b18d4, "a", new int[] {-0x2766}));
        Assert.assertNull(invokePlan(0x7f0b28d1, "a", new int[] {-0x2766}));
        Assert.assertNotNull(invokePlan(0x7f0b28d1, "a", new int[] {-0x2767}));
    }

    @Test
    public void exposesContextMenuActionsOnlyForExactEligibleLongPressEvents()
            throws Exception {
        Assert.assertEquals(Integer.valueOf(0x102001f),
                contextMenuActionFor(0x7f0b18d4, "a", "LONG_PRESS", -0x2766));
        Assert.assertEquals(Integer.valueOf(0x1020021),
                contextMenuActionFor(0x7f0b1808, "C", "LONG_PRESS", -0x2767));
        Assert.assertEquals(Integer.valueOf(0x1020020),
                contextMenuActionFor(0x7f0b1a03, "x", "LONG_PRESS", -0x2769));
        Assert.assertEquals(Integer.valueOf(0x1020022),
                contextMenuActionFor(0x7f0b18a3, "V", "LONG_PRESS", -0x2768));
        Assert.assertEquals(Integer.valueOf(0x102001f),
                contextMenuActionFor(0x7f0b28d1, "a", "LONG_PRESS", -0x2766));
        Assert.assertEquals(Integer.valueOf(0x1020021),
                contextMenuActionFor(0x7f0b28cb, "c", "LONG_PRESS", -0x2767));
        Assert.assertEquals(Integer.valueOf(0x1020020),
                contextMenuActionFor(0x7f0b28d0, "x", "LONG_PRESS", -0x2769));
        Assert.assertEquals(Integer.valueOf(0x1020022),
                contextMenuActionFor(0x7f0b28e2, "v", "LONG_PRESS", -0x2768));

        Assert.assertNull(contextMenuActionFor(
                0x7f0b1a0d, "z", "LONG_PRESS", -0x273d));
        Assert.assertNull(contextMenuActionFor(
                0x7f0b18a9, "Y", "LONG_PRESS", -0x27a3));
        Assert.assertNull(contextMenuActionFor(
                0x7f0b28c8, "z", "LONG_PRESS", -0x273d));
        Assert.assertNull(contextMenuActionFor(
                0x7f0b28e4, "y", "LONG_PRESS", -0x27a3));
        Assert.assertNull(contextMenuActionFor(
                0x7f0b18d4, "a", "PRESS", -0x2766));
        Assert.assertNull(contextMenuActionFor(
                0x7f0b28d1, "a", "LONG_PRESS", -0x2767));
        Assert.assertNull(contextMenuActionFor(
                0x7f0b19f6, "q", "LONG_PRESS", -0x2766));
    }

    @Test
    public void recognizesAllSixExactInjectedActionEvents() throws Exception {
        Assert.assertTrue(isQuickActionEvent(
                0x7f0b18d4, "a", "LONG_PRESS", -0x2766));
        Assert.assertTrue(isQuickActionEvent(
                0x7f0b28c8, "z", "LONG_PRESS", -0x273d));
        Assert.assertTrue(isQuickActionEvent(
                0x7f0b28cb, "c", "LONG_PRESS", -0x2767));
        Assert.assertTrue(isQuickActionEvent(
                0x7f0b1a03, "x", "LONG_PRESS", -0x2769));
        Assert.assertTrue(isQuickActionEvent(
                0x7f0b28e2, "v", "LONG_PRESS", -0x2768));
        Assert.assertTrue(isQuickActionEvent(
                0x7f0b18a9, "Y", "LONG_PRESS", -0x27a3));

        Assert.assertFalse(isQuickActionEvent(
                0x7f0b18d4, "a", "PRESS", -0x2766));
        Assert.assertFalse(isQuickActionEvent(
                0x7f0b18d4, "a", "LONG_PRESS", -0x2767));
        Assert.assertFalse(isQuickActionEvent(
                0x7f0b28d1, "A", "LONG_PRESS", -0x2766));
    }

    @Test
    public void recognizesOnlyTheAssignedActionInsidePatchedMetadata() throws Exception {
        Assert.assertTrue(containsAssignedAction(
                0x7f0b1a03, "x", new int[] {-0x2769}));
        Assert.assertTrue(containsAssignedAction(
                0x7f0b28e2, "v", new int[] {-0x2767, -0x2768}));
        Assert.assertFalse(containsAssignedAction(
                0x7f0b1a03, "x", new int[] {-0x2768}));
        Assert.assertFalse(containsAssignedAction(
                0x7f0b19f6, "q", new int[] {-0x2769}));
    }

    @Test
    public void globeChordUsesTheSameEnglishAndZhuyinMappings() {
        Assert.assertSame(GboardEditingShortcutPolicy.Shortcut.SELECT_ALL,
                GboardLongPressQuickActionsPolicy.shortcutForChord(
                        0x7f0b18d4, "a", 'a', -10043, -10009));
        Assert.assertSame(GboardEditingShortcutPolicy.Shortcut.CUT,
                GboardLongPressQuickActionsPolicy.shortcutForChord(
                        0x7f0b28d0, "x", -10009, -10043, -10009));
        Assert.assertSame(GboardEditingShortcutPolicy.Shortcut.REDO,
                GboardLongPressQuickActionsPolicy.shortcutForPointerTarget(
                        0x7f0b28e4, "y", -10043, -10043));
        Assert.assertNull(GboardLongPressQuickActionsPolicy.shortcutForChord(
                0x7f0b19f6, "q", 'q', -10043, -10009));
    }

    @Test
    public void zhuyinRangeAdmissionIsVersionOwnedAndBounded() {
        Assert.assertFalse(GboardLongPressQuickActionsPolicy.isZhuyinKeyId(0x7f0b295d));
        Assert.assertTrue(GboardLongPressQuickActionsPolicy.isZhuyinKeyId(0x7f0b28b9));
        Assert.assertTrue(GboardLongPressQuickActionsPolicy.isZhuyinKeyId(0x7f0b28e5));
        Assert.assertFalse(GboardLongPressQuickActionsPolicy.isZhuyinKeyId(0x7f0b298b));
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
            Assert.fail("missing GboardLongPressQuickActionsPolicy");
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
            Assert.fail("missing GboardLongPressQuickActionsPolicy");
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
            Assert.fail("missing GboardLongPressQuickActionsPolicy");
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
            Assert.fail("missing GboardLongPressQuickActionsPolicy");
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
