package dev.jason.gboardpatches.extension.addsymbols;

import android.util.Log;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unused")
public final class GboardAddSymbolsSoftKeyRuntime {
    private static final String TAG = "GboardAddSymbols";
    private static final String ACTION_SET_CLASS =
            "com.google.android.libraries.inputmethod.metadata.SoftKeyDef";
    private static final String ACTION_TYPE_CLASS = "qae";
    private static final String ACTION_DEF_CLASS =
            "com.google.android.libraries.inputmethod.metadata.ActionDef";
    private static final String ACTION_ENTRY_CLASS = "qbc";
    private static final String ACTION_BUILDER_CLASS = "qaf";
    private static final String METADATA_BUILDER_CLASS = "qcv";
    private static final String INTENTION_CLASS = "qbb";
    private static final String KEYBOARD_TYPE_CLASS = "qch";

    private static final int POPUP_LAYOUT_ATTRIBUTE_RES_ID = 0x7f04027f;
    private static final int SWITCH_KEYBOARD_KEYCODE = -0x2714;
    private static final int SWITCH_TO_ONE_HANDED_MODE_KEYCODE = -0x2749;
    private static final int OPEN_EXTENSION_KEYCODE = -0x274a;
    private static final int LAUNCH_PREFERENCE_ACTIVITY_KEYCODE = -0x2723;
    private static final int SWITCH_KEYBOARD_FROM_ACCESS_POINT_KEYCODE = -0x278b;
    private static final int SWITCH_KEYBOARD_FROM_ACCESS_POINT_AND_CLOSE_EXTENSION_KEYCODE =
            -0x2791;
    private static final String CUSTOM_POPUP_LABEL = "♥";
    private static final String CUSTOM_KEYBOARD_TYPE = "jasondev_symbol";
    private static final String EMOJI_OR_GIF_EXTENSION_INTERFACE_CLASS =
            "com.google.android.apps.inputmethod.libs.expression.extension.IEmojiOrGifExtension";

    private static final Map<ClassLoader, ReflectionHandles> HANDLES_BY_CLASS_LOADER =
            new WeakHashMap<>();
    private static final AtomicInteger PATCH_LOG_COUNT = new AtomicInteger(0);
    private static final AtomicInteger ERROR_LOG_COUNT = new AtomicInteger(0);

    private GboardAddSymbolsSoftKeyRuntime() {
    }

    private enum Decision {
        NONE,
        REPLACE,
        APPEND
    }

    private static Decision plan(boolean commaLike, boolean hasExpressionTemplate,
            int customEntryIndex, boolean alreadyContainsCustom) {
        if (!commaLike || !hasExpressionTemplate || alreadyContainsCustom) {
            return Decision.NONE;
        }
        return customEntryIndex >= 0 ? Decision.REPLACE : Decision.APPEND;
    }

    private static void logSoftKeyPatch(Decision decision) {
        if (PATCH_LOG_COUNT.getAndIncrement() < 12) {
            Log.i(TAG, "comma shortcut patched decision=" + decision
                    + ", popupAttr=0x"
                    + Integer.toHexString(POPUP_LAYOUT_ATTRIBUTE_RES_ID));
        }
    }

    private static void logSoftKeyFailure(Throwable throwable) {
        if (ERROR_LOG_COUNT.getAndIncrement() < 3) {
            Log.w(TAG, "soft-key patch failed", throwable);
        }
    }

    public static Object patchSoftKeyMetadata(Object softKeyView, Object metadata) {
        if (softKeyView == null || metadata == null) {
            return metadata;
        }
        try {
            ClassLoader classLoader = softKeyView.getClass().getClassLoader();
            if (classLoader == null) {
                return metadata;
            }
            ReflectionHandles handles = reflectionHandles(classLoader);
            boolean commaLike = isCommaLikeKey(handles, metadata);
            Object template = commaLike ? findExpressionTemplate(handles, metadata) : null;
            boolean alreadyContainsCustom = hasCustomShortcut(handles, metadata);
            int xdIndex = findXdEntryIndex(handles, template);
            Decision decision = plan(
                    commaLike, template != null, xdIndex, alreadyContainsCustom);
            if (decision == Decision.NONE) {
                return metadata;
            }

            Object customType = handles.keyboardTypeFactory.invoke(null, CUSTOM_KEYBOARD_TYPE);
            Object patchedLongPress = decision == Decision.REPLACE
                    ? handles.replaceEntry(template, xdIndex, customType)
                    : handles.appendEntry(template, customType);
            if (patchedLongPress == null) {
                return metadata;
            }

            Object builder = handles.metadataBuilderConstructor.newInstance();
            handles.copyMetadataMethod.invoke(builder, metadata);
            handles.replaceAction(builder, handles.longPressActionType, patchedLongPress);
            Object patched = handles.buildMetadataMethod.invoke(builder);
            logSoftKeyPatch(decision);
            return patched != null ? patched : metadata;
        } catch (Throwable throwable) {
            logSoftKeyFailure(throwable);
            return metadata;
        }
    }

