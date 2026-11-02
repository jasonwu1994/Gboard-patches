package dev.jason.gboardpatches.extension.ocr;

import android.app.Application;
import android.content.Context;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unused")
public final class GboardOcrRuntime {
    private static final String TAG = "GboardPatches";
    private static final String THIN_CREATOR_CLASS_NAME =
            "com.google.android.gms.vision.text.mlkit.TextRecognizerCreator";
    private static final AtomicInteger FAILURE_LOG_COUNT = new AtomicInteger();

    private static volatile Context applicationContext;
    private static volatile Field moduleRequestNameField;
    private static volatile Field contextField;
    private static volatile Field configField;
    private static volatile Field recognizerField;
    private static volatile Method mlKitContextSingletonMethod;
    private static volatile Method mlKitContextAccessorMethod;
    private static volatile Method moduleAvailabilityLookupMethod;
    private static volatile Method moduleLoaderFactoryMethod;
    private static volatile Method moduleLoaderCreateMethod;
    private static volatile Field remoteModuleLoaderPolicyField;
    private static volatile Method parcelCreateMethod;
    private static volatile Method parcelTransactMethod;
    private static volatile Method recognizerParcelCreateMethod;
    private static volatile Method recognizerReleaseMethod;
    private static volatile Method writeInterfaceMethod;
    private static volatile Method writeParcelableMethod;
    private static volatile Constructor<?> contextWrapperConstructor;
    private static volatile Constructor<?> textRecognizerCreatorConstructor;
    private static volatile Constructor<?> textRecognizerConstructor;
    private static volatile Constructor<?> payloadConstructor;
    private static volatile GboardOcrEngine engineOverrideForTests;
    private static volatile Boolean thickAvailabilityOverrideForTests;

    private GboardOcrRuntime() {
    }

    public static Object applyFlagValue(String flagName, Object stockResult) {
        try {
            if (("enable_ocr".equals(flagName) || "config_ocr".equals(flagName))
                    && stockResult instanceof Boolean) {
                return Boolean.TRUE;
            }
            if ("enabled_ocr_language_tags".equals(flagName)
                    && stockResult instanceof String) {
                return "*";
            }
        } catch (Throwable failure) {
            logFailure("failed to apply OCR flag value", failure);
        }
        return stockResult;
    }

    public static String applyConfigLabel(String stockResult) {
        return selectedString(stockResult, selectedEngine().getConfigLabel());
    }

    public static String applyLanguageHint(String stockResult) {
        return selectedString(stockResult, selectedEngine().getLanguageHint());
    }

    public static String applyLoggingLibrary(String stockResult) {
        try {
            GboardOcrEngine engine = selectedEngine();
            return engine.getLoggingLibraryName(isSelectedThickClientAvailable());
        } catch (Throwable failure) {
            logFailure("failed to select OCR logging library", failure);
            return stockResult;
        }
    }

    public static String applyOptionalModuleName(String stockResult) {
        return selectedString(stockResult, selectedEngine().getOptionalModuleName());
    }

    public static String applyModuleId(String stockResult) {
        try {
            GboardOcrEngine engine = selectedEngine();
            return engine.getModuleId(isSelectedThickClientAvailable());
        } catch (Throwable failure) {
            logFailure("failed to select OCR module id", failure);
            return stockResult;
        }
    }

    public static boolean applyModuleAvailability(boolean stockResult) {
        try {
            return isSelectedThickClientAvailable();
        } catch (Throwable failure) {
            logFailure("failed to resolve OCR module availability", failure);
            return stockResult;
        }
    }

    public static Object applyOptionalModuleRequest(Object stockResult) {
        try {
            GboardOcrEngine engine = selectedEngine();
            if (!engine.usesPayloadAwareThinInit()) {
                return stockResult;
            }
            if (stockResult instanceof Object[] && ((Object[]) stockResult).length == 0) {
                return stockResult;
            }
            String[] moduleNames = selectedOptionalModuleNames(engine);
            if (requestArrayMatches(stockResult, moduleNames)) {
                return stockResult;
            }
            return buildModuleRequestArray(
                    moduleNames,
                    stockResult,
                    GboardOcrRuntime.class.getClassLoader());
        } catch (Throwable failure) {
            logFailure("failed to build OCR optional module request", failure);
            return stockResult;
        }
    }

    public static void afterThinInit(Object delegateReceiver) {
        try {
            if (delegateReceiver == null) {
                return;
            }
            ClassLoader classLoader = delegateReceiver.getClass().getClassLoader();
            if (classLoader == null) {
                return;
            }
            ensureParcelHelpersResolved(classLoader);
            replaceThinRecognizerIfNeeded(delegateReceiver);
        } catch (Throwable failure) {
            logFailure("failed to replace OCR thin recognizer", failure);
        }
    }

