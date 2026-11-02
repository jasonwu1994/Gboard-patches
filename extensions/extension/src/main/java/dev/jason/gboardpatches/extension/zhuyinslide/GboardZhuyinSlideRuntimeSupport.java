package dev.jason.gboardpatches.extension.zhuyinslide;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class GboardZhuyinSlideRuntimeSupport {
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
    private static final String POINTER_TRACKER_CLASS = "qin";

    private static final int PLAIN_TEXT_KEYCODE = -0x2719;

    private final Method softKeyBindMethod;
    private final Field softKeyMetadataField;
    private final Field pointerCurrentOwnerField;
    private final Field actionDefsField;
    private final Field keyLabelTextsField;
    private final Field keyLabelIdsField;
    private final Method exactActionLookupMethod;
    private final Method fallbackActionLookupMethod;
    private final Field actionEntriesField;
    private final Field actionEntryKeyCodeField;
    private final Field actionEntryIntentionField;
    private final Field actionEntryPayloadField;
    private final Constructor<?> actionBuilderConstructor;
    private final Field actionBuilderTypeField;
    private final Method setPopupLabelsMethod;
    private final Method setSingleActionMethod;
    private final Method buildActionMethod;
    private final Constructor<?> metadataBuilderConstructor;
    private final Method copyMetadataMethod;
    private final Method putActionMethod;
    private final Method buildMetadataMethod;
    private final Method currentActionMethod;
    private final Method resolveGestureActionMethod;
    private final Object pressActionType;
    private final Object longPressActionType;
    private final Object slideUpActionType;
    private final Object slideDownActionType;
    private final Object commitIntention;

    GboardZhuyinSlideRuntimeSupport(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> softKeyViewClass = resolve(classLoader, SOFT_KEY_VIEW_CLASS);
        Class<?> actionSetClass = resolve(classLoader, ACTION_SET_CLASS);
        Class<?> actionTypeClass = resolve(classLoader, ACTION_TYPE_CLASS);
        Class<?> actionDefClass = resolve(classLoader, ACTION_DEF_CLASS);
        Class<?> actionEntryClass = resolve(classLoader, ACTION_ENTRY_CLASS);
        Class<?> actionBuilderClass = resolve(classLoader, ACTION_BUILDER_CLASS);
        Class<?> metadataBuilderClass = resolve(classLoader, METADATA_BUILDER_CLASS);
        Class<?> intentionClass = resolve(classLoader, INTENTION_CLASS);
        Class<?> pointerTrackerClass = resolve(classLoader, POINTER_TRACKER_CLASS);

        softKeyBindMethod = softKeyViewClass.getDeclaredMethod("s", actionSetClass, long.class);
        softKeyMetadataField = softKeyViewClass.getDeclaredField("e");
        pointerCurrentOwnerField = pointerTrackerClass.getDeclaredField("m");
        actionDefsField = actionSetClass.getDeclaredField("h");
        keyLabelTextsField = actionSetClass.getDeclaredField("i");
        keyLabelIdsField = actionSetClass.getDeclaredField("j");
        exactActionLookupMethod = actionSetClass.getDeclaredMethod("g", actionTypeClass);
        fallbackActionLookupMethod = actionSetClass.getDeclaredMethod("h", actionTypeClass);
        actionEntriesField = actionDefClass.getDeclaredField("f");
        actionEntryKeyCodeField = actionEntryClass.getDeclaredField("c");
        actionEntryIntentionField = actionEntryClass.getDeclaredField("d");
        actionEntryPayloadField = actionEntryClass.getDeclaredField("e");

        actionBuilderConstructor = actionBuilderClass.getDeclaredConstructor();
        actionBuilderTypeField = actionBuilderClass.getDeclaredField("a");
        setPopupLabelsMethod = actionBuilderClass.getDeclaredMethod("t", String[].class);
        setSingleActionMethod = actionBuilderClass.getDeclaredMethod(
                "s", int.class, intentionClass, Object.class);
        buildActionMethod = actionBuilderClass.getDeclaredMethod("c");

        metadataBuilderConstructor = metadataBuilderClass.getDeclaredConstructor();
        copyMetadataMethod = metadataBuilderClass.getDeclaredMethod("k", actionSetClass);
        putActionMethod = metadataBuilderClass.getDeclaredMethod("t", actionDefClass);
        buildMetadataMethod = metadataBuilderClass.getDeclaredMethod("b");

        currentActionMethod = pointerTrackerClass.getDeclaredMethod("i");
        resolveGestureActionMethod = pointerTrackerClass.getDeclaredMethod(
                "h", float.class, float.class, actionTypeClass);

        setAccessible(
                softKeyBindMethod,
                softKeyMetadataField,
                pointerCurrentOwnerField,
                actionDefsField,
                keyLabelTextsField,
                keyLabelIdsField,
                exactActionLookupMethod,
                fallbackActionLookupMethod,
                actionEntriesField,
                actionEntryKeyCodeField,
                actionEntryIntentionField,
                actionEntryPayloadField,
                actionBuilderConstructor,
                actionBuilderTypeField,
                setPopupLabelsMethod,
                setSingleActionMethod,
                buildActionMethod,
                metadataBuilderConstructor,
                copyMetadataMethod,
                putActionMethod,
                buildMetadataMethod,
                currentActionMethod,
                resolveGestureActionMethod);

        pressActionType = enumValue(actionTypeClass, "PRESS");
        longPressActionType = enumValue(actionTypeClass, "LONG_PRESS");
        slideUpActionType = enumValue(actionTypeClass, "SLIDE_UP");
        slideDownActionType = enumValue(actionTypeClass, "SLIDE_DOWN");
        commitIntention = enumValue(intentionClass, "COMMIT");
    }

    Object currentMetadata(Object softKeyView) throws ReflectiveOperationException {
        return softKeyView == null ? null : softKeyMetadataField.get(softKeyView);
    }

    boolean hasCurrentOwner(Object tracker) throws ReflectiveOperationException {
        return tracker != null && pointerCurrentOwnerField.get(tracker) != null;
    }

    Object pressAction(Object metadata) throws ReflectiveOperationException {
        return findExactAction(metadata, pressActionType);
    }

    Object longPressAction(Object metadata) throws ReflectiveOperationException {
        return findExactAction(metadata, longPressActionType);
    }

    Object slideUpAction(Object metadata) throws ReflectiveOperationException {
        return findExactAction(metadata, slideUpActionType);
    }

    Object slideDownAction(Object metadata) throws ReflectiveOperationException {
        return findExactAction(metadata, slideDownActionType);
    }

    Object findExactAction(Object metadata, Object actionType)
            throws ReflectiveOperationException {
        if (metadata == null || actionType == null) {
            return null;
        }
        return exactActionLookupMethod.invoke(metadata, actionType);
    }

    Object findFallbackAction(Object metadata, Object actionType)
            throws ReflectiveOperationException {
        if (metadata == null || actionType == null) {
            return null;
        }
        return fallbackActionLookupMethod.invoke(metadata, actionType);
    }

    String primaryLabel(Object metadata) throws ReflectiveOperationException {
        if (metadata == null) {
            return null;
        }
        Object labelsObject = keyLabelTextsField.get(metadata);
        if (!(labelsObject instanceof List<?> labels)) {
            return null;
        }
        for (Object label : labels) {
            if (label != null && !label.toString().trim().isEmpty()) {
                return label.toString();
            }
        }
        return null;
    }

    String[] payloadTokens(Object actionDef) throws ReflectiveOperationException {
        if (actionDef == null) {
            return new String[0];
        }
        Object entriesObject = actionEntriesField.get(actionDef);
        if (!(entriesObject instanceof List<?>)) {
            return new String[0];
        }
        List<String> tokens = new ArrayList<String>();
        for (Object entry : (List<?>) entriesObject) {
            if (entry == null) {
                continue;
            }
            Object payload = actionEntryPayloadField.get(entry);
            if (payload instanceof CharSequence) {
                String token = payload.toString();
                if (!token.trim().isEmpty()) {
                    tokens.add(token);
                }
            }
        }
        return tokens.toArray(new String[0]);
    }

    String singlePayload(Object actionDef) throws ReflectiveOperationException {
        String[] tokens = payloadTokens(actionDef);
        return tokens.length == 0 ? null : tokens[0];
    }

    Object buildPatchedMetadata(Object metadata, GboardZhuyinSlidePolicy.PatchPlan plan)
            throws ReflectiveOperationException {
        Object builder = metadataBuilderConstructor.newInstance();
        copyMetadataMethod.invoke(builder, metadata);
        if (plan.slideUpText != null) {
            putActionMethod.invoke(builder, buildPlainTextAction(
                    slideUpActionType, plan.slideUpText));
        }
        if (plan.slideDownText != null) {
            putActionMethod.invoke(builder, buildPlainTextAction(
                    slideDownActionType, plan.slideDownText));
        }
        return buildMetadataMethod.invoke(builder);
    }

    Object resolveGestureAction(Object tracker, float x, float y)
            throws ReflectiveOperationException {
        Object currentAction = currentActionMethod.invoke(tracker);
        return resolveGestureActionMethod.invoke(
                tracker, Float.valueOf(x), Float.valueOf(y), currentAction);
    }

    boolean isVerticalAction(Object action) {
        if (!(action instanceof Enum<?>)) {
            return false;
        }
        String name = ((Enum<?>) action).name();
        return "SLIDE_UP".equals(name) || "SLIDE_DOWN".equals(name);
    }

    private Object buildPlainTextAction(Object actionType, String text)
            throws ReflectiveOperationException {
        Object builder = actionBuilderConstructor.newInstance();
        actionBuilderTypeField.set(builder, actionType);
        setPopupLabelsMethod.invoke(builder, (Object) new String[] {text});
        setSingleActionMethod.invoke(
                builder, Integer.valueOf(PLAIN_TEXT_KEYCODE), commitIntention, text);
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
