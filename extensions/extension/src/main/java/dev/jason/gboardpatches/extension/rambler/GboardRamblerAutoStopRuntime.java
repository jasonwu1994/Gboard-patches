package dev.jason.gboardpatches.extension.rambler;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.LongSupplier;

/**
 * Stops Rambler voice typing after a chosen number of seconds without speech.
 *
 * <p>Gboard's voice input manager already stops listening and keeps the text when its timeout
 * timer runs out; for a Rambler session that timer is armed with a long session limit. This
 * runtime shortens the timer to the chosen silence length when a Rambler session starts and asks
 * for a restart whenever the recognizer's speech level shows speech, so the timer only runs out
 * after that many seconds of silence. Rambler sends no text while the user is speaking, so the
 * speech level is the only signal of ongoing speech.</p>
 *
 * <p>The timer is shared with the other recognizers and Gboard sets its length only for Rambler
 * sessions. The length found before the first change is therefore kept and put back as soon as a
 * session starts that this runtime does not shorten, so other voice typing keeps Gboard's own
 * timing. When Gboard has set a new length in the meantime, that length is left alone.</p>
 *
 * <p>The length Gboard set for the session also stays the session's upper bound: speech no
 * longer restarts the timer once a restart would let it run past that limit.</p>
 *
 * <p>Callers pass Gboard's own length objects through unchanged; new lengths are created with the
 * {@code ofSeconds(long)} factory of the same class, so the runtime does not depend on which
 * duration class the keyboard build uses.</p>
 */
public final class GboardRamblerAutoStopRuntime {
    /** Speech level (0..100) from which Gboard itself animates speech. */
    public static final int SPEECH_LEVEL = 50;
    static final long RESTART_INTERVAL_MS = 250L;
    private static final String TAG = "GboardRamblerAutoStop";

    private static final Object LOCK = new Object();
    private static final LongSupplier SYSTEM_CLOCK = GboardRamblerAutoStopRuntime::systemNow;
    private static volatile LongSupplier clock = SYSTEM_CLOCK;

    private static Object savedLength;
    private static Object appliedLength;
    private static int armedSeconds;
    private static long sessionLimitMs;
    private static long sessionStartMs;
    private static long lastSpeechMs;
    private static long lastRestartMs;
    private static int levelCount;
    private static int speechCount;
    private static int restartCount;
    private static int maxLevel;

    private GboardRamblerAutoStopRuntime() {
    }

    /**
     * Called when a voice session starts listening, before Gboard starts its timeout timer.
     *
     * @param context        the keyboard's context, used to read the setting
     * @param ramblerSession whether the starting session is a Rambler session
     * @param currentLength  the timer's current length, as Gboard stores it
     * @return the length to give the timer now, or {@code null} to leave it unchanged
     */
    public static Object onSessionStart(Context context, boolean ramblerSession,
            Object currentLength) {
        int seconds = GboardRamblerAutoStopSettings.OFF;
        if (ramblerSession) {
            try {
                seconds = GboardRamblerAutoStopSettings.readRuntimeSeconds(context);
            } catch (Throwable unreadable) {
                // An unreadable setting is the same as the feature being off.
            }
        }
        try {
            return onSessionStart(ramblerSession, seconds, currentLength);
        } catch (Throwable failed) {
            logWarning("session start skipped", failed);
            return null;
        }
    }

    static Object onSessionStart(boolean ramblerSession, int seconds, Object currentLength) {
        String previous;
        Object result = null;
        int armed;
        synchronized (LOCK) {
            int previousArmed = armedSeconds;
            long previousStartMs = sessionStartMs;
            long previousLastSpeechMs = lastSpeechMs;
            int previousLevels = levelCount;
            int previousSpeech = speechCount;
            int previousRestarts = restartCount;
            int previousMax = maxLevel;
            clearSessionCountersLocked();
            long now = clock.getAsLong();
            sessionStartMs = now;
            lastSpeechMs = now;
            previous = formatSummary(previousArmed, now, previousStartMs, previousLastSpeechMs,
                    previousLevels, previousSpeech, previousRestarts, previousMax);
            if (appliedLength != null && !appliedLength.equals(currentLength)) {
                // Gboard set a new length since the last change; that one is its own again.
                savedLength = null;
                appliedLength = null;
            }
            Object original = savedLength != null ? savedLength : currentLength;
            long limitMs = lengthMillis(original);
            boolean shorterThanLimit = limitMs <= 0L || seconds * 1000L < limitMs;
            Object shortened = ramblerSession && seconds > 0 && shorterThanLimit
                    ? lengthOfSeconds(currentLength, seconds) : null;
            if (shortened != null) {
                if (savedLength == null) {
                    savedLength = currentLength;
                }
                appliedLength = shortened;
                armedSeconds = seconds;
                sessionLimitMs = limitMs;
                result = shortened;
            } else if (savedLength != null) {
                result = savedLength;
                savedLength = null;
                appliedLength = null;
            }
            armed = armedSeconds;
        }
        if (previous != null) {
            log(previous);
        }
        if (ramblerSession) {
            log("Rambler session started; auto stop " + (armed > 0 ? armed + " s" : "off"));
        } else if (result != null) {
            log("non-Rambler session started; restored Gboard's timeout");
        }
        return result;
    }

