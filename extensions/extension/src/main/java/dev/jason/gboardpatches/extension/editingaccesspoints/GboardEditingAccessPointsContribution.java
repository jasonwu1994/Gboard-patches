package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.content.Context;
import android.graphics.drawable.Icon;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/** Draggable editing & cursor APs, sharing the existing catalog/controller hook pair. */
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
                Icon icon = GboardEditingAccessPointIcons.createIcon(context, action.token);
                if (icon == null) continue;
                String label = label(context, action);
                binding.register(controller, action, icon, label,
                        () -> GboardEditingAccessPointsRuntime.click(action));
            }
        }
    }

    /** Access Menu label in the patches settings language; English when none is packaged. */
    static String label(Context context, EditorAction action) {
        return GboardSettingsText.packagedOrDefault(context, labelRes(action), label(action));
    }

    static int labelRes(EditorAction action) {
        return switch (action) {
            case SELECT_ALL -> R.string.gboard_edit_ap_label_select_all;
            case COPY -> R.string.gboard_edit_ap_label_copy;
            case CUT -> R.string.gboard_edit_ap_label_cut;
            case PASTE -> R.string.gboard_edit_ap_label_paste;
            case FORWARD_DELETE -> R.string.gboard_edit_ap_label_forward_delete;
            case PASTE_PLAIN_TEXT -> R.string.gboard_edit_ap_label_paste_plain_text;
            case HOME -> R.string.gboard_edit_ap_label_home;
            case END -> R.string.gboard_edit_ap_label_end;
            case TAB -> R.string.gboard_edit_ap_label_tab;
            case ARROW_LEFT -> R.string.gboard_edit_ap_label_arrow_left;
            case ARROW_RIGHT -> R.string.gboard_edit_ap_label_arrow_right;
            case ARROW_UP -> R.string.gboard_edit_ap_label_arrow_up;
            case ARROW_DOWN -> R.string.gboard_edit_ap_label_arrow_down;
        };
    }

    /** English fallback; matches the English entries of the packaged labels. */
    static String label(EditorAction action) {
        return switch (action) {
            case SELECT_ALL -> "Select All";
            case COPY -> "Copy";
            case CUT -> "Cut";
            case PASTE -> "Paste";
            case FORWARD_DELETE -> "Forward Delete";
            case PASTE_PLAIN_TEXT -> "Paste Plain";
            case HOME -> "Home";
            case END -> "End";
            case TAB -> "Tab";
            case ARROW_LEFT -> "Left";
            case ARROW_RIGHT -> "Right";
            case ARROW_UP -> "Up";
            case ARROW_DOWN -> "Down";
        };
    }

    static final class Handles {
        final ClassLoader loader;
        final Method factory, token, icon, runnable, build, register;
        final Field iconObject, title;

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
            iconObject = field(builder, "c", Icon.class);
            title = field(builder, "d", String.class);
        }

        void register(Object controller, EditorAction action, Icon image,
                String text, Runnable callback) throws ReflectiveOperationException {
            Object builder = factory.invoke(null);
            token.invoke(builder, action.token);
            // No target drawable id: the self-drawn Icon object is the descriptor's image.
            icon.invoke(builder, 0);
            iconObject.set(builder, image);
            title.set(builder, text);
            runnable.invoke(builder, callback);
            register.invoke(controller, build.invoke(builder), false);
        }

        private static Field field(Class<?> owner, String name, Class<?> type)
                throws ReflectiveOperationException {
            Field field = owner.getDeclaredField(name);
            if (field.getType() != type || Modifier.isStatic(field.getModifiers())) {
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
