package dev.jason.gboardpatches.extension.websearch;

import android.content.Context;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class GboardFloatingWebSearchAccessPointContribution {
    public static final GboardFloatingWebSearchAccessPointContribution INSTANCE =
            new GboardFloatingWebSearchAccessPointContribution();
    public static final String TOKEN = "web_search";
    static final int GOOGLE_G_DRAWABLE_ID = 0x7f080467;

    private static volatile Handles handles;

    private GboardFloatingWebSearchAccessPointContribution() {
    }

    public Object extendOrderCatalog(Context context, Object original) {
        try {
            if (!GboardFloatingWebSearchRuntime.isEnabled(context)
                    || !(original instanceof Set<?> collection)) {
                return original;
            }
            Class<?> immutableCollection = Class.forName(
                    "wpa", false, original.getClass().getClassLoader());
            if (!immutableCollection.isInstance(original)) return original;
            List<String> values = copyStrings(collection);
            if (values.size() != collection.size() || values.contains(TOKEN)) return original;
            values.add(TOKEN);
            Method copy = immutableCollection.getDeclaredMethod("n", Collection.class);
            if (copy.getReturnType() != immutableCollection
                    || !Modifier.isStatic(copy.getModifiers())) return original;
            copy.setAccessible(true);
            return copy.invoke(null, values);
        } catch (Throwable ignored) {
            return original;
        }
    }

    public void register(Object controller, Context context) {
        try {
            if (controller == null || context == null
                    || !GboardFloatingWebSearchRuntime.isEnabled(context)) {
                return;
            }
            if (!isGoogleIcon(context)) return;
            Context application = context.getApplicationContext();
            Context safeContext = application != null ? application : context;
            Handles active = handles(controller.getClass().getClassLoader());
            if (!active.controllerClass.isInstance(controller)) return;
            Object builder = active.descriptorBuilderFactory.invoke(null);
            active.builderTokenMethod.invoke(builder, TOKEN);
            active.builderIconResourceMethod.invoke(builder, GOOGLE_G_DRAWABLE_ID);
            active.builderLabelTextField.set(builder, "Web Search");
            active.builderRunnableMethod.invoke(builder, new OpenAction(safeContext));
            Object descriptor = active.builderBuildMethod.invoke(builder);
            active.controllerRegisterMethod.invoke(controller, descriptor, false);
        } catch (Throwable ignored) {
            // A synthetic Access Point must fail closed.
        }
    }

    private static boolean isGoogleIcon(Context context) {
        if (!"drawable".equals(context.getResources().getResourceTypeName(GOOGLE_G_DRAWABLE_ID))) {
            return false;
        }
        String name = context.getResources().getResourceEntryName(GOOGLE_G_DRAWABLE_ID);
        return "APKTOOL_RENAMED_0x7f080467".equals(name)
                || "drawable_0x7f080467".equals(name);
    }

    static List<String> copyStrings(Collection<?> values) {
        List<String> result = new ArrayList<>();
        if (values != null) {
            for (Object value : values) {
                if (value instanceof String stringValue && !result.contains(stringValue)) {
                    result.add(stringValue);
                }
            }
        }
        return result;
    }

    private static Handles handles(ClassLoader classLoader) throws Throwable {
        Handles current = handles;
        if (current != null && current.classLoader == classLoader) {
            return current;
        }
        synchronized (GboardFloatingWebSearchAccessPointContribution.class) {
            current = handles;
            if (current == null || current.classLoader != classLoader) {
                current = Handles.resolve(classLoader);
                handles = current;
            }
            return current;
        }
    }

    private static final class OpenAction implements Runnable {
        private final WeakReference<Context> contextReference;

        OpenAction(Context context) {
            contextReference = new WeakReference<>(context);
        }

        @Override
        public void run() {
            Context context = null;
            try {
                context = contextReference.get();
                GboardFloatingWebSearchRuntime.open(context);
            } catch (Throwable ignored) {
                GboardFloatingWebSearchRuntime.showUnavailable(context);
            }
        }
    }

    private static final class Handles {
        final ClassLoader classLoader;
        final Class<?> controllerClass;
        final Method descriptorBuilderFactory;
        final Method builderTokenMethod;
        final Method builderRunnableMethod;
        final Method builderBuildMethod;
        final Method builderIconResourceMethod;
        final Field builderLabelTextField;
        final Method controllerRegisterMethod;

        Handles(ClassLoader classLoader, Class<?> controllerClass, Method descriptorBuilderFactory,
                Method builderTokenMethod, Method builderRunnableMethod,
                Method builderBuildMethod, Method builderIconResourceMethod,
                Field builderLabelTextField,
                Method controllerRegisterMethod) {
            this.classLoader = classLoader;
            this.controllerClass = controllerClass;
            this.descriptorBuilderFactory = descriptorBuilderFactory;
            this.builderTokenMethod = builderTokenMethod;
            this.builderRunnableMethod = builderRunnableMethod;
            this.builderBuildMethod = builderBuildMethod;
            this.builderIconResourceMethod = builderIconResourceMethod;
            this.builderLabelTextField = builderLabelTextField;
            this.controllerRegisterMethod = controllerRegisterMethod;
        }

        static Handles resolve(ClassLoader classLoader) throws Throwable {
            Class<?> descriptor = Class.forName("mtr", false, classLoader);
            Class<?> builder = Class.forName("mtm", false, classLoader);
            Class<?> controller = Class.forName("mwv", false, classLoader);
            Method descriptorBuilderFactory = method(descriptor, "c", builder, true);
            Method builderTokenMethod = method(builder, "l", void.class, false, String.class);
            Method builderRunnableMethod = method(builder, "q", void.class, false, Runnable.class);
            Method builderBuildMethod = method(builder, "a", descriptor, false);
            Method builderIconResourceMethod = method(builder, "i", void.class, false, int.class);
            Field builderLabelTextField = builder.getDeclaredField("d");
            if (builderLabelTextField.getType() != String.class
                    || Modifier.isStatic(builderLabelTextField.getModifiers())) {
                throw new NoSuchFieldException("mtm.d:String");
            }
            Method controllerRegisterMethod = method(
                    controller, "g", void.class, false, descriptor, boolean.class);
            builderLabelTextField.setAccessible(true);
            return new Handles(classLoader, controller, descriptorBuilderFactory, builderTokenMethod,
                    builderRunnableMethod, builderBuildMethod, builderIconResourceMethod,
                    builderLabelTextField, controllerRegisterMethod);
        }

        private static Method method(Class<?> owner, String name, Class<?> result,
                boolean isStatic, Class<?>... parameters) throws ReflectiveOperationException {
            Method method = owner.getDeclaredMethod(name, parameters);
            if (method.getReturnType() != result
                    || Modifier.isStatic(method.getModifiers()) != isStatic) {
                throw new NoSuchMethodException(owner.getName() + "." + name);
            }
            method.setAccessible(true);
            return method;
        }
    }
}