    private static boolean isCommaLikeKey(ReflectionHandles handles, Object metadata)
            throws Throwable {
        Object pressAction = handles.exactActionLookupMethod.invoke(
                metadata, handles.pressActionType);
        String pressText = handles.extractSinglePayload(pressAction);
        String primaryLabel = handles.extractPrimaryLabel(metadata);
        return "，".equals(pressText) || "，".equals(primaryLabel)
                || ",".equals(pressText) || ",".equals(primaryLabel);
    }

    private static Object findExpressionTemplate(ReflectionHandles handles, Object metadata)
            throws Throwable {
        for (Object action : handles.extractActionDefs(metadata)) {
            if (handles.isLongPress(action) && findXdEntryIndex(handles, action) >= 0) {
                return action;
            }
        }
        for (Object action : handles.extractActionDefs(metadata)) {
            if (!handles.isLongPress(action) || handles.popupLayout(action) == 0) {
                continue;
            }
            for (Object entry : handles.extractEntries(action)) {
                Integer keycode = handles.entryKeycode(entry);
                if (keycode != null && isExpressionAccessPointKeycode(keycode.intValue())) {
                    return action;
                }
                if (EMOJI_OR_GIF_EXTENSION_INTERFACE_CLASS.equals(
                        handles.entryPayload(entry))) {
                    return action;
                }
            }
        }
        return null;
    }

