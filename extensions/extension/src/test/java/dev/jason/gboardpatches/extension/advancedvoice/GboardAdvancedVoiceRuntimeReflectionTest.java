package dev.jason.gboardpatches.extension.advancedvoice;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class GboardAdvancedVoiceRuntimeReflectionTest {
    private static final String HOOKS_CLASS =
            "dev.jason.gboardpatches.extension.advancedvoice."
                    + "GboardAdvancedVoiceRuntime";

    @Test
    public void readsFlagNamesPerReceiverClassWithoutCacheContamination()
            throws Exception {
        Method readFlagName = hookMethod("readFlagName", Object.class);

        ReceiverA receiverA = new ReceiverA("enable_nga");
        ReceiverB receiverB = new ReceiverB(
                "enable_advanced_features_in_consolidated_sd_stack");

        Assert.assertEquals("enable_nga", readFlagName.invoke(null, receiverA));
        Assert.assertEquals("enable_advanced_features_in_consolidated_sd_stack",
                readFlagName.invoke(null, receiverB));
        Assert.assertEquals("enable_nga", readFlagName.invoke(null, receiverA));
        Assert.assertNull(readFlagName.invoke(null, new WrongTypeReceiver(7)));
        Assert.assertNull(readFlagName.invoke(null, new Object[]{null}));
    }

    @Test
    public void readsTheStockMemoizedSupplierInsteadOfOwningAnotherCache()
            throws Exception {
        Method readMemoizedNativeReadiness = hookMethod(
                "readMemoizedNativeReadiness", Field.class, Method.class);
        Field supplierField = SupplierHolder.class.getDeclaredField("b");
        supplierField.setAccessible(true);
        Method supplierGet = MemoizedBooleanSupplier.class.getDeclaredMethod("get");
        supplierGet.setAccessible(true);

        Assert.assertEquals(Boolean.TRUE,
                readMemoizedNativeReadiness.invoke(
                        null, supplierField, supplierGet));
        Assert.assertEquals(Boolean.TRUE,
                readMemoizedNativeReadiness.invoke(
                        null, supplierField, supplierGet));
        Assert.assertEquals(1, SupplierHolder.b.evaluationCount.get());
    }

    @Test
    public void changesOnlyTheExactZhTwFormatterDisableArgument() throws Exception {
        Method maybeEnableFormatter = hookMethod(
                "maybeEnableExactZhTwFormatter",
                Object[].class, Field.class, Field.class, Field.class);
        Field configurationData = FakeOrationContext.class.getDeclaredField("c");
        configurationData.setAccessible(true);
        Field disableAdvancedFeatures = FakeConfiguration.class.getDeclaredField("H");
        disableAdvancedFeatures.setAccessible(true);
        Field defaultConfiguration = FakeConfiguration.class.getDeclaredField("a");
        defaultConfiguration.setAccessible(true);

        Object[] zhTwArgs = formatterArgs(
                Locale.forLanguageTag("zh-TW"),
                new FakeOrationContext(new FakeConfiguration(false)),
                Boolean.TRUE);
        Assert.assertEquals(Boolean.TRUE, maybeEnableFormatter.invoke(
                null, zhTwArgs, configurationData, defaultConfiguration,
                disableAdvancedFeatures));
        Assert.assertEquals(Boolean.FALSE, zhTwArgs[4]);

        Object[] stockDisabledArgs = formatterArgs(
                Locale.forLanguageTag("zh-TW"),
                new FakeOrationContext(new FakeConfiguration(true)),
                Boolean.TRUE);
        Assert.assertEquals(Boolean.FALSE, maybeEnableFormatter.invoke(
                null, stockDisabledArgs, configurationData, defaultConfiguration,
                disableAdvancedFeatures));
        Assert.assertEquals(Boolean.TRUE, stockDisabledArgs[4]);

        Object[] zhCnArgs = formatterArgs(
                Locale.forLanguageTag("zh-CN"),
                new FakeOrationContext(new FakeConfiguration(false)),
                Boolean.TRUE);
        Assert.assertEquals(Boolean.FALSE, maybeEnableFormatter.invoke(
                null, zhCnArgs, configurationData, defaultConfiguration,
                disableAdvancedFeatures));
        Assert.assertEquals(Boolean.TRUE, zhCnArgs[4]);

        Assert.assertEquals(Boolean.FALSE, maybeEnableFormatter.invoke(
                null, new Object[0], configurationData, defaultConfiguration,
                disableAdvancedFeatures));
    }

    @Test
    public void restoresOfficialVoiceRowsOnlyForTheExactInitialNgaState()
            throws Exception {
        Method maybeRestore = hookMethod(
                "maybeRestoreInitialVoiceSettings",
                boolean.class,
                Throwable.class,
                Object.class,
                Object.class,
                Object.class,
                Object.class,
                Method.class,
                Method.class,
                Method.class,
                Method.class);
        Method preferenceFactory = FakePreferenceFactory.class.getDeclaredMethod(
                "N", FakeContext.class);
        Method preferenceRead = FakePreferences.class.getDeclaredMethod(
                "x", int.class, boolean.class);
        Method setAvailable = FakePreferenceController.class.getDeclaredMethod(
                "h", int.class, boolean.class);
        Method setChecked = FakePreferenceController.class.getDeclaredMethod(
                "c", int.class, boolean.class);
        preferenceFactory.setAccessible(true);
        preferenceRead.setAccessible(true);
        setAvailable.setAccessible(true);
        setChecked.setAccessible(true);

        Object initialState = new Object();
        FakeContext context = new FakeContext();
        FakePreferences preferences = new FakePreferences();
        preferences.values.put(0x7f140971, Boolean.TRUE);
        preferences.values.put(0x7f140972, Boolean.FALSE);
        FakePreferenceFactory.preferences = preferences;
        FakePreferenceController controller = new FakePreferenceController();

        Assert.assertEquals(Boolean.TRUE, maybeRestore.invoke(
                null,
                true,
                null,
                initialState,
                initialState,
                context,
                controller,
                preferenceFactory,
                preferenceRead,
                setAvailable,
                setChecked));
        Assert.assertEquals(Boolean.TRUE, controller.available.get(0x7f140971));
        Assert.assertEquals(Boolean.TRUE, controller.available.get(0x7f140972));
        Assert.assertEquals(Boolean.TRUE, controller.checked.get(0x7f140971));
        Assert.assertEquals(Boolean.FALSE, controller.checked.get(0x7f140972));

        assertInitialVoiceSettingsUnchanged(
                maybeRestore,
                false,
                null,
                initialState,
                initialState,
                context,
                preferenceFactory,
                preferenceRead,
                setAvailable,
                setChecked);
        assertInitialVoiceSettingsUnchanged(
                maybeRestore,
                true,
                null,
                new Object(),
                initialState,
                context,
                preferenceFactory,
                preferenceRead,
                setAvailable,
                setChecked);
        assertInitialVoiceSettingsUnchanged(
                maybeRestore,
                true,
                new IllegalStateException("stock initializer failed"),
                initialState,
                initialState,
                context,
                preferenceFactory,
                preferenceRead,
                setAvailable,
                setChecked);
    }

    @Test
    public void setterFailuresRollbackOfficialVoiceRowsToInitialState()
            throws Exception {
        Method maybeRestore = hookMethod(
                "maybeRestoreInitialVoiceSettings",
                boolean.class,
                Throwable.class,
                Object.class,
                Object.class,
                Object.class,
                Object.class,
                Method.class,
                Method.class,
                Method.class,
                Method.class);
        Method preferenceFactory = FakePreferenceFactory.class.getDeclaredMethod(
                "N", FakeContext.class);
        Method preferenceRead = FakePreferences.class.getDeclaredMethod(
                "x", int.class, boolean.class);
        Method setAvailable = FakePreferenceController.class.getDeclaredMethod(
                "h", int.class, boolean.class);
        Method setChecked = FakePreferenceController.class.getDeclaredMethod(
                "c", int.class, boolean.class);
        preferenceFactory.setAccessible(true);
        preferenceRead.setAccessible(true);
        setAvailable.setAccessible(true);
        setChecked.setAccessible(true);

        Object initialState = new Object();
        FakeContext context = new FakeContext();
        FakePreferences preferences = new FakePreferences();
        preferences.values.put(0x7f140971, Boolean.TRUE);
        preferences.values.put(0x7f140972, Boolean.TRUE);
        FakePreferenceFactory.preferences = preferences;

        for (int failingCall = 1; failingCall <= 4; failingCall++) {
            FakePreferenceController controller =
                    new FakePreferenceController(failingCall);

            Assert.assertEquals(Boolean.FALSE, maybeRestore.invoke(
                    null,
                    true,
                    null,
                    initialState,
                    initialState,
                    context,
                    controller,
                    preferenceFactory,
                    preferenceRead,
                    setAvailable,
                    setChecked));
            Assert.assertEquals(Boolean.FALSE, controller.available.get(0x7f140971));
            Assert.assertEquals(Boolean.FALSE, controller.available.get(0x7f140972));
            Assert.assertEquals(Boolean.FALSE, controller.checked.get(0x7f140971));
            Assert.assertEquals(Boolean.FALSE, controller.checked.get(0x7f140972));
        }
    }

    @Test
    public void rollbackSetterFailuresNeverEscapeToTheCaller() throws Exception {
        Method maybeRestore = hookMethod(
                "maybeRestoreInitialVoiceSettings",
                boolean.class,
                Throwable.class,
                Object.class,
                Object.class,
                Object.class,
                Object.class,
                Method.class,
                Method.class,
                Method.class,
                Method.class);
        Method preferenceFactory = FakePreferenceFactory.class.getDeclaredMethod(
                "N", FakeContext.class);
        Method preferenceRead = FakePreferences.class.getDeclaredMethod(
                "x", int.class, boolean.class);
        Method setAvailable = FakePreferenceController.class.getDeclaredMethod(
                "h", int.class, boolean.class);
        Method setChecked = FakePreferenceController.class.getDeclaredMethod(
                "c", int.class, boolean.class);
        preferenceFactory.setAccessible(true);
        preferenceRead.setAccessible(true);
        setAvailable.setAccessible(true);
        setChecked.setAccessible(true);

        Object initialState = new Object();
        FakeContext context = new FakeContext();
        FakePreferences preferences = new FakePreferences();
        preferences.values.put(0x7f140971, Boolean.TRUE);
        preferences.values.put(0x7f140972, Boolean.TRUE);
        FakePreferenceFactory.preferences = preferences;
        FakePreferenceController controller = new FakePreferenceController(1, 2);

        Assert.assertEquals(Boolean.FALSE, maybeRestore.invoke(
                null,
                true,
                null,
                initialState,
                initialState,
                context,
                controller,
                preferenceFactory,
                preferenceRead,
                setAvailable,
                setChecked));
    }

    private static void assertInitialVoiceSettingsUnchanged(
            Method maybeRestore,
            boolean enabled,
            Throwable initializerFailure,
            Object currentState,
            Object initialState,
            Object context,
            Method preferenceFactory,
            Method preferenceRead,
            Method setAvailable,
            Method setChecked) throws Exception {
        FakePreferenceController controller = new FakePreferenceController();
        Assert.assertEquals(Boolean.FALSE, maybeRestore.invoke(
                null,
                enabled,
                initializerFailure,
                currentState,
                initialState,
                context,
                controller,
                preferenceFactory,
                preferenceRead,
                setAvailable,
                setChecked));
        Assert.assertTrue(controller.available.isEmpty());
        Assert.assertTrue(controller.checked.isEmpty());
    }

    @Test
    public void requestsStockZhTwMddOnlyOnceWithTheProviderScope() throws Exception {
        Method requestZhTwMdd = hookMethod(
                "maybeRequestExactZhTwMdd",
                boolean.class,
                Throwable.class,
                AtomicBoolean.class,
                Object.class,
                Field.class,
                Constructor.class,
                Method.class);
        FakeMddProvider provider = new FakeMddProvider();
        Field scopeField = FakeMddProvider.class.getDeclaredField("d");
        scopeField.setAccessible(true);
        Constructor<?> requestConstructor = FakeMddRequest.class.getDeclaredConstructor(
                FakeMddProvider.class, Locale.class, FakeContinuation.class);
        requestConstructor.setAccessible(true);
        Method launchMethod = FakeCoroutineLauncher.class.getDeclaredMethod(
                "aq",
                FakeMddScope.class,
                FakeCoroutineContext.class,
                FakeMddRequest.class,
                int.class);
        launchMethod.setAccessible(true);
        AtomicBoolean guard = new AtomicBoolean(false);
        FakeCoroutineLauncher.reset();

        Assert.assertEquals(Boolean.TRUE, requestZhTwMdd.invoke(
                null,
                true,
                null,
                guard,
                provider,
                scopeField,
                requestConstructor,
                launchMethod));
        Assert.assertEquals(Boolean.FALSE, requestZhTwMdd.invoke(
                null,
                true,
                null,
                guard,
                provider,
                scopeField,
                requestConstructor,
                launchMethod));

        Assert.assertEquals(1, FakeCoroutineLauncher.launchCount.get());
        Assert.assertSame(provider.d, FakeCoroutineLauncher.scope);
        Assert.assertNull(FakeCoroutineLauncher.context);
        Assert.assertEquals(3, FakeCoroutineLauncher.mode);
        Assert.assertSame(provider, FakeCoroutineLauncher.request.provider);
        Assert.assertEquals("zh-TW",
                FakeCoroutineLauncher.request.locale.toLanguageTag());
        Assert.assertNull(FakeCoroutineLauncher.request.continuation);

        AtomicBoolean disabledGuard = new AtomicBoolean(false);
        Assert.assertEquals(Boolean.FALSE, requestZhTwMdd.invoke(
                null,
                false,
                null,
                disabledGuard,
                provider,
                scopeField,
                requestConstructor,
                launchMethod));
        Assert.assertFalse(disabledGuard.get());
        Assert.assertEquals(1, FakeCoroutineLauncher.launchCount.get());

        AtomicBoolean failedConstructorGuard = new AtomicBoolean(false);
        Assert.assertEquals(Boolean.FALSE, requestZhTwMdd.invoke(
                null,
                true,
                new IllegalStateException("qzh constructor failed"),
                failedConstructorGuard,
                provider,
                scopeField,
                requestConstructor,
                launchMethod));
        Assert.assertFalse(failedConstructorGuard.get());
        Assert.assertEquals(1, FakeCoroutineLauncher.launchCount.get());
    }

    @Test
    public void synchronousMddLaunchFailureLeavesTheGuardRetryable() throws Exception {
        Method requestZhTwMdd = hookMethod(
                "maybeRequestExactZhTwMdd",
                boolean.class,
                Throwable.class,
                AtomicBoolean.class,
                Object.class,
                Field.class,
                Constructor.class,
                Method.class);
        FakeMddProvider provider = new FakeMddProvider();
        Field scopeField = FakeMddProvider.class.getDeclaredField("d");
        scopeField.setAccessible(true);
        Constructor<?> requestConstructor = FakeMddRequest.class.getDeclaredConstructor(
                FakeMddProvider.class, Locale.class, FakeContinuation.class);
        requestConstructor.setAccessible(true);
        Method failingLaunchMethod = FakeFailingCoroutineLauncher.class.getDeclaredMethod(
                "aq",
                FakeMddScope.class,
                FakeCoroutineContext.class,
                FakeMddRequest.class,
                int.class);
        failingLaunchMethod.setAccessible(true);
        AtomicBoolean guard = new AtomicBoolean(false);

        try {
            requestZhTwMdd.invoke(
                    null,
                    true,
                    null,
                    guard,
                    provider,
                    scopeField,
                    requestConstructor,
                    failingLaunchMethod);
            Assert.fail("expected stock coroutine launch failure");
        } catch (InvocationTargetException expected) {
            Assert.assertTrue(expected.getCause() instanceof InvocationTargetException);
            Assert.assertTrue(expected.getCause().getCause()
                    instanceof IllegalStateException);
        }
        Assert.assertFalse(guard.get());

        Method workingLaunchMethod = FakeCoroutineLauncher.class.getDeclaredMethod(
                "aq",
                FakeMddScope.class,
                FakeCoroutineContext.class,
                FakeMddRequest.class,
                int.class);
        workingLaunchMethod.setAccessible(true);
        FakeCoroutineLauncher.reset();
        Assert.assertEquals(Boolean.TRUE, requestZhTwMdd.invoke(
                null,
                true,
                null,
                guard,
                provider,
                scopeField,
                requestConstructor,
                workingLaunchMethod));
        Assert.assertTrue(guard.get());
        Assert.assertEquals(1, FakeCoroutineLauncher.launchCount.get());
    }

    private static Object[] formatterArgs(
            Locale locale,
            FakeOrationContext context,
            Object formatterDisabled) {
        return new Object[]{
                locale,
                context,
                null,
                null,
                formatterDisabled,
                null,
                null,
                null,
                null
        };
    }

    private static Method hookMethod(String name, Class<?>... parameterTypes)
            throws Exception {
        Class<?> hooks;
        try {
            hooks = Class.forName(HOOKS_CLASS);
        } catch (ClassNotFoundException missing) {
            Assert.fail("missing 18.0.3 Advanced Voice runtime class");
            return null;
        }
        Method method = hooks.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static final class ReceiverA {
        private final String a;

        private ReceiverA(String flagName) {
            a = flagName;
        }
    }

    private static final class ReceiverB {
        private final String a;

        private ReceiverB(String flagName) {
            a = flagName;
        }
    }

    private static final class WrongTypeReceiver {
        private final int a;

        private WrongTypeReceiver(int value) {
            a = value;
        }
    }

    private static final class FakeConfiguration {
        private static final FakeConfiguration a = new FakeConfiguration(false);
        private final boolean H;

        private FakeConfiguration(boolean disableAdvancedFeatures) {
            H = disableAdvancedFeatures;
        }
    }

    private static final class FakeOrationContext {
        private final FakeConfiguration c;

        private FakeOrationContext(FakeConfiguration configuration) {
            c = configuration;
        }
    }

    private static final class FakeContext {
    }

    private static final class FakePreferenceFactory {
        private static FakePreferences preferences;

        private static FakePreferences N(FakeContext context) {
            return preferences;
        }
    }

    private static final class FakePreferences {
        private final Map<Integer, Boolean> values = new HashMap<>();

        private boolean x(int key, boolean defaultValue) {
            Boolean value = values.get(Integer.valueOf(key));
            return value != null ? value.booleanValue() : defaultValue;
        }
    }

    private static final class FakePreferenceController {
        private final Map<Integer, Boolean> available = new HashMap<>();
        private final Map<Integer, Boolean> checked = new HashMap<>();
        private final Set<Integer> failingCalls = new HashSet<>();
        private int setterCallCount;

        private FakePreferenceController(int... failingCalls) {
            for (int failingCall : failingCalls) {
                this.failingCalls.add(Integer.valueOf(failingCall));
            }
        }

        private void h(int key, boolean value) {
            failIfRequested();
            available.put(Integer.valueOf(key), Boolean.valueOf(value));
        }

        private void c(int key, boolean value) {
            failIfRequested();
            checked.put(Integer.valueOf(key), Boolean.valueOf(value));
        }

        private void failIfRequested() {
            setterCallCount++;
            if (failingCalls.remove(Integer.valueOf(setterCallCount))) {
                throw new IllegalStateException("setter failed at call " + setterCallCount);
            }
        }
    }

    private static final class SupplierHolder {
        private static final MemoizedBooleanSupplier b =
                new MemoizedBooleanSupplier();
    }

    private static final class MemoizedBooleanSupplier {
        private final AtomicInteger evaluationCount = new AtomicInteger();
        private Boolean cached;

        private synchronized Boolean get() {
            if (cached == null) {
                evaluationCount.incrementAndGet();
                cached = Boolean.TRUE;
            }
            return cached;
        }
    }

    private static final class FakeMddProvider {
        private final FakeMddScope d = new FakeMddScope();
    }

    private static final class FakeMddScope {
    }

    private static final class FakeCoroutineContext {
    }

    private static final class FakeContinuation {
    }

    private static final class FakeMddRequest {
        private final FakeMddProvider provider;
        private final Locale locale;
        private final FakeContinuation continuation;

        private FakeMddRequest(
                FakeMddProvider provider,
                Locale locale,
                FakeContinuation continuation) {
            this.provider = provider;
            this.locale = locale;
            this.continuation = continuation;
        }
    }

    private static final class FakeCoroutineLauncher {
        private static final AtomicInteger launchCount = new AtomicInteger();
        private static FakeMddScope scope;
        private static FakeCoroutineContext context;
        private static FakeMddRequest request;
        private static int mode;

        private static Object aq(
                FakeMddScope launchScope,
                FakeCoroutineContext launchContext,
                FakeMddRequest launchRequest,
                int launchMode) {
            launchCount.incrementAndGet();
            scope = launchScope;
            context = launchContext;
            request = launchRequest;
            mode = launchMode;
            return new Object();
        }

        private static void reset() {
            launchCount.set(0);
            scope = null;
            context = null;
            request = null;
            mode = 0;
        }
    }

    private static final class FakeFailingCoroutineLauncher {
        private static Object aq(
                FakeMddScope launchScope,
                FakeCoroutineContext launchContext,
                FakeMddRequest launchRequest,
                int launchMode) {
            throw new IllegalStateException("launch failed");
        }
    }
}
