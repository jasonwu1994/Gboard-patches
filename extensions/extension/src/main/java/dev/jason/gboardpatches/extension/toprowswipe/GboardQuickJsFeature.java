package dev.jason.gboardpatches.extension.toprowswipe;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import dev.jason.gboardpatches.extension.quickjs.QuickJsNgBridge;

final class GboardQuickJsFeature {
    static final long DEFAULT_TIMEOUT_MS = GboardTopRowSwipeSettings.DEFAULT_TIMEOUT_MS;
    static final long FUTURE_TIMEOUT_GRACE_MS = 100L;
    private static final int QUICK_JS_EXECUTOR_MAX_THREADS = 2;
    private static final long QUICK_JS_EXECUTOR_KEEP_ALIVE_SECONDS = 30L;

    private static final String TOAST_ALREADY_RUNNING = "Script already running";
    private static final String TOAST_RUNNING = "Running script...";
    private static final String TOAST_FINISHED = "Script finished";
    private static final String TOAST_TIMED_OUT = "Script timed out";
    private static final String TOAST_FAILED = "Script failed";
    private static final String TOAST_OVERLAY_TAG = "gboardpatches:gboard:quickjs-toast";
    private static final long TOAST_OVERLAY_DURATION_MS = 1_700L;
    private static final int TOAST_OVERLAY_BOTTOM_MARGIN_DP = 24;

    private static final Object EXECUTOR_LOCK = new Object();
    private static final Object TOAST_LOCK = new Object();
    private static final AtomicInteger THREAD_COUNT = new AtomicInteger(0);
    private static final ConcurrentMap<QuickJsSlotKey, Boolean> RUNNING_SLOT_KEYS =
            new ConcurrentHashMap<>();

    private static volatile ExecutorService quickJsExecutor = newExecutor();
    private static volatile ScheduledExecutorService quickJsTimeoutExecutor =
            newTimeoutExecutor();
    private static ToastHandle currentToastHandle;

    interface ScriptRuntime {
        Object evaluate(String wrappedScript);
    }

    interface ToastHandle {
        void cancel();
        void show();
    }

    interface ToastPresenter {
        ToastHandle make(Object context, String message);
    }

    static final class QuickJsSlotKey {
        final GboardTopRowSwipeRuntime.TopRowLayoutKind layoutKind;
        final int rowIndex;

