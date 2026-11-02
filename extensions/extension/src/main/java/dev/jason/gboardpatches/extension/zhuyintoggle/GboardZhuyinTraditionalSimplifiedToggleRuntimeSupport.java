package dev.jason.gboardpatches.extension.zhuyintoggle;

import android.content.Context;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

final class GboardZhuyinTraditionalSimplifiedToggleRuntimeSupport {
    private static final String SOFT_KEY_VIEW_CLASS =
            "com.google.android.libraries.inputmethod.widgets.SoftKeyView";
    private static final String ACTION_SET_CLASS =
            "com.google.android.libraries.inputmethod.metadata.SoftKeyDef";
    private static final String ACTION_TYPE_CLASS = "qae";
    private static final String ACTION_DEF_CLASS =
            "com.google.android.libraries.inputmethod.metadata.ActionDef";
    private static final String ACTION_ENTRY_CLASS = "qbc";
    private static final String ACTION_BUILDER_CLASS = "qaf";
    private static final String METADATA_BUILDER_CLASS = "qcv";
    private static final String INTENTION_CLASS = "qbb";
    private static final String GESTURE_DISPATCHER_CLASS = "qil";
    private static final String PREFERENCE_CLASS = "qvt";
    private static final String PREFERENCE_BASE_CLASS = "cht";

    private static final int PLAIN_TEXT_KEYCODE = -0x2719;
    public static final int PREFERENCE_KEY_RES_ID = 0x7f140966;
    private static final String PREFERENCE_KEY = "enable_sc_tc_conversion_zh_tw";

    private final Method softKeyBindMethod;
    private final Field softKeyMetadataField;
    private final Field softKeyBindTokenField;
    private final Field metadataKeyIdField;
    private final Field actionDefTypeField;
    private final Field actionDefEntriesField;
    private final Method exactActionLookupMethod;
    private final Field entryKeycodeField;
    private final Field entryPayloadField;
    private final Constructor<?> actionBuilderConstructor;
    private final Field actionBuilderTypeField;
    private final Method setPopupLabelsMethod;
    private final Method actionBuilderCopyMethod;
    private final Method setSingleActionMethod;
    private final Method buildActionMethod;
    private final Constructor<?> metadataBuilderConstructor;
    private final Method copyMetadataMethod;
    private final Method putActionMethod;
    private final Method buildMetadataMethod;
    private final Field dispatcherContextField;
    private final Field dispatcherPreferenceField;
    private final Method preferenceFactoryMethod;
    private final Method preferenceReadMethod;
    private final Method preferenceWriteMethod;
    private final Object slideUpActionType;
    private final Object commitIntention;

