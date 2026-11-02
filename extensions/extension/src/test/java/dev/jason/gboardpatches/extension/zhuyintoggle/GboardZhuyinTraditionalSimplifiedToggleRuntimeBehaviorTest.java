package dev.jason.gboardpatches.extension.zhuyintoggle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class GboardZhuyinTraditionalSimplifiedToggleRuntimeBehaviorTest {
    private static final int TOGGLE_KEY_ID = 0x7f0b28c5;

    private Context context;

    @Before
    public void resetFixtureState() throws Exception {
        context = RuntimeEnvironment.getApplication();
        invokeStatic("qvt", "reset");
        GboardZhuyinTraditionalSimplifiedToggleRuntime.VISIBLE_TOGGLE_KEYS.clear();
    }

    @Test
    public void bindPatchingBuildsExactEmptySlideUpLabelsAndRejectsOtherKeys()
            throws Exception {
        Object view = GboardZhuyinToggleTargetFixture.newSoftKeyView(context);
        Object traditionalMetadata = newMetadata(TOGGLE_KEY_ID);

        Object traditionalPatched =
                GboardZhuyinTraditionalSimplifiedToggleRuntime.patchIncomingSoftKeyMetadata(
                        view, traditionalMetadata);

        assertNotSame(traditionalMetadata, traditionalPatched);
        assertToggleAction(traditionalPatched, "簡");

        preferences().getClass()
                .getMethod("setSimplifiedEnabled", boolean.class)
                .invoke(preferences(), Boolean.TRUE);
        Object simplifiedMetadata = newMetadata(TOGGLE_KEY_ID);
        Object simplifiedPatched =
                GboardZhuyinTraditionalSimplifiedToggleRuntime.patchIncomingSoftKeyMetadata(
                        view, simplifiedMetadata);
        assertNotSame(simplifiedMetadata, simplifiedPatched);
        assertToggleAction(simplifiedPatched, "繁");

        Object otherKey = newMetadata(TOGGLE_KEY_ID - 1);
        assertSame(otherKey,
                GboardZhuyinTraditionalSimplifiedToggleRuntime.patchIncomingSoftKeyMetadata(
                        view, otherKey));
    }

    @Test
    public void equalButDistinctMetadataUsesIdentityMarkers() throws Exception {
        Object view = GboardZhuyinToggleTargetFixture.newSoftKeyView(context);
        Object first = toggleMetadata("簡");
        Object second = toggleMetadata("簡");
        assertEquals(first, second);
        assertNotSame(first, second);

        Object firstPatched =
                GboardZhuyinTraditionalSimplifiedToggleRuntime.patchIncomingSoftKeyMetadata(
                        view, first);
        assertEquals(firstPatched, second);

        Object secondPatched =
                GboardZhuyinTraditionalSimplifiedToggleRuntime.patchIncomingSoftKeyMetadata(
                        view, second);
        assertNotSame(second, secondPatched);
    }

    @Test
    public void weakIdentityMapPurgesEnqueuedKeysWithoutTestOnlyProductionApi()
            throws Exception {
        Class<?> mapClass = Class.forName(
                GboardZhuyinTraditionalSimplifiedToggleRuntime.class.getName()
                        + "$WeakIdentityMap");
        Constructor<?> constructor = mapClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object weakIdentityMap = constructor.newInstance();
        Method put = mapClass.getDeclaredMethod("put", Object.class, Object.class);
        Method get = mapClass.getDeclaredMethod("get", Object.class);
        put.setAccessible(true);
        get.setAccessible(true);
        Field valuesField = mapClass.getDeclaredField("values");
        valuesField.setAccessible(true);

        Object key = new Object();
        put.invoke(weakIdentityMap, key, "value");
        Map<?, ?> values = (Map<?, ?>) valuesField.get(weakIdentityMap);
        assertEquals(1, values.size());
        Reference<?> storedReference = (Reference<?>) values.keySet().iterator().next();
        storedReference.clear();
        assertTrue(storedReference.enqueue());

        get.invoke(weakIdentityMap, new Object());

        assertTrue(values.isEmpty());
    }

    @Test
    public void popupCopiesTrackedActionEntriesAndUntrackedFrameLayoutFailsOpen()
            throws Exception {
        Object sourceView = GboardZhuyinToggleTargetFixture.newSoftKeyView(context);
        Object base = newMetadata(TOGGLE_KEY_ID);
        Object patched =
                GboardZhuyinTraditionalSimplifiedToggleRuntime.patchIncomingSoftKeyMetadata(
                        sourceView, base);
        GboardZhuyinToggleTargetFixture.bind(sourceView, patched, 55L);
        GboardZhuyinTraditionalSimplifiedToggleRuntime.afterSoftKeyBound(sourceView);
        Object originalAction = action(patched, "SLIDE_UP");
        Object originalEntries = field(originalAction, "f").get(originalAction);

        preferences().getClass()
                .getMethod("setSimplifiedEnabled", boolean.class)
                .invoke(preferences(), Boolean.TRUE);
        Object popupAction =
                GboardZhuyinTraditionalSimplifiedToggleRuntime.patchPopupAction(
                        sourceView, originalAction);

        assertNotSame(originalAction, popupAction);
        assertSame(originalEntries, field(popupAction, "f").get(popupAction));
        assertEquals("繁", firstLabel(popupAction));

        FrameLayout untracked = new FrameLayout(context);
        assertSame(originalAction,
                GboardZhuyinTraditionalSimplifiedToggleRuntime.patchPopupAction(
                        untracked, originalAction));
    }

    @Test
    public void dispatchWriteFailureFallsBackWhileSuccessfulUnavailableRefreshConsumes()
            throws Exception {
        Object preferences = preferences();
        preferences.getClass()
                .getMethod("setThrowOnWrite", boolean.class)
                .invoke(preferences, Boolean.TRUE);
        Object dispatcher = newDispatcher(preferences);
        Object action = enumValue("qae", "SLIDE_UP");
        Object metadata = toggleMetadata("簡");

        assertFalse(GboardZhuyinTraditionalSimplifiedToggleRuntime.maybeToggle(
                dispatcher, action, metadata));
        assertFalse((Boolean) preferences.getClass()
                .getMethod("isSimplifiedEnabled")
                .invoke(preferences));
        assertEquals(1, ((Integer) preferences.getClass()
                .getMethod("getWriteAttempts")
                .invoke(preferences)).intValue());

        preferences.getClass()
                .getMethod("setThrowOnWrite", boolean.class)
                .invoke(preferences, Boolean.FALSE);
        assertTrue(GboardZhuyinTraditionalSimplifiedToggleRuntime.maybeToggle(
                dispatcher, action, metadata));
        assertTrue((Boolean) preferences.getClass()
                .getMethod("isSimplifiedEnabled")
                .invoke(preferences));
    }

    @Test
    public void visibleRebindPreservesTokensAndIsolatesOneViewFailure() throws Exception {
        Object successfulView = GboardZhuyinToggleTargetFixture.newSoftKeyView(context);
        Object failingView = GboardZhuyinToggleTargetFixture.newSoftKeyView(context);
        bindAndTrack(successfulView, newMetadata(TOGGLE_KEY_ID), 101L);
        bindAndTrack(failingView, newMetadata(TOGGLE_KEY_ID), 202L);
        GboardZhuyinToggleTargetFixture.setThrowOnBind(failingView, true);

        Object dispatcher = newDispatcher(preferences());
        assertTrue(GboardZhuyinTraditionalSimplifiedToggleRuntime.maybeToggle(
                dispatcher, enumValue("qae", "SLIDE_UP"), toggleMetadata("簡")));

        assertEquals(2, GboardZhuyinToggleTargetFixture.bindCount(successfulView));
        assertEquals(101L, GboardZhuyinToggleTargetFixture.bindToken(successfulView));
        assertToggleAction(GboardZhuyinToggleTargetFixture.boundMetadata(successfulView), "繁");
        assertEquals(1, GboardZhuyinToggleTargetFixture.bindCount(failingView));
        assertEquals(202L, GboardZhuyinToggleTargetFixture.bindToken(failingView));
    }

    private void bindAndTrack(Object view, Object metadata, long token) throws Exception {
        Object patched =
                GboardZhuyinTraditionalSimplifiedToggleRuntime.patchIncomingSoftKeyMetadata(
                        view, metadata);
        GboardZhuyinToggleTargetFixture.bind(view, patched, token);
        GboardZhuyinTraditionalSimplifiedToggleRuntime.afterSoftKeyBound(view);
    }

    private Object newMetadata(int keyId) throws Exception {
        return targetClass("com.google.android.libraries.inputmethod.metadata.SoftKeyDef").getConstructor(int.class).newInstance(Integer.valueOf(keyId));
    }

    private Object toggleMetadata(String label) throws Exception {
        return targetClass("com.google.android.libraries.inputmethod.metadata.SoftKeyDef")
                .getMethod("toggle", int.class, String.class)
                .invoke(null, Integer.valueOf(TOGGLE_KEY_ID), label);
    }

    private Object newDispatcher(Object preferences) throws Exception {
        return targetClass("qil")
                .getConstructor(Context.class, targetClass("qvt"))
                .newInstance(context, preferences);
    }

    private Object preferences() throws Exception {
        return targetClass("qvt").getMethod("instance").invoke(null);
    }

    private void assertToggleAction(Object metadata, String label) throws Exception {
        Object action = action(metadata, "SLIDE_UP");
        assertEquals("SLIDE_UP", ((Enum<?>) field(action, "e").get(action)).name());
        java.util.List<?> entries = (java.util.List<?>) field(action, "f").get(action);
        assertEquals(1, entries.size());
        Object entry = entries.get(0);
        assertEquals(-0x2719, field(entry, "c").getInt(entry));
        assertEquals("COMMIT", ((Enum<?>) field(entry, "d").get(entry)).name());
        assertEquals("", field(entry, "e").get(entry));
        assertEquals(label, firstLabel(action));
    }

    private Object action(Object metadata, String actionName) throws Exception {
        Class<?> actionTypeClass = targetClass("qae");
        return metadata.getClass()
                .getMethod("g", actionTypeClass)
                .invoke(metadata, enumValue("qae", actionName));
    }

    private String firstLabel(Object action) throws Exception {
        java.util.List<?> labels = (java.util.List<?>) field(action, "p").get(action);
        return (String) labels.get(0);
    }

    private Field field(Object receiver, String name) throws Exception {
        Field field = receiver.getClass().getField(name);
        field.setAccessible(true);
        return field;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object enumValue(String className, String value) throws Exception {
        Class<?> enumClass = targetClass(className);
        return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), value);
    }

    private Class<?> targetClass(String name) throws Exception {
        return Class.forName(name, true, GboardZhuyinToggleTargetFixture.classLoader());
    }

    private void invokeStatic(String className, String methodName) throws Exception {
        targetClass(className).getMethod(methodName).invoke(null);
    }
}
