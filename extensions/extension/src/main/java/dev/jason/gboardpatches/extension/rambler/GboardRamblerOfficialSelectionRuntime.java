package dev.jason.gboardpatches.extension.rambler;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import dev.jason.gboardpatches.extension.advancedvoice.GboardAdvancedVoiceRuntimeSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

/**
 * Keeps Agentic capability exposure aligned with Gboard's official selector.
 *
 * <p>Persists the user's explicit Rambler selection into Device-Protected (DE) storage so cold
 * starts and Direct Boot (before first unlock) resolve Rambler immediately instead of latching a
 * locked-storage {@code false}. Passive {@code Lnca#a(Context)} reads are prevented from
 * overwriting an active Rambler selection during default-selection suppression, Direct Boot, or
 * background refreshes.</p>
 */
public final class GboardRamblerOfficialSelectionRuntime {
    public static final String PREF_KEY_OFFICIAL_RAMBLER_SELECTED =
            "pref_rambler_official_selected";

    private static final String TAG = "GboardRambler";
    private static final String DEFAULT_SELECTION_CLASS = "nca";
    private static final String UNWRAPPED_SELECTION_METHOD = "morpheRamblerStocka";
    private static final String WRAPPED_SELECTION_METHOD = "a";
    private static final int MAX_LOG_COUNT = 50;

    private static final ThreadLocal<Integer> VOICE_SETTINGS_SCOPE_DEPTH =
            new ThreadLocal<Integer>();
    private static final ThreadLocal<Integer> DEFAULT_SELECTION_SUPPRESSION_DEPTH =
            new ThreadLocal<Integer>();
    private static final ThreadLocal<Boolean> READING_OFFICIAL_SELECTION =
            new ThreadLocal<Boolean>();
    private static final AtomicInteger LOG_COUNT = new AtomicInteger();

    private static volatile Boolean officialRamblerSelected;
    private static volatile Boolean persistedRamblerSelected;
    private static volatile Context applicationContext;
    private static volatile ClassLoader cachedSelectionLoader;
    private static volatile Method cachedSelectionMethod;
    private static volatile String selectionClassName = DEFAULT_SELECTION_CLASS;

    private GboardRamblerOfficialSelectionRuntime() {
    }

    public static void seedApplicationContext(Context context) {
        if (context == null) {
            return;
        }
        Context resolved = context;
        try {
            Context app = context.getApplicationContext();
            if (app != null) {
                resolved = app;
            }
        } catch (Throwable ignored) {
            // Fall back to the supplied context if getApplicationContext() is unavailable.
        }
        if (resolved != null) {
            applicationContext = resolved;
        }
    }

    public static boolean shouldEnableAgenticDictation() {
        if (isDefaultSelectionSuppressed()) {
            return false;
        }
        if (isVoiceSettingsScopeActive()) {
            return true;
        }
        if (isReadingOfficialSelection()) {
            // Allow Lnca#morpheRamblerStocka(Context) to read Gboard's persisted preference
            // without recursing back into resolveOfficialSelection() or failing its own gate.
            return true;
        }
        Boolean selected = officialRamblerSelected;
        if (selected == null) {
            selected = resolveOfficialSelection();
        }
        return Boolean.TRUE.equals(selected);
    }

    public static void enterVoiceSettingsScope() {
        VOICE_SETTINGS_SCOPE_DEPTH.set(Integer.valueOf(depth(VOICE_SETTINGS_SCOPE_DEPTH) + 1));
    }

    public static void exitVoiceSettingsScope() {
        decrement(VOICE_SETTINGS_SCOPE_DEPTH);
    }

    /**
     * Records an explicit user choice from Gboard's Voice typing selector and mirrors it to
     * Device-Protected storage so subsequent Direct Boot cold starts preserve the choice.
     */
    public static void updateOfficialSelection(boolean selected) {
        Boolean next = Boolean.valueOf(selected);
        Boolean previous = officialRamblerSelected;
        officialRamblerSelected = next;
        boolean persisted = persistSelection(resolveContext(), selected, true);
        persistedRamblerSelected = persisted ? next : null;
        if (!next.equals(previous)) {
            logInfo("explicit Rambler selection updated: " + previous + " -> " + next);
        }
    }