    private static boolean hasCustomShortcut(ReflectionHandles handles, Object metadata)
            throws Throwable {
        for (Object action : handles.extractActionDefs(metadata)) {
            if (!handles.isLongPress(action)) {
                continue;
            }
            for (Object entry : handles.extractEntries(action)) {
                Integer keycode = handles.entryKeycode(entry);
                if (keycode == null || (keycode.intValue() != SWITCH_KEYBOARD_KEYCODE
                        && keycode.intValue()
                        != SWITCH_KEYBOARD_FROM_ACCESS_POINT_AND_CLOSE_EXTENSION_KEYCODE)) {
                    continue;
                }
                if (CUSTOM_KEYBOARD_TYPE.equals(handles.entryPayload(entry))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int findXdEntryIndex(ReflectionHandles handles, Object action)
            throws Throwable {
        Object[] entries = handles.extractEntries(action);
        String[] labels = handles.popupLabels(action);
        for (int index = 0; index < entries.length; index++) {
            if (index < labels.length && "XD".equals(labels[index])) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isExpressionAccessPointKeycode(int keycode) {
        return keycode == OPEN_EXTENSION_KEYCODE
                || keycode == LAUNCH_PREFERENCE_ACTIVITY_KEYCODE
                || keycode == SWITCH_TO_ONE_HANDED_MODE_KEYCODE
                || keycode == SWITCH_KEYBOARD_FROM_ACCESS_POINT_KEYCODE
                || keycode == SWITCH_KEYBOARD_FROM_ACCESS_POINT_AND_CLOSE_EXTENSION_KEYCODE;
    }

    private static ReflectionHandles reflectionHandles(ClassLoader classLoader) throws Throwable {
        synchronized (HANDLES_BY_CLASS_LOADER) {
            ReflectionHandles cached = HANDLES_BY_CLASS_LOADER.get(classLoader);
            if (cached != null) {
                return cached;
            }
            ReflectionHandles created = new ReflectionHandles(classLoader);
            HANDLES_BY_CLASS_LOADER.put(classLoader, created);
            return created;
        }
    }

    private static final class ReflectionHandles {
        final Field actionDefsField;
        final Field keyLabelsField;
        final Method exactActionLookupMethod;
        final Field actionTypeField;
        final Field actionEntriesField;
        final Field popupLayoutField;
        final Field popupLabelsField;
        final Field popupIconsField;
        final Field entryKeycodeField;
        final Field entryPayloadField;
        final Constructor<?> entryConstructor;
        final Method keyboardTypeFactory;
        final Field keyboardTypeNameField;
        final Constructor<?> actionBuilderConstructor;
        final Field actionBuilderTypeField;
        final Method setActionEntriesMethod;
        final Method setPopupLabelsMethod;
        final Field actionBuilderIconsField;
        final Method copyActionMethod;
        final Method buildActionMethod;
        final Constructor<?> metadataBuilderConstructor;
        final Method putActionMethod;
        final Method copyMetadataMethod;
        final Method buildMetadataMethod;
        final Object pressActionType;
        final Object longPressActionType;

        ReflectionHandles(ClassLoader classLoader) throws Throwable {
            Class<?> actionSetClass = resolve(classLoader, ACTION_SET_CLASS);
            Class<?> actionTypeClass = resolve(classLoader, ACTION_TYPE_CLASS);
            Class<?> actionDefClass = resolve(classLoader, ACTION_DEF_CLASS);
            Class<?> actionEntryClass = resolve(classLoader, ACTION_ENTRY_CLASS);
            Class<?> actionBuilderClass = resolve(classLoader, ACTION_BUILDER_CLASS);
            Class<?> metadataBuilderClass = resolve(classLoader, METADATA_BUILDER_CLASS);
            Class<?> intentionClass = resolve(classLoader, INTENTION_CLASS);
            Class<?> keyboardTypeClass = resolve(classLoader, KEYBOARD_TYPE_CLASS);

            actionDefsField = field(actionSetClass, "h");
            keyLabelsField = field(actionSetClass, "i");
            exactActionLookupMethod = method(actionSetClass, "g", actionTypeClass);
            actionTypeField = field(actionDefClass, "e");
            actionEntriesField = field(actionDefClass, "f");
            popupLayoutField = field(actionDefClass, "i");
            popupLabelsField = field(actionDefClass, "p");
            popupIconsField = field(actionDefClass, "q");
            entryKeycodeField = field(actionEntryClass, "c");
            entryPayloadField = field(actionEntryClass, "e");
            entryConstructor = constructor(
                    actionEntryClass, int.class, intentionClass, Object.class, int.class);
            keyboardTypeFactory = method(resolve(classLoader, "qaz"), "ag", Object.class);
            keyboardTypeNameField = field(keyboardTypeClass, "m");
            actionBuilderConstructor = constructor(actionBuilderClass);
            actionBuilderTypeField = field(actionBuilderClass, "a");
            setActionEntriesMethod = method(actionBuilderClass, "n",
                    Array.newInstance(actionEntryClass, 0).getClass());
            setPopupLabelsMethod = method(actionBuilderClass, "t", String[].class);
            actionBuilderIconsField = field(actionBuilderClass, "d");
            copyActionMethod = method(actionBuilderClass, "i", actionDefClass);
            buildActionMethod = method(actionBuilderClass, "c");
            metadataBuilderConstructor = constructor(metadataBuilderClass);
            putActionMethod = method(metadataBuilderClass, "t", actionDefClass);
            copyMetadataMethod = method(metadataBuilderClass, "k", actionSetClass);
            buildMetadataMethod = method(metadataBuilderClass, "b");
            pressActionType = enumValue(actionTypeClass, "PRESS");
            longPressActionType = enumValue(actionTypeClass, "LONG_PRESS");
        }

        Object[] extractActionDefs(Object metadata) throws IllegalAccessException {
            Object value = metadata == null ? null : actionDefsField.get(metadata);
            return value instanceof List<?> items ? items.toArray() : new Object[0];
        }

        Object[] extractEntries(Object action) throws IllegalAccessException {
            Object value = action == null ? null : actionEntriesField.get(action);
            return value instanceof List<?> items ? items.toArray() : new Object[0];
        }

        boolean isLongPress(Object action) throws IllegalAccessException {
            return action != null && longPressActionType.equals(actionTypeField.get(action));
        }

        int popupLayout(Object action) throws IllegalAccessException {
            return action == null ? 0 : popupLayoutField.getInt(action);
        }

        String[] popupLabels(Object action) throws IllegalAccessException {
            Object value = action == null ? null : popupLabelsField.get(action);
            return value instanceof List<?> labels ? labels.toArray(new String[0]) : new String[0];
        }

        int[] popupIcons(Object action) throws IllegalAccessException {
            Object value = action == null ? null : popupIconsField.get(action);
            return value instanceof int[] ? (int[]) value : new int[0];
        }

        Integer entryKeycode(Object entry) throws IllegalAccessException {
            return entry == null ? null : Integer.valueOf(entryKeycodeField.getInt(entry));
        }

        String entryPayload(Object entry) throws IllegalAccessException {
            if (entry == null) {
                return null;
            }
            Object payload = entryPayloadField.get(entry);
            if (payload instanceof CharSequence) {
                return payload.toString();
            }
            if (payload != null && keyboardTypeNameField.getDeclaringClass().isInstance(payload)) {
                Object name = keyboardTypeNameField.get(payload);
                return name instanceof String ? (String) name : null;
            }
            return null;
        }

        String extractSinglePayload(Object action) throws IllegalAccessException {
            Object[] entries = extractEntries(action);
            return entries.length == 0 ? null : entryPayload(entries[0]);
        }

        String extractPrimaryLabel(Object metadata) throws IllegalAccessException {
            Object value = metadata == null ? null : keyLabelsField.get(metadata);
            if (!(value instanceof List<?> labels)) {
                return null;
            }
            for (Object label : labels) {
                if (label != null && !label.toString().isBlank()) {
                    return label.toString();
                }
            }
            return null;
        }

        Object replaceEntry(Object template, int index, Object customType) throws Throwable {
            Object[] entries = extractEntries(template);
            if (index < 0 || index >= entries.length) {
                return null;
            }
            Object builder = copiedActionBuilder(template);
            Object updatedEntries = Array.newInstance(
                    entryConstructor.getDeclaringClass(), entries.length);
            System.arraycopy(entries, 0, updatedEntries, 0, entries.length);
            Array.set(updatedEntries, index, entryConstructor.newInstance(
                    SWITCH_KEYBOARD_FROM_ACCESS_POINT_AND_CLOSE_EXTENSION_KEYCODE,
                    null, customType, Integer.MAX_VALUE));
            setActionEntriesMethod.invoke(builder, updatedEntries);
            String[] labels = normalizeLabels(popupLabels(template), entries.length);
            labels[index] = CUSTOM_POPUP_LABEL;
            setPopupLabelsMethod.invoke(builder, (Object) labels);
            actionBuilderIconsField.set(builder,
                    normalizeIcons(popupIcons(template), entries.length));
            return buildActionMethod.invoke(builder);
        }

        Object appendEntry(Object template, Object customType) throws Throwable {
            Object[] entries = extractEntries(template);
            Object builder = copiedActionBuilder(template);
            Object updatedEntries = Array.newInstance(
                    entryConstructor.getDeclaringClass(), entries.length + 1);
            System.arraycopy(entries, 0, updatedEntries, 0, entries.length);
            Array.set(updatedEntries, entries.length, entryConstructor.newInstance(
                    SWITCH_KEYBOARD_FROM_ACCESS_POINT_AND_CLOSE_EXTENSION_KEYCODE,
                    null, customType, Integer.MAX_VALUE));
            setActionEntriesMethod.invoke(builder, updatedEntries);
            String[] labels = normalizeLabels(popupLabels(template), entries.length + 1);
            labels[entries.length] = CUSTOM_POPUP_LABEL;
            setPopupLabelsMethod.invoke(builder, (Object) labels);
            actionBuilderIconsField.set(builder,
                    normalizeIcons(popupIcons(template), entries.length + 1));
            return buildActionMethod.invoke(builder);
        }

        void replaceAction(Object metadataBuilder, Object actionType, Object action)
                throws Throwable {
            putActionMethod.invoke(metadataBuilder, action);
        }

        private Object copiedActionBuilder(Object template) throws Throwable {
            Object builder = actionBuilderConstructor.newInstance();
            copyActionMethod.invoke(builder, template);
            actionBuilderTypeField.set(builder, longPressActionType);
            return builder;
        }

        private static String[] normalizeLabels(String[] source, int size) {
            String[] result = new String[size];
            if (source != null && source.length == 1 && size > 1) {
                for (int index = 0; index < size; index++) {
                    result[index] = source[0];
                }
            } else if (source != null) {
                System.arraycopy(source, 0, result, 0, Math.min(source.length, size));
            }
            return result;
        }

        private static int[] normalizeIcons(int[] source, int size) {
            int[] result = new int[size];
            if (source != null && source.length == 1 && size > 1) {
                for (int index = 0; index < size; index++) {
                    result[index] = source[0];
                }
            } else if (source != null) {
                System.arraycopy(source, 0, result, 0, Math.min(source.length, size));
            }
            return result;
        }
    }

    private static Class<?> resolve(ClassLoader classLoader, String name)
            throws ClassNotFoundException {
        return Class.forName(name, false, classLoader);
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Constructor<?> constructor(Class<?> owner, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Constructor<?> constructor = owner.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
    }
}