    GboardZhuyinTraditionalSimplifiedToggleRuntimeSupport(ClassLoader classLoader)
            throws ReflectiveOperationException {
        Class<?> softKeyViewClass = resolve(classLoader, SOFT_KEY_VIEW_CLASS);
        Class<?> actionSetClass = resolve(classLoader, ACTION_SET_CLASS);
        Class<?> actionTypeClass = resolve(classLoader, ACTION_TYPE_CLASS);
        Class<?> actionDefClass = resolve(classLoader, ACTION_DEF_CLASS);
        Class<?> actionEntryClass = resolve(classLoader, ACTION_ENTRY_CLASS);
        Class<?> actionBuilderClass = resolve(classLoader, ACTION_BUILDER_CLASS);
        Class<?> metadataBuilderClass = resolve(classLoader, METADATA_BUILDER_CLASS);
        Class<?> intentionClass = resolve(classLoader, INTENTION_CLASS);
        Class<?> gestureDispatcherClass = resolve(classLoader, GESTURE_DISPATCHER_CLASS);
        Class<?> preferenceClass = resolve(classLoader, PREFERENCE_CLASS);
        Class<?> preferenceBaseClass = resolve(classLoader, PREFERENCE_BASE_CLASS);

        softKeyBindMethod = softKeyViewClass.getDeclaredMethod("s", actionSetClass, long.class);
        softKeyMetadataField = softKeyViewClass.getDeclaredField("e");
        softKeyBindTokenField = softKeyViewClass.getDeclaredField("f");
        metadataKeyIdField = actionSetClass.getDeclaredField("f");
        actionDefTypeField = actionDefClass.getDeclaredField("e");
        actionDefEntriesField = actionDefClass.getDeclaredField("f");
        exactActionLookupMethod = actionSetClass.getDeclaredMethod("g", actionTypeClass);
        entryKeycodeField = actionEntryClass.getDeclaredField("c");
        entryPayloadField = actionEntryClass.getDeclaredField("e");

        actionBuilderConstructor = actionBuilderClass.getDeclaredConstructor();
        actionBuilderTypeField = actionBuilderClass.getDeclaredField("a");
        setPopupLabelsMethod = actionBuilderClass.getDeclaredMethod("t", String[].class);
        actionBuilderCopyMethod = actionBuilderClass.getDeclaredMethod("i", actionDefClass);
        setSingleActionMethod = actionBuilderClass.getDeclaredMethod(
                "s", int.class, intentionClass, Object.class);
        buildActionMethod = actionBuilderClass.getDeclaredMethod("c");

        metadataBuilderConstructor = metadataBuilderClass.getDeclaredConstructor();
        copyMetadataMethod = metadataBuilderClass.getDeclaredMethod("k", actionSetClass);
        putActionMethod = metadataBuilderClass.getDeclaredMethod("t", actionDefClass);
        buildMetadataMethod = metadataBuilderClass.getDeclaredMethod("b");

        dispatcherContextField = gestureDispatcherClass.getDeclaredField("b");
        dispatcherPreferenceField = gestureDispatcherClass.getDeclaredField("c");
        preferenceFactoryMethod = preferenceClass.getDeclaredMethod("I", Context.class);
        preferenceReadMethod = preferenceBaseClass.getDeclaredMethod(
                "x", int.class, boolean.class);
        preferenceWriteMethod = preferenceClass.getDeclaredMethod(
                "f", String.class, boolean.class);

        setAccessible(
                softKeyBindMethod,
                softKeyMetadataField,
                softKeyBindTokenField,
                metadataKeyIdField,
                actionDefTypeField,
                actionDefEntriesField,
                exactActionLookupMethod, entryKeycodeField, entryPayloadField,
                actionBuilderConstructor,
                actionBuilderTypeField,
                setPopupLabelsMethod,
                actionBuilderCopyMethod,
                setSingleActionMethod,
                buildActionMethod,
                metadataBuilderConstructor,
                copyMetadataMethod,
                putActionMethod,
                buildMetadataMethod,
                dispatcherContextField,
                dispatcherPreferenceField,
                preferenceFactoryMethod,
                preferenceReadMethod,
                preferenceWriteMethod);

        slideUpActionType = enumValue(actionTypeClass, "SLIDE_UP");
        commitIntention = enumValue(intentionClass, "COMMIT");
    }

    Object currentMetadata(Object softKeyView) throws ReflectiveOperationException {
        return softKeyView == null ? null : softKeyMetadataField.get(softKeyView);
    }

    int keyId(Object metadata) throws ReflectiveOperationException {
        if (metadata == null) {
            return View.NO_ID;
        }
        Object value = metadataKeyIdField.get(metadata);
        return value instanceof Integer ? ((Integer) value).intValue() : View.NO_ID;
    }

    String actionName(Object action) {
        return action instanceof Enum<?> ? ((Enum<?>) action).name() : null;
    }

    String actionDefinitionName(Object actionDef) throws ReflectiveOperationException {
        return actionDef == null ? null : actionName(actionDefTypeField.get(actionDef));
    }

    boolean isExactPlaceholderMetadata(Object metadata) throws ReflectiveOperationException {
        return metadata != null && isExactPlaceholderAction(
                exactActionLookupMethod.invoke(metadata, slideUpActionType));
    }

