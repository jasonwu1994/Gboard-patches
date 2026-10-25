/*
 * GboardWu cover/main Resize profile routing.
 *
 * Route only a trusted cover READ/SAVE for normal keyboard mode 1 from
 * native phone_portrait to foldable_portrait after the user opts in.
 * Gboard owns all geometry persistence. No diagnostics or preference writes.
 */
package dev.jason.gboardpatches.extension.modevariant;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.view.Display;

public final class GboardModeVariantRuntime {
    private static final Object LOCK = new Object();
    private static final long MAX_SCOPE_MS = 4000L;
    private static final long MAX_BIND_AGE_MS = 600000L;
    private static final int NORMAL_KEYBOARD_MODE = 1;
    private static final String NATIVE_MAIN_VARIANT = "phone_portrait";
    private static final String COVER_VARIANT = "foldable_portrait";
    private static final ThreadLocal<Pending> SCOPE = new ThreadLocal<>();

    private static volatile Context boundApplicationContext;
    private static volatile int trustedDisplay = -1;
    private static volatile long lastBindAt;
    private static volatile long bindSerial;

    private GboardModeVariantRuntime() {
    }

    private static final class Pending {
        final String action;
        final long startedAt;
        final int display;
        final long bindingSerial;
        final long bindingAgeMs;
        int modeId;
        String family = "NOT_OBSERVED";

        Pending(String action, int modeResourceId) {
            this.action = action;
            modeId = modeResourceId;
            startedAt = SystemClock.elapsedRealtime();
            synchronized (LOCK) {
                display = trustedDisplay;
                bindingSerial = bindSerial;
                bindingAgeMs = lastBindAt == 0L
                        ? -1L : Math.max(0L, startedAt - lastBindAt);
            }
        }
    }

    /** Identity comes only from the actual IME service, never from screen size. */
    public static void bindIme(Object receiver) {
        int id = -1;
        Context appContext = null;
        try {
            if (receiver instanceof InputMethodService) {
                InputMethodService ime = (InputMethodService) receiver;
                Context applicationContext = ime.getApplicationContext();
                appContext = applicationContext == null ? ime : applicationContext;
                Display display = ime.getDisplay();
                if (display != null) {
                    int candidate = display.getDisplayId();
                    if (candidate == 0 || candidate == 1) {
                        id = candidate;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Unknown binding must never trigger cover routing.
            id = -1;
            appContext = null;
        }
        synchronized (LOCK) {
            boundApplicationContext = appContext;
            trustedDisplay = id;
            lastBindAt = SystemClock.elapsedRealtime();
            bindSerial++;
        }
    }

    public static void beginRead(int modeResourceId) {
        try {
            SCOPE.set(new Pending("READ", modeResourceId));
        } catch (Throwable ignored) {
        }
    }

    public static void beginSave() {
        try {
            SCOPE.set(new Pending("SAVE", -1));
        } catch (Throwable ignored) {
        }
    }

    /** Observe native SAVE mode so floating, split and one-handed stay native. */
    public static void observeSaveKeyboardMode(int mode) {
        try {
            Pending p = SCOPE.get();
            if (p != null && "SAVE".equals(p.action) && !expired(p)) {
                p.modeId = mode;
            }
        } catch (Throwable ignored) {
        }
    }

    /** The native device-family enum is a required selection guard. */
    public static void observeFamily(Object familyValue) {
        try {
            Pending p = SCOPE.get();
            if (p == null || expired(p)) {
                return;
            }
            if (familyValue instanceof Enum<?>) {
                p.family = ((Enum<?>) familyValue).name();
            } else {
                p.family = "UNKNOWN_NON_ENUM";
            }
        } catch (Throwable ignored) {
        }
    }

    /** Change only the returned map key; never write Resize layout data. */
    public static String chooseVariant(String generatedVariant) {
        try {
            Pending p = SCOPE.get();
            SCOPE.remove();
            if (p == null || expired(p) || p.display != 1) {
                return generatedVariant;
            }

            // Cached process-local preference read only for a cover READ/SAVE.
            // Missing/malformed state fails closed; keep the read outside LOCK.
            if (!GboardCoverResizeSettings.isEnabled(boundApplicationContext)) {
                return generatedVariant;
            }

            synchronized (LOCK) {
                long now = SystemClock.elapsedRealtime();
                boolean trustworthy = trustedDisplay == 1
                        && p.bindingSerial == bindSerial
                        && p.bindingSerial != 0L
                        && p.bindingAgeMs >= 0L
                        && p.bindingAgeMs <= MAX_BIND_AGE_MS
                        && lastBindAt != 0L
                        && now - lastBindAt >= 0L
                        && now - lastBindAt <= MAX_BIND_AGE_MS;
                if (!trustworthy
                        || p.modeId != NORMAL_KEYBOARD_MODE
                        || !NATIVE_MAIN_VARIANT.equals(generatedVariant)
                        || !"DEVICE_PHONE".equals(p.family)) {
                    return generatedVariant;
                }
                return COVER_VARIANT;
            }
        } catch (Throwable ignored) {
            SCOPE.remove();
            return generatedVariant;
        }
    }

    private static boolean expired(Pending p) {
        long age = SystemClock.elapsedRealtime() - p.startedAt;
        return age < 0L || age > MAX_SCOPE_MS;
    }
}