    static GboardOcrEngine selectedEngine() {
        GboardOcrEngine override = engineOverrideForTests;
        if (override != null) {
            return override;
        }
        try {
            Context context = resolveContext();
            return context == null ? GboardOcrEngine.LATIN : GboardOcrSettings.readEngine(context);
        } catch (Throwable failure) {
            logFailure("failed to read OCR engine setting", failure);
            return GboardOcrEngine.LATIN;
        }
    }

    static String[] selectedOptionalModuleNames(GboardOcrEngine engine) {
        if (!engine.usesPayloadAwareThinInit()) {
            return engine.getRequestFeatureNames();
        }
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        names.add("vision.ocr");
        names.addAll(Arrays.asList(engine.getRequestFeatureNames()));
        return names.toArray(new String[0]);
    }

    static Object buildModuleRequestArray(String[] moduleNames, Object currentResult,
            ClassLoader fallbackClassLoader) throws Throwable {
        Class<?> componentType = resolveModuleRequestComponentType(
                currentResult,
                fallbackClassLoader);
        if (componentType == null) {
            return currentResult;
        }
        Constructor<?> constructor = componentType.getDeclaredConstructor(
                String.class, int.class, long.class, boolean.class);
        constructor.setAccessible(true);
        Object array = Array.newInstance(componentType, moduleNames.length);
        for (int index = 0; index < moduleNames.length; index++) {
            Array.set(array, index, constructor.newInstance(
                    moduleNames[index], Integer.valueOf(-1), Long.valueOf(1L), Boolean.FALSE));
        }
        return array;
    }

    static boolean requestArrayMatches(Object result, String[] expectedModuleNames) {
        if (!(result instanceof Object[] requests)
                || requests.length != expectedModuleNames.length) {
            return false;
        }
        for (int index = 0; index < requests.length; index++) {
            if (!expectedModuleNames[index].equals(readModuleRequestName(requests[index]))) {
                return false;
            }
        }
        return true;
    }

    static boolean shouldUsePayloadAwareThinCreator(Object recognizerConfig) {
        GboardOcrEngine engine = selectedEngine();
        return recognizerConfig != null
                && engine.usesPayloadAwareThinInit()
                && engine.getLanguageHint().equals(invokeString(recognizerConfig, "c"))
                && engine.getThinModuleId().equals(invokeString(recognizerConfig, "f"))
                && !invokeBoolean(recognizerConfig, "g");
    }

    static ThinPayloadSpec buildSelectedThinPayloadSpec() {
        GboardOcrEngine engine = selectedEngine();
        return new ThinPayloadSpec(
                engine.getConfigLabel(),
                engine.getOptionalModuleName(),
                true,
                engine.getLanguageOption(),
                engine.getLanguageHint());
    }

    static boolean replaceThinRecognizerIfNeeded(Object delegateReceiver) throws Throwable {
        Object recognizerConfig = readFieldValue(delegateReceiver, "b", configField);
        if (!shouldUsePayloadAwareThinCreator(recognizerConfig)) {
            return false;
        }
        configField = fieldFromReceiver(delegateReceiver, "b", configField);

        Object contextValue = readFieldValue(delegateReceiver, "a", contextField);
        if (!(contextValue instanceof Context context)) {
            return false;
        }
        contextField = fieldFromReceiver(delegateReceiver, "a", contextField);

        Object recognizer = createPayloadAwareThinRecognizer(
                delegateReceiver.getClass().getClassLoader(),
                context,
                buildSelectedThinPayloadSpec(),
                selectedEngine().getThinModuleId());
        if (recognizer == null) {
            return false;
        }

        Field field = fieldFromReceiver(delegateReceiver, "f", recognizerField);
        if (field == null) {
            releaseRecognizerBestEffort(recognizer);
            return false;
        }
        recognizerField = field;
        try {
            Object previous = field.get(delegateReceiver);
            if (previous != null) {
                Method release = delegateReceiver.getClass().getDeclaredMethod("b");
                release.setAccessible(true);
                release.invoke(delegateReceiver);
            }
            field.set(delegateReceiver, recognizer);
            return true;
        } catch (Throwable failure) {
            releaseRecognizerBestEffort(recognizer);
            throw failure;
        }
    }

    private static String selectedString(String stockResult, String selectedValue) {
        try {
            return selectedValue == null ? stockResult : selectedValue;
        } catch (Throwable failure) {
            logFailure("failed to select OCR string value", failure);
            return stockResult;
        }
    }

