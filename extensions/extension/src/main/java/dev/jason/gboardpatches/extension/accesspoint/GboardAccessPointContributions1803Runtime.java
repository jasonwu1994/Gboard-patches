package dev.jason.gboardpatches.extension.accesspoint;

import android.content.Context;
import dev.jason.gboardpatches.extension.duckemail.DuckEmailAccessPoint1803Contribution;
import android.util.Log;
import dev.jason.gboardpatches.extension.editingaccesspoints.GboardEditingAccessPoints1803Contribution;

import java.lang.reflect.Field;

import dev.jason.gboardpatches.extension.manualincognito.GboardManualIncognitoRuntime;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.websearch.GboardFloatingWebSearchAccessPoint1803Contribution;

public final class GboardAccessPointContributions1803Runtime {
    private static final String TAG = "GboardPatches";

    private GboardAccessPointContributions1803Runtime() {
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
                result = GboardFloatingWebSearchAccessPoint1803Contribution.INSTANCE
                        .extendOrderCatalog(context, result);
            }
        } catch (Throwable failure) {
            logFailure("Floating Web Search catalog contribution failed", failure);
        }
        try {
            if (hasFeature(context, GboardPatchesFeatureAvailability.FEATURE_EDITING_ACCESS_POINTS)) {
                result = GboardEditingAccessPoints1803Contribution.INSTANCE
                        .extendOrderCatalog(context, result);
            }
        } catch (Throwable failure) {
            logFailure("Editing buttons catalog contribution failed", failure);
        }
        try {
            if (hasFeature(context, GboardPatchesFeatureAvailability.FEATURE_DUCK_EMAIL)) {
                result = DuckEmailAccessPoint1803Contribution.INSTANCE.extendOrderCatalog(context, result);
            }
        } catch (Throwable failure) {
            logFailure("Duck email catalog contribution failed", failure);
        }
        return result;
    }

    public static void afterControllerCreated(Object controller, Context context) {
        try {
            synchronizeControllerOrderCatalog(controller, context);
        } catch (Throwable failure) {
            logFailure("Access Point controller catalog synchronization failed", failure);
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
                GboardFloatingWebSearchAccessPoint1803Contribution.INSTANCE.register(
                        controller, context);
            }
        } catch (Throwable failure) {
            logFailure("Floating Web Search controller contribution failed", failure);
        }
        try {
            if (hasFeature(context, GboardPatchesFeatureAvailability.FEATURE_EDITING_ACCESS_POINTS)) {
                GboardEditingAccessPoints1803Contribution.INSTANCE.register(controller, context);
            }
        } catch (Throwable failure) {
            logFailure("Editing buttons controller contribution failed", failure);
        }
        registerDuckEmail(controller, context);
    }

    private static void registerDuckEmail(Object controller, Context context) {
        try {
            if (hasFeature(context, GboardPatchesFeatureAvailability.FEATURE_DUCK_EMAIL)) {
                DuckEmailAccessPoint1803Contribution.INSTANCE.register(controller, context);
            }
        } catch (Throwable failure) {
            logFailure("Duck email controller contribution failed", failure);
        }
    }

    static void synchronizeControllerOrderCatalog(Object controller, Context context)
            throws Throwable {
        if (controller == null || context == null) {
            return;
        }
        Field orderHelperField = controller.getClass().getDeclaredField("g");
        orderHelperField.setAccessible(true);
        Object orderHelper = orderHelperField.get(controller);
        if (orderHelper == null) {
            return;
        }
        Field masterCatalogField = orderHelper.getClass().getDeclaredField("c");
        masterCatalogField.setAccessible(true);
        Object stockCatalog = masterCatalogField.get(orderHelper);
        Object expandedCatalog = includeOrderCatalog(context, stockCatalog);
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