    /**
     * Reconciles a passive {@code Lnca#a(Context)} read with Direct Boot storage and active scopes.
     *
     * <p>Prevents Direct Boot CE lockouts, {@code Lfhv#hU()} suppression, or background flag
     * refreshes from poisoning an active Rambler selection back to {@code false}.</p>
     */
    public static boolean observeOfficialSelectionRead(Context context, boolean stockResult) {
        try {
            if (context != null) {
                seedApplicationContext(context);
            }
            if (isDefaultSelectionSuppressed() || isReadingOfficialSelection()) {
                return stockResult;
            }
            if (!GboardAdvancedVoiceRuntimeSettings.isEnabled()) {
                return stockResult;
            }
            if (stockResult) {
                boolean stateChanged = !Boolean.TRUE.equals(officialRamblerSelected);
                officialRamblerSelected = Boolean.TRUE;
                if (stateChanged || !Boolean.TRUE.equals(persistedRamblerSelected)) {
                    if (persistSelection(
                            context != null ? context : resolveContext(), true, false)) {
                        persistedRamblerSelected = Boolean.TRUE;
                    }
                    logInfo("observed Rambler selection=true via nca#a");
                }
                return true;
            }
            Context resolved = context != null ? context : resolveContext();
            Boolean persisted = readPersistedSelection(resolved);
            boolean unlocked = resolved != null && GboardPatchesSettings.isUserUnlocked(resolved);
            if (Boolean.TRUE.equals(persisted) || Boolean.TRUE.equals(officialRamblerSelected)) {
                officialRamblerSelected = Boolean.TRUE;
                if (!Boolean.TRUE.equals(persisted)
                        && persistSelection(resolved, true, false)) {
                    persistedRamblerSelected = Boolean.TRUE;
                }
                logInfo(unlocked
                        ? "preserved active Rambler selection against passive nca#a=false"
                        : "preserved DE Rambler selection during Direct Boot");
                return true;
            }
            if (!unlocked) {
                // Leave officialRamblerSelected unlatched while CE storage is locked.
                return false;
            }
            officialRamblerSelected = Boolean.FALSE;
            return false;
        } catch (Throwable failure) {
            logWarning("observeOfficialSelectionRead failed", failure);
            return stockResult;
        }
    }

    public static void enterDefaultSelectionSuppression() {
        DEFAULT_SELECTION_SUPPRESSION_DEPTH.set(Integer.valueOf(
                depth(DEFAULT_SELECTION_SUPPRESSION_DEPTH) + 1));
    }

    public static void exitDefaultSelectionSuppression() {
        decrement(DEFAULT_SELECTION_SUPPRESSION_DEPTH);
    }

    private static boolean isVoiceSettingsScopeActive() {
        return depth(VOICE_SETTINGS_SCOPE_DEPTH) > 0;
    }

    private static boolean isDefaultSelectionSuppressed() {
        return depth(DEFAULT_SELECTION_SUPPRESSION_DEPTH) > 0;
    }

    private static boolean isReadingOfficialSelection() {
        return Boolean.TRUE.equals(READING_OFFICIAL_SELECTION.get());
    }

    private static int depth(ThreadLocal<Integer> scope) {
        Integer value = scope.get();
        return value == null ? 0 : value.intValue();
    }

    private static void decrement(ThreadLocal<Integer> scope) {
        int next = depth(scope) - 1;
        if (next <= 0) {
            scope.remove();
        } else {
            scope.set(Integer.valueOf(next));
        }
    }

    private static Boolean resolveOfficialSelection() {
        Context context = resolveContext();
        Boolean persisted = readPersistedSelection(context);
        if (persisted != null) {
            officialRamblerSelected = persisted;
            logInfo("restored Rambler selection=" + persisted + " from DE storage");
            return persisted;
        }
        if (context == null) {
            return null;
        }
        if (!GboardPatchesSettings.isUserUnlocked(context)) {
            // Avoid probing locked CE preferences during Direct Boot before DE migration exists.
            return null;
        }
        return probeStockOfficialSelection(context);
    }

    private static Boolean probeStockOfficialSelection(Context context) {
        if (isReadingOfficialSelection()) {
            return null;
        }
        READING_OFFICIAL_SELECTION.set(Boolean.TRUE);
        try {
            ClassLoader loader = resolveSelectionClassLoader(context);
            Method selection = resolveStockSelectionMethod(loader);
            if (selection == null) {
                return null;
            }
            Object value = selection.invoke(null, context);
            if (value instanceof Boolean result) {
                officialRamblerSelected = result;
                if (result.booleanValue()) {
                    if (persistSelection(context, true, false)) {
                        persistedRamblerSelected = Boolean.TRUE;
                    }
                    logInfo("migrated existing Rambler selection=true into DE storage");
                }
                return result;
            }
        } catch (Throwable failure) {
            logWarning("stock Rambler selection probe failed", failure);
        } finally {
            READING_OFFICIAL_SELECTION.remove();
        }
        return null;
    }