    private static boolean isSelectedThickClientAvailable() throws Throwable {
        Boolean override = thickAvailabilityOverrideForTests;
        if (override != null) {
            return override.booleanValue();
        }
        Context context = resolveMlKitContext();
        return context != null
                && moduleVersion(context, selectedEngine().getThickModuleId()) > 0;
    }

    private static Context resolveMlKitContext() throws Throwable {
        ClassLoader classLoader = GboardOcrRuntime.class.getClassLoader();
        if (classLoader == null) {
            return null;
        }
        Method singletonMethod = mlKitContextSingletonMethod;
        Method contextMethod = mlKitContextAccessorMethod;
        if (!isResolvedForClassLoader(singletonMethod, classLoader)
                || !isResolvedForClassLoader(contextMethod, classLoader)) {
            Class<?> mlKitContextClass = Class.forName("zlw", false, classLoader);
            singletonMethod = mlKitContextClass.getDeclaredMethod("b");
            singletonMethod.setAccessible(true);
            contextMethod = mlKitContextClass.getDeclaredMethod("a");
            contextMethod.setAccessible(true);
            mlKitContextSingletonMethod = singletonMethod;
            mlKitContextAccessorMethod = contextMethod;
        }
        Object contextValue = contextMethod.invoke(singletonMethod.invoke(null));
        return contextValue instanceof Context ? (Context) contextValue : null;
    }

    private static int moduleVersion(Context context, String moduleId) throws Throwable {
        ClassLoader classLoader = GboardOcrRuntime.class.getClassLoader();
        if (classLoader == null || context == null || moduleId == null) {
            return 0;
        }
        Method lookupMethod = moduleAvailabilityLookupMethod;
        if (!isResolvedForClassLoader(lookupMethod, classLoader)) {
            Class<?> moduleAvailabilityClass = Class.forName("lpv", false, classLoader);
            lookupMethod = moduleAvailabilityClass.getDeclaredMethod(
                    "a", Context.class, String.class);
            lookupMethod.setAccessible(true);
            moduleAvailabilityLookupMethod = lookupMethod;
        }
        Object availability = lookupMethod.invoke(null, context, moduleId);
        return availability instanceof Number ? ((Number) availability).intValue() : 0;
    }

    private static Object createPayloadAwareThinRecognizer(ClassLoader classLoader,
            Context context, ThinPayloadSpec payloadSpec, String moduleId) throws Throwable {
        if (classLoader == null || context == null || payloadSpec == null || moduleId == null) {
            return null;
        }
        Object moduleLoader = createRemoteModuleLoader(classLoader, context, moduleId);
        if (moduleLoader == null) {
            return null;
        }
        IBinder creatorBinder = (IBinder) moduleLoaderCreateMethod.invoke(
                moduleLoader,
                THIN_CREATOR_CLASS_NAME);
        if (creatorBinder == null) {
            return null;
        }

        if (!isResolvedForClassLoader(textRecognizerCreatorConstructor, classLoader)) {
            Class<?> creatorClass = Class.forName("aaic", false, classLoader);
            Constructor<?> constructor = creatorClass.getDeclaredConstructor(IBinder.class);
            constructor.setAccessible(true);
            textRecognizerCreatorConstructor = constructor;
        }
        Object creator = textRecognizerCreatorConstructor.newInstance(creatorBinder);
        Object payload = createThinPayload(classLoader, payloadSpec);
        Object wrappedContext = wrapContext(classLoader, context);
        Parcel request = (Parcel) parcelCreateMethod.invoke(creator);
        writeInterfaceMethod.invoke(null, request, wrappedContext);
        writeParcelableMethod.invoke(null, request, payload);
        Parcel reply = (Parcel) parcelTransactMethod.invoke(
                creator,
                Integer.valueOf(2),
                request);
        try {
            IBinder recognizerBinder = reply.readStrongBinder();
            if (recognizerBinder == null) {
                return null;
            }
            if (!isResolvedForClassLoader(textRecognizerConstructor, classLoader)) {
                Class<?> recognizerClass = Class.forName("aaib", false, classLoader);
                Constructor<?> constructor = recognizerClass.getDeclaredConstructor(IBinder.class);
                constructor.setAccessible(true);
                textRecognizerConstructor = constructor;
            }
            return textRecognizerConstructor.newInstance(recognizerBinder);
        } finally {
            reply.recycle();
        }
    }

