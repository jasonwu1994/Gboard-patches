package dev.jason.gboardpatches.extension.accesspoint;

import android.content.Context;
import android.util.Log;
import dev.jason.gboardpatches.extension.editingaccesspoints.GboardEditingAccessPointsContribution;

import java.lang.reflect.Field;
import java.util.Set;

import dev.jason.gboardpatches.extension.manualincognito.GboardManualIncognitoRuntime;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.websearch.GboardFloatingWebSearchAccessPointContribution;

public final class GboardAccessPointContributionsRuntime {
    private static final String TAG = "GboardPatches";

    private GboardAccessPointContributionsRuntime() {
    }

    public static Object includeOrderCatalog(Context context, Object original) {
        Object result = original;
        try {
            if (hasFeature(context, GboardPatchesFeatureAvailability.FEATURE_MANUAL_INCOGNITO)) {
                result = GboardManualIncognitoRuntime.includeAccessPointInOrderCatalog(
                        context, result);
            }
        } catch (Throwable failure) {
            logFailure("Manual Incognito catalog contribution failed", failure);
        }
        try {
            if (hasFeature(context,
                    GboardPatchesFeatureAvailability.FEATURE_FLOATING_WEB_SEARCH)) {
                result = GboardFloatingWebSearchAccessPointContribution.INSTANCE
                        .extendOrderCatalog(context, result);
            }
        } catch (Throwable failure) {
            logFailure("Floating Web Search catalog contribution failed", failure);
        }
        try {
            if (hasFeature(context, GboardPatchesFeatureAvailability.FEATURE_EDITING_ACCESS_POINTS)) {
                result = GboardEditingAccessPointsContribution.INSTANCE
                        .extendOrderCatalog(context, result);
            }
        } catch (Throwable failure) {
            logFailure("Editing buttons catalog contribution failed", failure);
        }
        return result;
    }

    public static void afterControllerCreated(Object controller, Context context) {
        try {
            synchronizeControllerOrderCatalog(controller, context);
        } catch (Throwable failure) {
            logFailure("Access Point controller catalog synchronization failed", failure);
            return;
        }
        try {
            if (hasFeature(context, GboardPatchesFeatureAvailability.FEATURE_MANUAL_INCOGNITO)) {
                GboardManualIncognitoRuntime.afterAccessPointControllerCreated(
                        controller, context);
            }
        } catch (Throwable failure) {
            logFailure("Manual Incognito controller contribution failed", failure);
        }
        try {
            if (hasFeature(context,
                    GboardPatchesFeatureAvailability.FEATURE_FLOATING_WEB_SEARCH)) {
                GboardFloatingWebSearchAccessPointContribution.INSTANCE.register(
                        controller, context);
            }
        } catch (Throwable failure) {
            logFailure("Floating Web Search controller contribution failed", failure);
        }
        try {
            if (hasFeature(context, GboardPatchesFeatureAvailability.FEATURE_EDITING_ACCESS_POINTS)) {
                GboardEditingAccessPointsContribution.INSTANCE.register(controller, context);
            }
        } catch (Throwable failure) {
            logFailure("Editing buttons controller contribution failed", failure);
        }
    }

    static void synchronizeControllerOrderCatalog(Object controller, Context context)
            throws Throwable {
        if (controller == null || context == null) {
            return;
        }
        ClassLoader loader = controller.getClass().getClassLoader();
        Class<?> controllerType = Class.forName("mwv", false, loader);
        Class<?> orderType = Class.forName("mwg", false, loader);
        Class<?> catalogType = Class.forName("wpa", false, loader);
        if (!controllerType.isInstance(controller)) {
            throw new IllegalArgumentException("Not the exact1853 AP controller");
        }
        Field orderHelperField = controllerType.getDeclaredField("g");
        if (orderHelperField.getType() != orderType) throw new NoSuchFieldException("mwv.g:mwg");
        orderHelperField.setAccessible(true);
        Object orderHelper = orderHelperField.get(controller);
        if (orderHelper == null) {
            return;
        }
        Field masterCatalogField = orderType.getDeclaredField("c");
        if (masterCatalogField.getType() != catalogType) throw new NoSuchFieldException("mwg.c:wpa");
        masterCatalogField.setAccessible(true);
        Object stockCatalog = masterCatalogField.get(orderHelper);
        if (!(stockCatalog instanceof Set<?>) || !catalogType.isInstance(stockCatalog)) {
            throw new IllegalStateException("AP master catalog must be1853 immutable Set");
        }
        Object expandedCatalog = includeOrderCatalog(context, stockCatalog);
        if (!(expandedCatalog instanceof Set<?>) || !catalogType.isInstance(expandedCatalog)) {
            throw new IllegalStateException("AP contribution must retain1853 immutable Set");
        }
        if (expandedCatalog != stockCatalog) {
            masterCatalogField.set(orderHelper, expandedCatalog);
        }
    }

    private static boolean hasFeature(Context context, String feature) {
        try {
            return GboardPatchesFeatureAvailability.hasFeature(context, feature);
        } catch (Throwable failure) {
            logFailure("Access Point feature availability failed", failure);
            return false;
        }
    }

    private static void logFailure(String message, Throwable failure) {
        try {
            Log.w(TAG, message, failure);
        } catch (Throwable ignored) {
            // Logging cannot affect Gboard's Access Point construction path.
        }
    }
}