    /**
     * Called with every speech level update (0..100).
     *
     * @return whether Gboard's timeout timer should restart now
     */
    public static boolean onSpeechLevel(boolean ramblerSession, int level) {
        if (!ramblerSession) {
            return false;
        }
        try {
            return shouldRestartOnSpeech(level);
        } catch (Throwable failed) {
            return false;
        }
    }

    private static boolean shouldRestartOnSpeech(int level) {
        synchronized (LOCK) {
            if (armedSeconds <= 0) {
                return false;
            }
            long now = clock.getAsLong();
            levelCount++;
            maxLevel = Math.max(maxLevel, level);
            if (level < SPEECH_LEVEL) {
                return false;
            }
            speechCount++;
            lastSpeechMs = now;
            if (lastRestartMs != 0L && now - lastRestartMs < RESTART_INTERVAL_MS) {
                return false;
            }
            if (sessionLimitMs > 0L
                    && now - sessionStartMs + armedSeconds * 1000L >= sessionLimitMs) {
                // Let the timer run out by Gboard's own session limit.
                return false;
            }
            lastRestartMs = now;
            restartCount++;
            return true;
        }
    }

    /** Seconds of silence armed for the current session; 0 when the timer is untouched. */
    public static int armedSeconds() {
        synchronized (LOCK) {
            return armedSeconds;
        }
    }

    static Object lengthOfSeconds(Object template, int seconds) {
        if (template == null) {
            return null;
        }
        try {
            Method factory = template.getClass().getMethod("ofSeconds", long.class);
            if (!Modifier.isStatic(factory.getModifiers())
                    || !factory.getReturnType().isInstance(template)) {
                return null;
            }
            return factory.invoke(null, (long) seconds);
        } catch (Throwable unavailable) {
            return null;
        }
    }

    /** The length in milliseconds, or 0 when it cannot be read. */
    static long lengthMillis(Object length) {
        if (length == null) {
            return 0L;
        }
        try {
            Object millis = length.getClass().getMethod("toMillis").invoke(length);
            return millis instanceof Long value && value.longValue() > 0L ? value.longValue() : 0L;
        } catch (Throwable unavailable) {
            return 0L;
        }
    }

    private static void clearSessionCountersLocked() {
        armedSeconds = 0;
        sessionLimitMs = 0L;
        lastRestartMs = 0L;
        levelCount = 0;
        speechCount = 0;
        restartCount = 0;
        maxLevel = 0;
    }

    private static String formatSummary(int previousArmed, long now, long previousStartMs,
            long previousLastSpeechMs, int previousLevels, int previousSpeech,
            int previousRestarts, int previousMax) {
        if (previousArmed <= 0) {
            return null;
        }
        return "previous Rambler session: auto stop " + previousArmed + " s, ran "
                + seconds(now - previousStartMs) + " s, last speech "
                + seconds(previousLastSpeechMs - previousStartMs) + " s (levels=" + previousLevels
                + ", speech=" + previousSpeech + ", restarts=" + previousRestarts
                + ", max=" + previousMax + ")";
    }

    private static String seconds(long millis) {
        return String.valueOf(Math.round(millis / 100.0) / 10.0);
    }

    private static long systemNow() {
        try {
            return SystemClock.elapsedRealtime();
        } catch (Throwable notOnDevice) {
            return System.nanoTime() / 1_000_000L;
        }
    }

    private static void log(String message) {
        try {
            Log.i(TAG, message);
        } catch (Throwable notOnDevice) {
            // Host tests run without the Android logger.
        }
    }

    private static void logWarning(String message, Throwable failure) {
        try {
            Log.w(TAG, message, failure);
        } catch (Throwable notOnDevice) {
            // Host tests run without the Android logger.
        }
    }

    static void setClockForTests(LongSupplier replacement) {
        clock = replacement != null ? replacement : SYSTEM_CLOCK;
    }

    static void resetForTests() {
        clock = SYSTEM_CLOCK;
        synchronized (LOCK) {
            savedLength = null;
            appliedLength = null;
            clearSessionCountersLocked();
            long now = clock.getAsLong();
            sessionStartMs = now;
            lastSpeechMs = now;
        }
    }
}