    private static Object createRemoteModuleLoader(ClassLoader classLoader, Context context,
            String moduleId) throws Throwable {
        Class<?> moduleLoaderClass = Class.forName("lpv", false, classLoader);
        if (!isResolvedForClassLoader(moduleLoaderFactoryMethod, classLoader)) {
            Class<?> versionPolicyClass = Class.forName("lpu", false, classLoader);
            Method method = moduleLoaderClass.getDeclaredMethod(
                    "d", Context.class, versionPolicyClass, String.class);
            method.setAccessible(true);
            moduleLoaderFactoryMethod = method;
        }
        if (remoteModuleLoaderPolicyField == null
                || remoteModuleLoaderPolicyField.getDeclaringClass().getClassLoader()
                != classLoader) {
            Field field = moduleLoaderClass.getDeclaredField("b");
            field.setAccessible(true);
            remoteModuleLoaderPolicyField = field;
        }
        if (!isResolvedForClassLoader(moduleLoaderCreateMethod, classLoader)) {
            Method method = moduleLoaderFactoryMethod.getReturnType()
                    .getDeclaredMethod("c", String.class);
            method.setAccessible(true);
            moduleLoaderCreateMethod = method;
        }
        Object versionPolicy = remoteModuleLoaderPolicyField.get(null);
        return moduleLoaderFactoryMethod.invoke(null, context, versionPolicy, moduleId);
    }

    private static Object wrapContext(ClassLoader classLoader, Context context) throws Throwable {
        if (!isResolvedForClassLoader(contextWrapperConstructor, classLoader)) {
            Class<?> contextWrapperClass = Class.forName("lpi", false, classLoader);
            Constructor<?> constructor = contextWrapperClass.getDeclaredConstructor(Object.class);
            constructor.setAccessible(true);
            contextWrapperConstructor = constructor;
        }
        return contextWrapperConstructor.newInstance(context);
    }

    private static Object createThinPayload(ClassLoader classLoader, ThinPayloadSpec payloadSpec)
            throws Throwable {
        if (!isResolvedForClassLoader(payloadConstructor, classLoader)) {
            Class<?> payloadClass = Class.forName("aaih", false, classLoader);
            Constructor<?> constructor = payloadClass.getDeclaredConstructor(
                    String.class,
                    String.class,
                    String.class,
                    boolean.class,
                    int.class,
                    String.class,
                    boolean.class);
            constructor.setAccessible(true);
            payloadConstructor = constructor;
        }
        return payloadConstructor.newInstance(
                payloadSpec.configLabel,
                payloadSpec.optionalModule,
                null,
                Boolean.valueOf(payloadSpec.enableOptionalModule),
                Integer.valueOf(payloadSpec.languageOption),
                payloadSpec.languageHint,
                Boolean.FALSE);
    }

    private static void ensureParcelHelpersResolved(ClassLoader classLoader) throws Throwable {
        if (isResolvedForClassLoader(parcelCreateMethod, classLoader)
                && isResolvedForClassLoader(parcelTransactMethod, classLoader)
                && isResolvedForClassLoader(recognizerParcelCreateMethod, classLoader)
                && isResolvedForClassLoader(recognizerReleaseMethod, classLoader)
                && isResolvedForClassLoader(writeInterfaceMethod, classLoader)
                && isResolvedForClassLoader(writeParcelableMethod, classLoader)) {
            return;
        }
        Class<?> creatorWrapperClass = Class.forName("aaic", false, classLoader);
        Class<?> recognizerWrapperClass = Class.forName("aaib", false, classLoader);
        Class<?> parcelHelperClass = Class.forName("emv", false, classLoader);
        parcelCreateMethod = findMethodInHierarchy(creatorWrapperClass, "a");
        parcelTransactMethod = findMethodInHierarchy(
                creatorWrapperClass, "z", int.class, Parcel.class);
        recognizerParcelCreateMethod = findMethodInHierarchy(recognizerWrapperClass, "a");
        recognizerReleaseMethod = findMethodInHierarchy(
                recognizerWrapperClass, "C", int.class, Parcel.class);
        writeInterfaceMethod = parcelHelperClass.getDeclaredMethod(
                "d", Parcel.class, android.os.IInterface.class);
        writeInterfaceMethod.setAccessible(true);
        writeParcelableMethod = parcelHelperClass.getDeclaredMethod(
                "c", Parcel.class, android.os.Parcelable.class);
        writeParcelableMethod.setAccessible(true);
    }

    private static void releaseRecognizerBestEffort(Object recognizer) {
        if (recognizer == null) {
            return;
        }
        try {
            Method create = recognizerParcelCreateMethod;
            Method release = recognizerReleaseMethod;
            if (create == null || release == null
                    || !create.getDeclaringClass().isInstance(recognizer)) {
                return;
            }
            Parcel request = (Parcel) create.invoke(recognizer);
            release.invoke(recognizer, Integer.valueOf(2), request);
        } catch (Throwable ignored) {
            // The formal zqb#b() cleanup is best effort as well.
        }
    }