    boolean isExactPlaceholderAction(Object action) throws IllegalAccessException {
        if (action == null) return false;
        Object value = actionDefEntriesField.get(action);
        if (!(value instanceof List<?> entries) || entries.size() != 1) return false;
        Object entry = entries.get(0);
        if (entry == null || entryKeycodeField.getInt(entry) != PLAIN_TEXT_KEYCODE) return false;
        Object payload = entryPayloadField.get(entry);
        return payload instanceof CharSequence && ((CharSequence) payload).length() == 0;
    }

    Object buildToggleMetadata(Object metadata, String label)
            throws ReflectiveOperationException {
        if (metadata == null || label == null) {
            return metadata;
        }
        Object builder = metadataBuilderConstructor.newInstance();
        copyMetadataMethod.invoke(builder, metadata);
        putActionMethod.invoke(builder, buildToggleAction(label));
        Object built = buildMetadataMethod.invoke(builder);
        return built != null ? built : metadata;
    }

    Object copyPopupActionWithLabel(Object actionDef, String label)
            throws ReflectiveOperationException {
        if (actionDef == null || label == null) {
            return actionDef;
        }
        Object builder = actionBuilderConstructor.newInstance();
        actionBuilderCopyMethod.invoke(builder, actionDef);
        setPopupLabelsMethod.invoke(builder, (Object) new String[] {label});
        Object built = buildActionMethod.invoke(builder);
        return built != null ? built : actionDef;
    }

    void rebind(Object softKeyView, Object metadata) throws ReflectiveOperationException {
        if (softKeyView == null || metadata == null) {
            return;
        }
        Object tokenValue = softKeyBindTokenField.get(softKeyView);
        long token = tokenValue instanceof Long ? ((Long) tokenValue).longValue() : 0L;
        softKeyBindMethod.invoke(softKeyView, metadata, token);
    }

    Context dispatcherContext(Object dispatcher) throws ReflectiveOperationException {
        Object value = dispatcher == null ? null : dispatcherContextField.get(dispatcher);
        return value instanceof Context ? (Context) value : null;
    }

    Object dispatcherPreferences(Object dispatcher) throws ReflectiveOperationException {
        return dispatcher == null ? null : dispatcherPreferenceField.get(dispatcher);
    }

    Boolean readSimplifiedEnabled(Context context) throws ReflectiveOperationException {
        if (context == null) {
            return null;
        }
        Object preferences = preferenceFactoryMethod.invoke(null, context);
        return readSimplifiedEnabled(preferences);
    }

    Boolean readSimplifiedEnabled(Object preferences) throws ReflectiveOperationException {
        if (preferences == null) {
            return null;
        }
        Object value = preferenceReadMethod.invoke(
                preferences, Integer.valueOf(PREFERENCE_KEY_RES_ID), Boolean.FALSE);
        return value instanceof Boolean ? (Boolean) value : null;
    }

    boolean writeSimplifiedEnabled(Object preferences, boolean enabled)
            throws ReflectiveOperationException {
        if (preferences == null) {
            return false;
        }
        preferenceWriteMethod.invoke(
                preferences,
                PREFERENCE_KEY,
                Boolean.valueOf(enabled));
        return true;
    }

    private Object buildToggleAction(String label) throws ReflectiveOperationException {
        Object builder = actionBuilderConstructor.newInstance();
        actionBuilderTypeField.set(builder, slideUpActionType);
        setPopupLabelsMethod.invoke(builder, (Object) new String[] {label});
        setSingleActionMethod.invoke(
                builder,
                Integer.valueOf(PLAIN_TEXT_KEYCODE),
                commitIntention,
                "");
        return buildActionMethod.invoke(builder);
    }

    private static Class<?> resolve(ClassLoader classLoader, String className)
            throws ClassNotFoundException {
        return Class.forName(className, false, classLoader);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
    }

    private static void setAccessible(Object... members) {
        for (Object member : members) {
            if (member instanceof Method) {
                ((Method) member).setAccessible(true);
            } else if (member instanceof Field) {
                ((Field) member).setAccessible(true);
            } else if (member instanceof Constructor<?>) {
                ((Constructor<?>) member).setAccessible(true);
            }
        }
    }
}