        QuickJsSlotKey(GboardTopRowSwipeRuntime.TopRowLayoutKind layoutKind, int rowIndex) {
            this.layoutKind = layoutKind;
            this.rowIndex = rowIndex;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof QuickJsSlotKey otherKey)) {
                return false;
            }
            return rowIndex == otherKey.rowIndex && layoutKind == otherKey.layoutKind;
        }

        @Override
        public int hashCode() {
            return Objects.hash(layoutKind, Integer.valueOf(rowIndex));
        }
    }

    static final class EvaluationResult {
        final boolean success;
        final boolean timedOut;
        final boolean shouldCommit;
        final String committedText;
        final String errorMessage;

        EvaluationResult(boolean success, boolean timedOut, boolean shouldCommit,
                String committedText, String errorMessage) {
            this.success = success;
            this.timedOut = timedOut;
            this.shouldCommit = shouldCommit;
            this.committedText = committedText;
            this.errorMessage = errorMessage;
        }
    }

    static final class QuickJsTimedOutException extends RuntimeException {
        QuickJsTimedOutException(String message) {
            super(message);
        }
    }

    private GboardQuickJsFeature() {
    }

    static EvaluationResult evaluateScript(String scriptBody) {
        return evaluateScript(new QuickJsNgScriptRuntime(DEFAULT_TIMEOUT_MS),
                scriptBody,
                "",
                DEFAULT_TIMEOUT_MS,
                "");
    }

    static EvaluationResult evaluateScript(ScriptRuntime runtime, String scriptBody) {
        return evaluateScript(runtime, scriptBody, "", DEFAULT_TIMEOUT_MS, "");
    }

    static EvaluationResult evaluateScript(ScriptRuntime runtime, String scriptBody,
            long timeoutMs) {
        return evaluateScript(runtime, scriptBody, "", timeoutMs, "");
    }

    static EvaluationResult evaluateScript(ScriptRuntime runtime, String scriptBody,
            String input, long timeoutMs) {
        return evaluateScript(runtime, scriptBody, input, timeoutMs, "");
    }

    static EvaluationResult evaluateScript(ScriptRuntime runtime, String scriptBody,
            String input, long timeoutMs, String globalJavaScript) {
        if (scriptBody == null) {
            return successNoCommit();
        }
        if (runtime == null) {
            return failed("QuickJS runtime unavailable");
        }
        if (timeoutMs <= 0L) {
            return failed("QuickJS timeout must be positive");
        }

        Future<EvaluationResult> future;
        try {
            future = executor().submit(() -> evaluateScriptOnCurrentThread(
                    runtime,
                    scriptBody,
                    input,
                    timeoutMs,
                    globalJavaScript));
        } catch (RejectedExecutionException e) {
            return failed("QuickJS executor unavailable");
        }

        try {
            return future.get(timeoutMs + FUTURE_TIMEOUT_GRACE_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return timedOut("QuickJS execution timed out");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return failed("QuickJS execution interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return failed(cause != null ? nonBlankMessage(cause, "QuickJS execution failed")
                    : nonBlankMessage(e, "QuickJS execution failed"));
        } catch (Throwable e) {
            return failed(nonBlankMessage(e, "QuickJS execution failed"));
        }
    }

    private static EvaluationResult evaluateScriptOnCurrentThread(ScriptRuntime runtime,
            String scriptBody, String input, long timeoutMs, String globalJavaScript) {
        if (scriptBody == null) {
            return successNoCommit();
        }
        if (runtime == null) {
            return failed("QuickJS runtime unavailable");
        }
        if (timeoutMs <= 0L) {
            return failed("QuickJS timeout must be positive");
        }

        try {
            Object value = runtime.evaluate(wrapScript(scriptBody, input, globalJavaScript));
            if (value == null) {
                return successNoCommit();
            }
            return commit(String.valueOf(value));
        } catch (QuickJsTimedOutException e) {
            return timedOut(nonBlankMessage(e, "QuickJS execution timed out"));
        } catch (Throwable e) {
            return failed(nonBlankMessage(e, "QuickJS execution failed"));
        }
    }

    static String wrapScript(String scriptBody) {
        return wrapScript(scriptBody, "");
    }

    static String wrapScript(String scriptBody, String input) {
        return wrapScript(scriptBody, input, "");
    }

    static String wrapScript(String scriptBody, String input, String globalJavaScript) {
        String body = scriptBody != null ? scriptBody : "";
        String global = globalJavaScript != null ? globalJavaScript : "";
        if (global.isBlank()) {
            return "(function(input){\n" + body + "\n})(" + jsonStringLiteral(input) + ")";
        }
        return "(function(){\n"
                + global
                + "\nreturn (function(input){\n"
                + body
                + "\n})(" + jsonStringLiteral(input) + ");\n})()";
    }

    static String selectedInputText(InputConnection inputConnection) {
        if (inputConnection == null) {
            return "";
        }
        try {
            CharSequence selectedText = inputConnection.getSelectedText(0);
            if (selectedText == null || selectedText.length() == 0) {
                return "";
            }
            return selectedText.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    static Object valueFromNativeResult(QuickJsNgBridge.NativeEvalResult result) {
        if (result == null) {
            throw new RuntimeException("QuickJS native bridge returned null");
        }
        if (result.timedOut) {
            String error = result.errorString();
            throw new QuickJsTimedOutException(error != null
                    ? error : "QuickJS execution timed out");
        }
        if (!result.success) {
            String error = result.errorString();
            throw new RuntimeException(error != null ? error : "QuickJS evaluation failed");
        }
        return result.valueString();
    }

    static final class QuickJsNgScriptRuntime implements ScriptRuntime {
        private final long timeoutMs;
        private final GboardTopRowSwipeSettings.JavaScriptRuntimeLimits javaScriptRuntimeLimits;

        QuickJsNgScriptRuntime(long timeoutMs) {
            this(timeoutMs, GboardTopRowSwipeSettings.defaultJavaScriptRuntimeLimits());
        }

        QuickJsNgScriptRuntime(long timeoutMs,
                GboardTopRowSwipeSettings.JavaScriptRuntimeLimits javaScriptRuntimeLimits) {
            this.timeoutMs = timeoutMs;
            this.javaScriptRuntimeLimits = javaScriptRuntimeLimits != null
                    ? javaScriptRuntimeLimits
                    : GboardTopRowSwipeSettings.defaultJavaScriptRuntimeLimits();
        }

        @Override
        public Object evaluate(String wrappedScript) {
            return valueFromNativeResult(QuickJsNgBridge.nativeEvaluate(
                    wrappedScript,
                    timeoutMs,
                    javaScriptRuntimeLimits.memoryLimitBytes,
                    javaScriptRuntimeLimits.maxStackBytes,
                    javaScriptRuntimeLimits.responseBodyLimitBytes));
        }
    }

    static boolean maybeConsumeQuickJsTopRowPress(GboardTarget target,
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles,
            Object gestureDispatcher,
            Object keyMetadata) {
        GboardTopRowSwipeRuntime.QuickJsSlotConfig config =
                GboardTopRowSwipeRuntime.quickJsSlotConfig(keyMetadata);
        return maybeConsumeQuickJsTopRowPress(
                target,
                handles,
                gestureDispatcher,
                config,
                false);
    }

    static boolean maybeConsumeQuickJsTopRowPress(GboardTarget target,
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles,
            Object gestureDispatcher,
            GboardTopRowSwipeRuntime.QuickJsSlotConfig config,
            boolean consumeMissingConfig) {
        if (config == null) {
            if (!consumeMissingConfig) {
                return false;
            }
            if (target != null) {
                target.logTargetError(
                        "QuickJS top-row marker reached dispatch without slot config",
                        null);
            }
            Context context = null;
            try {
                context = handles != null
                        ? handles.extractGestureDispatcherContext(gestureDispatcher) : null;
            } catch (Throwable ignored) {
                context = null;
            }
            showOverwriteToast(context, TOAST_FAILED);
            return true;
        }

        Context context;
        try {
            context = handles.extractGestureDispatcherContext(gestureDispatcher);
        } catch (Throwable throwable) {
            if (target != null) {
                target.logTargetError("Failed to resolve gesture dispatcher context for QuickJS",
                        throwable);
            }
            return true;
        }

        if (config.timeoutMs <= 0) {
            if (target != null) {
                target.logTargetError("QuickJS top-row slot has non-positive timeout: "
                        + config.timeoutMs, null);
            }
            showOverwriteToast(context, TOAST_FAILED);
            return true;
        }

        InputMethodService inputMethodService =
                unwrapInputMethodService(context);
        if (inputMethodService == null) {
            if (target != null) {
                target.logTargetError("InputMethodService unavailable for QuickJS top-row slot",
                        null);
            }
            showOverwriteToast(context, TOAST_FAILED);
            return true;
        }

        QuickJsSlotKey slotKey = new QuickJsSlotKey(config.layoutKind, config.rowIndex);
        if (!tryBeginSlotRun(slotKey)) {
            showOverwriteToast(context, TOAST_ALREADY_RUNNING);
            return true;
        }

        InputConnection launchInputConnection =
                inputMethodService.getCurrentInputConnection();
        String scriptInput = selectedInputText(launchInputConnection);
        GboardTopRowSwipeSettings.JavaScriptRuntimeLimits javaScriptRuntimeLimits =
                GboardTopRowSwipeRuntime.javaScriptRuntimeLimits(context);

        executeSlotScript(target, inputMethodService, launchInputConnection, slotKey,
                config, scriptInput, GboardTopRowSwipeRuntime.globalJavaScript(context),
                javaScriptRuntimeLimits);
        return true;
    }

    static void resetRuntimeStateForTests() {
        synchronized (EXECUTOR_LOCK) {
            quickJsExecutor.shutdownNow();
            quickJsTimeoutExecutor.shutdownNow();
            quickJsExecutor = newExecutor();
            quickJsTimeoutExecutor = newTimeoutExecutor();
        }
        RUNNING_SLOT_KEYS.clear();
        synchronized (TOAST_LOCK) {
            if (currentToastHandle != null) {
                currentToastHandle.cancel();
            }
            currentToastHandle = null;
        }
    }

    static boolean tryBeginSlotRunForTests(QuickJsSlotKey slotKey) {
        return tryBeginSlotRun(slotKey);
    }

    static void finishSlotRunForTests(QuickJsSlotKey slotKey) {
        finishSlotRun(slotKey);
    }

    static void executeSlotScriptForTests(ScriptRuntime runtime,
            QuickJsSlotKey slotKey,
            GboardTopRowSwipeRuntime.QuickJsSlotConfig config,
            String input) {
        executeSlotScript(null, null, null, slotKey, config, input, "", runtime);
    }

    private static void executeSlotScript(GboardTarget target,
            InputMethodService inputMethodService,
            InputConnection launchInputConnection,
            QuickJsSlotKey slotKey,
            GboardTopRowSwipeRuntime.QuickJsSlotConfig config,
            String input) {
        executeSlotScript(target, inputMethodService, launchInputConnection, slotKey,
                config, input, "",
                new QuickJsNgScriptRuntime(config != null
                        ? config.timeoutMs : DEFAULT_TIMEOUT_MS));
    }

    private static void executeSlotScript(GboardTarget target,
            InputMethodService inputMethodService,
            InputConnection launchInputConnection,
            QuickJsSlotKey slotKey,
            GboardTopRowSwipeRuntime.QuickJsSlotConfig config,
            String input,
            String globalJavaScript) {
        executeSlotScript(target, inputMethodService, launchInputConnection, slotKey,
                config, input, globalJavaScript,
                GboardTopRowSwipeSettings.defaultJavaScriptRuntimeLimits());
    }

    static void executeSlotScript(GboardTarget target,
            InputMethodService inputMethodService,
            InputConnection launchInputConnection,
            QuickJsSlotKey slotKey,
            GboardTopRowSwipeRuntime.QuickJsSlotConfig config,
            String input,
            String globalJavaScript,
            GboardTopRowSwipeSettings.JavaScriptRuntimeLimits javaScriptRuntimeLimits) {
        executeSlotScript(target, inputMethodService, launchInputConnection, slotKey,
                config, input, globalJavaScript,
                new QuickJsNgScriptRuntime(config != null
                        ? config.timeoutMs : DEFAULT_TIMEOUT_MS,
                        javaScriptRuntimeLimits));
    }

    private static void executeSlotScript(GboardTarget target,
            InputMethodService inputMethodService,
            InputConnection launchInputConnection,
            QuickJsSlotKey slotKey,
            GboardTopRowSwipeRuntime.QuickJsSlotConfig config,
            String input,
            String globalJavaScript,
            ScriptRuntime runtime) {
        showOverwriteToast(inputMethodService, TOAST_RUNNING);
        if (config == null) {
            finishSlotScript(target, inputMethodService, launchInputConnection, slotKey,
                    failed("QuickJS slot config unavailable"));
            return;
        }
        if (runtime == null) {
            finishSlotScript(target, inputMethodService, launchInputConnection, slotKey,
                    failed("QuickJS runtime unavailable"));
            return;
        }
        if (config.timeoutMs <= 0) {
            finishSlotScript(target, inputMethodService, launchInputConnection, slotKey,
                    failed("QuickJS timeout must be positive"));
            return;
        }

        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Future<?>> workerFutureRef = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> timeoutFutureRef = new AtomicReference<>();
        try {
            Future<?> workerFuture = executor().submit(() -> {
                EvaluationResult result = evaluateScriptOnCurrentThread(
                        runtime,
                        config.scriptSource,
                        input,
                        config.timeoutMs,
                        globalJavaScript);
                ScheduledFuture<?> timeoutFuture = timeoutFutureRef.get();
                if (timeoutFuture != null) {
                    timeoutFuture.cancel(false);
                }
                completeSlotScript(target, inputMethodService, launchInputConnection,
                        slotKey, result, completed);
            });
            workerFutureRef.set(workerFuture);
        } catch (RejectedExecutionException e) {
            finishSlotScript(target, inputMethodService, launchInputConnection, slotKey,
                    failed("QuickJS executor unavailable"));
            return;
        } catch (Throwable e) {
            finishSlotScript(target, inputMethodService, launchInputConnection, slotKey,
                    failed(nonBlankMessage(e, "QuickJS execution failed")));
            return;
        }

        long timeoutDelayMs = config.timeoutMs + FUTURE_TIMEOUT_GRACE_MS;
        try {
            ScheduledFuture<?> timeoutFuture = timeoutExecutor().schedule(() -> {
                if (completed.compareAndSet(false, true)) {
                    cancelFuture(workerFutureRef.get());
                    postToMainThread(() -> finishSlotScript(
                            target,
                            inputMethodService,
                            launchInputConnection,
                            slotKey,
                            timedOut("QuickJS execution timed out")));
                }
            }, timeoutDelayMs, TimeUnit.MILLISECONDS);
            timeoutFutureRef.set(timeoutFuture);
            if (completed.get()) {
                timeoutFuture.cancel(false);
            }
        } catch (RejectedExecutionException e) {
            if (completed.compareAndSet(false, true)) {
                cancelFuture(workerFutureRef.get());
                finishSlotScript(target, inputMethodService, launchInputConnection, slotKey,
                        failed(nonBlankMessage(e, "QuickJS timeout guard unavailable")));
            }
        } catch (Throwable e) {
            if (completed.compareAndSet(false, true)) {
                cancelFuture(workerFutureRef.get());
                finishSlotScript(target, inputMethodService, launchInputConnection, slotKey,
                        failed(nonBlankMessage(e, "QuickJS timeout guard failed")));
            }
        }
    }

    private static void cancelFuture(Future<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    private static void completeSlotScript(GboardTarget target,
            InputMethodService inputMethodService,
            InputConnection launchInputConnection,
            QuickJsSlotKey slotKey,
            EvaluationResult result,
            AtomicBoolean completed) {
        if (completed.compareAndSet(false, true)) {
            postToMainThread(() -> finishSlotScript(
                    target,
                    inputMethodService,
                    launchInputConnection,
                    slotKey,
                    result));
        }
    }

    private static void finishSlotScript(GboardTarget target,
            InputMethodService inputMethodService,
            InputConnection launchInputConnection,
            QuickJsSlotKey slotKey,
            EvaluationResult result) {
        try {
            EvaluationResult safeResult = result != null
                    ? result : failed("QuickJS execution returned no result");
            boolean commitFailed = false;
            if (safeResult.shouldCommit && safeResult.committedText != null) {
                InputConnection currentInputConnection = inputMethodService != null
                        ? inputMethodService.getCurrentInputConnection() : null;
                InputConnection inputConnection = commitConnectionForResult(
                        launchInputConnection,
                        currentInputConnection);
                if (inputConnection == null) {
                    commitFailed = true;
                    if (target != null) {
                        target.logTargetError(commitConnectionUnavailableMessage(
                                launchInputConnection,
                                currentInputConnection), null);
                    }
                } else {
                    try {
                        boolean committed = inputConnection.commitText(
                                safeResult.committedText,
                                1);
                        if (!committed) {
                            commitFailed = true;
                            if (target != null) {
                                target.logTargetError(
                                        "InputConnection rejected QuickJS top-row slot result commit",
                                        null);
                            }
                        }
                    } catch (Throwable throwable) {
                        commitFailed = true;
                        if (target != null) {
                            target.logTargetError(
                                    "Failed to commit QuickJS top-row slot result",
                                    throwable);
                        }
                    }
                }
            }

            if (safeResult.timedOut) {
                if (target != null) {
                    target.logTargetError("QuickJS top-row slot timed out: "
                            + safeResult.errorMessage, null);
                }
            } else if (!safeResult.success || safeResult.errorMessage != null) {
                if (target != null) {
                    target.logTargetError("QuickJS top-row slot execution failed: "
                            + safeResult.errorMessage, null);
                }
            }
            showOverwriteToast(inputMethodService,
                    completionToastMessage(safeResult, commitFailed));
        } finally {
            finishSlotRun(slotKey);
        }
    }

    static String completionToastMessage(EvaluationResult result, boolean commitFailed) {
        if (commitFailed) {
            return TOAST_FAILED;
        }
        EvaluationResult safeResult = result != null
                ? result : failed("QuickJS execution returned no result");
        if (safeResult.timedOut) {
            return TOAST_TIMED_OUT;
        }
        if (!safeResult.success || safeResult.errorMessage != null) {
            return TOAST_FAILED;
        }
        return TOAST_FINISHED;
    }

    static InputConnection commitConnectionForResult(
            InputConnection launchInputConnection,
            InputConnection currentInputConnection) {
        if (launchInputConnection == null || currentInputConnection == null
                || launchInputConnection != currentInputConnection) {
            return null;
        }
        return launchInputConnection;
    }

    private static String commitConnectionUnavailableMessage(
            InputConnection launchInputConnection,
            InputConnection currentInputConnection) {
        if (launchInputConnection == null || currentInputConnection == null) {
            return "InputConnection unavailable for QuickJS top-row slot";
        }
        return "InputConnection changed before QuickJS top-row slot finished";
    }

    private static boolean tryBeginSlotRun(QuickJsSlotKey slotKey) {
        return slotKey != null
                && RUNNING_SLOT_KEYS.putIfAbsent(slotKey, Boolean.TRUE) == null;
    }

    private static void finishSlotRun(QuickJsSlotKey slotKey) {
        if (slotKey != null) {
            RUNNING_SLOT_KEYS.remove(slotKey);
        }
    }

    private static EvaluationResult commit(String committedText) {
        return new EvaluationResult(true, false, true, committedText, null);
    }

    private static EvaluationResult successNoCommit() {
        return new EvaluationResult(true, false, false, null, null);
    }

    private static EvaluationResult failed(String errorMessage) {
        return new EvaluationResult(false, false, false, null, errorMessage);
    }

    private static EvaluationResult timedOut(String errorMessage) {
        return new EvaluationResult(false, true, false, null, errorMessage);
    }

    private static ExecutorService executor() {
        synchronized (EXECUTOR_LOCK) {
            if (quickJsExecutor.isShutdown() || quickJsExecutor.isTerminated()) {
                quickJsExecutor = newExecutor();
            }
            return quickJsExecutor;
        }
    }

    private static ScheduledExecutorService timeoutExecutor() {
        synchronized (EXECUTOR_LOCK) {
            if (quickJsTimeoutExecutor.isShutdown()
                    || quickJsTimeoutExecutor.isTerminated()) {
                quickJsTimeoutExecutor = newTimeoutExecutor();
            }
            return quickJsTimeoutExecutor;
        }
    }

    private static ExecutorService newExecutor() {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable,
                    "gboardpatches-gboard-quickjs-" + THREAD_COUNT.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                0,
                QUICK_JS_EXECUTOR_MAX_THREADS,
                QUICK_JS_EXECUTOR_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static ScheduledExecutorService newTimeoutExecutor() {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable,
                    "gboardpatches-gboard-quickjs-timeout-" + THREAD_COUNT.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadScheduledExecutor(factory);
    }

    private static String nonBlankMessage(Throwable throwable, String fallback) {
        if (throwable == null || throwable.getMessage() == null
                || throwable.getMessage().isBlank()) {
            return fallback;
        }
        return throwable.getMessage();
    }

    private static String jsonStringLiteral(String value) {
        String safeValue = value != null ? value : "";
        StringBuilder builder = new StringBuilder(safeValue.length() + 2);
        builder.append('"');
        for (int index = 0; index < safeValue.length(); index++) {
            char ch = safeValue.charAt(index);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20 || ch == '\u2028' || ch == '\u2029') {
                        appendUnicodeEscape(builder, ch);
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        builder.append('"');
        return builder.toString();
    }

    private static void appendUnicodeEscape(StringBuilder builder, char ch) {
        builder.append("\\u");
        String hex = Integer.toHexString(ch);
        for (int index = hex.length(); index < 4; index++) {
            builder.append('0');
        }
        builder.append(hex);
    }

    private static void showOverwriteToast(Context context, String message) {
        if (context == null || message == null) {
            return;
        }
        postToMainThread(() -> showOverwriteToastNow(new AndroidToastPresenter(),
                context,
                message));
    }

    static void showOverwriteToastForTests(ToastPresenter presenter, String message) {
        showOverwriteToastNow(presenter, null, message);
    }

    private static void showOverwriteToastNow(ToastPresenter presenter, Object context,
            String message) {
        if (presenter == null || message == null) {
            return;
        }
        synchronized (TOAST_LOCK) {
            if (currentToastHandle != null) {
                currentToastHandle.cancel();
                currentToastHandle = null;
            }
            try {
                currentToastHandle = presenter.make(context, message);
                if (currentToastHandle != null) {
                    currentToastHandle.show();
                }
            } catch (Throwable ignored) {
                currentToastHandle = null;
            }
        }
    }

    private static final class AndroidToastPresenter implements ToastPresenter {
        @Override
        public ToastHandle make(Object rawContext, String message) {
            if (!(rawContext instanceof Context context)) {
                return null;
            }
            ToastHandle imeOverlay = makeImeOverlayToast(context, message);
            if (imeOverlay != null) {
                return imeOverlay;
            }
            return makeSystemToast(context, message);
        }
    }

    private static ToastHandle makeImeOverlayToast(Context context, String message) {
        try {
            InputMethodService inputMethodService =
                    unwrapInputMethodService(context);
            if (inputMethodService == null || inputMethodService.getWindow() == null
                    || inputMethodService.getWindow().getWindow() == null) {
                return null;
            }
            View decorView = inputMethodService.getWindow().getWindow().getDecorView();
            if (!(decorView instanceof FrameLayout host)) {
                return null;
            }
            Context viewContext = decorView.getContext() != null
                    ? decorView.getContext() : context;
            TextView toastView = new TextView(viewContext);
            toastView.setTag(TOAST_OVERLAY_TAG);
            toastView.setText(message);
            toastView.setTextColor(Color.WHITE);
            toastView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            toastView.setGravity(Gravity.CENTER);
            int horizontalPadding = dp(viewContext, 16);
            int verticalPadding = dp(viewContext, 9);
            toastView.setPadding(horizontalPadding, verticalPadding,
                    horizontalPadding, verticalPadding);
            toastView.setBackground(toastBackground(dp(viewContext, 18)));
            toastView.setElevation(dp(viewContext, 8));

            return new ImeOverlayToastHandle(
                    host,
                    toastView,
                    buildImeOverlayLayoutParams(viewContext));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ToastHandle makeSystemToast(Context context, String message) {
        try {
            Toast toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
            try {
                toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0,
                        dp(context, 72));
            } catch (Throwable ignored) {
            }
            return new SystemToastHandle(toast);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static FrameLayout.LayoutParams buildImeOverlayLayoutParams(Context context) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = dp(context, TOAST_OVERLAY_BOTTOM_MARGIN_DP);
        return params;
    }

    static FrameLayout.LayoutParams buildImeOverlayLayoutParamsForTests() {
        return buildImeOverlayLayoutParams(null);
    }

    private static GradientDrawable toastBackground(int cornerRadiusPx) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xDD222222);
        background.setCornerRadius(cornerRadiusPx);
        return background;
    }

    private static int dp(Context context, int value) {
        float density = context != null && context.getResources() != null
                ? context.getResources().getDisplayMetrics().density : 1f;
        return Math.round(value * density);
    }

    private static final class ImeOverlayToastHandle implements ToastHandle {
        private final FrameLayout host;
        private final TextView toastView;
        private final FrameLayout.LayoutParams layoutParams;
        private final Handler handler;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private Runnable removal;

        ImeOverlayToastHandle(FrameLayout host, TextView toastView,
                FrameLayout.LayoutParams layoutParams) {
            this.host = host;
            this.toastView = toastView;
            this.layoutParams = layoutParams;
            this.handler = mainHandler();
        }

        @Override
        public void cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            if (removal != null) {
                handler.removeCallbacks(removal);
            }
            try {
                if (toastView.getParent() instanceof ViewGroup parent) {
                    parent.removeView(toastView);
                }
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void show() {
            if (cancelled.get()) {
                return;
            }
            removeExistingImeOverlay(host);
            host.addView(toastView, layoutParams);
            removal = this::cancel;
            handler.postDelayed(removal, TOAST_OVERLAY_DURATION_MS);
        }
    }

    private static final class SystemToastHandle implements ToastHandle {
        private final Toast toast;

        SystemToastHandle(Toast toast) {
            this.toast = toast;
        }

        @Override
        public void cancel() {
            if (toast != null) {
                toast.cancel();
            }
        }

        @Override
        public void show() {
            if (toast != null) {
                toast.show();
            }
        }
    }

    private static void removeExistingImeOverlay(FrameLayout host) {
        if (host == null) {
            return;
        }
        for (int index = host.getChildCount() - 1; index >= 0; index--) {
            View child = host.getChildAt(index);
            if (child != null && TOAST_OVERLAY_TAG.equals(child.getTag())) {
                host.removeViewAt(index);
            }
        }
    }

    private static Handler mainHandler() {
        Looper mainLooper = Looper.getMainLooper();
        return new Handler(mainLooper != null ? mainLooper : Looper.myLooper());
    }

    private static void postToMainThread(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        try {
            Looper mainLooper = Looper.getMainLooper();
            if (mainLooper == null || Looper.myLooper() == mainLooper) {
                runnable.run();
                return;
            }
            mainHandler().post(runnable);
        } catch (Throwable ignored) {
            runnable.run();
        }
    }

    private static InputMethodService unwrapInputMethodService(Context context) {
        Context current = context;
        int depth = 0;
        while (current != null && depth < 12) {
            if (current instanceof InputMethodService inputMethodService) {
                return inputMethodService;
            }
            if (!(current instanceof ContextWrapper wrapper)) {
                return null;
            }
            Context baseContext = wrapper.getBaseContext();
            if (baseContext == current) {
                return null;
            }
            current = baseContext;
            depth++;
        }
        return null;
    }
}