    private static Class<?> resolveModuleRequestComponentType(Object currentResult,
            ClassLoader fallbackClassLoader) throws ClassNotFoundException {
        if (currentResult instanceof Object[] requests) {
            Class<?> componentType = currentResult.getClass().getComponentType();
            if (componentType != null && componentType != Object.class) {
                return componentType;
            }
            if (requests.length > 0 && requests[0] != null) {
                return requests[0].getClass();
            }
        }
        return fallbackClassLoader == null
                ? null
                : Class.forName("lfy", false, fallbackClassLoader);
    }

    private static String readModuleRequestName(Object request) {
        if (request == null) {
            return null;
        }
        try {
            Field field = moduleRequestNameField;
            if (field == null || !field.getDeclaringClass().isInstance(request)) {
                field = request.getClass().getDeclaredField("a");
                field.setAccessible(true);
                moduleRequestNameField = field;
            }
            Object value = field.get(request);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String invokeString(Object receiver, String methodName) {
        try {
            Method method = receiver.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            Object value = method.invoke(receiver);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean invokeBoolean(Object receiver, String methodName) {
        try {
            Method method = receiver.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            Object value = method.invoke(receiver);
            return value instanceof Boolean && ((Boolean) value).booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object readFieldValue(Object receiver, String fieldName, Field cachedField) {
        try {
            Field field = fieldFromReceiver(receiver, fieldName, cachedField);
            return field == null ? null : field.get(receiver);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field fieldFromReceiver(Object receiver, String fieldName, Field cachedField) {
        if (receiver == null) {
            return null;
        }
        if (cachedField != null && cachedField.getDeclaringClass().isInstance(receiver)) {
            return cachedField;
        }
        try {
            Field field = receiver.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findMethodInHierarchy(Class<?> type, String methodName,
            Class<?>... parameterTypes) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(methodName);
    }

    private static boolean isResolvedForClassLoader(Method method, ClassLoader classLoader) {
        return method != null && method.getDeclaringClass().getClassLoader() == classLoader;
    }

    private static boolean isResolvedForClassLoader(Constructor<?> constructor,
            ClassLoader classLoader) {
        return constructor != null
                && constructor.getDeclaringClass().getClassLoader() == classLoader;
    }

    private static Context resolveContext() {
        Context cached = applicationContext;
        if (cached != null) {
            return cached;
        }
        Context resolved = reflectedApplicationContext(
                "android.app.ActivityThread", "currentApplication");
        if (resolved == null) {
            resolved = reflectedApplicationContext(
                    "android.app.AppGlobals", "getInitialApplication");
        }
        applicationContext = resolved;
        return resolved;
    }

    private static Context reflectedApplicationContext(String className, String methodName) {
        try {
            Method method = Class.forName(className).getDeclaredMethod(methodName);
            method.setAccessible(true);
            Object value = method.invoke(null);
            if (!(value instanceof Application application)) {
                return null;
            }
            Context context = application.getApplicationContext();
            return context == null ? application : context;
        } catch (Throwable failure) {
            logFailure("failed to resolve application context", failure);
            return null;
        }
    }

    private static void logFailure(String message, Throwable failure) {
        if (FAILURE_LOG_COUNT.getAndIncrement() >= 8) {
            return;
        }
        try {
            Log.w(TAG, "[gboard-ocr] " + message, failure);
        } catch (Throwable ignored) {
            // Logging must not affect Gboard.
        }
    }

    static void setEngineOverrideForTests(GboardOcrEngine engine) {
        engineOverrideForTests = engine;
    }

    static void setThickAvailabilityOverrideForTests(Boolean available) {
        thickAvailabilityOverrideForTests = available;
    }

    static void resetForTests() {
        engineOverrideForTests = null;
        thickAvailabilityOverrideForTests = null;
        applicationContext = null;
        moduleRequestNameField = null;
        FAILURE_LOG_COUNT.set(0);
    }

    static final class ThinPayloadSpec {
        final String configLabel;
        final String optionalModule;
        final boolean enableOptionalModule;
        final int languageOption;
        final String languageHint;

        ThinPayloadSpec(String configLabel, String optionalModule,
                boolean enableOptionalModule, int languageOption, String languageHint) {
            this.configLabel = configLabel;
            this.optionalModule = optionalModule;
            this.enableOptionalModule = enableOptionalModule;
            this.languageOption = languageOption;
            this.languageHint = languageHint;
        }
    }
}
