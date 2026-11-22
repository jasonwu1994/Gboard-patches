package dev.jason.gboardpatches.extension.shiftdelete;

import android.view.KeyEvent;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rewrites one keyboard soft-key event into a copy that carries another key code.
 *
 * <p>Nothing here is named per version: the event's members are derived from shapes that are
 * unique in the keyboard (an enum with DOWN/UP/PRESS, an entry array, the soft-key metadata,
 * a hardware KeyEvent, a static copy and a four-argument entry constructor), so a renamed
 * field resolves without a new adapter. Any ambiguity throws, and the caller then leaves the
 * keyboard on its native behaviour.
 */
public final class GboardShiftDeleteEventBinding {
    /** Maps one event's action name and key code to the code to deliver. */
    @FunctionalInterface
    public interface KeyMapper {
        int map(String action, int code);
    }

    private static final String SOFT_KEY_DEF =
            "com.google.android.libraries.inputmethod.metadata.SoftKeyDef";
    private static final List<String> ACTION_NAMES = List.of("DOWN", "UP", "PRESS");
    // Distinct non-zero values that tell the entry's code and flags ints apart.
    private static final int PROBE_CODE = 0x5a17;
    private static final int PROBE_FLAGS = 0x2b3c;

    // Resolved members are public for diagnostics and tests only; remap is the supported entry.
    public final Class<?> eventType;
    public final Field action;
    public final Field entries;
    public final Field metadata;
    public final Field hardwareEvent;
    public final Field code;
    public final Field intention;
    public final Field payload;
    public final Field flags;
    public final Method copy;
    private final Constructor<?> entryConstructor;
    private final Class<?> entryType;

    private GboardShiftDeleteEventBinding(Class<?> event) throws ReflectiveOperationException {
        eventType = event;
        action = uniqueField(event, "action enum", f -> hasActionNames(f.getType()));
        entries = uniqueField(event, "entry array", f -> f.getType().isArray()
                && entryConstructorOrNull(f.getType().getComponentType()) != null);
        entryType = entries.getType().getComponentType();
        entryConstructor = entryConstructorOrNull(entryType);
        Class<?> intentionType = entryConstructor.getParameterTypes()[1];
        metadata = uniqueField(event, "SoftKeyDef", f -> f.getType().getName().equals(SOFT_KEY_DEF));
        hardwareEvent = uniqueField(event, "KeyEvent", f -> f.getType() == KeyEvent.class);
        intention = uniqueField(entryType, "intention", f -> f.getType() == intentionType);
        payload = uniqueField(entryType, "payload", f -> f.getType() == Object.class);
        // The entry has two instance ints; the constructor's own stores tell them apart.
        Object probe = entryConstructor.newInstance(PROBE_CODE, null, null, PROBE_FLAGS);
        code = uniqueField(entryType, "code", f -> f.getType() == int.class
                && f.getInt(probe) == PROBE_CODE);
        flags = uniqueField(entryType, "flags", f -> f.getType() == int.class
                && f.getInt(probe) == PROBE_FLAGS);
        copy = uniqueMethod(event, "static event copy", m -> Modifier.isStatic(m.getModifiers())
                && m.getReturnType() == event
                && Arrays.equals(m.getParameterTypes(), new Class<?>[] {event}));
    }

    /** Resolves every member the remap needs; throws when any of them is missing or ambiguous. */
    public static GboardShiftDeleteEventBinding resolve(Class<?> eventType)
            throws ReflectiveOperationException {
        return new GboardShiftDeleteEventBinding(eventType);
    }

    public Class<?> eventType() {
        return eventType;
    }

    public Object remap(Object event, KeyMapper mapper) throws ReflectiveOperationException {
        if (event == null || event.getClass() != eventType) return event;
        Object data = entries.get(event);
        if (data == null || Array.getLength(data) != 1) return event;
        Object entry = Array.get(data, 0);
        if (entry == null) return event;
        int original = code.getInt(entry);
        if (hardwareEvent.get(event) != null || metadata.get(event) == null) return event;
        // Events carrying a payload come from another feature and must never arm this one.
        if (payload.get(entry) != null) return event;
        Object type = action.get(event);
        if (!(type instanceof Enum<?> kind)) return event;
        int mapped = mapper.map(kind.name(), original);
        if (mapped == original) return event;
        Object replacement = entryConstructor.newInstance(
                mapped, intention.get(entry), null, flags.getInt(entry));
        Object result = copy.invoke(null, event);
        Object array = Array.newInstance(entryType, 1);
        Array.set(array, 0, replacement);
        entries.set(result, array);
        return result;
    }

    private static boolean hasActionNames(Class<?> type) {
        if (!type.isEnum()) return false;
        Set<String> names = new HashSet<>();
        for (Object constant : type.getEnumConstants()) names.add(((Enum<?>) constant).name());
        return names.containsAll(ACTION_NAMES);
    }

    /** {@code (int code, Enum intention, Object payload, int flags)}; null unless exactly one. */
    private static Constructor<?> entryConstructorOrNull(Class<?> type) {
        Constructor<?> found = null;
        for (Constructor<?> candidate : type.getDeclaredConstructors()) {
            Class<?>[] p = candidate.getParameterTypes();
            if (p.length == 4 && p[0] == int.class && p[1].isEnum()
                    && p[2] == Object.class && p[3] == int.class) {
                if (found != null) return null;
                found = candidate;
            }
        }
        if (found != null) found.setAccessible(true);
        return found;
    }

    private interface Shape<T> {
        boolean matches(T member) throws ReflectiveOperationException;
    }

    private static Field uniqueField(Class<?> owner, String role, Shape<Field> shape)
            throws ReflectiveOperationException {
        Field found = null;
        for (Field field : owner.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            if (!shape.matches(field)) continue;
            if (found != null) {
                throw new NoSuchFieldException("Shift delete " + role + " is ambiguous in "
                        + owner.getName() + ": " + found.getName() + ", " + field.getName());
            }
            found = field;
        }
        if (found == null) {
            throw new NoSuchFieldException("Shift delete " + role + " missing in " + owner.getName());
        }
        return found;
    }

    private static Method uniqueMethod(Class<?> owner, String role, Shape<Method> shape)
            throws ReflectiveOperationException {
        Method found = null;
        for (Method method : owner.getDeclaredMethods()) {
            if (!shape.matches(method)) continue;
            if (found != null) {
                throw new NoSuchMethodException("Shift delete " + role + " is ambiguous in " + owner.getName());
            }
            found = method;
        }
        if (found == null) {
            throw new NoSuchMethodException("Shift delete " + role + " missing in " + owner.getName());
        }
        found.setAccessible(true);
        return found;
    }
}
