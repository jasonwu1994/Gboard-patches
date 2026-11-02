package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.content.Context;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/** Four normal draggable APs, sharing the existing catalog/controller hook pair. */
public final class GboardEditingAccessPointsContribution {
    public static final GboardEditingAccessPointsContribution INSTANCE =
            new GboardEditingAccessPointsContribution();
    private static volatile Handles handles;

    private GboardEditingAccessPointsContribution() {}

    public Object extendOrderCatalog(Context context, Object original) {
        try {
            if (!(original instanceof Collection<?> stock)) return original;
            prepare(context.getClassLoader());
            List<String> result = extend(stock,
                    action -> GboardEditingAccessPointsRuntime.enabled(context, action));
            Class<?> catalog = Class.forName("wpa", false, original.getClass().getClassLoader());
            Method copy = catalog.getDeclaredMethod("n", Collection.class);
            copy.setAccessible(true);
            return copy.invoke(null, result);
        } catch (Throwable ignored) {
            return original;
        }
    }

    static List<String> extend(Collection<?> stock, Predicate<EditorAction> enabled) {
        List<String> result = new ArrayList<>();
        if (stock != null) {
            for (Object value : stock) {
                if (value instanceof String token && !result.contains(token)) result.add(token);
            }
        }
        for (EditorAction action : EditorAction.values()) {
            if (enabled.test(action) && !result.contains(action.token)) result.add(action.token);
        }
        return result;
    }

    public static synchronized void prepare(ClassLoader loader) throws ReflectiveOperationException {
        if (handles == null || handles.loader != loader) handles = new Handles(loader);
    }

    public void register(Object controller, Context context) throws Throwable {
        if (controller == null || context == null) return;
        prepare(controller.getClass().getClassLoader());
        Handles binding = handles;
        for (EditorAction action : EditorAction.values()) {
            if (GboardEditingAccessPointsRuntime.enabled(context, action)) {
                int icon = icon(action);
                // Reject missing/wrong resource types before admitting the descriptor.
                if (!"drawable".equals(context.getResources().getResourceTypeName(icon))) continue;
                String label = label(action);
                binding.register(controller, action, icon, label,
                        () -> GboardEditingAccessPointsRuntime.click(action));
            }
        }
    }

    static int icon(EditorAction action) {
        return switch (action) {
            case SELECT_ALL -> 0x7f0804c1;
            case COPY -> 0x7f08042b;
            case CUT -> 0x7f08042c;
            case PASTE -> 0x7f08042e;
        };
    }

    // Access Menu labels stay English regardless of the target Context locale.
    static String label(EditorAction action) {
        return switch (action) {
            case SELECT_ALL -> "Select All";
            case COPY -> "Copy";
            case CUT -> "Cut";
            case PASTE -> "Paste";
        };
    }

    static final class Handles {
        final ClassLoader loader;
        final Method factory, token, icon, runnable, build, register;
        final Field title;

        Handles(ClassLoader loader) throws ReflectiveOperationException {
            this.loader = loader;
            Class<?> descriptor = Class.forName("mtr", false, loader);
            Class<?> builder = Class.forName("mtm", false, loader);
            Class<?> controller = Class.forName("mwv", false, loader);
            factory = method(descriptor, "c", builder, true);
            token = method(builder, "l", void.class, false, String.class);
            icon = method(builder, "i", void.class, false, int.class);
            runnable = method(builder, "q", void.class, false, Runnable.class);
            build = method(builder, "a", descriptor, false);
            register = method(controller, "g", void.class, false, descriptor, boolean.class);
            title = field(builder, "d");
        }

        void register(Object controller, EditorAction action, int iconId,
                String text, Runnable callback) throws ReflectiveOperationException {
            Object builder = factory.invoke(null);
            token.invoke(builder, action.token);
            icon.invoke(builder, iconId);
            title.set(builder, text);
            runnable.invoke(builder, callback);
            register.invoke(controller, build.invoke(builder), false);
        }

        private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
            Field field = owner.getDeclaredField(name);
            if (field.getType() != String.class || Modifier.isStatic(field.getModifiers())) {
                throw new NoSuchFieldException(name);
            }
            field.setAccessible(true);
            return field;
        }

        private static Method method(Class<?> owner, String name, Class<?> result,
                boolean isStatic, Class<?>... args) throws ReflectiveOperationException {
            Method method = owner.getDeclaredMethod(name, args);
            if (method.getReturnType() != result
                    || Modifier.isStatic(method.getModifiers()) != isStatic) {
                throw new NoSuchMethodException(name);
            }
            method.setAccessible(true);
            return method;
        }
    }
}
