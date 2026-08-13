package dev.jason.gboardpatches.extension.manualincognito;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class GboardManualIncognitoAccessPoint1777Support {
    static final String TOKEN = "manual_incognito";
    private static final String LABEL = "Incognito";
    private static final int OFFICIAL_INCOGNITO_ICON_RESOURCE_ID = 0x7f0806c9;
    private static final float OFFICIAL_INCOGNITO_ICON_SCALE = 1.2f;

    private static final String DESCRIPTOR_CLASS = "loi";
    private static final String BUILDER_CLASS = "lod";
    private static final String ORDER_HELPER_CLASS = "lra";
    private static final String IMMUTABLE_COLLECTION_CLASS = "vbq";
    private static final String CONTROLLER_CLASS = "lrq";

    private static final Map<Object, WeakReference<Context>> CONTROLLERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile Handles handles;
    private static volatile ClassLoader catalogCopyClassLoader;
    private static volatile Method catalogCopyMethod;

    private GboardManualIncognitoAccessPoint1777Support() {
    }

    static Object includeToken(Object original) throws Throwable {
        if (!(original instanceof Collection<?> collection)) {
            return original;
        }
        ClassLoader classLoader = original.getClass().getClassLoader();
        return catalogCopyMethod(classLoader).invoke(
                null, appendToken(collection));
    }

    static void register(Object controller, Context context,
            GboardManualIncognitoSettings.RequestedPolicy requested) throws Throwable {
        if (controller == null || context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        Context safeContext = appContext != null ? appContext : context;
        CONTROLLERS.put(controller, new WeakReference<>(safeContext));

        Handles activeHandles = handles(controller.getClass().getClassLoader());
        Object orderHelper = activeHandles.controllerOrderHelperField.get(controller);
        setTokenPresence(activeHandles, orderHelper, requested.featureEnabled);
        if (!requested.featureEnabled) {
            return;
        }

        Object builder = activeHandles.descriptorBuilderFactory.invoke(null);
        activeHandles.builderTokenMethod.invoke(builder, TOKEN);
        Icon icon = enlargedOfficialIncognitoIcon(safeContext);
        if (icon != null) {
            activeHandles.builderIconField.set(builder, icon);
        } else {
            activeHandles.builderIconResourceMethod.invoke(
                    builder, OFFICIAL_INCOGNITO_ICON_RESOURCE_ID);
        }
        activeHandles.builderLabelTextField.set(builder, LABEL);
        activeHandles.builderContentDescriptionTextField.set(builder, LABEL);
        activeHandles.builderRunnableMethod.invoke(
                builder, new ToggleAction(controller, safeContext));
        Object descriptor = activeHandles.builderBuildMethod.invoke(builder);
        activeHandles.controllerRegisterMethod.invoke(controller, descriptor, false);
    }

    static void refreshRegisteredControllers(
            GboardManualIncognitoSettings.RequestedPolicy requested) {
        List<Map.Entry<Object, WeakReference<Context>>> snapshot;
        synchronized (CONTROLLERS) {
            snapshot = new ArrayList<>(CONTROLLERS.entrySet());
        }
        for (Map.Entry<Object, WeakReference<Context>> entry : snapshot) {
            Object controller = entry.getKey();
            Context context = entry.getValue() == null ? null : entry.getValue().get();
            if (controller == null || context == null) {
                continue;
            }
            try {
                register(controller, context, requested);
            } catch (Throwable ignored) {
                // A later controller construction or input session will retry.
            }
        }
    }

    static List<String> appendToken(Collection<?> original) {
        List<String> result = new ArrayList<>();
        if (original != null) {
            for (Object value : original) {
                if (value instanceof String stringValue && !result.contains(stringValue)) {
                    result.add(stringValue);
                }
            }
        }
        if (!result.contains(TOKEN)) {
            result.add(TOKEN);
        }
        return result;
    }

    static List<String> removeToken(Collection<?> original) {
        List<String> result = new ArrayList<>();
        if (original != null) {
            for (Object value : original) {
                if (value instanceof String stringValue
                        && !TOKEN.equals(stringValue)
                        && !result.contains(stringValue)) {
                    result.add(stringValue);
                }
            }
        }
        return result;
    }

    private static void setTokenPresence(Handles activeHandles,
            Object orderHelper, boolean present) throws Throwable {
        Object original = activeHandles.orderMasterCatalogField.get(orderHelper);
        if (!(original instanceof Collection<?> collection)
                || collection.contains(TOKEN) == present) {
            return;
        }
        Object replacement = activeHandles.immutableCollectionCopyMethod.invoke(
                null,
                present ? appendToken(collection) : removeToken(collection));
        activeHandles.orderMasterCatalogField.set(orderHelper, replacement);
    }

    private static Handles handles(ClassLoader classLoader) throws Throwable {
        Handles current = handles;
        if (current != null && current.classLoader == classLoader) {
            return current;
        }
        synchronized (GboardManualIncognitoAccessPoint1777Support.class) {
            current = handles;
            if (current == null || current.classLoader != classLoader) {
                current = Handles.resolve(classLoader);
                handles = current;
            }
            return current;
        }
    }

    private static Method catalogCopyMethod(ClassLoader classLoader) throws Throwable {
        Method current = catalogCopyMethod;
        if (current != null && catalogCopyClassLoader == classLoader) {
            return current;
        }
        synchronized (GboardManualIncognitoAccessPoint1777Support.class) {
            current = catalogCopyMethod;
            if (current == null || catalogCopyClassLoader != classLoader) {
                Class<?> immutableCollectionClass = Class.forName(
                        IMMUTABLE_COLLECTION_CLASS, false, classLoader);
                current = immutableCollectionClass.getDeclaredMethod(
                        "k", Collection.class);
                current.setAccessible(true);
                catalogCopyClassLoader = classLoader;
                catalogCopyMethod = current;
            }
            return current;
        }
    }

    private static Icon enlargedOfficialIncognitoIcon(Context context) {
        Drawable drawable = context.getDrawable(OFFICIAL_INCOGNITO_ICON_RESOURCE_ID);
        if (drawable == null) {
            return null;
        }
        int iconSizePx = Math.max(1,
                Math.max(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight()));
        Bitmap bitmap = Bitmap.createBitmap(
                iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(context.getResources().getDisplayMetrics().densityDpi);
        int scaledSizePx = Math.round(iconSizePx * OFFICIAL_INCOGNITO_ICON_SCALE);
        int insetPx = (iconSizePx - scaledSizePx) / 2;
        drawable.setBounds(insetPx, insetPx,
                insetPx + scaledSizePx, insetPx + scaledSizePx);
        drawable.draw(new Canvas(bitmap));
        return Icon.createWithBitmap(bitmap);
    }

    private static final class ToggleAction implements Runnable {
        private final WeakReference<Object> controllerReference;
        private final WeakReference<Context> contextReference;

        ToggleAction(Object controller, Context context) {
            controllerReference = new WeakReference<>(controller);
            contextReference = new WeakReference<>(context);
        }

        @Override
        public void run() {
            Object controller = controllerReference.get();
            Context context = contextReference.get();
            if (controller != null && context != null) {
                GboardManualIncognitoRuntime.toggleManualRequested(context, controller);
            }
        }
    }

    private static final class Handles {
        final ClassLoader classLoader;
        final Method descriptorBuilderFactory;
        final Method builderTokenMethod;
        final Method builderRunnableMethod;
        final Method builderBuildMethod;
        final Field builderIconField;
        final Method builderIconResourceMethod;
        final Field builderLabelTextField;
        final Field builderContentDescriptionTextField;
        final Field controllerOrderHelperField;
        final Field orderMasterCatalogField;
        final Method immutableCollectionCopyMethod;
        final Method controllerRegisterMethod;

        Handles(ClassLoader classLoader, Method descriptorBuilderFactory,
                Method builderTokenMethod, Method builderRunnableMethod,
                Method builderBuildMethod, Field builderIconField,
                Method builderIconResourceMethod, Field builderLabelTextField,
                Field builderContentDescriptionTextField,
                Field controllerOrderHelperField, Field orderMasterCatalogField,
                Method immutableCollectionCopyMethod, Method controllerRegisterMethod) {
            this.classLoader = classLoader;
            this.descriptorBuilderFactory = descriptorBuilderFactory;
            this.builderTokenMethod = builderTokenMethod;
            this.builderRunnableMethod = builderRunnableMethod;
            this.builderBuildMethod = builderBuildMethod;
            this.builderIconField = builderIconField;
            this.builderIconResourceMethod = builderIconResourceMethod;
            this.builderLabelTextField = builderLabelTextField;
            this.builderContentDescriptionTextField = builderContentDescriptionTextField;
            this.controllerOrderHelperField = controllerOrderHelperField;
            this.orderMasterCatalogField = orderMasterCatalogField;
            this.immutableCollectionCopyMethod = immutableCollectionCopyMethod;
            this.controllerRegisterMethod = controllerRegisterMethod;
        }

        static Handles resolve(ClassLoader classLoader) throws Throwable {
            Class<?> descriptorClass = Class.forName(
                    DESCRIPTOR_CLASS, false, classLoader);
            Class<?> builderClass = Class.forName(BUILDER_CLASS, false, classLoader);
            Class<?> orderHelperClass = Class.forName(
                    ORDER_HELPER_CLASS, false, classLoader);
            Class<?> immutableCollectionClass = Class.forName(
                    IMMUTABLE_COLLECTION_CLASS, false, classLoader);
            Class<?> controllerClass = Class.forName(
                    CONTROLLER_CLASS, false, classLoader);

            Method descriptorBuilderFactory = descriptorClass.getDeclaredMethod("c");
            Method builderTokenMethod = builderClass.getDeclaredMethod("k", String.class);
            Method builderRunnableMethod = builderClass.getDeclaredMethod("q", Runnable.class);
            Method builderBuildMethod = builderClass.getDeclaredMethod("a");
            Field builderIconField = builderClass.getDeclaredField("c");
            Method builderIconResourceMethod = builderClass.getDeclaredMethod("h", int.class);
            Field builderLabelTextField = builderClass.getDeclaredField("d");
            Field builderContentDescriptionTextField = builderClass.getDeclaredField("e");
            Field controllerOrderHelperField = controllerClass.getDeclaredField("g");
            Field orderMasterCatalogField = orderHelperClass.getDeclaredField("c");
            Method immutableCollectionCopyMethod = immutableCollectionClass.getDeclaredMethod(
                    "k", Collection.class);
            Method controllerRegisterMethod = controllerClass.getDeclaredMethod(
                    "t", descriptorClass, boolean.class);

            descriptorBuilderFactory.setAccessible(true);
            builderTokenMethod.setAccessible(true);
            builderRunnableMethod.setAccessible(true);
            builderBuildMethod.setAccessible(true);
            builderIconField.setAccessible(true);
            builderIconResourceMethod.setAccessible(true);
            builderLabelTextField.setAccessible(true);
            builderContentDescriptionTextField.setAccessible(true);
            controllerOrderHelperField.setAccessible(true);
            orderMasterCatalogField.setAccessible(true);
            immutableCollectionCopyMethod.setAccessible(true);
            controllerRegisterMethod.setAccessible(true);

            return new Handles(
                    classLoader,
                    descriptorBuilderFactory,
                    builderTokenMethod,
                    builderRunnableMethod,
                    builderBuildMethod,
                    builderIconField,
                    builderIconResourceMethod,
                    builderLabelTextField,
                    builderContentDescriptionTextField,
                    controllerOrderHelperField,
                    orderMasterCatalogField,
                    immutableCollectionCopyMethod,
                    controllerRegisterMethod);
        }
    }
}