    private static ClassLoader resolveSelectionClassLoader(Context context) {
        ClassLoader loader = null;
        try {
            loader = context.getClassLoader();
        } catch (Throwable ignored) {
            // Fall through to class-based loaders.
        }
        if (loader == null) {
            loader = GboardRamblerOfficialSelectionRuntime.class.getClassLoader();
        }
        if (loader == null) {
            loader = context.getClass().getClassLoader();
        }
        return loader;
    }

    private static Method resolveStockSelectionMethod(ClassLoader loader)
            throws ReflectiveOperationException {
        if (loader != null && loader == cachedSelectionLoader && cachedSelectionMethod != null) {
            return cachedSelectionMethod;
        }
        Class<?> support = Class.forName(selectionClassName, false, loader);
        Method method;
        try {
            method = support.getDeclaredMethod(UNWRAPPED_SELECTION_METHOD, Context.class);
        } catch (NoSuchMethodException missingUnwrapped) {
            method = support.getDeclaredMethod(WRAPPED_SELECTION_METHOD, Context.class);
        }
        method.setAccessible(true);
        cachedSelectionLoader = loader;
        cachedSelectionMethod = method;
        return method;
    }

    private static Boolean readPersistedSelection(Context context) {
        Boolean cached = persistedRamblerSelected;
        if (cached != null) {
            return cached;
        }
        if (context == null) {
            return null;
        }
        Boolean stored = GboardPatchesSettings.readDeviceProtectedSetting(
                context,
                preferences -> preferences.contains(PREF_KEY_OFFICIAL_RAMBLER_SELECTED)
                        ? Boolean.valueOf(
                                preferences.getBoolean(PREF_KEY_OFFICIAL_RAMBLER_SELECTED, false))
                        : null,
                null);
        if (stored != null) {
            persistedRamblerSelected = stored;
        }
        return stored;
    }

    private static boolean persistSelection(
            Context context, boolean selected, boolean synchronous) {
        if (context == null) {
            return false;
        }
        return GboardPatchesSettings.writeDeviceProtectedBoolean(
                context, PREF_KEY_OFFICIAL_RAMBLER_SELECTED, selected, synchronous);
    }

    private static Context resolveContext() {
        Context cached = applicationContext;
        if (cached != null) {
            return cached;
        }
        Context resolved = GboardAdvancedVoiceRuntimeSettings.resolveApplicationContext();
        if (resolved == null) {
            resolved = reflectedApplicationContext(
                    "android.app.ActivityThread", "currentApplication");
        }
        if (resolved == null) {
            resolved = reflectedApplicationContext(
                    "android.app.AppGlobals", "getInitialApplication");
        }
        if (resolved != null) {
            applicationContext = resolved;
        }
        return resolved;
    }

    private static Context reflectedApplicationContext(String className, String methodName) {
        try {
            Object application = Class.forName(className).getMethod(methodName).invoke(null);
            if (!(application instanceof Context context)) {
                return null;
            }
            Context app = context.getApplicationContext();
            return app != null ? app : context;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void logInfo(String message) {
        if (LOG_COUNT.getAndIncrement() >= MAX_LOG_COUNT) {
            return;
        }
        try {
            Log.i(TAG, message);
        } catch (Throwable ignored) {
            // Host tests run without the Android logger.
        }
    }

    private static void logWarning(String message, Throwable failure) {
        if (LOG_COUNT.getAndIncrement() >= MAX_LOG_COUNT) {
            return;
        }
        try {
            Log.w(TAG, message, failure);
        } catch (Throwable ignored) {
            // Host tests run without the Android logger.
        }
    }

    static Boolean cachedOfficialSelectionForTests() {
        return officialRamblerSelected;
    }

    static void setSelectionClassNameForTests(String className) {
        selectionClassName = className != null ? className : DEFAULT_SELECTION_CLASS;
        cachedSelectionLoader = null;
        cachedSelectionMethod = null;
    }

    static void resetForTests() {
        VOICE_SETTINGS_SCOPE_DEPTH.remove();
        DEFAULT_SELECTION_SUPPRESSION_DEPTH.remove();
        READING_OFFICIAL_SELECTION.remove();
        officialRamblerSelected = null;
        persistedRamblerSelected = null;
        applicationContext = null;
        cachedSelectionLoader = null;
        cachedSelectionMethod = null;
        selectionClassName = DEFAULT_SELECTION_CLASS;
        LOG_COUNT.set(0);
    }
}
