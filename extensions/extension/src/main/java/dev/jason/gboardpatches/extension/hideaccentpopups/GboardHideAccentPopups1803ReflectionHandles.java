package dev.jason.gboardpatches.extension.hideaccentpopups;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Reflection handles for the Gboard 18.0.3 SoftKeyDef / ActionDef metadata model. */
final class GboardHideAccentPopups1803ReflectionHandles {
    private final Method exactActionLookup;       // SoftKeyDef.h(pmy)
    private final Field actionEntries;             // ActionDef.d : pnu[]
    private final Field actionPopupLabels;         // ActionDef.n : String[]
    private final Field actionPopupIcons;          // ActionDef.o : int[]
    private final Field entryKeycode;              // pnu.c : int
    private final Field entryPayload;              // pnu.e : Object
    private final Constructor<?> actionBuilder;    // pmz (ActionDef builder)
    private final Method actionBuilderCopy;        // pmz.j(ActionDef), copies all but entries
    private final Field actionBuilderEntries;      // pmz.b
    private final Field actionBuilderLabels;       // pmz.c
    private final Field actionBuilderIcons;        // pmz.d
    private final Method actionBuilderBuild;       // pmz.c()
    private final Constructor<?> metadataBuilder;  // ppo (SoftKeyDef builder)
    private final Method metadataBuilderCopy;      // ppo.j(SoftKeyDef)
    private final Method metadataBuilderPut;       // ppo.t(ActionDef)
    private final Field metadataBuilderActions;    // ppo.b : EnumMap<pmy, ActionDef>
    private final Method metadataBuilderBuild;     // ppo.d()
    private final Object pressType;
    private final Object longPressType;

    GboardHideAccentPopups1803ReflectionHandles(ClassLoader loader) throws Throwable {
        Class<?> softKeyDef = Class.forName(
                "com.google.android.libraries.inputmethod.metadata.SoftKeyDef", false, loader);
        Class<?> actionDef = Class.forName(
                "com.google.android.libraries.inputmethod.metadata.ActionDef", false, loader);
        Class<?> actionType = Class.forName("pmy", false, loader);
        Class<?> entry = Class.forName("pnu", false, loader);
        Class<?> actionBuilderClass = Class.forName("pmz", false, loader);
        Class<?> metadataBuilderClass = Class.forName("ppo", false, loader);

        exactActionLookup = accessible(softKeyDef.getDeclaredMethod("h", actionType));
        actionEntries = accessible(actionDef.getDeclaredField("d"));
        actionPopupLabels = accessible(actionDef.getDeclaredField("n"));
        actionPopupIcons = accessible(actionDef.getDeclaredField("o"));
        entryKeycode = accessible(entry.getDeclaredField("c"));
        entryPayload = accessible(entry.getDeclaredField("e"));
        actionBuilder = accessible(actionBuilderClass.getDeclaredConstructor());
        actionBuilderCopy = accessible(actionBuilderClass.getDeclaredMethod("j", actionDef));
        actionBuilderEntries = accessible(actionBuilderClass.getDeclaredField("b"));
        actionBuilderLabels = accessible(actionBuilderClass.getDeclaredField("c"));
        actionBuilderIcons = accessible(actionBuilderClass.getDeclaredField("d"));
        actionBuilderBuild = accessible(actionBuilderClass.getDeclaredMethod("c"));
        metadataBuilder = accessible(metadataBuilderClass.getDeclaredConstructor());
        metadataBuilderCopy = accessible(metadataBuilderClass.getDeclaredMethod("j", softKeyDef));
        metadataBuilderPut = accessible(metadataBuilderClass.getDeclaredMethod("t", actionDef));
        metadataBuilderActions = accessible(metadataBuilderClass.getDeclaredField("b"));
        metadataBuilderBuild = accessible(metadataBuilderClass.getDeclaredMethod("d"));

        if (actionEntries.getType().getComponentType() != entry
                || actionPopupLabels.getType() != String[].class
                || actionPopupIcons.getType() != int[].class
                || !Map.class.isAssignableFrom(metadataBuilderActions.getType())) {
            throw new IllegalStateException("18.0.3 SoftKeyDef/ActionDef shape drift");
        }
        pressType = enumValue(actionType, "PRESS");
        longPressType = enumValue(actionType, "LONG_PRESS");
    }

    String extractPressText(Object metadata) throws Throwable {
        Object[] entries = entries(exactActionLookup.invoke(metadata, pressType));
        Object payload = entries.length == 0 || entries[0] == null
                ? null : entryPayload.get(entries[0]);
        return payload instanceof CharSequence ? payload.toString() : null;
    }

    /**
     * Returns a copy of {@code metadata} without the accented long-press entries, or {@code null}
     * when the key has nothing to hide.
     */
    Object withoutAccentedLongPressEntries(Object metadata, String pressText) throws Throwable {
        Object longPress = exactActionLookup.invoke(metadata, longPressType);
        Object[] entries = entries(longPress);
        int[] keycodes = new int[entries.length];
        Object[] payloads = new Object[entries.length];
        for (int index = 0; index < entries.length; index++) {
            if (entries[index] != null) {
                keycodes[index] = entryKeycode.getInt(entries[index]);
                payloads[index] = entryPayload.get(entries[index]);
            }
        }
        boolean[] keep = GboardHideAccentPopupsPolicy.planKeepMask(pressText, keycodes, payloads);
        if (keep == null) {
            return null;
        }

        Object builder = metadataBuilder.newInstance();
        metadataBuilderCopy.invoke(builder, metadata);
        Object filteredEntries = filter(entries, keep);
        if (Array.getLength(filteredEntries) == 0) {
            // Nothing but accents: drop LONG_PRESS so the key behaves like one without a popup.
            ((Map<?, ?>) metadataBuilderActions.get(builder)).remove(longPressType);
        } else {
            Object action = actionBuilder.newInstance();
            actionBuilderCopy.invoke(action, longPress);
            actionBuilderEntries.set(action, filteredEntries);
            // Popup labels/icons are either per-entry (filter them too) or shared by all entries.
            Object labels = actionPopupLabels.get(longPress);
            if (labels != null && Array.getLength(labels) == entries.length) {
                actionBuilderLabels.set(action, filter(labels, keep));
            }
            Object icons = actionPopupIcons.get(longPress);
            if (icons != null && Array.getLength(icons) == entries.length) {
                actionBuilderIcons.set(action, filter(icons, keep));
            }
            Object patchedAction = actionBuilderBuild.invoke(action);
            if (patchedAction == null) {
                return null;
            }
            metadataBuilderPut.invoke(builder, patchedAction);
        }
        return metadataBuilderBuild.invoke(builder);
    }

    private Object[] entries(Object action) throws IllegalAccessException {
        Object value = action == null ? null : actionEntries.get(action);
        return value instanceof Object[] ? (Object[]) value : new Object[0];
    }

    /** Copies the elements of {@code array} (any array type) whose {@code keep} flag is set. */
    private static Object filter(Object array, boolean[] keep) {
        int count = 0;
        for (boolean kept : keep) {
            count += kept ? 1 : 0;
        }
        Object result = Array.newInstance(array.getClass().getComponentType(), count);
        for (int index = 0, target = 0; index < keep.length; index++) {
            if (keep[index]) {
                Array.set(result, target++, Array.get(array, index));
            }
        }
        return result;
    }

    private static <T extends AccessibleObject> T accessible(T member) {
        member.setAccessible(true);
        return member;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
    }
}
