package dev.jason.gboardpatches.extension.ocr;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class GboardOcrModuleInstaller {
    interface Callback {
        void onSuccess();

        void onFailure(Throwable failure);
    }

    interface AvailabilityCallback {
        void onResult(boolean installed);

        void onFailure(Throwable failure);
    }

    interface TaskCompletion {
        void onComplete(Object task) throws Throwable;
    }

    private GboardOcrModuleInstaller() {
    }

    static void request(Context context, GboardOcrEngine engine, Callback callback) {
        if (context == null || engine == null || callback == null) {
            notifyFailure(context, callback,
                    new IllegalArgumentException("Missing OCR module install argument"));
            return;
        }
        try {
            ClassLoader classLoader = requireClassLoader();
            List<Object> optionalApis = createOptionalApis(engine, classLoader);
            Object client = createClient(context, classLoader);
            Class<?> statusListenerClass = Class.forName("lmw", false, classLoader);
            AtomicBoolean completed = new AtomicBoolean();
            Object statusListener = Proxy.newProxyInstance(
                    classLoader,
                    new Class<?>[]{statusListenerClass},
                    (proxy, method, arguments) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return handleObjectMethod(proxy, method, arguments);
                        }
                        if ("a".equals(method.getName()) && arguments != null
                                && arguments.length == 1) {
                            handleInstallStatus(context, client, proxy, arguments[0],
                                    completed, callback);
                        }
                        return null;
                    });
            Object task = createInstallTask(client, optionalApis, statusListener, classLoader);
            attachTaskCompletion(task, completedTask -> {
                try {
                    if (!invokeTaskBoolean(completedTask, "g")) {
                        finishInstallFailure(context, client, statusListener, completed, callback,
                                taskFailureOrDefault(completedTask,
                                        "OCR module install request failed"));
                        return;
                    }
                    Object response = invokeTaskResult(completedTask);
                    int sessionId = readIntField(response, "a");
                    boolean alreadyInstalled = readBooleanField(response, "b");
                    if (sessionId == 0 || alreadyInstalled) {
                        finishInstallSuccess(
                                context, client, statusListener, completed, callback);
                    }
                } catch (Throwable failure) {
                    finishInstallFailure(context, client, statusListener, completed, callback,
                            unwrap(failure));
                }
            });
        } catch (Throwable failure) {
            notifyFailure(context, callback, unwrap(failure));
        }
    }

    static void checkAvailability(Context context, GboardOcrEngine engine,
            AvailabilityCallback callback) {
        if (context == null || engine == null || callback == null) {
            notifyAvailabilityFailure(context, callback,
                    new IllegalArgumentException("Missing OCR module availability argument"));
            return;
        }
        try {
            ClassLoader classLoader = requireClassLoader();
            List<Object> optionalApis = createOptionalApis(engine, classLoader);
            Object client = createClient(context, classLoader);
            Object task = createAvailabilityTask(client, optionalApis, classLoader);
            if (task == null) {
                notifyOnMain(context, () -> callback.onResult(true));
                return;
            }
            attachTaskCompletion(task, completedTask -> {
                try {
                    if (!invokeTaskBoolean(completedTask, "g")) {
                        notifyAvailabilityFailure(context, callback,
                                taskFailureOrDefault(completedTask,
                                        "OCR module availability request failed"));
                        return;
                    }
                    Object response = invokeTaskResult(completedTask);
                    boolean installed = readBooleanField(response, "a");
                    notifyOnMain(context, () -> callback.onResult(installed));
                } catch (Throwable failure) {
                    notifyAvailabilityFailure(context, callback, unwrap(failure));
                }
            });
        } catch (Throwable failure) {
            notifyAvailabilityFailure(context, callback, unwrap(failure));
        }
    }

    private static ClassLoader requireClassLoader() {
        ClassLoader classLoader = GboardOcrModuleInstaller.class.getClassLoader();
        if (classLoader == null) {
            throw new IllegalStateException("Gboard class loader unavailable");
        }
        return classLoader;
    }

    private static List<Object> createOptionalApis(GboardOcrEngine engine,
            ClassLoader classLoader) throws Throwable {
        String[] featureNames = GboardOcrRuntime.selectedOptionalModuleNames(engine);
        Object requestArray = GboardOcrRuntime.buildModuleRequestArray(
                featureNames,
                null,
                classLoader);
        if (requestArray == null || !requestArray.getClass().isArray()
                || Array.getLength(requestArray) == 0) {
            throw new IllegalStateException("Could not build OCR module request array");
        }

        Class<?> optionalApiClass = Class.forName("zmb", false, classLoader);
        Constructor<?> optionalApiConstructor = optionalApiClass.getDeclaredConstructor(
                requestArray.getClass());
        optionalApiConstructor.setAccessible(true);
        Object optionalApi = optionalApiConstructor.newInstance(requestArray);
        List<Object> optionalApis = new ArrayList<Object>();
        optionalApis.add(optionalApi);
        return optionalApis;
    }

    private static Object createClient(Context context, ClassLoader classLoader) throws Throwable {
        Class<?> moduleInstallClientClass = Class.forName("lnn", false, classLoader);
        Constructor<?> clientConstructor = moduleInstallClientClass.getDeclaredConstructor(
                Context.class);
        clientConstructor.setAccessible(true);
        Context applicationContext = context.getApplicationContext();
        return clientConstructor.newInstance(
                applicationContext == null ? context : applicationContext);
    }

    private static Object createInstallTask(Object client, List<Object> optionalApis,
            Object statusListener, ClassLoader classLoader) throws Throwable {
        Class<?> requestClass = Class.forName("srk", false, classLoader);
        Class<?> statusListenerClass = Class.forName("lmw", false, classLoader);
        Constructor<?> requestConstructor = requestClass.getDeclaredConstructor(
                List.class,
                statusListenerClass);
        requestConstructor.setAccessible(true);
        Object installRequest = requestConstructor.newInstance(optionalApis, statusListener);

        Class<?> moduleInstallClientClass = Class.forName("lnn", false, classLoader);
        Method installMethod = moduleInstallClientClass.getDeclaredMethod("c", requestClass);
        installMethod.setAccessible(true);
        Object task = installMethod.invoke(client, installRequest);
        if (task == null) {
            throw new IllegalStateException("ModuleInstall returned no task");
        }
        return task;
    }

    private static Object createAvailabilityTask(Object client, List<Object> optionalApis,
            ClassLoader classLoader) throws Throwable {
        Class<?> optionalApiInterface = Class.forName("lhq", false, classLoader);
        Object optionalApiArray = Array.newInstance(optionalApiInterface, optionalApis.size());
        for (int index = 0; index < optionalApis.size(); index++) {
            Array.set(optionalApiArray, index, optionalApis.get(index));
        }
        Method availabilityMethod = client.getClass().getDeclaredMethod(
                "a", optionalApiArray.getClass());
        availabilityMethod.setAccessible(true);
        Object task = availabilityMethod.invoke(client, optionalApiArray);
        if (task == null) {
            throw new IllegalStateException("ModuleInstall availability returned no task");
        }
        return task;
    }

    private static void attachTaskCompletion(Object task, TaskCompletion completion)
            throws Throwable {
        ClassLoader classLoader = task.getClass().getClassLoader();
        if (classLoader == null) {
            classLoader = GboardOcrModuleInstaller.class.getClassLoader();
        }
        if (classLoader == null) {
            throw new IllegalStateException("Task class loader unavailable");
        }

        Class<?> listenerClass = Class.forName("med", false, classLoader);
        registerCompletionListener(task, listenerClass, completion);
    }

    static Method resolveCompletionRegistration(Class<?> taskClass, Class<?> listenerClass)
            throws NoSuchMethodException {
        if (taskClass == null || listenerClass == null || !listenerClass.isInterface()) {
            throw new NoSuchMethodException("Expected task class and completion listener interface");
        }
        Method method = taskClass.getMethod("l", listenerClass);
        if (method.getReturnType() != void.class || Modifier.isStatic(method.getModifiers())
                || !Modifier.isPublic(method.getModifiers())) {
            throw new NoSuchMethodException("Expected public instance void task completion registration");
        }
        method.setAccessible(true);
        return method;
    }

    static void registerCompletionListener(Object task, Class<?> listenerClass, TaskCompletion completion)
            throws Throwable {
        ClassLoader classLoader = task.getClass().getClassLoader();
        if (classLoader == null) classLoader = GboardOcrModuleInstaller.class.getClassLoader();
        if (classLoader == null) throw new IllegalStateException("Task class loader unavailable");
        Method addCompleteListener = resolveCompletionRegistration(task.getClass(), listenerClass);
        InvocationHandler handler = (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, arguments);
            }
            if ("a".equals(method.getName()) && arguments != null
                    && arguments.length == 1) {
                try {
                    completion.onComplete(arguments[0]);
                } catch (Throwable ignored) {
                    // The completion handler reports failures through its callback.
                }
            }
            return null;
        };
        Object listener = Proxy.newProxyInstance(
                classLoader,
                new Class<?>[]{listenerClass},
                handler);
        addCompleteListener.invoke(task, listener);
    }

    private static void handleInstallStatus(Context context, Object client,
            Object statusListener, Object status, AtomicBoolean completed, Callback callback) {
        try {
            int state = readIntField(status, "b");
            int errorCode = readIntField(status, "e");
            if (state == 4) {
                finishInstallSuccess(context, client, statusListener, completed, callback);
            } else if (state == 3) {
                finishInstallFailure(context, client, statusListener, completed, callback,
                        new IllegalStateException(
                                "OCR module download canceled with error code " + errorCode));
            } else if (state == 5) {
                finishInstallFailure(context, client, statusListener, completed, callback,
                        new IllegalStateException(
                                "OCR module download failed with error code " + errorCode));
            }
        } catch (Throwable failure) {
            finishInstallFailure(context, client, statusListener, completed, callback,
                    unwrap(failure));
        }
    }

    private static void finishInstallSuccess(Context context, Object client,
            Object statusListener, AtomicBoolean completed, Callback callback) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        unregisterStatusListener(client, statusListener);
        notifyOnMain(context, callback::onSuccess);
    }

    private static void finishInstallFailure(Context context, Object client,
            Object statusListener, AtomicBoolean completed, Callback callback,
            Throwable failure) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        unregisterStatusListener(client, statusListener);
        notifyFailure(context, callback, failure);
    }

    private static void unregisterStatusListener(Object client, Object statusListener) {
        try {
            Method unregisterMethod = client.getClass().getDeclaredMethod(
                    "b", statusListener.getClass().getInterfaces()[0]);
            unregisterMethod.setAccessible(true);
            unregisterMethod.invoke(client, statusListener);
        } catch (Throwable ignored) {
            // Terminal callback delivery is more important than listener cleanup.
        }
    }

    private static boolean invokeTaskBoolean(Object task, String methodName) throws Throwable {
        Method method = task.getClass().getMethod(methodName);
        method.setAccessible(true);
        Object result = method.invoke(task);
        return result instanceof Boolean && ((Boolean) result).booleanValue();
    }

    private static Object invokeTaskResult(Object task) throws Throwable {
        Method method = task.getClass().getMethod("e");
        method.setAccessible(true);
        return method.invoke(task);
    }

    private static Throwable taskFailureOrDefault(Object task, String message) {
        Throwable failure = invokeTaskFailure(task);
        return failure == null ? new IllegalStateException(message) : failure;
    }

    private static boolean readBooleanField(Object value, String fieldName) {
        try {
            Field field = value.getClass().getField(fieldName);
            field.setAccessible(true);
            return field.getBoolean(value);
        } catch (Throwable failure) {
            throw new IllegalStateException("Could not read " + fieldName, unwrap(failure));
        }
    }

    private static int readIntField(Object value, String fieldName) {
        try {
            Field field = value.getClass().getField(fieldName);
            field.setAccessible(true);
            return field.getInt(value);
        } catch (Throwable failure) {
            throw new IllegalStateException("Could not read " + fieldName, unwrap(failure));
        }
    }

    private static void setField(Object value, String fieldName, Object fieldValue)
            throws Throwable {
        Field field = value.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(value, fieldValue);
    }

    private static void setIntField(Object value, String fieldName, int fieldValue)
            throws Throwable {
        Field field = value.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(value, fieldValue);
    }

    private static Throwable invokeTaskFailure(Object task) {
        try {
            Method method = task.getClass().getMethod("d");
            method.setAccessible(true);
            Object result = method.invoke(task);
            return result instanceof Throwable ? (Throwable) result : null;
        } catch (Throwable failure) {
            return unwrap(failure);
        }
    }

    private static Object handleObjectMethod(Object proxy, Method method, Object[] arguments) {
        if ("toString".equals(method.getName())) {
            return "GboardOcrModuleInstallListener";
        }
        if ("hashCode".equals(method.getName())) {
            return Integer.valueOf(System.identityHashCode(proxy));
        }
        if ("equals".equals(method.getName())) {
            return Boolean.valueOf(arguments != null && arguments.length == 1
                    && proxy == arguments[0]);
        }
        return null;
    }

    private static void notifyFailure(Context context, Callback callback, Throwable failure) {
        if (callback != null) {
            notifyOnMain(context, () -> callback.onFailure(failure));
        }
    }

    private static void notifyAvailabilityFailure(Context context,
            AvailabilityCallback callback, Throwable failure) {
        if (callback != null) {
            notifyOnMain(context, () -> callback.onFailure(failure));
        }
    }

    private static void notifyOnMain(Context context, Runnable action) {
        Looper mainLooper;
        try {
            mainLooper = Looper.getMainLooper();
        } catch (Throwable ignored) {
            runSafely(action);
            return;
        }
        if (mainLooper == null || Looper.myLooper() == mainLooper) {
            runSafely(action);
        } else {
            new Handler(mainLooper).post(() -> runSafely(action));
        }
    }

    private static void runSafely(Runnable action) {
        try {
            action.run();
        } catch (Throwable ignored) {
            // Module installation callbacks must not affect Gboard.
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof InvocationTargetException
                && ((InvocationTargetException) current).getCause() != null) {
            current = ((InvocationTargetException) current).getCause();
        }
        return current;
    }
}

interface GboardOcrModuleInstallRequester {
    void request(Context context, GboardOcrEngine engine,
            GboardOcrModuleInstaller.Callback callback);
}

interface GboardOcrModuleAvailabilityChecker {
    void check(Context context, GboardOcrEngine engine,
            GboardOcrModuleInstaller.AvailabilityCallback callback);
}
